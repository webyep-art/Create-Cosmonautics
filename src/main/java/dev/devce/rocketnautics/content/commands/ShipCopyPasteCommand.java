package dev.devce.rocketnautics.content.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.neoforged.fml.loading.FMLPaths;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class ShipCopyPasteCommand {
    private record ClipboardEntry(CompoundTag tag, ChunkPos sourcePlotPos) {}
    private static final Map<UUID, ClipboardEntry> clipboard = new HashMap<>();
    
    private static final Path STORAGE_PATH = FMLPaths.CONFIGDIR.get().resolve("rocketnautics_ships");

    static {
        try {
            Files.createDirectories(STORAGE_PATH);
        } catch (IOException e) {
            Sable.LOGGER.error("Failed to create ship storage directory", e);
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rn")
            .then(Commands.literal("ship")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("copy")
                    .executes(ShipCopyPasteCommand::copyShip))
                .then(Commands.literal("paste")
                    .executes(ShipCopyPasteCommand::pasteShip))
                .then(Commands.literal("save")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ShipCopyPasteCommand::saveShip)))
                .then(Commands.literal("load")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ShipCopyPasteCommand::loadShip)))
                .then(Commands.literal("list")
                    .executes(ShipCopyPasteCommand::listShips))
                .then(Commands.literal("delete")
                    .executes(ShipCopyPasteCommand::deleteShip))
            )
        );
    }

    private static int copyShip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        
        HitResult hit = player.pick(128.0, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit) {
            ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getContaining(source.getLevel(), blockHit.getBlockPos());
            if (subLevel != null) {
                CompoundTag tag = subLevel.getPlot().save();
                clipboard.put(player.getUUID(), new ClipboardEntry(tag, subLevel.getPlot().plotPos));
                source.sendSuccess(() -> Component.literal("Ship copied to local clipboard."), true);
                return 1;
            }
        }
        source.sendFailure(Component.literal("No ship found in view!"));
        return 0;
    }

    private static int pasteShip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ClipboardEntry entry = clipboard.get(player.getUUID());

        if (entry == null) {
            source.sendFailure(Component.literal("Clipboard is empty!"));
            return 0;
        }

        return spawnShip(source, player, entry);
    }

    private static int saveShip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        
        HitResult hit = player.pick(128.0, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit) {
            ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getContaining(source.getLevel(), blockHit.getBlockPos());
            if (subLevel != null) {
                CompoundTag tag = subLevel.getPlot().save();
                CompoundTag fileTag = new CompoundTag();
                fileTag.put("Data", tag);
                fileTag.putLong("PlotPos", ChunkPos.asLong(subLevel.getPlot().plotPos.x, subLevel.getPlot().plotPos.z));
                
                try {
                    NbtIo.writeCompressed(fileTag, STORAGE_PATH.resolve(name + ".nbt"));
                    source.sendSuccess(() -> Component.literal("Ship saved cross-world as '" + name + "'."), true);
                    return 1;
                } catch (IOException e) {
                    source.sendFailure(Component.literal("Failed to save ship file: " + e.getMessage()));
                    return 0;
                }
            }
        }
        source.sendFailure(Component.literal("No ship found in view!"));
        return 0;
    }

    private static int loadShip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        Path filePath = STORAGE_PATH.resolve(name + ".nbt");

        if (!Files.exists(filePath)) {
            source.sendFailure(Component.literal("Global ship '" + name + "' not found!"));
            return 0;
        }

        try {
            CompoundTag fileTag = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
            CompoundTag data = fileTag.getCompound("Data");
            long plotLong = fileTag.getLong("PlotPos");
            ClipboardEntry entry = new ClipboardEntry(data, new ChunkPos(plotLong));
            return spawnShip(source, player, entry);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to read ship file: " + e.getMessage()));
            return 0;
        }
    }

    private static int spawnShip(CommandSourceStack source, ServerPlayer player, ClipboardEntry entry) {
        HitResult hit = player.pick(128.0, 1.0f, false);
        Vector3d targetPos;
        if (hit.getType() == HitResult.Type.MISS) {
            net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            targetPos = new Vector3d(eyePos.x + look.x * 10.0, eyePos.y + look.y * 10.0, eyePos.z + look.z * 10.0);
        } else {
            targetPos = new Vector3d(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
        }

        ServerSubLevelContainer container = (ServerSubLevelContainer) dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(source.getLevel());
        if (container == null) return 0;
        Pose3d pose = new Pose3d(targetPos.add(0, 0.1, 0), new Quaterniond(), new Vector3d(0), new Vector3d(1));
        ServerSubLevel newShip = (ServerSubLevel) container.allocateNewSubLevel(pose);

        CompoundTag remappedTag = entry.tag.copy();
        remapBlockEntityPositions(remappedTag, entry.sourcePlotPos, newShip.getPlot().plotPos);

        newShip.getPlot().load(remappedTag);
        newShip.updateLastPose();

        source.sendSuccess(() -> Component.literal("Ship spawned!"), true);
        return 1;
    }

    private static int deleteShip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        
        HitResult hit = player.pick(128.0, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit) {
            ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getContaining(source.getLevel(), blockHit.getBlockPos());
            if (subLevel != null) {
                UUID id = subLevel.getUniqueId();
                subLevel.markRemoved();
                source.sendSuccess(() -> Component.literal("Ship " + id.toString().substring(0, 8) + " has been marked for removal."), true);
                return 1;
            }
        }
        source.sendFailure(Component.literal("No ship found in view to delete!"));
        return 0;
    }

    private static int listShips(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try (Stream<Path> stream = Files.list(STORAGE_PATH)) {
            source.sendSuccess(() -> Component.literal("--- [Global Ship Library] ---").withStyle(ChatFormatting.GOLD), false);
            stream.filter(p -> p.toString().endsWith(".nbt"))
                  .forEach(p -> {
                      String name = p.getFileName().toString().replace(".nbt", "");
                      source.sendSuccess(() -> Component.literal("- " + name).withStyle(ChatFormatting.AQUA), false);
                  });
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to list ships: " + e.getMessage()));
        }
        return 1;
    }

    public static void remapBlockEntityPositions(CompoundTag rootTag, ChunkPos sourcePlot, ChunkPos targetPlot) {
        int logSize = rootTag.contains("log_size") ? rootTag.getInt("log_size") : 7;
        int shift = logSize + 4;
        int offsetX = (targetPlot.x - sourcePlot.x) << shift;
        int offsetZ = (targetPlot.z - sourcePlot.z) << shift;

        if (offsetX == 0 && offsetZ == 0) return;
        if (!rootTag.contains("chunks")) return;

        CompoundTag chunks = rootTag.getCompound("chunks");
        for (String key : chunks.getAllKeys()) {
            CompoundTag chunkTag = chunks.getCompound(key);
            if (!chunkTag.contains("block_entities")) continue;

            ListTag beList = chunkTag.getList("block_entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < beList.size(); i++) {
                CompoundTag beTag = beList.getCompound(i);
                if (beTag.contains("x")) beTag.putInt("x", beTag.getInt("x") + offsetX);
                if (beTag.contains("z")) beTag.putInt("z", beTag.getInt("z") + offsetZ);
            }
        }
    }
}
