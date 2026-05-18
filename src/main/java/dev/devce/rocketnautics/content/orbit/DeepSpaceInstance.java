package dev.devce.rocketnautics.content.orbit;

import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.physics.SpaceTransitionHandler;
import dev.devce.rocketnautics.network.DeepSpacePositionPayload;
import it.unimi.dsi.fastutil.doubles.DoubleObjectPair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.util.*;

public final class DeepSpaceInstance {

    private final DeepSpaceData manager;
    private final int sideLength;
    private final int negXCorner;
    private final int negZCorner;
    private final AABB boundingBox;
    private Vector3d center;
    private final long id;

    private final DeepSpacePosition position = new DeepSpacePosition();

    private CubePlanet lastOrbiting;

    private final Set<UUID> knownSublevels = new ObjectOpenHashSet<>();
    private final Map<UUID, DoubleObjectPair<Vector3d>> pendingPhysics = new Object2ObjectOpenHashMap<>();

    public DeepSpaceInstance(DeepSpaceData manager, int sideLength, int negXCorner, int negZCorner, long id) {
        this.manager = manager;
        this.sideLength = sideLength;
        this.negXCorner = negXCorner;
        this.negZCorner = negZCorner;
        this.id = id;
        this.position.setLocalUniverseTicks(manager.getUniverseTicks());
        this.boundingBox = new AABB(negXCorner, DeepSpaceData.LOGICAL_INSTANCE_HEIGHT, negZCorner, negXCorner + sideLength, DeepSpaceData.LOGICAL_INSTANCE_HEIGHT + sideLength, negZCorner + sideLength);
    }

    public DeepSpaceInstance(DeepSpaceData manager, CompoundTag tag) {
        this.manager = manager;
        this.sideLength = tag.getInt("SideLength");
        this.negXCorner = tag.getInt("NegX");
        this.negZCorner = tag.getInt("NegZ");
        this.id = tag.getLong("Id");
        this.position.setLocalUniverseTicks(tag.getLong("LocalTicks"));
        this.position.init(manager.getUniverse(), tag.getString("Frame"), DeepSpaceHelper.read(DeepSpaceHelper.STAMPED_PVCOORDS_CODEC, tag.get("Coords")));
        this.boundingBox = new AABB(negXCorner, DeepSpaceData.LOGICAL_INSTANCE_HEIGHT, negZCorner, negXCorner + sideLength, DeepSpaceData.LOGICAL_INSTANCE_HEIGHT + sideLength, negZCorner + sideLength);
    }

