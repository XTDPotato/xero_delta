package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.SafetyBoxInspectAnimation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record InspectAnimationPacket(int entityId) implements CustomPacketPayload {
    public static final Type<InspectAnimationPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "inspect_animation"));

    public static final StreamCodec<FriendlyByteBuf, InspectAnimationPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeVarInt(packet.entityId),
        buffer -> new InspectAnimationPacket(buffer.readVarInt()));

    @Override
    public Type<InspectAnimationPacket> type() {
        return TYPE;
    }

    public static void handle(InspectAnimationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SafetyBoxInspectAnimation.start(packet.entityId));
    }
}
