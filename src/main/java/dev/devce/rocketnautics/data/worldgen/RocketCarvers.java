package dev.devce.rocketnautics.data.worldgen;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.world.ConfigurablyBiasedToBottomFloat;
import dev.devce.rocketnautics.content.world.CraterCarverConfiguration;
import dev.devce.rocketnautics.content.world.RilleCarverConfiguration;
import dev.devce.rocketnautics.registry.RocketTags;
import dev.devce.rocketnautics.registry.RocketWorldCarvers;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.TrapezoidFloat;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.carver.*;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;

public class RocketCarvers {
    public static final ResourceKey<ConfiguredWorldCarver<?>> SINUOUS_RILLE = register("sinuous_rille");
    public static final ResourceKey<ConfiguredWorldCarver<?>> STRAIGHT_RILLE = register("straight_rille");
    public static final ResourceKey<ConfiguredWorldCarver<?>> STANDARD_CRATER = register("standard_crater");
    public static final ResourceKey<ConfiguredWorldCarver<?>> MARIA_CAVES = register("moon/maria_caves");
    public static final ResourceKey<ConfiguredWorldCarver<?>> MOON_CANYON = register("moon/canyon");

    public static void bootstrap(BootstrapContext<ConfiguredWorldCarver<?>> context) {
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);
        context.register(STRAIGHT_RILLE, RocketWorldCarvers.RILLE_CARVER.get().configured(
                new RilleCarverConfiguration(
                        0.025f,
                        ConstantHeight.of(VerticalAnchor.top()),
                        ConstantFloat.of(1),
                        VerticalAnchor.bottom(),
                        CarverDebugSettings.DEFAULT,
                        blocks.getOrThrow(RocketTags.BlockTags.RILLE_CARVABLE.tag),
                        12,
                        9,
                        5,
                        10f,
                        0f,
                        3948672
                )
        ));
        context.register(SINUOUS_RILLE, RocketWorldCarvers.RILLE_CARVER.get().configured(
                new RilleCarverConfiguration(
                        0.03f,
                        ConstantHeight.of(VerticalAnchor.top()),
                        ConstantFloat.of(1),
                        VerticalAnchor.bottom(),
                        CarverDebugSettings.DEFAULT,
                        blocks.getOrThrow(RocketTags.BlockTags.RILLE_CARVABLE.tag),
                        12,
                        9,
                        5,
                        60f,
                        50f,
                        459837463
                )
        ));
        context.register(STANDARD_CRATER, RocketWorldCarvers.CRATER_CARVER.get().configured(
                new CraterCarverConfiguration(
                        0.05f,
                        ConstantHeight.of(VerticalAnchor.top()),
                        ConstantFloat.of(1),
                        VerticalAnchor.bottom(),
                        CarverDebugSettings.DEFAULT,
                        blocks.getOrThrow(RocketTags.BlockTags.CRATER_CARVABLE.tag),
                        ConfigurablyBiasedToBottomFloat.of(3, 16 * 6, 5, 3),
                        UniformFloat.of(0.4f, 0.6f),
                        UniformFloat.of(1f, 1.3f)
                )
        ));
        context.register(MARIA_CAVES, WorldCarver.CAVE.configured(
                new CaveCarverConfiguration(
                        0.05F,
                        UniformHeight.of(VerticalAnchor.aboveBottom(8), VerticalAnchor.absolute(48)),
                        UniformFloat.of(0.1F, 0.9F),
                        VerticalAnchor.aboveBottom(8),
                        CarverDebugSettings.of(false, Blocks.CRIMSON_BUTTON.defaultBlockState()),
                        blocks.getOrThrow(RocketTags.BlockTags.GENERIC_CARVABLE.tag),
                        UniformFloat.of(0.7F, 1.4F),
                        UniformFloat.of(0.8F, 1.3F),
                        UniformFloat.of(-1.0F, -0.4F)
                )
        ));
        context.register(MOON_CANYON, WorldCarver.CANYON.configured(
                new CanyonCarverConfiguration(
                        0.02F,
                        UniformHeight.of(VerticalAnchor.absolute(10), VerticalAnchor.absolute(67)),
                        ConstantFloat.of(3.0F),
                        VerticalAnchor.aboveBottom(8),
                        CarverDebugSettings.of(false, Blocks.WARPED_BUTTON.defaultBlockState()),
                        blocks.getOrThrow(RocketTags.BlockTags.GENERIC_CARVABLE.tag),
                        UniformFloat.of(-0.125F, 0.125F),
                        new CanyonCarverConfiguration.CanyonShapeConfiguration(
                                UniformFloat.of(0.75F, 1.0F), TrapezoidFloat.of(0.0F, 6.0F, 2.0F), 3, UniformFloat.of(0.75F, 1.0F), 1.0F, 0.0F
                        )
                )
        ));
    }

    private static ResourceKey<ConfiguredWorldCarver<?>> register(String name) {
        return ResourceKey.create(Registries.CONFIGURED_CARVER, RocketNautics.path(name));
    }
}
