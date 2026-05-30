package dev.devce.rocketnautics.content.orbit.universe;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record PlanetDimensionData(ResourceKey<Level> key, int transitionHeight, boolean renderUniverseInDimension, int controlDimensionDayTimeID, boolean applyGravityCorrectionToEntities) {

    public PlanetDimensionData(ResourceKey<Level> key, int transitionHeight, boolean renderUniverseInDimension, boolean applyGravityCorrectionToEntities) {
        this(key, transitionHeight, renderUniverseInDimension, -1, applyGravityCorrectionToEntities);
    }

    public boolean controlsDimensionDayTime() {
        return controlDimensionDayTimeID >= 0;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceKey(key);
        buf.writeVarInt(transitionHeight);
        buf.writeBoolean(renderUniverseInDimension);
        buf.writeVarInt(controlDimensionDayTimeID);
        buf.writeBoolean(applyGravityCorrectionToEntities);
    }

    public static PlanetDimensionData read(FriendlyByteBuf buf) {
        ResourceKey<Level> key = buf.readResourceKey(Registries.DIMENSION);
        int transitionHeight = buf.readVarInt();
        boolean renderUniverseInDimension = buf.readBoolean();
        int controlDimensionDayTimeID = buf.readVarInt();
        boolean applyGravityCorrectionToEntities = buf.readBoolean();
        return new PlanetDimensionData(key, transitionHeight, renderUniverseInDimension, controlDimensionDayTimeID, applyGravityCorrectionToEntities);
    }
}
