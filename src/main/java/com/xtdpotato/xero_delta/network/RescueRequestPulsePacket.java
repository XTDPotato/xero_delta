package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.RescueRequestClientState;
import com.xtdpotato.xero_delta.client.RescueRequestSoundPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Starts the expanding avatar-border animation for an accepted rescue request. */
public record RescueRequestPulsePacket(UUID playerId, byte stage, String soundId) implements CustomPacketPayload {
    public static final Type<RescueRequestPulsePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "rescue_request_pulse"));
    public static final StreamCodec<FriendlyByteBuf, RescueRequestPulsePacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeUUID(packet.playerId);
            buffer.writeByte(packet.stage);
            buffer.writeUtf(packet.soundId == null ? "" : packet.soundId, 128);
        },
        buffer -> new RescueRequestPulsePacket(buffer.readUUID(), buffer.readByte(), buffer.readUtf(128)));

    @Override
    public Type<RescueRequestPulsePacket> type() {
        return TYPE;
    }

    public static void handle(RescueRequestPulsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            RescueRequestClientState.INSTANCE.trigger(packet.playerId, packet.stage);
            RescueRequestSoundPlayer.play(packet.soundId);
        });
    }
}
