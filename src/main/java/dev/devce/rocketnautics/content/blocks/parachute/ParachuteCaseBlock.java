package dev.devce.rocketnautics.content.blocks.parachute;

import dev.devce.rocketnautics.registry.RocketBlocks;
import dev.devce.rocketnautics.registry.RocketItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3d;
import java.util.List;
import java.util.ArrayList;
import org.joml.Quaterniond;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.neoforged.fml.loading.FMLPaths;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.SubLevel;

public class ParachuteCaseBlock extends Block implements com.simibubi.create.content.equipment.wrench.IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty HAS_PARACHUTE = BooleanProperty.create("has_parachute");
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;



    public static class ParachuteLink {
        public final UUID parentId;
        public final Vector3d localOffset;
        public final Quaterniond localRotation;
        public final BlockPos casePos;

        public ParachuteLink(UUID parentId, Vector3d localOffset, Quaterniond localRotation, BlockPos casePos) {
            this.parentId = parentId;
            this.localOffset = localOffset;
            this.localRotation = localRotation;
            this.casePos = casePos;
        }
    }

    public static final Map<UUID, ParachuteLink> PARACHUTE_TO_ROCKET = new ConcurrentHashMap<>();
    /** Флаг: данные из SavedData уже загружены в текущей сессии сервера */
    private static volatile boolean dataLoaded = false;

    static {
        SableEventPlatform.INSTANCE.onPhysicsTick((physicsSystem, timeStep) -> {
            ServerLevel level = physicsSystem.getLevel();
            ServerLevel parentWorld = getParentLevel(level);
            SubLevelContainer container = SubLevelContainer.getContainer(parentWorld != null ? parentWorld : level);
            if (container == null) return;

            // Загружаем сохранённые ссылки из SavedData при первом тике сессии
            if (!dataLoaded && parentWorld != null) {
                ParachuteLinkSavedData.loadLinks(parentWorld);
            }

            for (SubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel instanceof ServerSubLevel serverSubLevel) {
                    RigidBodyHandle handle = physicsSystem.getPhysicsHandle(serverSubLevel);
                    if (handle != null) {
                        ParachuteLink link = PARACHUTE_TO_ROCKET.get(serverSubLevel.getUniqueId());
                        if (link != null) {
                            if (serverSubLevel.isRemoved()) {
                                PARACHUTE_TO_ROCKET.remove(serverSubLevel.getUniqueId());
                                ParachuteLinkSavedData.saveLinks(parentWorld);
                                continue;
                            }
                            SubLevel parentSubLevel = link.parentId != null ? container.getSubLevel(link.parentId) : null;
                            if (link.parentId != null && parentSubLevel instanceof ServerSubLevel parentServerSubLevel) {
                                if (parentServerSubLevel.isRemoved()) {
                                    PARACHUTE_TO_ROCKET.remove(serverSubLevel.getUniqueId());
                                    ParachuteLinkSavedData.saveLinks(parentWorld);
                                    continue;
                                }
                                RigidBodyHandle parentHandle = physicsSystem.getPhysicsHandle(parentServerSubLevel);
                                if (parentHandle != null) {
                                    // 1. Аэродинамическое сопротивление (Air Drag) для парашюта
                                    Vector3d parachuteVel = handle.getLinearVelocity(new Vector3d());
                                    double speed = parachuteVel.length();
                                    if (speed > 0.1) {
                                        // Сила сопротивления квадратична скорости
                                        double dragCoeff = 1500.0; // Высокое сопротивление парашюта
                                        Vector3d dragImpulse = new Vector3d(parachuteVel).normalize().negate().mul(speed * speed * dragCoeff * timeStep);
                                        handle.applyLinearImpulse(dragImpulse);
                                    }

                                    // 2. Физическая "веревка" (Soft Distance Constraint)
                                    // Якорь на ракете (позиция кейса)
                                    net.minecraft.world.phys.Vec3 parentAnchorVec = dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(
                                        parentServerSubLevel.getLevel(),
                                        new net.minecraft.world.phys.Vec3(link.casePos.getX() + 0.5, link.casePos.getY() + 0.5, link.casePos.getZ() + 0.5)
                                    );
                                    Vector3d anchorParent = new Vector3d(parentAnchorVec.x, parentAnchorVec.y, parentAnchorVec.z);

                                    // Якорь на парашюте (центр парашюта)
                                    Vector3d anchorPara = serverSubLevel.logicalPose().position();

                                    Vector3d diff = new Vector3d(anchorParent).sub(anchorPara);
                                    double distance = diff.length();
                                    double ropeLength = 10.0; // Газ длина веревки 10 блоков

                                    if (distance > ropeLength) {
                                        // Веревка натянулась
                                        Vector3d dir = new Vector3d(diff).normalize();
                                        
                                        Vector3d parentVel = parentHandle.getLinearVelocity(new Vector3d());
                                        Vector3d relVel = new Vector3d(parentVel).sub(parachuteVel);
                                        // Относительная скорость вдоль веревки
                                        double relVelAlongDir = relVel.dot(dir);
                                        
                                        // Пружина + Демпфер (Закон Гука)
                                        double k = 15000.0; // Жесткость веревки
                                        double c = 2000.0;  // Гашение колебаний
                                        double forceMag = (distance - ropeLength) * k - relVelAlongDir * c;
                                        
                                        if (forceMag > 0) {
                                            Vector3d impulse = new Vector3d(dir).mul(forceMag * timeStep);
                                            // Тянем парашют к ракете
                                            handle.applyLinearImpulse(impulse);
                                            // Тянем ракету к парашюту (физическое торможение ракеты!)
                                            parentHandle.applyLinearImpulse(new Vector3d(impulse).negate());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private static ServerLevel getParentLevel(ServerLevel level) {
        Object lvlObj = level;
        if (lvlObj instanceof ServerSubLevel ssl) {
            if (level.getServer() == null) return level;
            for (ServerLevel sl : level.getServer().getAllLevels()) {
                var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sl);
                if (container != null) {
                    for (var slItem : container.getAllSubLevels()) {
                        if (slItem.getUniqueId().equals(ssl.getUniqueId())) {
                            return sl;
                        }
                    }
                }
            }
        }
        return level;
    }

    /**
     * Сохранение/загрузка PARACHUTE_TO_ROCKET между перезапусками сервера.
     * Без этого стропы зависают в воздухе после перезахода.
     */
    public static class ParachuteLinkSavedData extends net.minecraft.world.level.saveddata.SavedData {
        private static final String DATA_NAME = "rocketnautics_parachute_links";

        private ParachuteLinkSavedData() {}

        private static final net.minecraft.world.level.saveddata.SavedData.Factory<ParachuteLinkSavedData> FACTORY =
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                ParachuteLinkSavedData::new, ParachuteLinkSavedData::load, null);

        /** Загружает данные из диска и заполняет PARACHUTE_TO_ROCKET. */
        public static void loadLinks(ServerLevel overworld) {
            try {
                overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
            } catch (Exception e) {
                dev.ryanhcode.sable.Sable.LOGGER.error("Failed to load parachute links", e);
            }
            dataLoaded = true;
        }

        /** Помечает данные как изменённые — Minecraft сохранит при следующем авто-сохранении. */
        public static void saveLinks(ServerLevel overworld) {
            try {
                overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME).setDirty();
            } catch (Exception e) {
                dev.ryanhcode.sable.Sable.LOGGER.error("Failed to schedule parachute links save", e);
            }
        }

        public static ParachuteLinkSavedData load(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
            ParachuteLinkSavedData data = new ParachuteLinkSavedData();
            net.minecraft.nbt.ListTag list = tag.getList("links", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                net.minecraft.nbt.CompoundTag lt = list.getCompound(i);
                try {
                    UUID paraId  = lt.getUUID("paraId");
                    UUID parentId = lt.hasUUID("parentId") ? lt.getUUID("parentId") : null;
                    Vector3d lo  = v3d(lt, "lo");
                    Quaterniond lr = quat(lt, "lr");
                    BlockPos cp  = new BlockPos(lt.getInt("cx"), lt.getInt("cy"), lt.getInt("cz"));
                    PARACHUTE_TO_ROCKET.put(paraId, new ParachuteLink(parentId, lo, lr, cp));
                } catch (Exception e) {
                    dev.ryanhcode.sable.Sable.LOGGER.error("Failed to restore parachute link entry", e);
                }
            }
            return data;
        }

        @Override
        public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (java.util.Map.Entry<UUID, ParachuteLink> e : PARACHUTE_TO_ROCKET.entrySet()) {
                net.minecraft.nbt.CompoundTag lt = new net.minecraft.nbt.CompoundTag();
                lt.putUUID("paraId", e.getKey());
                ParachuteLink lk = e.getValue();
                if (lk.parentId != null) lt.putUUID("parentId", lk.parentId);
                wv3d(lt, "lo", lk.localOffset); wquat(lt, "lr", lk.localRotation);
                lt.putInt("cx", lk.casePos.getX()); lt.putInt("cy", lk.casePos.getY()); lt.putInt("cz", lk.casePos.getZ());
                list.add(lt);
            }
            tag.put("links", list);
            return tag;
        }

        private static Vector3d v3d(net.minecraft.nbt.CompoundTag t, String p) {
            return new Vector3d(t.getDouble(p+"x"), t.getDouble(p+"y"), t.getDouble(p+"z"));
        }
        private static void wv3d(net.minecraft.nbt.CompoundTag t, String p, Vector3d v) {
            t.putDouble(p+"x", v.x); t.putDouble(p+"y", v.y); t.putDouble(p+"z", v.z);
        }
        private static Quaterniond quat(net.minecraft.nbt.CompoundTag t, String p) {
            return new Quaterniond(t.getDouble(p+"x"), t.getDouble(p+"y"), t.getDouble(p+"z"), t.getDouble(p+"w"));
        }
        private static void wquat(net.minecraft.nbt.CompoundTag t, String p, Quaterniond q) {
            t.putDouble(p+"x", q.x); t.putDouble(p+"y", q.y); t.putDouble(p+"z", q.z); t.putDouble(p+"w", q.w);
        }
    }

    public ParachuteCaseBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(HAS_PARACHUTE, false)
                .setValue(OPEN, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            facing = facing.getOpposite();
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_PARACHUTE, OPEN);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!state.getValue(HAS_PARACHUTE) && stack.is(RocketItems.PARACHUTE_CAPSULE.get())) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(HAS_PARACHUTE, true).setValue(OPEN, false), 3);
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            if (level.hasNeighborSignal(pos) && state.getValue(HAS_PARACHUTE) && !state.getValue(OPEN)) {
                deployParachute(level, pos, state);
            }
        }
    }

    private void deployParachute(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;

        // 1. Mark block as open and take parachute
        level.setBlock(pos, state.setValue(HAS_PARACHUTE, false).setValue(OPEN, true), 3);

        // 2. Play deployment sounds
        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 3.0F, 1.0F);
        level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 2.0F, 0.8F);

        // 3. Spawning cool smoke/cloud particles to simulate ejection/opening
        if (level instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 20; i++) {
                double px = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 2.0;
                double py = pos.getY() + 1.5 + (level.random.nextDouble() - 0.5) * 2.0;
                double pz = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 2.0;
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, 1, 0, 0.1, 0, 0.05);
                serverLevel.sendParticles(ParticleTypes.CLOUD, px, py, pz, 1, 0, 0.05, 0, 0.02);
            }
        }

        // 4. Calculate spawn position
        Direction facing = state.getValue(FACING);
        double spawnOffsetDistance = 10.0; // 10 блоков веревка

        dev.ryanhcode.sable.sublevel.SubLevel ship = (dev.ryanhcode.sable.sublevel.SubLevel) dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos);
        Vector3d targetWorldPos;

        if (ship != null) {
            net.minecraft.world.phys.Vec3 localOffset = new net.minecraft.world.phys.Vec3(
                pos.getX() + 0.5 + facing.getStepX() * spawnOffsetDistance,
                pos.getY() + 0.5 + facing.getStepY() * spawnOffsetDistance,
                pos.getZ() + 0.5 + facing.getStepZ() * spawnOffsetDistance
            );
            net.minecraft.world.phys.Vec3 worldPos = dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, localOffset);
            targetWorldPos = new Vector3d(worldPos.x, worldPos.y, worldPos.z);
        } else {
            targetWorldPos = new Vector3d(
                pos.getX() + 0.5 + facing.getStepX() * spawnOffsetDistance,
                pos.getY() + 0.5 + facing.getStepY() * spawnOffsetDistance,
                pos.getZ() + 0.5 + facing.getStepZ() * spawnOffsetDistance
            );
        }

        // 5. Spawn the parachute ship from global library ("par1") and connect cords
        if (!level.isClientSide) {
            spawnShipAndConnect((ServerLevel) (Object) level, "par1", targetWorldPos, pos);
        }
    }

    private boolean spawnShipAndConnect(ServerLevel level, String name, Vector3d targetPos, BlockPos casePos) {
        Path storagePath = FMLPaths.CONFIGDIR.get().resolve("rocketnautics_ships");
        Path filePath = storagePath.resolve(name + ".nbt");
        
        if (!Files.exists(filePath)) {
            // Try to extract from built-in mod assets
            try (java.io.InputStream is = ParachuteCaseBlock.class.getResourceAsStream("/assets/rocketnautics/ships/" + name + ".nbt")) {
                if (is != null) {
                    Files.createDirectories(storagePath);
                    Files.copy(is, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    dev.ryanhcode.sable.Sable.LOGGER.error("Ship file " + name + ".nbt not found in global library or mod resources!");
                    return false;
                }
            } catch (IOException e) {
                dev.ryanhcode.sable.Sable.LOGGER.error("Failed to copy default ship " + name + " from resources: " + e.getMessage(), e);
                return false;
            }
        }

        try {
            CompoundTag fileTag = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
            CompoundTag data = fileTag.getCompound("Data");
            long plotLong = fileTag.getLong("PlotPos");
            
            ServerLevel parentWorld = getParentLevel(level);
            ServerSubLevelContainer container = (ServerSubLevelContainer) dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(parentWorld);
            if (container == null) return false;
            
            SubLevel parentShip = (SubLevel) dev.ryanhcode.sable.Sable.HELPER.getContaining(level, casePos);
            Quaterniond initialRot = parentShip != null ? new Quaterniond(parentShip.logicalPose().orientation()) : new Quaterniond();
            Pose3d pose = new Pose3d(new Vector3d(targetPos), initialRot, new Vector3d(0), new Vector3d(1));
            ServerSubLevel newShip = (ServerSubLevel) container.allocateNewSubLevel(pose);

            CompoundTag remappedTag = data.copy();
            remapBlockEntityPositions(remappedTag, new ChunkPos(plotLong), newShip.getPlot().plotPos);

            newShip.getPlot().load(remappedTag);
            newShip.updateLastPose();

            // Find all rope connectors in the newly spawned parachute ship from remapped NBT directly!
            List<BlockPos> connectors = new ArrayList<>();
            if (remappedTag.contains("chunks")) {
                CompoundTag chunks = remappedTag.getCompound("chunks");
                for (String key : chunks.getAllKeys()) {
                    CompoundTag chunkTag = chunks.getCompound(key);
                    if (chunkTag.contains("block_entities")) {
                        ListTag beList = chunkTag.getList("block_entities", Tag.TAG_COMPOUND);
                        for (int i = 0; i < beList.size(); i++) {
                            CompoundTag beTag = beList.getCompound(i);
                            if (beTag.contains("id") && beTag.getString("id").equals("simulated:rope_connector")) {
                                if (beTag.contains("x") && beTag.contains("y") && beTag.contains("z")) {
                                    connectors.add(new BlockPos(beTag.getInt("x"), beTag.getInt("y"), beTag.getInt("z")));
                                }
                            }
                        }
                    }
                }
            }

            BlockState state = level.getBlockState(casePos);
            Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.UP;
            double spawnOffsetDistance = 10.0;
            Vector3d targetLocalPos = new Vector3d(
                casePos.getX() + 0.5 + facing.getStepX() * spawnOffsetDistance,
                casePos.getY() + 0.5 + facing.getStepY() * spawnOffsetDistance,
                casePos.getZ() + 0.5 + facing.getStepZ() * spawnOffsetDistance
            );
            
            UUID parentId = parentShip != null ? parentShip.getUniqueId() : null;

            ParachuteLink link = new ParachuteLink(parentId, parentShip != null ? targetLocalPos : new Vector3d(targetPos), new Quaterniond(), casePos);
            PARACHUTE_TO_ROCKET.put(newShip.getUniqueId(), link);
            // Сохраняем на диск сразу — иначе после перезахода стропы зависнут
            ParachuteLinkSavedData.saveLinks(parentWorld);

            return true;
        } catch (IOException e) {
            dev.ryanhcode.sable.Sable.LOGGER.error("Failed to read/spawn/connect ship file: " + e.getMessage(), e);
            return false;
        }
    }

    private void remapBlockEntityPositions(CompoundTag rootTag, ChunkPos sourcePlot, ChunkPos targetPlot) {
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
