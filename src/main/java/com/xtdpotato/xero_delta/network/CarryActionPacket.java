package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.DownedManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CarryActionPacket(int targetEntityId) implements CustomPacketPayload {
    public static final Type<CarryActionPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "carry_action"));
    public static final StreamCodec<FriendlyByteBuf, CarryActionPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeVarInt(packet.targetEntityId + 1),
        buffer -> new CarryActionPacket(buffer.readVarInt() - 1));

    @Override public Type<CarryActionPacket> type() { return TYPE; }

    public static void handle(CarryActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DownedManager.toggleCarry(player, packet.targetEntityId);
            }
        });
    }
}
