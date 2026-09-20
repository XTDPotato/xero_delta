package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.screen.HtmlDialogScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Opens a stackable HTML dialog or closes the client's current top dialog. */
public record DialogPacket(byte action, String options, String data)
    implements CustomPacketPayload {
    public static final byte OPEN = 0;
    public static final byte CLOSE = 1;
    public static final int MAX_OPTIONS_LENGTH = 2_048;
    public static final int MAX_DATA_LENGTH = 30_000;
    public static final Type<DialogPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "dialog"));
    public static final StreamCodec<FriendlyByteBuf, DialogPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeByte(packet.action);
            buffer.writeUtf(packet.options, MAX_OPTIONS_LENGTH);
            buffer.writeUtf(packet.data, MAX_DATA_LENGTH);
        },
        buffer -> new DialogPacket(buffer.readByte(),
            buffer.readUtf(MAX_OPTIONS_LENGTH),
            buffer.readUtf(MAX_DATA_LENGTH)));

    public static DialogPacket open(String options, String data) {
        return new DialogPacket(OPEN, options == null ? "" : options,
            data == null ? "" : data);
    }

    public static DialogPacket close() {
        return new DialogPacket(CLOSE, "", "");
    }

    public static void handle(DialogPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.action == CLOSE) HtmlDialogScreen.closeTop();
            else if (packet.action == OPEN) HtmlDialogScreen.open(packet.options, packet.data);
        });
    }

    @Override
    public Type<DialogPacket> type() {
        return TYPE;
    }
}