package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.MedicalUseManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Requests one server-validated medical wheel use by item id. */
public record MedicalWheelUsePacket(String itemId, String sourceId) implements CustomPacketPayload {
    public static final Type<MedicalWheelUsePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "medical_wheel_use"));
    public static final StreamCodec<FriendlyByteBuf, MedicalWheelUsePacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> {
            buffer.writeUtf(packet.itemId, 128);
            buffer.writeUtf(packet.sourceId, 256);
        }, buffer -> new MedicalWheelUsePacket(buffer.readUtf(128), buffer.readUtf(256)));

    @Override
    public Type<MedicalWheelUsePacket> type() {
        return TYPE;
    }

    public static void handle(MedicalWheelUsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ResourceLocation id = ResourceLocation.tryParse(packet.itemId);
            if (id != null) MedicalUseManager.startFromWheel(player, id, packet.sourceId);
        });
    }
}
