package dev.devce.rocketnautics.network;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public record DeepSpacePositionPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<DeepSpacePositionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "arbitrary"));
    public static final StreamCodec<FriendlyByteBuf, DeepSpacePositionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByteArray(payload.data),
            buf -> new DeepSpacePositionPayload(buf.readByteArray())
    );

    public static DeepSpacePositionPayload of(DeepSpacePosition position, UniverseDefinition universe) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        position.write(buf, universe);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        return new DeepSpacePositionPayload(bytes);
    }

    @OnlyIn(Dist.CLIENT)
    public void handle() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBytes(data);
        DeepSpaceHandler.receivePosition(buf);
        buf.release();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
