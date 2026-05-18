package dev.devce.rocketnautics.api.orbit;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.network.FriendlyByteBuf;
import org.hipparchus.CalculusFieldElement;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orekit.frames.FieldTransform;
import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.frames.TransformProvider;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holder for carefully defined frames that can be sent across network to the client.
 */
public final class FrameTree {
    // objects shared across each tree
    private final AtomicInteger freeID; // always getAndIncrement
    private final Map<String, FrameTree> completeTreeByName;
    private final Int2ObjectMap<FrameTree> completeTreeByID;
    // per-object fields
    private final int id;
    private final @Nullable FrameTree parent;
    private final @NotNull Frame orekitFrame;
    private final Type type;
    private final KeplerianOrbit orbit;
    private final Vector3D position;
    private final Set<FrameTree> descendants = new ObjectOpenHashSet<>();
    private boolean retired = false;

    /**
     * Create a new frame tree root. Name and frame are preset as {@link Frame#getRoot()}
     */
    public FrameTree() {
        this.parent = null;
        this.freeID = new AtomicInteger(1);
        this.completeTreeByName = new Object2ObjectOpenHashMap<>();
        this.completeTreeByID = new Int2ObjectOpenHashMap<>();
        this.id = 0;
        this.orekitFrame = Frame.getRoot();
        // the root can be under multiple names since when it is retired, the entire tree is retired
        this.completeTreeByName.put(orekitFrame.getName(), this);
        this.completeTreeByName.put("root", this);
        this.completeTreeByName.put("", this);
        this.completeTreeByID.put(id, this);
        this.type = Type.ROOT;
        this.orbit = null;
        this.position = null;
    }

    private FrameTree(String name, FrameTree parent, KeplerianOrbit orbit, int id) {
        this.parent = parent;
        this.freeID = parent.freeID;
        this.id = id;
        parent.descendants.add(this);
        this.completeTreeByName = parent.completeTreeByName;
        this.completeTreeByName.put(name, this);
        this.completeTreeByID = parent.completeTreeByID;
        this.completeTreeByID.put(id, this);
        this.orekitFrame = new Frame(parent.orekitFrame, new TransformProvider() {
            @Override
            public Transform getTransform(AbsoluteDate date) {
                return new Transform(date, orbit.getPVCoordinates(date, FrameTree.this.parent.orekitFrame).negate());
            }

            @Override
            public <T extends CalculusFieldElement<T>> FieldTransform<T> getTransform(FieldAbsoluteDate<T> date) {
                throw new UnsupportedOperationException("Cosmonautics is not configured for arbitrary fields!");
            }
        }, name, true);
        this.type = Type.KEPLERIAN_ORBIT;
        this.orbit = orbit;
        this.position = null;
    }

    private FrameTree(String name, FrameTree parent, Vector3D position, int id) {
        this.parent = parent;
        this.freeID = parent.freeID;
        this.id = id;
        parent.descendants.add(this);
        this.completeTreeByName = parent.completeTreeByName;
        this.completeTreeByName.put(name, this);
        this.completeTreeByID = parent.completeTreeByID;
        this.completeTreeByID.put(id, this);
        this.orekitFrame = new Frame(parent.orekitFrame, new TransformProvider() {
            final Vector3D correction = position.negate();

            @Override
            public Transform getTransform(AbsoluteDate date) {
                return new Transform(date, correction);
            }

            @Override
            public <T extends CalculusFieldElement<T>> FieldTransform<T> getTransform(FieldAbsoluteDate<T> date) {
                throw new UnsupportedOperationException("Cosmonautics is not configured for arbitrary fields!");
            }
        }, name, true);
        this.type = Type.FIXED;
        this.orbit = null;
        this.position = position;
    }

    public @NotNull FrameTree getRoot() {
        FrameTree root = this;
        while (root.parent != null) {
            root = root.parent;
        }
        return root;
    }

    @NotNull
    public Optional<FrameTree> getInTreeByName(String name) {
        if (retired) return Optional.empty();
        return Optional.ofNullable(completeTreeByName.get(name));
    }

    @NotNull
    public Optional<FrameTree> getInTreeByID(int id) {
        if (retired) return Optional.empty();
        return Optional.ofNullable(completeTreeByID.get(id));
    }

    public boolean isInTree(FrameTree tree) {
        if (retired || tree.retired) return false;
        return this.completeTreeByName == tree.completeTreeByName;
    }

