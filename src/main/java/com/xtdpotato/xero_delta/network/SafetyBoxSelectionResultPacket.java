package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.screen.SafetyBoxPickerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SafetyBoxSelectionResultPacket(boolean success, String messageKey)
    implements CustomPacketPayload {
    public static final Type<SafetyBoxSelectionResultPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID,
            "safety_box_selection_result"));
    public static final StreamCodec<FriendlyByteBuf, SafetyBoxSelectionResultPacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> {
            buffer.writeBoolean(packet.success);
            buffer.writeUtf(packet.messageKey, 256);
        }, buffer -> new SafetyBoxSelectionResultPacket(
            buffer.readBoolean(), buffer.readUtf(256)));

    @Override public Type<SafetyBoxSelectionResultPacket> type() { return TYPE; }

    public static void handle(SafetyBoxSelectionResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof SafetyBoxPickerScreen screen) {
                screen.showSelectionResult(packet.success,
                    Component.translatable(packet.messageKey));
            }
        });
    }
}
