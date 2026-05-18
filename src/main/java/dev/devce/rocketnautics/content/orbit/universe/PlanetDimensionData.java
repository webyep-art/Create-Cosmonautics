package dev.devce.rocketnautics.content.orbit.universe;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record PlanetDimensionData(ResourceKey<Level> key, int transitionHeight) {

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceKey(key);
        buf.writeVarInt(transitionHeight);
    }

    public static PlanetDimensionData read(FriendlyByteBuf buf) {
        ResourceKey<Level> key = buf.readResourceKey(Registries.DIMENSION);
        int transitionHeight = buf.readVarInt();
        return new PlanetDimensionData(key, transitionHeight);
    }
}
