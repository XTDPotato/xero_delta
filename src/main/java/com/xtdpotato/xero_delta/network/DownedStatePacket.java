package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.DownedClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DownedStatePacket(byte stage, int remainingTicks, int durationTicks,
                                int rescueTicks, int rescueDurationTicks,
                                boolean carried, boolean carrying,
                                boolean rescuing, boolean beingRescued,
                                String rescuerName, byte carryPhase, int carryTicks)
    implements CustomPacketPayload {
    public static final Type<DownedStatePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "downed_state"));
    public static final StreamCodec<FriendlyByteBuf, DownedStatePacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeByte(packet.stage);
            buffer.writeVarInt(packet.remainingTicks);
            buffer.writeVarInt(packet.durationTicks);
            buffer.writeVarInt(packet.rescueTicks);
            buffer.writeVarInt(packet.rescueDurationTicks);
            buffer.writeBoolean(packet.carried);
            buffer.writeBoolean(packet.carrying);
            buffer.writeBoolean(packet.rescuing);
            buffer.writeBoolean(packet.beingRescued);
            buffer.writeUtf(packet.rescuerName, 64);
            buffer.writeByte(packet.carryPhase);
            buffer.writeVarInt(packet.carryTicks);
        },
        buffer -> new DownedStatePacket(buffer.readByte(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(),
            buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(64),
            buffer.readByte(), buffer.readVarInt()));

    @Override public Type<DownedStatePacket> type() { return TYPE; }

    public static void handle(DownedStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DownedClientState.INSTANCE.update(packet));
    }
}