    // cannot be codec-driven due to the need for the DeepSpaceData object during deserialization.
    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SideLength", sideLength);
        tag.putInt("NegX", negXCorner);
        tag.putInt("NegZ", negZCorner);
        tag.putLong("Id", id);
        tag.putLong("LocalTicks", position.getLocalUniverseTicks());
        tag.putString("Frame", position.getCurrentOrbit().getFrame().getName());
        Tag c = DeepSpaceHelper.write(DeepSpaceHelper.STAMPED_PVCOORDS_CODEC, position.getCurrentOrbit().getPVCoordinates());
        if (c != null) tag.put("Coords", c);
        return tag;
    }

    public boolean isCorrupted() {
        return position.isCorrupted();
    }

    public DeepSpaceData getManager() {
        return manager;
    }

    public long getId() {
        return id;
    }

    public int getSideLength() {
        return sideLength;
    }

    public int getNegXCorner() {
        return negXCorner;
    }

    public int getNegZCorner() {
        return negZCorner;
    }

    public DeepSpacePosition getPosition() {
        return position;
    }

    public void tick(MinecraftServer server) {
        // handle physics
        if (!pendingPhysics.isEmpty()) {
            TimeStampedPVCoordinates coords = position.getCurrentPVCoords();
            Vector3d momentum = new Vector3d();
            double mass = 0;
            for (DoubleObjectPair<Vector3d> value : pendingPhysics.values()) {
                mass += value.firstDouble();
                value.right().mulAdd(value.firstDouble(), momentum, momentum);
            }
            pendingPhysics.clear();
            if (mass != 0 && momentum.lengthSquared() > 1e-20) {
                Vector3D velocityChange = DeepSpaceHelper.adapt(momentum.div(mass));
                position.init(manager.getUniverse(), position.getFrame(),
                        new TimeStampedPVCoordinates(coords.getDate(), coords.getPosition(), coords.getVelocity().add(velocityChange)));
                manager.setDirty();
            }
        }
        // update position
        position.propagate(manager.getUniverse());
        // handle render data
        if (server.getTickCount() % 20 == 0) {
            ServerLevel deepSpace = server.getLevel(DeepSpaceData.DEEP_SPACE_DIM);
            List<ServerPlayer> players = deepSpace.getPlayers(p -> boundingBox().contains(p.position()));
            for (ServerPlayer player : players) {
                PacketDistributor.sendToPlayer(player, DeepSpacePositionPayload.of(position, manager.getUniverse()));
            }
        }
        // check for planetary intersection
        if (lastOrbiting == null || lastOrbiting.orekitFrame() != getPosition().getFrame()) {
            OptionalInt id = getManager().getUniverse().getIDByFrameName(getPosition().getFrame().getName());
            if (id.isPresent()) {
                lastOrbiting = getManager().getUniverse().getPlanetById(id.getAsInt());
            } else {
                lastOrbiting = null;
            }
        }
        if (lastOrbiting != null && lastOrbiting.linkedDimension() != null) {
            // rotate the frame to view the planet aligned with the cardinal axes
            Vector3D p = lastOrbiting.getRotationAtTime(position.getLocalUniverseTime())
                    .applyTo(getPosition().getCurrentPosition());
            double dx = Math.max(0, Math.abs(p.getX()) - lastOrbiting.radius());
            double dy = Math.max(0, Math.abs(p.getY()) - lastOrbiting.radius());
            double dz = Math.max(0, Math.abs(p.getZ()) - lastOrbiting.radius());
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < lastOrbiting.linkedDimension().transitionHeight()) {
                final ServerLevel deepSpace = server.getLevel(DeepSpaceData.DEEP_SPACE_DIM);
                final ResourceKey<Level> target = lastOrbiting.linkedDimension().key();
                List<ServerPlayer> players = deepSpace.getPlayers(pl -> boundingBox().contains(pl.position()));
                // at most one of these will be true
                boolean xMajor = dx > dy && dx > dz;
                boolean zMajor = dz > dx && dz > dy;
                boolean yMajor = dy > dx && dy > dz;
                double scaleFactor = 30_000_000 / lastOrbiting.radius();
                double safe = SpaceTransitionHandler.TRANSITION_SAFE_OFFSET;
                Vec3 pos = null;
                if (xMajor) {
                    if (p.getX() > 0) {
                        // pos y => neg z
                        // pos z => neg x
                        pos = new Vec3(-p.getZ() * scaleFactor, dx - safe, -p.getY() * scaleFactor);
                    } else {
                        // pos y => neg z
                        // pos z => pos x
                        pos = new Vec3(p.getZ() * scaleFactor, dx - safe, -p.getY() * scaleFactor);
                    }
                }
                if (zMajor) {
                    if (p.getZ() > 0) {
                        // pos y => neg z
                        // pos x => pos x
                        pos = new Vec3(p.getX() * scaleFactor, dz - safe, -p.getY() * scaleFactor);
                    } else {
                        // pos y => neg z
                        // pos x => neg x
                        pos = new Vec3(-p.getX() * scaleFactor, dz - safe, -p.getY() * scaleFactor);
                    }
                }
                if (yMajor) {
                    // pos x => pos x
                    // pos z => pos z
                    pos = new Vec3(p.getX() * scaleFactor, dy - safe, p.getZ() * scaleFactor);
                }
                if (pos != null) {
                    for (ServerPlayer pl : players) {
                        Vec3 offset = pl.position().subtract(boundingBox().getCenter());
                        // TODO rotate the ships back to the correct orientation
                        SpaceTransitionHandler.initiateTransition(pl, deepSpace, target, pos.add(offset), Vec3.ZERO);
                    }
                    manager.retireInstance(this.getId());
                }
            }
        }
    }

    public void applyVelocity(UUID id, Vector3dc velocity, double mass) {
        knownSublevels.add(id);
        pendingPhysics.compute(id, (k, v) -> {
            if (v == null) {
                return DoubleObjectPair.of(mass, new Vector3d(velocity));
            }
            v.right().add(velocity);
            return v;
        });
    }

    public AABB boundingBox() {
        return boundingBox;
    }

    public Vector3dc getCenter() {
        if (center == null) {
            Vec3 v = boundingBox().getCenter();
            center = new Vector3d(v.x(), v.y(), v.z());
        }
        return center;
    }
}
