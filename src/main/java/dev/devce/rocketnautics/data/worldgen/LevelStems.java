package dev.devce.rocketnautics.data.worldgen;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.data.worldgen.noise.RocketNoiseGenSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

import java.util.List;
import java.util.Optional;

/**
 * @see dev.devce.rocketnautics.content.RocketDimensions
 */
public class LevelStems {
    public static final ResourceKey<LevelStem> DEEP_SPACE = register("deep_space");
    public static final ResourceKey<LevelStem> MOON = register("moon");

    public static void bootstrap(BootstrapContext<LevelStem> context) {
        HolderGetter<NoiseGeneratorSettings> noise = context.lookup(Registries.NOISE_SETTINGS);
        HolderGetter<DimensionType> types = context.lookup(Registries.DIMENSION_TYPE);
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        context.register(DEEP_SPACE, new LevelStem(
                types.getOrThrow(DimensionTypes.DEEP_SPACE),
                new FlatLevelSource(new FlatLevelGeneratorSettings(
                        Optional.empty(),
                        biomes.getOrThrow(Biomes.THE_VOID),
                        List.of()
                ))
        ));
        context.register(MOON, new LevelStem(
                types.getOrThrow(DimensionTypes.MOON),
                new NoiseBasedChunkGenerator(
                        MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(ImmutableList.<Pair<Climate.ParameterPoint, Holder<Biome>>>builder()
                                .add(Pair.of(Climate.parameters(
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.span(-0.05f, 2f),
                                        Climate.Parameter.span(-2, 2),
                                        Climate.Parameter.span(-2, 0f),
                                        Climate.Parameter.span(-1, 1),
                                        0
                                        ), biomes.getOrThrow(RocketBiomes.LUNAR_HIGHLANDS)))
                                .add(Pair.of(Climate.parameters(
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.span(-2f, -0.05f),
                                        Climate.Parameter.span(-2, 2),
                                        Climate.Parameter.span(-2, 2),
                                        Climate.Parameter.span(-1, 1),
                                        0
                                ), biomes.getOrThrow(RocketBiomes.LUNAR_MARIA)))
                                .add(Pair.of(Climate.parameters(
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.span(0.01f, 2f),
                                        Climate.Parameter.span(-2, 2),
                                        Climate.Parameter.span(-2, 2),
                                        Climate.Parameter.span(-2, -1),
                                        0.01f
                                ), biomes.getOrThrow(RocketBiomes.LUNAR_AGED_CHASM)))
                                .add(Pair.of(Climate.parameters(
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.span(-2f, -0.05f),
                                        Climate.Parameter.span(-2, 2),
                                        Climate.Parameter.span(-2, 2),
                                        Climate.Parameter.span(-2, -1),
                                        0.01f
                                ), biomes.getOrThrow(RocketBiomes.LUNAR_BASALT_CHASM)))
                                .add(Pair.of(Climate.parameters(
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.span(0.01f, 2f),
                                        Climate.Parameter.span(-2, 2),
                                        Climate.Parameter.span(-2, 2),
                                        Climate.Parameter.span(1, 2),
                                        0
                                ), biomes.getOrThrow(RocketBiomes.LUNAR_AGED_SPIKES)))
                                .add(Pair.of(Climate.parameters(
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.span(-2f, -0.05f),
                                        Climate.Parameter.span(-2, 2),
                                        Climate.Parameter.span(-2, 2),
                                        Climate.Parameter.span(1, 2),
                                        0
                                ), biomes.getOrThrow(RocketBiomes.LUNAR_BASALT_SPIKES)))
                                .add(Pair.of(Climate.parameters(
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.point(0),
                                        Climate.Parameter.span(-0.05f, 2f),
                                        Climate.Parameter.span(-2, 2),
                                        Climate.Parameter.span(0f, 2),
                                        Climate.Parameter.span(-1, 1),
                                        0
                                ), biomes.getOrThrow(RocketBiomes.LUNAR_MEGAREGOLITH)))
                                .build())),
                        noise.getOrThrow(RocketNoiseGenSettings.MOON_GENERATOR)
                )
        ));
    }

    private static ResourceKey<LevelStem> register(String name) {
        return ResourceKey.create(Registries.LEVEL_STEM, RocketNautics.path(name));
    }
}
