package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.DamageDirectionClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Horizontal world-space direction of one damage event. */
public record DamageDirectionPacket(double directionX, double directionZ, float damage)
    implements CustomPacketPayload {
    public static final Type<DamageDirectionPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "damage_direction"));
    public static final StreamCodec<FriendlyByteBuf, DamageDirectionPacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> {
            buffer.writeDouble(packet.directionX);
            buffer.writeDouble(packet.directionZ);
            buffer.writeFloat(packet.damage);
        }, buffer -> new DamageDirectionPacket(
            buffer.readDouble(), buffer.readDouble(), buffer.readFloat()));

    @Override
    public Type<DamageDirectionPacket> type() {
        return TYPE;
    }

    public static void handle(DamageDirectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DamageDirectionClientState.INSTANCE.add(
            packet.directionX, packet.directionZ, packet.damage));
    }
}