    /**
     * Create a child whose offset is described by an orbit. Returns null if this name has already been claimed.
     * Throws if the orbit's frame is not this FrameTree's frame.
     */
    @Nullable
    public FrameTree createChild(String name, KeplerianOrbit orbit) {
        if (retired || completeTreeByName.containsKey(name)) return null;
        if (orbit.getFrame() != orekitFrame) {
            throw new IllegalArgumentException("Orbit is not centered on our frame!");
        }
        return new FrameTree(name, this, orbit, freeID.getAndIncrement());
    }

    /**
     * Create a child whose offset is described by a fixed position. Returns null if this name has already been claimed.
     */
    @Nullable
    public FrameTree createChild(String name, Vector3D position) {
        if (retired || completeTreeByName.containsKey(name)) return null;
        return new FrameTree(name, this, position, freeID.getAndIncrement());
    }

    public @NotNull Frame getOrekitFrame() {
        return orekitFrame;
    }

    public String getName() {
        return orekitFrame.getName();
    }

    /**
     * Gets a uniquely assigned id within the complete tree this FrameTree belongs to.
     */
    public int getId() {
        return id;
    }

    /**
     * Removes references to this and descendants in the tree structure, allowing it to be garbage collected.
     */
    public void retire() {
        if (parent != null) {
            this.parent.descendants.remove(this);
        }
        retireRecurse();
    }

    private void retireRecurse() {
        this.completeTreeByName.remove(orekitFrame.getName());
        this.completeTreeByID.remove(id);
        this.descendants.forEach(FrameTree::retireRecurse);
        this.descendants.clear();
        retired = true;
    }

    public boolean isRetired() {
        return retired;
    }

    private enum Type {
        ROOT, KEPLERIAN_ORBIT, FIXED
    }

    /**
     * Writes a tree to a bytebuffer. Writes the entire tree, not just children of this node.
     * @param buf the bytebuffer to write to.
     */
    public void writeTree(FriendlyByteBuf buf) {
        this.getRoot().writeInternal(buf, -1);
        // write another parent id of -2 as a stop indicator
        buf.writeVarInt(-2);
    }

    private void writeInternal(FriendlyByteBuf buf, int parentID) {
        if (parentID != -1) {
            buf.writeVarInt(parentID);
            buf.writeByteArray(orekitFrame.getName().getBytes(StandardCharsets.UTF_8));
            buf.writeVarInt(id);
            buf.writeEnum(type);
        }
        switch (type) {
            case KEPLERIAN_ORBIT -> {
                DeepSpaceHelper.STAMPED_PVCOORDS_CODEC_S.encode(buf, orbit.getPVCoordinates());
                buf.writeDouble(orbit.getMu());
            }
            case FIXED -> {
                DeepSpaceHelper.VEC3D_CODEC_S.encode(buf, position);
            }
        }
        for (FrameTree t : descendants) {
            t.writeInternal(buf, id);
        }
    }

    /**
     * Reads a tree from a bytebuffer.
     * @param buf the byte buffer to read from.
     * @return the root node of the written tree.
     */
    public static FrameTree readTree(FriendlyByteBuf buf) {
        FrameTree root = new FrameTree();
        int largestSeen = 0;
        while (true) {
            int nextParentID = buf.readVarInt();
            if (nextParentID == -2) break; // go until our stop indicator
            FrameTree parent = root.getInTreeByID(nextParentID).get();
            String nextName = new String(buf.readByteArray(), StandardCharsets.UTF_8);
            int nextID = buf.readVarInt();
            Type type = buf.readEnum(Type.class);
            if (nextID > largestSeen) {
                largestSeen = nextID;
            }
            root.freeID.set(nextID); // force the created child to have the desired id
            switch (type) {
                case KEPLERIAN_ORBIT -> {
                    TimeStampedPVCoordinates coords = DeepSpaceHelper.STAMPED_PVCOORDS_CODEC_S.decode(buf);
                    double mu = buf.readDouble();
                    KeplerianOrbit orbit = new KeplerianOrbit(coords, parent.orekitFrame, mu);
                    parent.createChild(nextName, orbit);
                }
                case FIXED -> {
                    Vector3D position = DeepSpaceHelper.VEC3D_CODEC_S.decode(buf);
                    parent.createChild(nextName, position);
                }
            }
        }
        root.freeID.set(largestSeen + 1);
        return root;
    }
}
