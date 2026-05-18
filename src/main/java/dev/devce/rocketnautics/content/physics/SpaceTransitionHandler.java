

package dev.devce.rocketnautics.content.physics;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.commands.ShipCopyPasteCommand;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.network.DebugLogPayload;
import dev.devce.rocketnautics.network.SeamlessTransitionPayload;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.loading.FMLPaths;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles the seamless transition of ships and players between dimensions (Overworld <-> Space).
 * This includes saving ship data to NBT, managing teleportation, rebuilding ships in the target dimension,
 * and recovering player seating/entities.
 */
@EventBusSubscriber(modid = RocketNautics.MODID)
public class SpaceTransitionHandler {
    
    
    public static final double TRANSITION_SAFE_OFFSET = 1000.0;
    private static final int REBUILD_DELAY_TICKS = 3;
    private static final int SEATING_RECOVERY_TIMEOUT = 30;

    
    private static final String KEY_PLAYER_REL_X = "player_rel_x";
    private static final String KEY_PLAYER_REL_Y = "player_rel_y";
    private static final String KEY_PLAYER_REL_Z = "player_rel_z";
    private static final String KEY_SHIP_ENTITIES = "ship_entities";
    private static final String KEY_PLOT_REL_X = "plot_rel_x";
    private static final String KEY_PLOT_REL_Y = "plot_rel_y";
    private static final String KEY_PLOT_REL_Z = "plot_rel_z";
    private static final String KEY_WAS_SEATED = "was_seated";
    private static final String KEY_VEHICLE_TYPE = "vehicle_type";
    private static final String KEY_OLD_PLOT_X = "old_plot_x";
    private static final String KEY_OLD_PLOT_Z = "old_plot_z";
    private static final String KEY_OLD_POSE = "old_pose";
    private static final String KEY_POSE_QX = "qx";
    private static final String KEY_POSE_QY = "qy";
    private static final String KEY_POSE_QZ = "qz";
    private static final String KEY_POSE_QW = "qw";

