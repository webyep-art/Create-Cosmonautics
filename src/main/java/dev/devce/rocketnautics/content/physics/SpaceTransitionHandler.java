

package dev.devce.rocketnautics.content.physics;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.network.DebugLogPayload;
import dev.devce.rocketnautics.network.SeamlessTransitionPayload;
import dev.egg.SubLevelWarper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.NotNull;
import org.joml.*;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.io.File;
import java.util.function.BooleanSupplier;

/**
 * Handles the seamless transition of ships and players between dimensions (Overworld <-> Space).
 * This includes saving ship data to NBT, managing teleportation, rebuilding ships in the target dimension,
 * and recovering player seating/entities.
 */
@EventBusSubscriber(modid = RocketNautics.MODID)
public class SpaceTransitionHandler {
    
    public static double WARP_ENTITY_DETECTION_TOLERANCE = 0;
    public static Quaterniondc PREMUL_ROTATION = null;

    public static final int CHUNK_LOADING_PARTITION_SIZES = 16;
    public static final double TRANSITION_SAFE_OFFSET = 1000.0;
    public static final double OVERWORLD_SPACE_Y = 20000.0;

    private static final Deque<Runnable> TICK_TASKS = new ArrayDeque<>();

    /**
     * Initializes autonomous transition listeners.
     * Ships that reach threshold altitudes will automatically jump dimensions and bring players with them.
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
                    Vector3d pos = ship.logicalPose().position();
                    DeepSpaceData instance = DeepSpaceData.getInstance(level.getServer());
                    CubePlanet linked = instance.getUniverse().getPlanetByDimension(level.dimension());
                    if (linked != null && linked.linkedDimension() != null && linked.linkedDimension().transitionHeight() < pos.y()) {
                        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(ship);
                        double captureSize = ship.boundingBox().size().length();
                        DeepSpaceInstance claimed = instance.claimNewInstance((int) (captureSize / 16 + 2));
                        Quaterniond rotation = initInstance(claimed, ship.logicalPose().position(), handle.getLinearVelocity(new Vector3d()), linked, ship);
                        ServerLevel deepSpace = level.getServer().getLevel(DeepSpaceData.DEEP_SPACE_DIM);
                        // Handle all players nearby
                        Map<ServerPlayer, UUID> riding = new Object2ObjectOpenHashMap<>();
                        for (ServerPlayer pl : level.getPlayers(pl -> ship.boundingBox().toMojang().inflate(10).contains(pl.position()))) {
                            PacketDistributor.sendToPlayer(pl, new SeamlessTransitionPayload(true));
                            if (pl.isPassenger()) {
                                riding.put(pl, pl.getVehicle().getUUID());
                            }
                            //ship.logicalPose().orientation().transformInverse(rotation.transform(ship.logicalPose().orientation().transform(offset)))
                            Vector3f offset = pl.position().toVector3f().sub(pos.get(new Vector3f()));
                            Vector3f o = rotation.transform(offset);
                            pl.teleportTo(deepSpace, claimed.getCenter().x() + o.x(), claimed.getCenter().y() + o.y(), claimed.getCenter().z() + o.z(), pl.getYRot(), pl.getXRot());
                        }
                        // note that unlike when we're exiting a deep space instance, we know for a fact the involved sublevels are loaded.
                        WARP_ENTITY_DETECTION_TOLERANCE = 10;
                        PREMUL_ROTATION = rotation;
                        SubLevelWarper.WarpSubLevel(ship, deepSpace, claimed.getCenter().get(new Vector3d()));
                        WARP_ENTITY_DETECTION_TOLERANCE = 0;
                        PREMUL_ROTATION = null;
                        riding.forEach((p, e) -> p.startRiding(deepSpace.getEntity(e), true));
                    }
                }
            }
        });
    }

    public static void exitDeepSpace(MinecraftServer server, CubePlanet destination, Rotation correctionRotation, Vector3D positionInPlanetFrame, @NotNull Direction.Axis majorAxis, DeepSpaceInstance instance, Runnable afterFinished) {
        assert destination.linkedDimension() != null;
        final ServerLevel deepSpace = server.getLevel(DeepSpaceData.DEEP_SPACE_DIM);
        double scaleFactor = 30_000_000 / destination.radius();
        double targetHeight = destination.linkedDimension().transitionHeight() - SpaceTransitionHandler.TRANSITION_SAFE_OFFSET;
        Vector3D p = correctionRotation.applyInverseTo(positionInPlanetFrame);
        Vector3D axi;
        Vector3d destPos = switch (majorAxis) {
            case X -> {
                if (p.getX() > 0) {
                    // pos y => neg z
                    // pos z => neg x
                    axi = Vector3D.PLUS_I;
                    yield new Vector3d(-p.getZ() * scaleFactor, targetHeight, -p.getY() * scaleFactor);
                } else {
                    // pos y => neg z
                    // pos z => pos x
                    axi = Vector3D.MINUS_I;
                    yield new Vector3d(p.getZ() * scaleFactor, targetHeight, -p.getY() * scaleFactor);
                }
            }
            case Z -> {
                if (p.getZ() > 0) {
                    // pos y => neg z
                    // pos x => pos x
                    axi = Vector3D.PLUS_K;
                    yield new Vector3d(p.getX() * scaleFactor, targetHeight, -p.getY() * scaleFactor);
                } else {
                    // pos y => neg z
                    // pos x => neg x
                    axi = Vector3D.MINUS_K;
                    yield new Vector3d(-p.getX() * scaleFactor, targetHeight, -p.getY() * scaleFactor);
                }
            }
            case Y -> {

                if (p.getY() > 0) {
                    axi = Vector3D.PLUS_J;
                } else {
                    axi = Vector3D.MINUS_J;
                }
                // pos x => pos x
                // pos z => pos z
                yield new Vector3d(p.getX() * scaleFactor, targetHeight, p.getZ() * scaleFactor);
            }
        };
        ServerLevel destLevel = server.getLevel(destination.linkedDimension().key());
        if (destLevel == null) return;
        Rotation rotation = correctionRotation.compose(new Rotation(Vector3D.PLUS_J, axi), RotationConvention.VECTOR_OPERATOR);
        Quaterniond rot = DeepSpaceHelper.adapt(rotation).conjugate();
        // Handle all players in the instance
        Map<ServerPlayer, UUID> riding = new Object2ObjectOpenHashMap<>();
        for (ServerPlayer pl : deepSpace.getPlayers(pl -> instance.boundingBox().contains(pl.position()))) {
            PacketDistributor.sendToPlayer(pl, new SeamlessTransitionPayload(true));
            if (pl.isPassenger()) {
                riding.put(pl, pl.getVehicle().getUUID());
            }
            Vec3 offset = pl.position().subtract(instance.boundingBox().getCenter());
            Vector3f o = rot.transform(offset.toVector3f());
            pl.teleportTo(destLevel, destPos.x() + o.x(), destPos.y() + o.y(), destPos.z() + o.z(), pl.getYRot(), pl.getXRot());
        }
        // Handle ships currently in the instance
        List<ChunkPos> unloaded = instance.interiorPositions().filter(cPos -> !deepSpace.hasChunk(cPos.x, cPos.z)).toList();
        Set<UUID> seen = new ObjectOpenHashSet<>();
        if (unloaded.size() != instance.getChunkSideLength() * instance.getChunkSideLength()) {
            exitLoadedFromDeepSpace(instance, deepSpace, rot, destLevel, destPos, seen);
        }
        // whatever the player was riding was absolutely loaded
        riding.forEach((pl, e) -> pl.startRiding(destLevel.getEntity(e), true));
        if (unloaded.isEmpty()) {
            afterFinished.run();
            return;
        }
        // dispatch a task to handle parts of the instance that were unloaded at a restricted rate
        List<List<ChunkPos>> partitions = Lists.partition(unloaded, CHUNK_LOADING_PARTITION_SIZES);
        for (List<ChunkPos> part : partitions) {
            TICK_TASKS.addLast(() -> {
                SubLevelHoldingChunkMap map = SubLevelContainer.getContainer(deepSpace).getHoldingChunkMap();
                part.forEach(cPos -> {
                    deepSpace.getChunkSource().chunkMap.getDistanceManager().addRegionTicket(TicketType.UNKNOWN, cPos, 1, cPos);
                    map.updateChunkStatus(cPos, true);
                });
                deepSpace.getChunkSource().chunkMap.getDistanceManager().tickingTicketsTracker.runAllUpdates();
                map.processChanges();
                exitLoadedFromDeepSpace(instance, deepSpace, rot, destLevel, destPos, seen);
                part.forEach(cPos -> {
                    map.updateChunkStatus(cPos, false);
                });
            });
        }
        TICK_TASKS.addLast(afterFinished);
    }

    private static void exitLoadedFromDeepSpace(DeepSpaceInstance instance, ServerLevel deepSpace, Quaterniond rot, ServerLevel destLevel, Vector3d destPos, Set<UUID> seen) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(deepSpace);
        if (container != null) {
            List<UUID> shipIds = container.getAllSubLevels().stream()
                    .map(SubLevel::getUniqueId).filter(seen::add).toList();
            for (UUID id : shipIds) {
                SubLevel sl = container.getSubLevel(id);
                if (!(sl instanceof ServerSubLevel ship) || ship.isRemoved()) continue;
                Vector3d pos = ship.logicalPose().position();
                if (instance.boundingBox().contains(pos.x(), pos.y(), pos.z())) {
                    Vector3d offset = pos.sub(instance.getCenter(), new Vector3d());
                    WARP_ENTITY_DETECTION_TOLERANCE = 10;
                    PREMUL_ROTATION = rot;
                    SubLevelWarper.WarpSubLevel(ship, destLevel, rot.transform(offset).add(destPos));
                    WARP_ENTITY_DETECTION_TOLERANCE = 0;
                    PREMUL_ROTATION = null;
                }
            }
        }
    }

    private static Quaterniond initInstance(DeepSpaceInstance instance, Vector3dc dimPosition, Vector3dc velocity, CubePlanet planet, ServerSubLevel ship) {
        AbsoluteDate currentDate = instance.getManager().getUniverseTime();
        Rotation rotation = planet.getRotationAtTime(currentDate);
        rotation = rotation.compose(new Rotation(Vector3D.PLUS_J, Vector3D.PLUS_K), RotationConvention.VECTOR_OPERATOR);
        Vector3d scaledPosition = dimPosition.mul(planet.radius() / 30_000_000, new Vector3d());
        Vector3D unrotatedPosition = new Vector3D(scaledPosition.x(), dimPosition.y() + planet.radius() + TRANSITION_SAFE_OFFSET, scaledPosition.z());
        if (velocity.lengthSquared() < 1e-5) {
            velocity = new Vector3d(0, 1, 0);
        }
        Vector3D actualPosition = rotation.applyTo(unrotatedPosition);
        TimeStampedPVCoordinates coords = new TimeStampedPVCoordinates(currentDate, actualPosition,
                rotation.applyTo(DeepSpaceHelper.adapt(velocity))
                        .add(actualPosition.crossProduct(planet.rotationDescription().getRotationRate()))); // compensate for rotation rate of the planet
        instance.getPosition().init(instance.getManager().getUniverse(), planet.orekitFrame(), coords);
        return DeepSpaceHelper.adapt(rotation);
    }

    // TODO delay warps until chunks are loaded?
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

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // TODO can't we just detect the dimension change on the client and turn it off without a packet?
        PacketDistributor.sendToPlayer(player, new SeamlessTransitionPayload(false));
    }

    @SubscribeEvent
    public static void handleTickTasks(ServerTickEvent.Post event) {
        if (!TICK_TASKS.isEmpty()) {
            TICK_TASKS.removeFirst().run();
        }
    }

    @SubscribeEvent
    public static void finishTickTasks(ServerStoppingEvent event) {
        if (!TICK_TASKS.isEmpty()) {
            RocketNautics.LOGGER.info("Finishing queued sublevel transfer operations between dimensions before server halts.");
            RocketNautics.LOGGER.info("This may take a while due to needing to read them all from disk and teleport them.");
            while (!TICK_TASKS.isEmpty()) {
                TICK_TASKS.removeFirst().run();
            }
            RocketNautics.LOGGER.info("Queued sublevel transfer operations complete.");
            RocketNautics.LOGGER.info("The rest of unloading may take a while as all of those sublevels are now written to disk again.");
        }
    }
}
