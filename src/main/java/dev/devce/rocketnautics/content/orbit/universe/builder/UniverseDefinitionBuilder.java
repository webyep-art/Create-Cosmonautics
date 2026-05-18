package dev.devce.rocketnautics.content.orbit.universe.builder;

import dev.devce.rocketnautics.api.orbit.FrameTree;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.PointGravitySource;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.UnaryOperator;

public class UniverseDefinitionBuilder {
    private final FrameTree tree = new FrameTree();
    private final Map<FrameTree, PointGravitySource> sources = new Object2ObjectOpenHashMap<>();
    private final Map<FrameTree, CubePlanet> planets = new Object2ObjectOpenHashMap<>();

    private void validateFrameTree(FrameTree tree) {
        if (!this.tree.isInTree(tree)) {
            throw new IllegalArgumentException("FrameTree is not in the builder's tree!");
        }
    }

    public UniverseDefinition build() {
        return new UniverseDefinition(tree, sources.values(), planets.values());
    }

    public FrameTree getTreeRoot() {
        return tree;
    }

    public @Nullable FrameTree getFrameByName(@NotNull String name) {
        return tree.getInTreeByName(name).orElse(null);
    }

    public PointGravitySource getGravitySource(FrameTree frame, boolean remove) {
        if (frame == null || !sources.containsKey(frame)) {
            throw new IllegalArgumentException("Gravity source could not be found!");
        }
        if (remove) return sources.remove(frame);
        return sources.get(frame);
    }

    public UniverseDefinitionBuilder gravitySource(PointGravitySource source) {
        validateFrameTree(source.frame());
        if (sources.containsKey(source.frame())) {
            throw new IllegalArgumentException("Frame already has an associated gravity source defined!");
        }
        sources.put(source.frame(), source);
        return this;
    }

    public CubePlanet getCubePlanet(FrameTree frame, boolean remove) {
        if (frame == null || !sources.containsKey(frame)) {
            throw new IllegalArgumentException("Planet could not be found!");
        }
        if (remove) return planets.remove(frame);
        return planets.get(frame);
    }

    public UniverseDefinitionBuilder cubePlanet(CubePlanet planet) {
        validateFrameTree(planet.frame());
        if (planets.containsKey(planet.frame())) {
            throw new IllegalArgumentException("Frame already has an associated planet defined!");
        }
        planets.put(planet.frame(), planet);
        return this;
    }

    public UniverseDefinitionBuilder cubePlanet(UnaryOperator<PlanetDefinitionBuilder> op) {
        op.apply(new PlanetDefinitionBuilder(this)).build();
        return this;
    }
}
