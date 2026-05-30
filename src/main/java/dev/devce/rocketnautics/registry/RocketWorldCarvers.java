package dev.devce.rocketnautics.registry;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.world.CraterCarverConfiguration;
import dev.devce.rocketnautics.content.world.CraterWorldCarver;
import dev.devce.rocketnautics.content.world.RilleCarverConfiguration;
import dev.devce.rocketnautics.content.world.RilleWorldCarver;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RocketWorldCarvers {
    public static final DeferredRegister<WorldCarver<?>> CARVERS = DeferredRegister.create(Registries.CARVER, RocketNautics.MODID);

    public static final DeferredHolder<WorldCarver<?>, RilleWorldCarver> RILLE_CARVER = CARVERS.register("rille", () -> new RilleWorldCarver(RilleCarverConfiguration.CODEC));
    public static final DeferredHolder<WorldCarver<?>, CraterWorldCarver> CRATER_CARVER = CARVERS.register("crater", () -> new CraterWorldCarver(CraterCarverConfiguration.CODEC));

    public static void register(IEventBus eventBus) {
        CARVERS.register(eventBus);
    }
}
