package dev.devce.rocketnautics.compat.computercraft;

import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.devce.rocketnautics.registry.RocketBlockEntities;

public class ComputerCraftCompat {
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), RocketBlockEntities.ROCKET_THRUSTER.get(), (be, side) -> new ThrusterPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), RocketBlockEntities.BOOSTER_THRUSTER.get(), (be, side) -> new ThrusterPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), RocketBlockEntities.VECTOR_THRUSTER.get(), (be, side) -> new ThrusterPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), RocketBlockEntities.RCS_THRUSTER.get(), (be, side) -> new ThrusterPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), RocketBlockEntities.SPUTNIK.get(), (be, side) -> new SputnikPeripheral(be));
    }
}
