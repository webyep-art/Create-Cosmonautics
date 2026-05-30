package dev.devce.rocketnautics.data.worldgen;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.registry.RocketBlocks;
import dev.devce.rocketnautics.registry.RocketFeatures;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.ColumnFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseProvider;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.List;

public class ConfiguredFeatures {
    public static final ResourceKey<ConfiguredFeature<?, ?>> TITANIUM_ORE = register("titanium_ore");
    public static final ResourceKey<ConfiguredFeature<?, ?>> LUNAR_ROCK = register("moon/rock");
    public static final ResourceKey<ConfiguredFeature<?, ?>> LUNAR_MOSS = register("moon/moss");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SMALL_AGED_COLUMNS = register("moon/small_aged_columns");
    public static final ResourceKey<ConfiguredFeature<?, ?>> LARGE_AGED_COLUMNS = register("moon/large_aged_columns");
    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        context.register(TITANIUM_ORE, new ConfiguredFeature<>(
                Feature.ORE,
                new OreConfiguration(
                        List.of(
                                OreConfiguration.target(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), RocketBlocks.TITANIUM_ORE.getDefaultState()),
                                OreConfiguration.target(new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES), RocketBlocks.DEEPSLATE_TITANIUM_ORE.getDefaultState())
                        ),
                        9
                )
        ));
        HolderGetter<NormalNoise.NoiseParameters> noises = context.lookup(Registries.NOISE);
        context.register(LUNAR_ROCK, new ConfiguredFeature<>(
                Feature.SIMPLE_BLOCK,
                new SimpleBlockConfiguration(
                        new NoiseProvider(
                                385934896,
                                new NormalNoise.NoiseParameters(-3, DoubleList.of(1, 2, 1)),
                                1f,
                                List.of(RocketBlocks.LUNAR_ROCK_SMOOTH.getDefaultState(), RocketBlocks.LUNAR_ROCK_SPIKY.getDefaultState(), RocketBlocks.LUNAR_ROCK_TALL.getDefaultState())
                        )
                )
        ));
        context.register(LUNAR_MOSS, new ConfiguredFeature<>(
                Feature.SIMPLE_BLOCK,
                new SimpleBlockConfiguration(
                        new NoiseProvider(
                                58934673468485L,
                                new NormalNoise.NoiseParameters(-3, DoubleList.of(1, 2, 1)),
                                1f,
                                List.of(RocketBlocks.LUNAR_MOSS_SCRAGGLY.getDefaultState(), RocketBlocks.LUNAR_MOSS_SHORT.getDefaultState(), RocketBlocks.LUNAR_MOSS_STIFF.getDefaultState())
                        )
                )
        ));
        context.register(SMALL_AGED_COLUMNS, new ConfiguredFeature<>(
                RocketFeatures.AGED_COLUMNS.get(),
                new ColumnFeatureConfiguration(ConstantInt.of(1), UniformInt.of(1, 4))
        ));
        context.register(LARGE_AGED_COLUMNS, new ConfiguredFeature<>(
                RocketFeatures.AGED_COLUMNS.get(),
                new ColumnFeatureConfiguration(UniformInt.of(2, 3), UniformInt.of(5, 10))
        ));
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> register(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, RocketNautics.path(name));
    }
}
