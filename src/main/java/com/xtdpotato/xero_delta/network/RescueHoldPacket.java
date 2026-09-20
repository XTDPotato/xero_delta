package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.DownedManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Start, heartbeat and release messages for the hold-F rescue interaction. */
public record RescueHoldPacket(byte action, int targetEntityId) implements CustomPacketPayload {
    public static final byte START = 0;
    public static final byte HEARTBEAT = 1;
    public static final byte CANCEL = 2;
    public static final Type<RescueHoldPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "rescue_hold"));
    public static final StreamCodec<FriendlyByteBuf, RescueHoldPacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> {
            buffer.writeByte(packet.action);
            buffer.writeVarInt(packet.targetEntityId + 1);
        }, buffer -> new RescueHoldPacket(buffer.readByte(), buffer.readVarInt() - 1));

    public static RescueHoldPacket start(int entityId) {
        return new RescueHoldPacket(START, entityId);
    }

    public static RescueHoldPacket heartbeat(int entityId) {
        return new RescueHoldPacket(HEARTBEAT, entityId);
    }

    public static RescueHoldPacket cancel() {
        return new RescueHoldPacket(CANCEL, -1);
    }

    @Override
    public Type<RescueHoldPacket> type() {
        return TYPE;
    }

    public static void handle(RescueHoldPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DownedManager.handleRescueHold(player, packet.action, packet.targetEntityId);
            }
        });
    }
}
