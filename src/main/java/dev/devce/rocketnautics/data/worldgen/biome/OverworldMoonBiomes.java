package dev.devce.rocketnautics.data.worldgen.biome;

import com.google.common.collect.ImmutableMap;
import dev.devce.rocketnautics.data.worldgen.PlacedFeatures;
import dev.devce.rocketnautics.data.worldgen.RocketCarvers;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.data.worldgen.placement.NetherPlacements;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;

// TODO ambient sounds and such
public class OverworldMoonBiomes {
    public static Biome lunarMaria(HolderGetter<PlacedFeature> features, HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        return new Biome.BiomeBuilder()
                .temperature(1)
                .downfall(0)
                .hasPrecipitation(false)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .fogColor(0xdedede)
                        .waterColor(0xbacce0)
                        .waterFogColor(0xbacce0)
                        .skyColor(0xffffff)
                        .build())
                .generationSettings(new BiomeGenerationSettings(
                        ImmutableMap.of(
                                GenerationStep.Carving.AIR,
                                HolderSet.direct(carvers.getOrThrow(RocketCarvers.STRAIGHT_RILLE),
                                        carvers.getOrThrow(RocketCarvers.SINUOUS_RILLE),
                                        carvers.getOrThrow(RocketCarvers.STANDARD_CRATER),
                                        carvers.getOrThrow(RocketCarvers.MARIA_CAVES))
                        ),
                        List.of()
                ))
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .build();
    }

    public static Biome lunarHighlands(HolderGetter<PlacedFeature> features, HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        return new Biome.BiomeBuilder()
                .temperature(1)
                .downfall(0)
                .hasPrecipitation(false)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .fogColor(0xdedede)
                        .waterColor(0xbacce0)
                        .waterFogColor(0xbacce0)
                        .skyColor(0xffffff)
                        .build())
                .generationSettings(new BiomeGenerationSettings(
                        ImmutableMap.of(
                                GenerationStep.Carving.AIR, // extra starting crater carving step compared to the maria. This also allows for overlapping small craters.
                                HolderSet.direct(carvers.getOrThrow(RocketCarvers.STANDARD_CRATER), carvers.getOrThrow(RocketCarvers.STRAIGHT_RILLE), carvers.getOrThrow(RocketCarvers.SINUOUS_RILLE), carvers.getOrThrow(RocketCarvers.STANDARD_CRATER))
                        ),
                        List.of(
                                HolderSet.direct(features.getOrThrow(PlacedFeatures.LUNAR_ROCK), features.getOrThrow(PlacedFeatures.LUNAR_MOSS))
                        )
                ))
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .build();
    }

    public static Biome lunarBasaltChasm(HolderGetter<PlacedFeature> features, HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        return new Biome.BiomeBuilder()
                .temperature(1)
                .downfall(0)
                .hasPrecipitation(false)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .fogColor(0xdedede)
                        .waterColor(0xbacce0)
                        .waterFogColor(0xbacce0)
                        .skyColor(0xffffff)
                        .build())
                .generationSettings(new BiomeGenerationSettings(
                        ImmutableMap.of(
                                GenerationStep.Carving.AIR,
                                HolderSet.direct(carvers.getOrThrow(net.minecraft.data.worldgen.Carvers.CANYON),
                                        carvers.getOrThrow(net.minecraft.data.worldgen.Carvers.CAVE_EXTRA_UNDERGROUND))
                        ),
                        List.of()
                ))
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .build();
    }

    public static Biome lunarAgedChasm(HolderGetter<PlacedFeature> features, HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        return lunarBasaltChasm(features, carvers);
    }

    public static Biome lunarBasaltSpikes(HolderGetter<PlacedFeature> features, HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        return new Biome.BiomeBuilder()
                .temperature(1)
                .downfall(0)
                .hasPrecipitation(false)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .fogColor(0xdedede)
                        .waterColor(0xbacce0)
                        .waterFogColor(0xbacce0)
                        .skyColor(0xffffff)
                        .build())
                .generationSettings(new BiomeGenerationSettings(
                        ImmutableMap.of(
                                GenerationStep.Carving.AIR,
                                HolderSet.direct(carvers.getOrThrow(RocketCarvers.MOON_CANYON),
                                        carvers.getOrThrow(RocketCarvers.MARIA_CAVES))
                        ),
                        List.of(
                                HolderSet.direct(features.getOrThrow(NetherPlacements.SMALL_BASALT_COLUMNS), features.getOrThrow(NetherPlacements.LARGE_BASALT_COLUMNS))
                        )
                ))
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .build();
    }

    public static Biome lunarAgedSpikes(HolderGetter<PlacedFeature> features, HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        return new Biome.BiomeBuilder()
                .temperature(1)
                .downfall(0)
                .hasPrecipitation(false)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .fogColor(0xdedede)
                        .waterColor(0xbacce0)
                        .waterFogColor(0xbacce0)
                        .skyColor(0xffffff)
                        .build())
                .generationSettings(new BiomeGenerationSettings(
                        ImmutableMap.of(
                                GenerationStep.Carving.AIR,
                                HolderSet.direct(carvers.getOrThrow(RocketCarvers.MOON_CANYON))
                        ),
                        List.of(
                                HolderSet.direct(features.getOrThrow(PlacedFeatures.SMALL_AGED_COLUMNS), features.getOrThrow(PlacedFeatures.LARGE_AGED_COLUMNS))
                        )
                ))
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .build();
    }

    public static Biome lunarMegaregolith(HolderGetter<PlacedFeature> features, HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        return new Biome.BiomeBuilder()
                .temperature(1)
                .downfall(0)
                .hasPrecipitation(false)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .fogColor(0xdedede)
                        .waterColor(0xbacce0)
                        .waterFogColor(0xbacce0)
                        .skyColor(0xffffff)
                        .build())
                .generationSettings(new BiomeGenerationSettings(
                        ImmutableMap.of(
                                GenerationStep.Carving.AIR, // extra starting crater carving step compared to the maria. This also allows for overlapping small craters.
                                HolderSet.direct(carvers.getOrThrow(RocketCarvers.STANDARD_CRATER), carvers.getOrThrow(RocketCarvers.STRAIGHT_RILLE), carvers.getOrThrow(RocketCarvers.SINUOUS_RILLE), carvers.getOrThrow(RocketCarvers.STANDARD_CRATER))
                        ),
                        List.of(
                                HolderSet.direct(features.getOrThrow(PlacedFeatures.LUNAR_ROCK))
                        )
                ))
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .build();
    }

}
