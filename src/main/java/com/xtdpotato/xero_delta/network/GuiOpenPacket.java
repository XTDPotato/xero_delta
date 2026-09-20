package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.ClientGuiOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authorized request to open one of Xero Delta's client-only screens. */
public record GuiOpenPacket(String screenId) implements CustomPacketPayload {
    public static final Type<GuiOpenPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "gui_open"));
    public static final StreamCodec<FriendlyByteBuf, GuiOpenPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeUtf(packet.screenId, 64),
        buffer -> new GuiOpenPacket(buffer.readUtf(64)));

    @Override
    public Type<GuiOpenPacket> type() {
        return TYPE;
    }

    public static void handle(GuiOpenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientGuiOpener.open(packet.screenId));
    }
}
