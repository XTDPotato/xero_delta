package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record InspectRequestPacket() implements CustomPacketPayload {
    public static final Type<InspectRequestPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "inspect_request"));

    public static final StreamCodec<FriendlyByteBuf, InspectRequestPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> { }, buffer -> new InspectRequestPacket());

    @Override
    public Type<InspectRequestPacket> type() {
        return TYPE;
    }

    public static void handle(InspectRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new InspectAnimationPacket(player.getId()));
        });
    }
}
