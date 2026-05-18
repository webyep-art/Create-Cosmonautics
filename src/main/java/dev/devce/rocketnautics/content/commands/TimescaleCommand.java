package dev.devce.rocketnautics.content.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TimescaleCommand {
    public static final SimpleCommandExceptionType NOT_IN_INSTANCE = new SimpleCommandExceptionType(Component.literal("Player is not in a deep space instance!"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rn")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("timescale")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 1000))
                                .executes(context -> {
                                    int value = IntegerArgumentType.getInteger(context, "value");
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    if (!DeepSpaceData.isDeepSpace(player.level())) {
                                        throw NOT_IN_INSTANCE.create();
                                    }
                                    DeepSpaceInstance instance = DeepSpaceData.getInstance(context.getSource().getServer())
                                            .getInstanceForPos(player.getBlockX(), player.getBlockZ());
                                    if (instance == null) {
                                        throw NOT_IN_INSTANCE.create();
                                    }
                                    instance.getPosition().setTimescale(value);
                                    context.getSource().sendSuccess(() -> Component.literal("Instance timescale set to: " + value), true);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("reset")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    if (!DeepSpaceData.isDeepSpace(player.level())) {
                                        throw NOT_IN_INSTANCE.create();
                                    }
                                    DeepSpaceInstance instance = DeepSpaceData.getInstance(context.getSource().getServer())
                                            .getInstanceForPos(player.getBlockX(), player.getBlockZ());
                                    if (instance == null) {
                                        throw NOT_IN_INSTANCE.create();
                                    }
                                    instance.getPosition().setTimescale(1);
                                    context.getSource().sendSuccess(() -> Component.literal("Instance timescale reset to 1"), true);
                                    return 1;
                                })
                        )
                )
        );
    }
}
