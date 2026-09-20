package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.SafetyBoxInspectAnimation;
import com.xtdpotato.xero_delta.screen.SafetyBoxLayoutPack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Instructs a client to reread its user-managed safety_box/default package. */
public record SafetyBoxPackReloadPacket() implements CustomPacketPayload {
    public static final Type<SafetyBoxPackReloadPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "safety_box_pack_reload"));

    public static final StreamCodec<FriendlyByteBuf, SafetyBoxPackReloadPacket> STREAM_CODEC = StreamCodec.unit(
        new SafetyBoxPackReloadPacket());

    @Override
    public Type<SafetyBoxPackReloadPacket> type() {
        return TYPE;
    }

    public static void handle(SafetyBoxPackReloadPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            SafetyBoxInspectAnimation.reload();
            if (SafetyBoxLayoutPack.refreshRuntimePack()) Minecraft.getInstance().reloadResourcePacks();
        });
    }
}
