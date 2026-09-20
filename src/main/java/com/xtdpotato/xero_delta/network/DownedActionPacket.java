package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.DownedManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative local-player actions available while downed. */
public record DownedActionPacket(byte action) implements CustomPacketPayload {
    public static final byte REQUEST_RESCUE = 0;
    public static final byte ABANDON_RESCUE = 1;
    public static final byte START_ABANDON_HOLD = 2;
    public static final byte CANCEL_ABANDON_HOLD = 3;
    public static final byte SWITCH_SPECTATOR = 4;
    public static final Type<DownedActionPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "downed_action"));
    public static final StreamCodec<FriendlyByteBuf, DownedActionPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeByte(packet.action),
        buffer -> new DownedActionPacket(buffer.readByte()));

    @Override
    public Type<DownedActionPacket> type() {
        return TYPE;
    }

    public static void handle(DownedActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (packet.action == REQUEST_RESCUE) DownedManager.requestRescue(player);
            else if (packet.action == ABANDON_RESCUE) DownedManager.abandonRescue(player);
            else if (packet.action == START_ABANDON_HOLD) DownedManager.startAbandonHold(player);
            else if (packet.action == CANCEL_ABANDON_HOLD) DownedManager.cancelAbandonHold(player);
            else if (packet.action == SWITCH_SPECTATOR) DownedManager.switchSpectatorTarget(player);
        });
    }
}
