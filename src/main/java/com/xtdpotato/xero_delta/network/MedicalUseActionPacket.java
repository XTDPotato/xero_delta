package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.MedicalUseManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MedicalUseActionPacket(byte action) implements CustomPacketPayload {
    public static final byte START_AUTO = 0;
    public static final byte CANCEL = 1;
    public static final Type<MedicalUseActionPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "medical_use_action"));
    public static final StreamCodec<FriendlyByteBuf, MedicalUseActionPacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> buffer.writeByte(packet.action),
            buffer -> new MedicalUseActionPacket(buffer.readByte()));

    @Override
    public Type<MedicalUseActionPacket> type() {
        return TYPE;
    }

    public static void handle(MedicalUseActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (packet.action == START_AUTO) MedicalUseManager.startAuto(player);
            else if (packet.action == CANCEL) MedicalUseManager.cancel(player);
        });
    }
}
