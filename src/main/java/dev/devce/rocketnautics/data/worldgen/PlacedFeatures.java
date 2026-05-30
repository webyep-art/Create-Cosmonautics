package dev.devce.rocketnautics.data.worldgen;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.registry.RocketBlocks;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.placement.*;

import java.util.List;

public class PlacedFeatures {
    public static final ResourceKey<PlacedFeature> TITANIUM_ORE = register("titanium_ore");
    public static final ResourceKey<PlacedFeature> LUNAR_ROCK = register("moon/rock");
    public static final ResourceKey<PlacedFeature> LUNAR_MOSS = register("moon/moss");
    public static final ResourceKey<PlacedFeature> SMALL_AGED_COLUMNS = register("moon/small_aged_columns");
    public static final ResourceKey<PlacedFeature> LARGE_AGED_COLUMNS = register("moon/large_aged_columns");
    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> features = context.lookup(Registries.CONFIGURED_FEATURE);
        context.register(TITANIUM_ORE, new PlacedFeature(
                features.getOrThrow(ConfiguredFeatures.TITANIUM_ORE),
                List.of(
                        CountPlacement.of(7),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.of(TrapezoidHeight.of(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(160))),
                        BiomeFilter.biome()
                )
        ));
        Vec3i down = new Vec3i(0, -1, 0);
        context.register(LUNAR_ROCK, new PlacedFeature(
                features.getOrThrow(ConfiguredFeatures.LUNAR_ROCK),
                List.of(
                        NoiseBasedCountPlacement.of(8, 3, 1),
                        InSquarePlacement.spread(),
                        HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG),
                        BiomeFilter.biome(),
                        BlockPredicateFilter.forPredicate(BlockPredicate.matchesBlocks(
                                down,
                                List.of(RocketBlocks.LUNAR_REGOLITH.get(), RocketBlocks.LUNAR_AGED_BASALT.get())
                        ))
                )
        ));
        context.register(LUNAR_MOSS, new PlacedFeature(
                features.getOrThrow(ConfiguredFeatures.LUNAR_MOSS),
                List.of(
                        NoiseBasedCountPlacement.of(8, 3, 1),
                        InSquarePlacement.spread(),
                        HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG),
                        BiomeFilter.biome(),
                        BlockPredicateFilter.forPredicate(BlockPredicate.matchesBlocks(
                                down,
                                List.of(RocketBlocks.LUNAR_LOOSE_REGOLITH.get())
                        ))
                )
        ));
        context.register(SMALL_AGED_COLUMNS, new PlacedFeature(
                features.getOrThrow(ConfiguredFeatures.SMALL_AGED_COLUMNS),
                List.of(
                        CountOnEveryLayerPlacement.of(4),
                        BiomeFilter.biome()
                )
        ));
        context.register(LARGE_AGED_COLUMNS, new PlacedFeature(
                features.getOrThrow(ConfiguredFeatures.LARGE_AGED_COLUMNS),
                List.of(
                        CountOnEveryLayerPlacement.of(2),
                        BiomeFilter.biome()
                )
        ));
    }

    private static ResourceKey<PlacedFeature> register(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, RocketNautics.path(name));
    }
}
