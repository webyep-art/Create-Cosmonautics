package dev.devce.rocketnautics.network;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.client.PlanetColors;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlanetRenderPayload(int id, byte[] renderData, int powerSize) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlanetRenderPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "planet_render"));

    public static final StreamCodec<ByteBuf, PlanetRenderPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.id());
                buf.writeBytes(payload.renderData());
                buf.writeInt(payload.powerSize());
            }, (buf) -> {
                int id = buf.readInt();
                byte[] data = new byte[PlanetColors.ARRAY_SIZE];
                buf.readBytes(data);
                int powerSize = buf.readInt();
                return new PlanetRenderPayload(id, data, powerSize);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
