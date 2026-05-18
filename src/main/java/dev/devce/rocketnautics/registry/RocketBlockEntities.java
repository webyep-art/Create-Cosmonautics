package dev.devce.rocketnautics.registry;

import com.tterrag.registrate.util.entry.BlockEntityEntry;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.client.render.HologramTableRenderer;
import dev.devce.rocketnautics.client.render.VectorThrusterRenderer;
import dev.devce.rocketnautics.content.blocks.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class RocketBlockEntities {
    private static final RocketRegistrate REGISTRATE = RocketNautics.getRegistrate();

    public static final BlockEntityEntry<RocketThrusterBlockEntity> ROCKET_THRUSTER = REGISTRATE
            .blockEntity("rocket_thruster", RocketThrusterBlockEntity::new)
            .validBlocks(RocketBlocks.ROCKET_THRUSTER)
            .register();

    public static final BlockEntityEntry<BoosterThrusterBlockEntity> BOOSTER_THRUSTER = REGISTRATE
            .blockEntity("booster_thruster", BoosterThrusterBlockEntity::new)
            .validBlocks(RocketBlocks.BOOSTER_THRUSTER)
            .register();

    public static final BlockEntityEntry<VectorThrusterBlockEntity> VECTOR_THRUSTER = REGISTRATE
            .blockEntity("vector_thruster", VectorThrusterBlockEntity::new)
            .validBlocks(RocketBlocks.VECTOR_THRUSTER)
            .renderer(() -> VectorThrusterRenderer::new)
            .register();

    public static final BlockEntityEntry<RCSThrusterBlockEntity> RCS_THRUSTER = REGISTRATE
            .blockEntity("rcs_thruster", RCSThrusterBlockEntity::new)
            .validBlocks(RocketBlocks.RCS_THRUSTER)
            .register();

    public static final BlockEntityEntry<SputnikBlockEntity> SPUTNIK = REGISTRATE
            .blockEntity("sputnik", SputnikBlockEntity::new)
            .validBlocks(RocketBlocks.SPUTNIK)
            .register();

    public static final BlockEntityEntry<HologramTableBlockEntity> HOLOGRAM_TABLE = REGISTRATE
            .blockEntity("hologram_table", HologramTableBlockEntity::new)
            .validBlocks(RocketBlocks.HOLOGRAM_TABLE)
            .renderer(() -> HologramTableRenderer::new)
            .register();

    public static void register(IEventBus eventBus) {
        eventBus.addListener(RocketBlockEntities::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ROCKET_THRUSTER.get(), (be, side) -> be.fuelTank);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, VECTOR_THRUSTER.get(), (be, side) -> be.fuelTank);


        if (net.neoforged.fml.ModList.get().isLoaded("computercraft")) {
            dev.devce.rocketnautics.compat.computercraft.ComputerCraftCompat.registerCapabilities(event);
        }
    }
}
