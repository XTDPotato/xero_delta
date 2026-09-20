package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.StaminaClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record StaminaStatePacket(float current, float maximum, boolean exhausted)
    implements CustomPacketPayload {
    public static final Type<StaminaStatePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "stamina_state"));
    public static final StreamCodec<FriendlyByteBuf, StaminaStatePacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeFloat(packet.current);
            buffer.writeFloat(packet.maximum);
            buffer.writeBoolean(packet.exhausted);
        },
        buffer -> new StaminaStatePacket(buffer.readFloat(), buffer.readFloat(),
            buffer.readBoolean()));

    @Override public Type<StaminaStatePacket> type() { return TYPE; }

    public static void handle(StaminaStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> StaminaClientState.INSTANCE.update(packet));
    }
}
