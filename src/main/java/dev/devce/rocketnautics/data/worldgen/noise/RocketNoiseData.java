package dev.devce.rocketnautics.data.worldgen.noise;

import dev.devce.rocketnautics.RocketNautics;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class RocketNoiseData {
    public static final ResourceKey<NormalNoise.NoiseParameters> MOON_CONTINENTS = register("moon/continents");
    public static final ResourceKey<NormalNoise.NoiseParameters> MOON_RIDGES = register("moon/ridges");
    public static final ResourceKey<NormalNoise.NoiseParameters> MOON_WRINKLE_RIDGES = register("moon/wrinkle_ridges");
    public static final ResourceKey<NormalNoise.NoiseParameters> MOON_EROSION = register("moon/erosion");
    public static final ResourceKey<NormalNoise.NoiseParameters> MOON_JAGGEDNESS = register("moon/jaggedness");
    public static final ResourceKey<NormalNoise.NoiseParameters> MOON_MEGAREGOLITH = register("moon/megaregolith");
    public static void bootstrap(BootstrapContext<NormalNoise.NoiseParameters> context) {
        context.register(MOON_CONTINENTS, new NormalNoise.NoiseParameters(-10, DoubleList.of(2, 1, 1, 2, 0, 0, 0.5)));
        context.register(MOON_RIDGES, new NormalNoise.NoiseParameters(-8, DoubleList.of(1, 0, 1, 0, 1)));
        context.register(MOON_WRINKLE_RIDGES, new NormalNoise.NoiseParameters(-9, DoubleList.of(1, 2, 0, 0, 2, 0, 0, 1, 2, 0, 2, 4)));
        context.register(MOON_EROSION, new NormalNoise.NoiseParameters(-10, DoubleList.of(1, 0, 0, 0, 2, 2, 0, 0.5)));
        context.register(MOON_JAGGEDNESS, new NormalNoise.NoiseParameters(-3, DoubleList.of(1, 2, 1)));
        context.register(MOON_MEGAREGOLITH, new NormalNoise.NoiseParameters(-7, DoubleList.of(1, 2, 1, 4, 2)));
    }

    private static ResourceKey<NormalNoise.NoiseParameters> register(String name) {
        return ResourceKey.create(Registries.NOISE, RocketNautics.path(name));
    }
}

