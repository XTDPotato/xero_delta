package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.SafetyBoxInspectAnimation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Stops an inspection animation on the local client. */
public record InspectCancelPacket(int entityId) implements CustomPacketPayload {
    public static final Type<InspectCancelPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "inspect_cancel"));

    public static final StreamCodec<FriendlyByteBuf, InspectCancelPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeVarInt(packet.entityId),
        buffer -> new InspectCancelPacket(buffer.readVarInt()));

    @Override
    public Type<InspectCancelPacket> type() {
        return TYPE;
    }

    public static void handle(InspectCancelPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SafetyBoxInspectAnimation.cancel(packet.entityId));
    }
}
