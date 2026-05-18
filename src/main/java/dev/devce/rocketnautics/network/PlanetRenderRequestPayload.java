package dev.devce.rocketnautics.network;

import dev.devce.rocketnautics.RocketNautics;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlanetRenderRequestPayload(int[] ids, int powerScale) implements CustomPacketPayload {
    public static final Type<PlanetRenderRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "planet_render_request"));

    public static final StreamCodec<FriendlyByteBuf, PlanetRenderRequestPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.ids().length);
                for (int id : payload.ids()) {
                    buf.writeVarInt(id);
                }
                buf.writeVarInt(payload.powerScale());
            }, (buf) -> {
                int length = buf.readVarInt();
                int[] ids = new int[length];
                for (int i = 0; i < length; i++) {
                    ids[i] = buf.readVarInt();
                }
                int scale = buf.readVarInt();
                return new PlanetRenderRequestPayload(ids, scale);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
