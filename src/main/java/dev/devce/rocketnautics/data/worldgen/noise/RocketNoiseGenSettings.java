package dev.devce.rocketnautics.data.worldgen.noise;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.data.worldgen.RocketBiomes;
import dev.devce.rocketnautics.registry.RocketBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.List;

public class RocketNoiseGenSettings {
    public static final ResourceKey<NoiseGeneratorSettings> MOON_GENERATOR = register("moon");

    public static void bootstrap(BootstrapContext<NoiseGeneratorSettings> context) {
        HolderGetter<DensityFunction> densities = context.lookup(Registries.DENSITY_FUNCTION);
        HolderGetter<NormalNoise.NoiseParameters> noises = context.lookup(Registries.NOISE);
        context.register(MOON_GENERATOR, new NoiseGeneratorSettings(
                NoiseSettings.create(-64, 384, 1, 2),
                RocketBlocks.LUNAR_AGED_BASALT.getDefaultState(),
                Blocks.MAGMA_BLOCK.defaultBlockState(),
                RocketNoiseRouterData.moon(noises, densities),
                SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.verticalGradient("bedrock_floor", VerticalAnchor.bottom(), VerticalAnchor.aboveBottom(5)), SurfaceRules.state(Blocks.BEDROCK.defaultBlockState())),
                        SurfaceRules.ifTrue(
                                SurfaceRules.isBiome(RocketBiomes.LUNAR_MARIA, RocketBiomes.LUNAR_BASALT_SPIKES, RocketBiomes.LUNAR_BASALT_CHASM),
                                SurfaceRules.sequence(
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.hole(),
                                                SurfaceRules.sequence(
                                                        SurfaceRules.ifTrue(
                                                                SurfaceRules.stoneDepthCheck(-1, false, 2, CaveSurface.FLOOR),
                                                                SurfaceRules.state(Blocks.AIR.defaultBlockState())
                                                        ),
                                                        SurfaceRules.ifTrue(
                                                                SurfaceRules.isBiome(RocketBiomes.LUNAR_MARIA),
                                                                SurfaceRules.ifTrue(
                                                                        SurfaceRules.not(SurfaceRules.stoneDepthCheck(1, false, 5, CaveSurface.FLOOR)),
                                                                        SurfaceRules.ifTrue(
                                                                                SurfaceRules.noiseCondition(RocketNoiseData.MOON_JAGGEDNESS, -0.5, 0.5),
                                                                                SurfaceRules.state(Blocks.LAVA.defaultBlockState())
                                                                        )
                                                                )
                                                        ),
                                                        SurfaceRules.state(Blocks.BASALT.defaultBlockState())
                                                )
                                        ),
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.steep(),
                                                SurfaceRules.state(Blocks.BASALT.defaultBlockState())
                                        ),
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.noiseCondition(RocketNoiseData.MOON_EROSION, 0, 2),
                                                SurfaceRules.state(Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X))
                                        ),
                                        SurfaceRules.state(Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z))
                                )
                        ),
                        SurfaceRules.ifTrue(
                                SurfaceRules.isBiome(RocketBiomes.LUNAR_AGED_CHASM, RocketBiomes.LUNAR_AGED_SPIKES),
                                SurfaceRules.sequence(
                                        SurfaceRules.state(RocketBlocks.LUNAR_AGED_BASALT.getDefaultState())
                                )
                        ),
                        SurfaceRules.ifTrue(
                                SurfaceRules.isBiome(RocketBiomes.LUNAR_HIGHLANDS),
                                SurfaceRules.sequence(
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.stoneDepthCheck(-3, false, 11, CaveSurface.FLOOR),
                                                SurfaceRules.ifTrue(
                                                        SurfaceRules.noiseCondition(RocketNoiseData.MOON_JAGGEDNESS, -0.6, 0.6),
                                                        SurfaceRules.state(RocketBlocks.LUNAR_LOOSE_REGOLITH.getDefaultState())
                                                )
                                        ),
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.stoneDepthCheck(10, true, 20, CaveSurface.FLOOR),
                                                SurfaceRules.state(RocketBlocks.LUNAR_REGOLITH.getDefaultState())
                                        ),
                                        SurfaceRules.state(RocketBlocks.LUNAR_AGED_BASALT.getDefaultState())
                                )
                        ),
                        SurfaceRules.ifTrue(
                                SurfaceRules.isBiome(RocketBiomes.LUNAR_MEGAREGOLITH),
                                SurfaceRules.sequence(
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.steep(),
                                                SurfaceRules.state(RocketBlocks.LUNAR_AGED_BASALT.getDefaultState())
                                        ),
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.stoneDepthCheck(0, true, 0, CaveSurface.FLOOR),
                                                SurfaceRules.sequence(
                                                        SurfaceRules.ifTrue(
                                                                SurfaceRules.noiseCondition(RocketNoiseData.MOON_JAGGEDNESS, 0.6, 2),
                                                                SurfaceRules.state(RocketBlocks.LUNAR_LOOSE_REGOLITH.getDefaultState())
                                                        ),
                                                        SurfaceRules.ifTrue(
                                                                SurfaceRules.noiseCondition(RocketNoiseData.MOON_JAGGEDNESS, -2, -0.6),
                                                                SurfaceRules.state(RocketBlocks.LUNAR_REGOLITH.getDefaultState())
                                                        )
                                                )
                                        ),
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.noiseCondition(RocketNoiseData.MOON_JAGGEDNESS, 0.4, 2),
                                                SurfaceRules.state(RocketBlocks.LUNAR_AGED_BASALT.getDefaultState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X))
                                        ),
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.noiseCondition(RocketNoiseData.MOON_JAGGEDNESS, -2, -0.4),
                                                SurfaceRules.state(RocketBlocks.LUNAR_AGED_BASALT.getDefaultState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z))
                                        ),
                                        SurfaceRules.state(RocketBlocks.LUNAR_AGED_BASALT.getDefaultState())
                                )
                        )
                ),
                List.of(),
                0,
                false,
                true,
                false,
                false
        ));
    }

    private static ResourceKey<NoiseGeneratorSettings> register(String name) {
        return ResourceKey.create(Registries.NOISE_SETTINGS, RocketNautics.path(name));
    }
}