    /** Target dimension for space exploration. */
    public static final ResourceKey<Level> SPACE_DIM = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "space"));

    /** Directory where temporary ship data is stored during transitions. */
    private static final Path SHIPS_DIR = FMLPaths.CONFIGDIR.get().resolve("rocketnautics_ships");
    
    // Maps to track pending tasks during the asynchronous transition process
    private static final Map<UUID, Integer> PENDING_REBUILDS = new ConcurrentHashMap<>();
    private static final Map<UUID, TeleportTask> PENDING_TELEPORTS = new ConcurrentHashMap<>();
    private static final Map<UUID, SeatingTask> PENDING_SEATING = new ConcurrentHashMap<>();
    private static final Map<UUID, AutonomousTask> PENDING_AUTONOMOUS = new ConcurrentHashMap<>();

    private record TeleportTask(ResourceKey<Level> targetDim, Vec3 location, Vec3 deltaMovementOverride) {}
    private record SeatingTask(UUID shipUUID, double relPlotX, double relPlotY, double relPlotZ, String vehicleType, int ticksLeft, boolean blockClicked) {}
    
    /** Represents a ship jumping dimensions without a player. */
    private record AutonomousTask(ResourceKey<Level> targetDim, double x, double y, double z, Vector3d velocity, String name, int ticksLeft) {
        public AutonomousTask withTicksDecrement() {
            return new AutonomousTask(targetDim, x, y, z, velocity, name, ticksLeft - 1);
        }
    }

    /**
     * Initializes autonomous transition listeners.
     * Ships that reach threshold altitudes without players will automatically jump dimensions.
     */
    public static void init() {
        SableEventPlatform.INSTANCE.onPhysicsTick((physicsSystem, timeStep) -> {
            ServerLevel level = physicsSystem.getLevel();

            // Handle ships currently in the world
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (!DeepSpaceData.isDeepSpace(level) && container != null) {

                List<UUID> shipIds = container.getAllSubLevels().stream().map(SubLevel::getUniqueId).toList();
                for (UUID id : shipIds) {
                    SubLevel sl = container.getSubLevel(id);
                    if (!(sl instanceof ServerSubLevel ship) || ship.isRemoved()) continue;
                    if (PENDING_AUTONOMOUS.containsKey(id)) continue;

                    // Only process ships WITHOUT players
                    boolean hasPlayer = false;
                    for (ServerPlayer player : level.players()) {
                        if (Sable.HELPER.getContaining(level, player.blockPosition()) == ship) {
                            hasPlayer = true;
                            break;
                        }
                    }
                    if (hasPlayer) continue;

                    double y = ship.logicalPose().position().y;
                    DeepSpaceData instance = DeepSpaceData.getInstance(level.getServer());
                    CubePlanet linked = instance.getUniverse().getPlanetByDimension(level.dimension());
                    // make sure autonomous transfers happen much later compared to player-driven transfers.
                    if (linked != null && linked.linkedDimension() != null && linked.linkedDimension().transitionHeight() + TRANSITION_SAFE_OFFSET < y) {
                        var handle = physicsSystem.getPhysicsHandle(ship);

                        try {
                            CompoundTag tag = ship.getPlot().save();
                            String shipName = ship.getName();
                            if (shipName == null) shipName = "Unidentified Object";

                            DeepSpaceInstance claimed = instance.claimNewInstance((int) (ship.boundingBox().size().length() / 16 + 2));
                            initInstance(claimed, ship.logicalPose().position(), handle.getLinearVelocity(new Vector3d()), linked, ship);
                            
                            tag.putString("ShipName", shipName);
                            saveShipMetadata(ship, ship.logicalPose(), tag);

                            File file = new File(SHIPS_DIR.toFile(), "auto_" + id + ".nbt");
                            if (!SHIPS_DIR.toFile().exists()) SHIPS_DIR.toFile().mkdirs();
                            NbtIo.writeCompressed(tag, file.toPath());
                            PENDING_AUTONOMOUS.put(id, new AutonomousTask(DeepSpaceData.DEEP_SPACE_DIM, claimed.getCenter().x(), claimed.getCenter().y(), claimed.getCenter().z(), new Vector3d(), shipName, 10));
                            ship.markRemoved();
                            RocketNautics.LOGGER.info("Autonomous jump: {} to Deep Space Instance {}", id, claimed.getId());
                        } catch (IOException e) {
                            RocketNautics.LOGGER.error("Failed autonomous jump", e);
                        }
                    }
                }
            }

            
            var iterator = PENDING_AUTONOMOUS.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                AutonomousTask task = entry.getValue();
                
                if (task.targetDim == level.dimension()) {
                    if (task.ticksLeft > 0) {
                        PENDING_AUTONOMOUS.put(entry.getKey(), task.withTicksDecrement());
                    } else {
                        
                        UUID originalUUID = entry.getKey();
                        File file = new File(SHIPS_DIR.toFile(), "auto_" + originalUUID + ".nbt");
                        if (file.exists()) {
                            try {
                                CompoundTag tag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
                                
                                CompoundTag poseTag = tag.getCompound(KEY_OLD_POSE);
                                Quaterniond rot = new Quaterniond(
                                    poseTag.getDouble(KEY_POSE_QX), poseTag.getDouble(KEY_POSE_QY), 
                                    poseTag.getDouble(KEY_POSE_QZ), poseTag.getDouble(KEY_POSE_QW)
                                );
                                Pose3d pose = new Pose3d(new Vector3d(task.x, task.y, task.z), rot, new Vector3d(0), new Vector3d(1));

                                ServerSubLevelContainer containerInTarget = SubLevelContainer.getContainer(level);
                                ServerSubLevel newShip = (ServerSubLevel) containerInTarget.allocateNewSubLevel(pose);
                                newShip.setName(task.name);
                                newShip.getPlot().load(tag);
                                newShip.getPlot().updateBoundingBox();
                                newShip.updateBoundingBox();

                                
                                var handle = physicsSystem.getPhysicsHandle(newShip);
                                handle.addLinearAndAngularVelocity(task.velocity, new Vector3d());

                                file.delete();
                                RocketNautics.LOGGER.info("Autonomous rebuild: {} in {}", originalUUID, level.dimension().location());
                            } catch (IOException e) {
                                RocketNautics.LOGGER.error("Failed autonomous rebuild", e);
                            }
                        }
                        iterator.remove();
                    }
                }
            }
        });
    }

    private static void sendDebug(ServerPlayer player, String msg, int color) {
        PacketDistributor.sendToPlayer(player, new DebugLogPayload(msg, color));
        RocketNautics.LOGGER.info("[SPACE TRANSITION] {}", msg);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        ServerLevel currentLevel = (ServerLevel) player.level();

        if (PENDING_TELEPORTS.containsKey(player.getUUID())) {
            executeTeleport(player, PENDING_TELEPORTS.remove(player.getUUID()));
            return;
        }

        if (PENDING_SEATING.containsKey(player.getUUID())) {
            processSeating(player);
            return;
        }

        if (PENDING_REBUILDS.containsKey(player.getUUID())) {
            processRebuildCounter(player, currentLevel);
            return;
        }

        checkTransitionConditions(player, currentLevel);
    }

    /**
     * Checks if a player has reached the transition altitude and starts the jump sequence.
     */
    private static void checkTransitionConditions(ServerPlayer player, ServerLevel level) {
        double y = player.getY();
//        if (level.dimension() == SPACE_DIM && y <= 0) {
//            // Drop from space back to the Overworld
//            initiateTransition(player, level, Level.OVERWORLD, OVERWORLD_SPACE_Y - TRANSITION_SAFE_OFFSET);
//        } else if (level.dimension() == Level.OVERWORLD && y >= OVERWORLD_SPACE_Y) {
//            // Ascend from the Overworld into space
//            initiateTransition(player, level, SPACE_DIM, 10.0);
//        }
        if (DeepSpaceData.isDeepSpace(level)) return;
        DeepSpaceData instance = DeepSpaceData.getInstance(level.getServer());
        CubePlanet linked = instance.getUniverse().getPlanetByDimension(level.dimension());
        if (linked != null && linked.linkedDimension() != null && linked.linkedDimension().transitionHeight() < y) {
            ServerSubLevel ship = findShipUnderPlayer(player, level);
            int size = ship == null ? 2 : (int) (ship.boundingBox().size().length() / 16 + 2);
            DeepSpaceInstance claimed = instance.claimNewInstance(size);
            Vector3d v = ship == null ? new Vector3d() : ship.latestLinearVelocity;
            // don't forgot to convert from m/t to m/s
            Vector3d p = new Vector3d(player.getX(), player.getY(), player.getZ());
            initInstance(claimed, p, v, linked, ship);
            initiateTransition(player, level, DeepSpaceData.DEEP_SPACE_DIM, new Vec3(claimed.getCenter().get(new Vector3f())), Vec3.ZERO);
        }
    }

    private static void initInstance(DeepSpaceInstance instance, Vector3dc dimPosition, Vector3dc velocity, CubePlanet planet, ServerSubLevel ship) {
        AbsoluteDate currentDate = instance.getManager().getUniverseTime();
        Rotation rotation = planet.getRotationAtTime(currentDate);
        rotation = rotation.compose(new Rotation(Vector3D.PLUS_I, Vector3D.PLUS_J), RotationConvention.FRAME_TRANSFORM);
        Vector3d scaledPosition = dimPosition.mul(planet.radius() / 30_000_000, new Vector3d());
        Vector3D unrotatedPosition = new Vector3D(scaledPosition.x(), dimPosition.y() + planet.radius() + TRANSITION_SAFE_OFFSET, scaledPosition.z());
        if (velocity.lengthSquared() < 1e-5) {
            velocity = new Vector3d(0, 1, 0);
        }
        ship.updateLastPose();
        ship.logicalPose().orientation().mul(DeepSpaceHelper.adapt(rotation));
        Vector3D actualPosition = rotation.applyInverseTo(unrotatedPosition);
        TimeStampedPVCoordinates coords = new TimeStampedPVCoordinates(currentDate, actualPosition,
                rotation.applyInverseTo(DeepSpaceHelper.adapt(velocity))
                        .add(planet.rotationDescription().getRotationRate().crossProduct(actualPosition))); // compensate for rotation rate of the planet
        instance.getPosition().init(instance.getManager().getUniverse(), planet.orekitFrame(), coords);
    }

    private static void processRebuildCounter(ServerPlayer player, ServerLevel level) {
        int ticksLeft = PENDING_REBUILDS.get(player.getUUID());
        if (ticksLeft <= 0) {
            PENDING_REBUILDS.remove(player.getUUID());
            File transitionFile = getTransitionFile(player.getUUID());
            if (transitionFile.exists()) {
                rebuildShipAfterTransition(player, level, transitionFile);
            }
        } else {
            if (areChunksReady(player, level)) {
                PENDING_REBUILDS.remove(player.getUUID());
                File transitionFile = getTransitionFile(player.getUUID());
                if (transitionFile.exists()) {
                    sendDebug(player, "Chunks ready early! Rebuilding...", 0x55FF55);
                    rebuildShipAfterTransition(player, level, transitionFile);
                }
            } else {
                PENDING_REBUILDS.put(player.getUUID(), ticksLeft - 1);
            }
        }
    }

    private static boolean areChunksReady(ServerPlayer player, ServerLevel level) {
        int cx = player.chunkPosition().x;
        int cz = player.chunkPosition().z;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!level.getChunkSource().hasChunk(cx + dx, cz + dz)) return false;
            }
        }
        return true;
    }

    /**
     * Prepares both the ship and the player for dimension hopping.
     * The ship is serialized to NBT, and the player is queued for teleportation.
     */
    public static void initiateTransition(ServerPlayer player, ServerLevel fromLevel, ResourceKey<Level> toDim, Vec3 position, Vec3 deltaMovementOverride) {
        sendDebug(player, "Initiating dimension jump...", 0x55FF55);
        
        ServerSubLevel ship = findShipUnderPlayer(player, fromLevel);
        if (ship != null) {
            saveShipForTransition(player, ship);
            destroyShipInSourceDimension(ship);
        }

        // Enable client-side transition overlay
        PacketDistributor.sendToPlayer(player, new SeamlessTransitionPayload(true));
        PENDING_TELEPORTS.put(player.getUUID(), new TeleportTask(toDim, position, deltaMovementOverride));
        sendDebug(player, "Teleport task queued for " + toDim.location(), 0xFFFF55);
    }

    private static ServerSubLevel findShipUnderPlayer(ServerPlayer player, ServerLevel level) {
        ServerSubLevel ship = (ServerSubLevel) Sable.HELPER.getContaining(level, player.blockPosition());
        if (ship == null && player.getVehicle() != null) {
            ship = (ServerSubLevel) Sable.HELPER.getContaining(level, player.getVehicle().blockPosition());
        }
        return ship;
    }

    private static void destroyShipInSourceDimension(ServerSubLevel ship) {
        ship.deleteAllEntities();
        
        
        ship.markRemoved();
    }

    private static void executeTeleport(ServerPlayer player, TeleportTask task) {
        ServerLevel toLevel = player.getServer().getLevel(task.targetDim);
        if (toLevel == null) {
            sendDebug(player, "ERROR: Target dimension " + task.targetDim.location() + " is NULL!", 0xFF5555);
            return;
        }
        
        sendDebug(player, "Executing teleport to " + task.targetDim.location() + " at " + task.location, 0x55FF55);
        
        DimensionTransition transition = new DimensionTransition(
            toLevel, 
            task.location(),
            task.deltaMovementOverride(),
            player.getYRot(), 
            player.getXRot(), 
            DimensionTransition.DO_NOTHING
        );
        player.changeDimension(transition);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PacketDistributor.sendToPlayer(player, new SeamlessTransitionPayload(false));
        PENDING_REBUILDS.put(player.getUUID(), REBUILD_DELAY_TICKS);
    }

    private static void saveShipForTransition(ServerPlayer player, ServerSubLevel ship) {
        try {
            CompoundTag tag = ship.getPlot().save();
            Pose3d pose = ship.logicalPose();
            
            
            Vec3 playerWorldPos = Sable.HELPER.projectOutOfSubLevel(player.level(), player.position());
            tag.putDouble(KEY_PLAYER_REL_X, playerWorldPos.x - pose.position().x);
            tag.putDouble(KEY_PLAYER_REL_Y, playerWorldPos.y - pose.position().y);
            tag.putDouble(KEY_PLAYER_REL_Z, playerWorldPos.z - pose.position().z);
            
            saveShipEntities(player, ship, tag);
            saveShipMetadata(ship, pose, tag);

            NbtIo.writeCompressed(tag, getTransitionFile(player.getUUID()).toPath());
        } catch (IOException e) {
            sendDebug(player, "CRITICAL: Ship save failed!", 0xFF5555);
            RocketNautics.LOGGER.error("Failed to save ship for transition", e);
        }
    }

    private static void saveShipEntities(ServerPlayer player, ServerSubLevel ship, CompoundTag tag) {
        ListTag entityList = new ListTag();
        ChunkPos minChunk = ship.getPlot().getChunkMin();
        Entity vehicle = player.getVehicle();

        
        if (vehicle != null) {
            CompoundTag vehicleTag = new CompoundTag();
            if (vehicle.saveAsPassenger(vehicleTag)) {
                tag.putBoolean(KEY_WAS_SEATED, true);
                tag.putString(KEY_VEHICLE_TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString());
                
                double relX = vehicle.getX() - minChunk.getMinBlockX();
                double relY = vehicle.getY();
                double relZ = vehicle.getZ() - minChunk.getMinBlockZ();
                
                vehicleTag.putDouble(KEY_PLOT_REL_X, relX);
                vehicleTag.putDouble(KEY_PLOT_REL_Y, relY);
                vehicleTag.putDouble(KEY_PLOT_REL_Z, relZ);
                entityList.add(vehicleTag);
                
                
                tag.putDouble("vehicle_plot_rel_x", relX);
                tag.putDouble("vehicle_plot_rel_y", relY);
                tag.putDouble("vehicle_plot_rel_z", relZ);
            }
        }

        
        AABB plotBounds = new AABB(minChunk.getMinBlockX(), -64, minChunk.getMinBlockZ(), minChunk.getMaxBlockX() + 1, 320, minChunk.getMaxBlockZ() + 1);
        for (Entity e : ((ServerLevel)player.level()).getEntitiesOfClass(Entity.class, plotBounds)) {
            if (e != player && e != vehicle) {
                CompoundTag entityTag = new CompoundTag();
                if (e.saveAsPassenger(entityTag)) {
                    entityTag.putDouble(KEY_PLOT_REL_X, e.getX() - minChunk.getMinBlockX());
                    entityTag.putDouble(KEY_PLOT_REL_Y, e.getY());
                    entityTag.putDouble(KEY_PLOT_REL_Z, e.getZ() - minChunk.getMinBlockZ());
                    entityList.add(entityTag);
                }
            }
        }
        tag.put(KEY_SHIP_ENTITIES, entityList);
    }

    private static void saveShipMetadata(ServerSubLevel ship, Pose3d pose, CompoundTag tag) {
        tag.putInt(KEY_OLD_PLOT_X, ship.getPlot().plotPos.x);
        tag.putInt(KEY_OLD_PLOT_Z, ship.getPlot().plotPos.z);
        
        CompoundTag poseTag = new CompoundTag();
        poseTag.putDouble(KEY_POSE_QX, pose.orientation().x);
        poseTag.putDouble(KEY_POSE_QY, pose.orientation().y);
        poseTag.putDouble(KEY_POSE_QZ, pose.orientation().z);
        poseTag.putDouble(KEY_POSE_QW, pose.orientation().w);
        tag.put(KEY_OLD_POSE, poseTag);
    }

    /**
     * Re-assembles the ship in the new dimension using the saved NBT data.
     * Maps the ship's plot to a new location and restores entities.
     */
    private static void rebuildShipAfterTransition(ServerPlayer player, ServerLevel level, File file) {
        try {
            CompoundTag tag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            
            // Calculate where the ship should be relative to the player's new position
            Pose3d newPose = calculateNewPose(player, tag);
            
            // Allocate a new SubLevel (ship instance)
            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
            ServerSubLevel newShip = (ServerSubLevel) container.allocateNewSubLevel(newPose);
            
            // Remap block entity coordinates within the plot NBT
            ChunkPos sourcePlotPos = new ChunkPos(tag.getInt(KEY_OLD_PLOT_X), tag.getInt(KEY_OLD_PLOT_Z));
            ShipCopyPasteCommand.remapBlockEntityPositions(tag, sourcePlotPos, newShip.getPlot().plotPos);
            
            // Load block data and restore entities
            newShip.getPlot().load(tag);
            rebuildEntities(level, newShip, tag);
            
            finalizeShipRebuild(player, newShip, tag, file);
            
        } catch (IOException e) {
            sendDebug(player, "CRITICAL: Ship rebuild failed!", 0xFF5555);
            RocketNautics.LOGGER.error("Failed to rebuild ship after transition", e);
        }
    }

    private static Pose3d calculateNewPose(ServerPlayer player, CompoundTag tag) {
        double px = player.getX() - tag.getDouble(KEY_PLAYER_REL_X);
        double py = player.getY() - tag.getDouble(KEY_PLAYER_REL_Y);
        double pz = player.getZ() - tag.getDouble(KEY_PLAYER_REL_Z);
        
        CompoundTag poseTag = tag.getCompound(KEY_OLD_POSE);
        Quaterniond rot = new Quaterniond(
            poseTag.getDouble(KEY_POSE_QX), 
            poseTag.getDouble(KEY_POSE_QY), 
            poseTag.getDouble(KEY_POSE_QZ), 
            poseTag.getDouble(KEY_POSE_QW)
        );
        
        return new Pose3d(new Vector3d(px, py, pz), rot, new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
    }

    private static void rebuildEntities(ServerLevel level, ServerSubLevel ship, CompoundTag tag) {
        ListTag entityList = tag.getList(KEY_SHIP_ENTITIES, Tag.TAG_COMPOUND);
        ChunkPos newMinChunk = ship.getPlot().getChunkMin();
        
        for (int i = 0; i < entityList.size(); i++) {
            CompoundTag entityTag = entityList.getCompound(i);
            EntityType.create(entityTag, level).ifPresent(e -> {
                double ex = newMinChunk.getMinBlockX() + entityTag.getDouble(KEY_PLOT_REL_X);
                double ey = entityTag.getDouble(KEY_PLOT_REL_Y);
                double ez = newMinChunk.getMinBlockZ() + entityTag.getDouble(KEY_PLOT_REL_Z);
                e.setPos(ex, ey, ez);
                ((EntityMovementExtension) e).sable$setTrackingSubLevel(ship);
                level.addFreshEntity(e);
            });
        }
    }

    private static void finalizeShipRebuild(ServerPlayer player, ServerSubLevel ship, CompoundTag tag, File file) {
        ship.getPlot().updateBoundingBox();
        ship.updateBoundingBox();
        
        if (tag.getBoolean(KEY_WAS_SEATED)) {
            PENDING_SEATING.put(player.getUUID(), new SeatingTask(
                ship.getUniqueId(), 
                tag.getDouble("vehicle_plot_rel_x"), 
                tag.getDouble("vehicle_plot_rel_y"), 
                tag.getDouble("vehicle_plot_rel_z"), 
                tag.getString(KEY_VEHICLE_TYPE), 
                SEATING_RECOVERY_TIMEOUT, 
                false
            ));
            sendDebug(player, "Seating recovery scheduled...", 0xFFFF55);
        }
        
        file.delete();
        sendDebug(player, "TRANSITION COMPLETE!", 0xFFAA00);
    }

    private static void processSeating(ServerPlayer player) {
        SeatingTask task = PENDING_SEATING.get(player.getUUID());
        if (task == null) return;

        if (player.getVehicle() != null) {
            PENDING_SEATING.remove(player.getUUID());
            sendDebug(player, "Seating confirmed!", 0x55FF55);
            return;
        }

        if (task.ticksLeft > 0) {
            if (!task.vehicleType.isEmpty() && searchAndSeat(player, task)) {
                PENDING_SEATING.remove(player.getUUID());
                return;
            }
            if (task.ticksLeft % 2 == 0) {
                tryBlockSeating(player, task);
            }
            PENDING_SEATING.put(player.getUUID(), new SeatingTask(
                task.shipUUID, task.relPlotX, task.relPlotY, task.relPlotZ,
                task.vehicleType, task.ticksLeft - 1, task.blockClicked));
            return;
        }

        sendDebug(player, "Seating timeout — applying magnetic boots fallback", 0xFF8800);
        applyMagneticBoots(player, task);
        PENDING_SEATING.remove(player.getUUID());
    }

    private static void applyMagneticBoots(ServerPlayer player, SeatingTask task) {
        ServerLevel level = (ServerLevel) player.level();
        ServerSubLevel ship = findShipByUUID(level, task.shipUUID);
        if (ship == null) {
            sendDebug(player, "Magnetic boots: ship not found!", 0xFF5555);
            return;
        }

        ChunkPos minChunk = ship.getPlot().getChunkMin();
        double tx = minChunk.getMinBlockX() + task.relPlotX;
        double ty = task.relPlotY;
        double tz = minChunk.getMinBlockZ() + task.relPlotZ;

        
        if (tryBlockSeating(player, task)) {
            PENDING_SEATING.put(player.getUUID(), new SeatingTask(
                task.shipUUID, task.relPlotX, task.relPlotY, task.relPlotZ,
                task.vehicleType, 20, true));
            return;
        }

        
        Vec3 worldPos = Sable.HELPER.projectOutOfSubLevel(level, new Vec3(tx, ty + 0.5, tz));
        player.teleportTo(worldPos.x, worldPos.y, worldPos.z);
        ((EntityMovementExtension) player).sable$setTrackingSubLevel(ship);
        sendDebug(player, "Magnetic Boots Activated! (Emergency Teleport)", 0xFFAA00);
    }

    private static boolean searchAndSeat(ServerPlayer player, SeatingTask task) {
        ServerLevel level = (ServerLevel) player.level();
        ServerSubLevel ship = findShipByUUID(level, task.shipUUID);
        if (ship == null) return false;

        ChunkPos minChunk = ship.getPlot().getChunkMin();
        double tx = minChunk.getMinBlockX() + task.relPlotX;
        double ty = task.relPlotY;
        double tz = minChunk.getMinBlockZ() + task.relPlotZ;

        for (Entity e : level.getEntities().getAll()) {
            if (e == player) continue;
            if (!BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(task.vehicleType)) continue;

            SubLevel trackingShip = ((EntityMovementExtension) e).sable$getTrackingSubLevel();
            if (trackingShip == null || !trackingShip.getUniqueId().equals(task.shipUUID)) continue;

            if (e.distanceToSqr(tx, ty, tz) < 25.0) {
                player.startRiding(e, true);
                sendDebug(player, "Seating recovery: entity found and mounted!", 0x55FF55);
                return true;
            }
        }

        
        AABB plotBounds = new AABB(
            minChunk.getMinBlockX(), -64, minChunk.getMinBlockZ(),
            minChunk.getMaxBlockX() + 16, 320, minChunk.getMaxBlockZ() + 16
        );
        for (Entity e : level.getEntitiesOfClass(Entity.class, plotBounds)) {
            if (e == player) continue;
            if (!BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(task.vehicleType)) continue;
            if (e.distanceToSqr(tx, ty, tz) < 25.0) {
                if (((EntityMovementExtension) e).sable$getTrackingSubLevel() == null) {
                    ((EntityMovementExtension) e).sable$setTrackingSubLevel(ship);
                }
                player.startRiding(e, true);
                sendDebug(player, "Seating recovery: entity found via AABB!", 0x55FFAA);
                return true;
            }
        }

        return false;
    }

    private static boolean tryBlockSeating(ServerPlayer player, SeatingTask task) {
        ServerLevel level = (ServerLevel) player.level();
        ServerSubLevel ship = findShipByUUID(level, task.shipUUID);
        if (ship == null) return false;

        ChunkPos minChunk = ship.getPlot().getChunkMin();
        double localX = task.relPlotX;
        double localY = task.relPlotY;
        double localZ = task.relPlotZ;

        double worldX = minChunk.getMinBlockX() + localX;
        double worldY = localY;
        double worldZ = minChunk.getMinBlockZ() + localZ;

        AABB seatSearch = new AABB(worldX - 2, worldY - 1, worldZ - 2, worldX + 3, worldY + 3, worldZ + 3);
        for (Entity e : level.getEntitiesOfClass(Entity.class, seatSearch)) {
            if (e == player) continue;
            String typeName = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
            if (typeName.contains("seat")) {
                ((EntityStickExtension) e).sable$setPlotPosition(new Vec3(localX, localY, localZ));
                boolean mounted = player.startRiding(e, true);
                if (mounted || player.getVehicle() == e) {
                    sendDebug(player, "Force-mounted on existing seat: " + typeName, 0x55FF55);
                    return true;
                }
            }
        }

        ResourceLocation seatId = ResourceLocation.fromNamespaceAndPath("create", "seat");
        EntityType<?> seatType = BuiltInRegistries.ENTITY_TYPE.get(seatId);

        if (seatType == null) return false;

        Entity seatEntity = seatType.create(level);
        if (seatEntity == null) return false;

        seatEntity.setPos(worldX, worldY, worldZ);
        level.addFreshEntity(seatEntity);

        ((EntityStickExtension) seatEntity).sable$setPlotPosition(new Vec3(localX, localY, localZ));

        boolean mounted = player.startRiding(seatEntity, true);
        if (mounted || player.getVehicle() == seatEntity) {
            sendDebug(player, "Force-seated on create:seat with plot tracking!", 0x55FF55);
            return true;
        }

        seatEntity.discard();
        return false;
    }

    private static ServerSubLevel findShipByUUID(ServerLevel level, UUID uuid) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (SubLevel sl : container.getAllSubLevels()) {
                if (sl.getUniqueId().equals(uuid)) return (ServerSubLevel) sl;
            }
        }
        return null;
    }

    private static File getTransitionFile(UUID playerUUID) {
        return SHIPS_DIR.resolve("transition_" + playerUUID.toString() + ".nbt").toFile();
    }
}
