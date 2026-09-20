package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.SafetyBoxAccessClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public record SafetyBoxAccessPacket(Map<String, Long> access) implements CustomPacketPayload {
    public static final Type<SafetyBoxAccessPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "safety_box_access"));
    public static final StreamCodec<FriendlyByteBuf, SafetyBoxAccessPacket> STREAM_CODEC =
        StreamCodec.of((buf, packet) -> {
            buf.writeVarInt(packet.access.size());
            packet.access.forEach((id, expiresAt) -> {
                buf.writeUtf(id);
                buf.writeLong(expiresAt);
            });
        }, buf -> {
            int size = Math.min(256, Math.max(0, buf.readVarInt()));
            Map<String, Long> values = new HashMap<>();
            for (int i = 0; i < size; i++) values.put(buf.readUtf(256), buf.readLong());
            return new SafetyBoxAccessPacket(Map.copyOf(values));
        });

    @Override public Type<SafetyBoxAccessPacket> type() { return TYPE; }

    public static void handle(SafetyBoxAccessPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SafetyBoxAccessClientState.INSTANCE.update(packet.access));
    }
}
