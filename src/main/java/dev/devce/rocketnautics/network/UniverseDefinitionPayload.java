package dev.devce.rocketnautics.network;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UniverseDefinitionPayload(UniverseDefinition definition) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UniverseDefinitionPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "universe_definition"));

    public static final StreamCodec<FriendlyByteBuf, UniverseDefinitionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> payload.definition().write(buf),
            (buf) -> new UniverseDefinitionPayload(UniverseDefinition.read(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
