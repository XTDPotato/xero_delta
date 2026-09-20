package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.XeroTitleOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-bound rich notice rendered by Xero Delta instead of vanilla actionbar text. */
public record XeroTitlePacket(String position, String content, int durationTicks)
    implements CustomPacketPayload {
    public static final Type<XeroTitlePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "title_actionbar"));
    public static final StreamCodec<FriendlyByteBuf, XeroTitlePacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeUtf(packet.position, 64);
            buf.writeUtf(packet.content, 16_384);
            buf.writeVarInt(packet.durationTicks);
        },
        buf -> new XeroTitlePacket(buf.readUtf(64), buf.readUtf(16_384),
            Math.max(10, Math.min(1_200, buf.readVarInt())))
    );

    @Override
    public Type<XeroTitlePacket> type() {
        return TYPE;
    }

    public static void handle(XeroTitlePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> XeroTitleOverlay.show(
            packet.position, packet.content, packet.durationTicks));
    }
}