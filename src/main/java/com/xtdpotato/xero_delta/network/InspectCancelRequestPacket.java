package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client request to cancel the local player's inspection for all observers. */
public record InspectCancelRequestPacket() implements CustomPacketPayload {
    public static final Type<InspectCancelRequestPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "inspect_cancel_request"));

    public static final StreamCodec<FriendlyByteBuf, InspectCancelRequestPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> { }, buffer -> new InspectCancelRequestPacket());

    @Override
    public Type<InspectCancelRequestPacket> type() {
        return TYPE;
    }

    public static void handle(InspectCancelRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new InspectCancelPacket(player.getId()));
        });
    }
}
