package dev.devce.rocketnautics.content.orbit.universe;

import dev.devce.rocketnautics.api.orbit.FrameTree;
import dev.devce.rocketnautics.content.orbit.universe.builder.UniverseDefinitionBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import org.orekit.frames.Frame;

import java.util.*;

public class UniverseDefinition {
    private final FrameTree tree;
    private final Int2ObjectAVLTreeMap<PointGravitySource> gravitySources;
    private final Int2ObjectAVLTreeMap<CubePlanet> planets;
    private final Map<ResourceKey<Level>, CubePlanet> planetsByDimension = new Object2ObjectOpenHashMap<>();

    public static UniverseDefinitionBuilder builder() {
        return new UniverseDefinitionBuilder();
    }

    public UniverseDefinition(FrameTree tree, Collection<PointGravitySource> gravity, Collection<CubePlanet> planets) {
        this.tree = tree;
        gravitySources = new Int2ObjectAVLTreeMap<>();
        for (PointGravitySource source : gravity) {
            gravitySources.put(source.id(), source);
        }
        this.planets = new Int2ObjectAVLTreeMap<>();
        for (CubePlanet planet : planets) {
            this.planets.put(planet.id(), planet);
            if (planet.linkedDimension() != null) {
                this.planetsByDimension.put(planet.linkedDimension().key(), planet);
            }
        }
    }

    @UnmodifiableView
    public Collection<PointGravitySource> getGravitySources() {
        return gravitySources.values();
    }

    @UnmodifiableView
    public Collection<CubePlanet> getPlanets() {
        return planets.values();
    }

    @NotNull
    public Optional<Frame> getFrameByName(String name) {
        return tree.getInTreeByName(name).map(FrameTree::getOrekitFrame);
    }

    public OptionalInt getIDByFrameName(String name) {
        Optional<FrameTree> f = tree.getInTreeByName(name);
        return f.map(frameTree -> OptionalInt.of(frameTree.getId())).orElseGet(OptionalInt::empty);
    }

    @NotNull
    public Optional<Frame> getFrameByID(int id) {
        return tree.getInTreeByID(id).map(FrameTree::getOrekitFrame);
    }

    public int getFrameIDByName(String name) {
        Optional<FrameTree> frame = tree.getInTreeByName(name);
        return frame.map(FrameTree::getId).orElse(-1);
    }

    @NotNull
    public Optional<String> getFrameNameByID(int id) {
        Optional<FrameTree> frame = tree.getInTreeByID(id);
        return frame.map(FrameTree::getName);
    }

    public @Nullable PointGravitySource getGravitySourceById(int id) {
        return gravitySources.get(id);
    }

    public @Nullable CubePlanet getPlanetById(int id) {
        return planets.get(id);
    }

    public @Nullable CubePlanet getPlanetByDimension(ResourceKey<Level> dimension) {
        return planetsByDimension.get(dimension);
    }

    public void write(FriendlyByteBuf buf) {
        tree.writeTree(buf);
        buf.writeVarInt(gravitySources.size());
        for (PointGravitySource source : gravitySources.values()) {
            source.write(buf);
        }
        buf.writeVarInt(planets.size());
        for (CubePlanet planet : planets.values()) {
            planet.write(buf);
        }
    }

    public static UniverseDefinition read(FriendlyByteBuf buf) {
        FrameTree tree = FrameTree.readTree(buf);
        int count = buf.readVarInt();
        List<PointGravitySource> gravity = new ObjectArrayList<>();
        for (int i = 0; i < count; i++) {
            gravity.add(PointGravitySource.read(buf, tree));
        }
        count = buf.readVarInt();
        List<CubePlanet> planets = new ObjectArrayList<>();
        for (int i = 0; i < count; i++) {
            CubePlanet planet = CubePlanet.read(buf, tree);
            planets.add(planet);
        }
        return new UniverseDefinition(tree, gravity, planets);
    }
}
