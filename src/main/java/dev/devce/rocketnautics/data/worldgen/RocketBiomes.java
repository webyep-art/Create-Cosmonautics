package dev.devce.rocketnautics.data.worldgen;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.data.worldgen.biome.OverworldMoonBiomes;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class RocketBiomes {
    public static ResourceKey<Biome> LUNAR_MARIA = register("lunar_maria");
    public static ResourceKey<Biome> LUNAR_HIGHLANDS = register("lunar_highlands");
    public static ResourceKey<Biome> LUNAR_BASALT_CHASM = register("lunar_basalt_chasm");
    public static ResourceKey<Biome> LUNAR_AGED_CHASM = register("lunar_aged_chasm");
    public static ResourceKey<Biome> LUNAR_BASALT_SPIKES = register("lunar_basalt_spikes");
    public static ResourceKey<Biome> LUNAR_AGED_SPIKES = register("lunar_aged_spikes");
    public static ResourceKey<Biome> LUNAR_MEGAREGOLITH = register("lunar_megaregolith");

    public static void bootstrap(BootstrapContext<Biome> context) {
        HolderGetter<PlacedFeature> features = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);
        context.register(LUNAR_MARIA, OverworldMoonBiomes.lunarMaria(features, carvers));
        context.register(LUNAR_HIGHLANDS, OverworldMoonBiomes.lunarHighlands(features, carvers));
        context.register(LUNAR_BASALT_CHASM, OverworldMoonBiomes.lunarBasaltChasm(features, carvers));
        context.register(LUNAR_AGED_CHASM, OverworldMoonBiomes.lunarAgedChasm(features, carvers));
        context.register(LUNAR_BASALT_SPIKES, OverworldMoonBiomes.lunarBasaltSpikes(features, carvers));
        context.register(LUNAR_AGED_SPIKES, OverworldMoonBiomes.lunarAgedSpikes(features, carvers));
        context.register(LUNAR_MEGAREGOLITH, OverworldMoonBiomes.lunarMegaregolith(features, carvers));
    }

    private static ResourceKey<Biome> register(String p_48229_) {
        return ResourceKey.create(Registries.BIOME, RocketNautics.path(p_48229_));
    }
}
