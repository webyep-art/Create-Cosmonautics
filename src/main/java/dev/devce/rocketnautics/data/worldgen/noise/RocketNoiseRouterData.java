package dev.devce.rocketnautics.data.worldgen.noise;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.world.StretchedNoise;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class RocketNoiseRouterData {
    public static final ResourceKey<DensityFunction> MOON_CONTINENTS = register("moon/continents");
    public static final ResourceKey<DensityFunction> MOON_RIDGES = register("moon/ridges");
    public static final ResourceKey<DensityFunction> MOON_RIDGES_FOLDED = register("moon/ridges_folded");
    public static final ResourceKey<DensityFunction> MOON_WRINKLE_RIDGES = register("moon/wrinkle_ridges");
    public static final ResourceKey<DensityFunction> MOON_EROSION = register("moon/erosion");
    public static final ResourceKey<DensityFunction> MOON_VARIANCE = register("moon/variance");
    public static final ResourceKey<DensityFunction> MOON_MEGAREGOLITH = register("moon/megaregolith");
    // reminders for noise router
    // "ridges" is also known as "weirdness" and is used to further generate "ridges folded" which is then used as "ridges" and/or "peaks and valleys"
    // confusing? yep. The goal is a specific subdivision structure in the "ridges folded" noise.
    public static void bootstrap(BootstrapContext<DensityFunction> context) {
        HolderGetter<DensityFunction> densities = context.lookup(Registries.DENSITY_FUNCTION);
        HolderGetter<NormalNoise.NoiseParameters> noises = context.lookup(Registries.NOISE);
        DensityFunction shiftX = getFunction(densities, ResourceKey.create(Registries.DENSITY_FUNCTION, ResourceLocation.withDefaultNamespace("shift_x")));
        DensityFunction shiftZ = getFunction(densities, ResourceKey.create(Registries.DENSITY_FUNCTION, ResourceLocation.withDefaultNamespace("shift_z")));
        DensityFunction continents = registerAndWrap(context, MOON_CONTINENTS, DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(RocketNoiseData.MOON_CONTINENTS))));
        DensityFunction ridges = registerAndWrap(context, MOON_RIDGES, DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(RocketNoiseData.MOON_RIDGES))));
        DensityFunction ridgesFolded = registerAndWrap(context, MOON_RIDGES_FOLDED, peaksAndValleys(ridges));
        DensityFunction wrinkleRidges = registerAndWrap(context, MOON_WRINKLE_RIDGES, DensityFunctions.flatCache(new StretchedNoise(new DensityFunction.NoiseHolder(noises.getOrThrow(RocketNoiseData.MOON_WRINKLE_RIDGES)), 1, 0.1, 0.1)));
        context.register(MOON_EROSION, DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(RocketNoiseData.MOON_EROSION))));
        context.register(MOON_VARIANCE, DensityFunctions.flatCache(
                DensityFunctions.mul(
                        DensityFunctions.constant(0.6),
                        DensityFunctions.add(
                                DensityFunctions.mul(
                                        ridgesFolded,
                                        DensityFunctions.mul(// zero out ridges folded over the course from 0.01 to -0.04
                                                DensityFunctions.constant(20),
                                                DensityFunctions.add(continents, DensityFunctions.constant(0.04))
                                        ).clamp(0, 1)
                                ),
                                DensityFunctions.mul(
                                        wrinkleRidges.quarterNegative(),
                                        DensityFunctions.mul(// zero out wrinkle ridges over the course from -0.03 to -0.01
                                                DensityFunctions.constant(-50),
                                                DensityFunctions.add(continents, DensityFunctions.constant(0.01))
                                        ).clamp(0, 1)
                                )
                        )
                )
        ));
        context.register(MOON_MEGAREGOLITH, peaksAndValleys(new StretchedNoise(new DensityFunction.NoiseHolder(noises.getOrThrow(RocketNoiseData.MOON_MEGAREGOLITH)), 0.2, 0.5, 1)));
    }

    public static NoiseRouter moon(HolderGetter<NormalNoise.NoiseParameters> noises, HolderGetter<DensityFunction> densities) {
        // general ideas behind moon worldgen:
        // continents determines highlands or maria. Major influence on average world height.
        // ridges determines appearance of special biomes -- very positive is unnatural basalt spikes, very negative is chasms
        // ridges folded determines some terrain detail. Minor, localized influence of average world height; primarily influences the highlands to form rugged terrain
        // lava noise is stretched massively along one axis, primarily influences the maria to form wrinkle ridges
        // erosion is just a miscellaneous parameter used for whatever
        // utilize canyon carvers with extreme values to generate lunar rilles
        // utilize a custom carver to generate craters
        DensityFunction shiftX = getFunction(densities, ResourceKey.create(Registries.DENSITY_FUNCTION, ResourceLocation.withDefaultNamespace("shift_x")));
        DensityFunction shiftZ = getFunction(densities, ResourceKey.create(Registries.DENSITY_FUNCTION, ResourceLocation.withDefaultNamespace("shift_z")));
        var continents = getFunction(densities, MOON_CONTINENTS);
        var wrinkleRidges = getFunction(densities, MOON_WRINKLE_RIDGES);
        var ridges = getFunction(densities, MOON_RIDGES);
        var megaregolith = getFunction(densities, MOON_MEGAREGOLITH);
        var variance = getFunction(densities, MOON_VARIANCE);
        var ridgesCoordinate = toCoordinate(densities.getOrThrow(MOON_RIDGES));
        var erosionCoordinate = toCoordinate(densities.getOrThrow(MOON_EROSION));
        var continentsCoordinate = toCoordinate(densities.getOrThrow(MOON_CONTINENTS));
        // decreases by 1 every 64 blocks
        var depth = DensityFunctions.yClampedGradient(-64, 256, 2, -3);
        var primaryHeightSpline = DensityFunctions.spline(
                CubicSpline.builder(continentsCoordinate)
                        .addPoint(-0.05f, 0f)
                        .addPoint(0f, CubicSpline.builder(erosionCoordinate)
                                .addPoint(-1f, 0f)
                                .addPoint(-0.5f, 0.9f)
                                .addPoint(-0.2f, 0.99f)
                                .addPoint(2f, 1f)
                                .build())
                        .addPoint(0.01f, 1f)
                        .build()
        );
        var primaryDensityModifier = DensityFunctions.add(
                DensityFunctions.add(
                        DensityFunctions.spline( // chasms
                                CubicSpline.builder(ridgesCoordinate)
                                        .addPoint(-1.5f, -10f)
                                        .addPoint(-1.1f, -5f)
                                        .addPoint(-1, 0)
                                        .build()
                        ).clamp(-2, 0),
                        depth
                ),
                DensityFunctions.add(
                        variance,
                        DensityFunctions.mul(
                                DensityFunctions.mul(
                                        DensityFunctions.add(
                                                DensityFunctions.add(
                                                        depth,
                                                        DensityFunctions.constant(0.6)
                                                ),
                                                DensityFunctions.mul(
                                                        variance,
                                                        DensityFunctions.constant(0.6)
                                                )
                                        ).clamp(0, 1),
                                        megaregolith
                                ).square(),
                                DensityFunctions.mul(// zero out megaregolith from 0.05 to 0
                                        DensityFunctions.constant(-60),
                                        continents
                                ).clamp(-3, 0)
                        )
                )
        );
        return new NoiseRouter(
                DensityFunctions.noise(noises.getOrThrow(Noises.AQUIFER_BARRIER), 1, 0.5),
                DensityFunctions.noise(noises.getOrThrow(Noises.AQUIFER_FLUID_LEVEL_FLOODEDNESS), 1, 0.67),
                DensityFunctions.noise(noises.getOrThrow(Noises.AQUIFER_FLUID_LEVEL_SPREAD), 1, 0.7142857142857143),
                wrinkleRidges,
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                continents,
                getFunction(densities, MOON_EROSION),
                depth,
                ridges,
                DensityFunctions.interpolated(
                        DensityFunctions.add(
                                DensityFunctions.rangeChoice(continents, -0.05f, 0.01f,
                                        primaryHeightSpline.clamp(0, 1),
                                        DensityFunctions.rangeChoice(continents, 0, 100,
                                                DensityFunctions.constant(1),
                                                DensityFunctions.constant(0))
                                ),
                                primaryDensityModifier
                        )
                ),
                DensityFunctions.add(
                        DensityFunctions.interpolated(
                                DensityFunctions.add(
                                        DensityFunctions.rangeChoice(continents, -0.05f, 0.01f,
                                                DensityFunctions.add(
                                                        primaryHeightSpline,
                                                        DensityFunctions.mul(// maximize effects away from the transition interfaces
                                                                DensityFunctions.mul(
                                                                        DensityFunctions.constant(0.5),
                                                                        DensityFunctions.add(
                                                                                DensityFunctions.constant(-0.03),
                                                                                DensityFunctions.add(DensityFunctions.constant(0.02), continents).abs()
                                                                        )
                                                                ),
                                                                DensityFunctions.shiftedNoise2d(shiftX, DensityFunctions.constant(0), 1, noises.getOrThrow(RocketNoiseData.MOON_JAGGEDNESS))
                                                        )
                                                ).clamp(0, 1),
                                                DensityFunctions.rangeChoice(continents, 0, 100,
                                                        DensityFunctions.constant(1),
                                                        DensityFunctions.constant(0))
                                        ),
                                        DensityFunctions.add(
                                                primaryDensityModifier,
                                                DensityFunctions.mul(
                                                        DensityFunctions.constant(0.0005),
                                                        DensityFunctions.shiftedNoise2d(DensityFunctions.constant(0), shiftZ, 1, noises.getOrThrow(RocketNoiseData.MOON_JAGGEDNESS))
                                                )
                                        )
                                )
                        ),
                        DensityFunctions.yClampedGradient(-64, -56, 8, 0)
                ),
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero()
        );
    }

    private static DensityFunction peaksAndValleys(DensityFunction p_224438_) {
        return DensityFunctions.mul(
                DensityFunctions.add(
                        DensityFunctions.add(p_224438_.abs(), DensityFunctions.constant(-0.6666666666666666)).abs(), DensityFunctions.constant(-0.3333333333333333)
                ),
                DensityFunctions.constant(-3.0)
        );
    }

    private static DensityFunctions.Spline.Coordinate toCoordinate(Holder<DensityFunction> func) {
        return new DensityFunctions.Spline.Coordinate(func);
    }

    private static DensityFunction registerAndWrap(BootstrapContext<DensityFunction> context, ResourceKey<DensityFunction> p_255905_, DensityFunction p_255856_) {
        return new DensityFunctions.HolderHolder(context.register(p_255905_, p_255856_));
    }

    private static DensityFunction getFunction(HolderGetter<DensityFunction> p_256312_, ResourceKey<DensityFunction> p_256077_) {
        return new DensityFunctions.HolderHolder(p_256312_.getOrThrow(p_256077_));
    }

    private static ResourceKey<DensityFunction> register(String name) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, RocketNautics.path(name));
    }
}
