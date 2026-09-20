package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.MailClientState;
import com.xtdpotato.xero_delta.mail.MailAttachment;
import com.xtdpotato.xero_delta.mail.MailData;
import com.xtdpotato.xero_delta.mail.MailMessage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record MailSyncPacket(List<MessageView> messages, List<SentView> sentMessages,
                             int unreadCount, int mailboxCount, int mailboxLimit,
                             boolean canCompose,
                             boolean openScreen, UUID selectedMailId, String resultMessage,
                             boolean success, long resultValue) implements CustomPacketPayload {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);
    private static final int MAX_MESSAGES = MailData.MAX_MAIL_LIMIT;
    public static final Type<MailSyncPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "mail_sync"));

    public record MessageView(UUID id, String recipientName, String senderName, String title,
                              String text, long createdAtEpochMillis, boolean read,
                              List<AttachmentView> attachments) {}

    public record SentView(UUID id, String title, String recipients,
                           String payloadJson, long createdAtEpochMillis, boolean retracted) {}

    public record AttachmentView(UUID id, MailAttachment.Type type, long amount, ItemStack stack,
                                 String value, String label, boolean claimed) {
        public boolean isClaimable() {
            return type == MailAttachment.Type.CURRENCY || type == MailAttachment.Type.EXPERIENCE_POINTS
                || type == MailAttachment.Type.EXPERIENCE_LEVELS || type == MailAttachment.Type.ITEM;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MailSyncPacket> STREAM_CODEC =
        StreamCodec.of(MailSyncPacket::encode, MailSyncPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, MailSyncPacket packet) {
        buf.writeVarInt(Math.min(MAX_MESSAGES, packet.messages.size()));
        for (MessageView message : packet.messages.stream().limit(MAX_MESSAGES).toList()) {
            buf.writeUUID(message.id); buf.writeUtf(message.recipientName, 64); buf.writeUtf(message.senderName, 64);
            buf.writeUtf(message.title, 256); buf.writeUtf(message.text, 8192);
            buf.writeLong(message.createdAtEpochMillis); buf.writeBoolean(message.read);
            buf.writeVarInt(Math.min(64, message.attachments.size()));
            for (AttachmentView attachment : message.attachments.stream().limit(64).toList()) {
                buf.writeUUID(attachment.id); buf.writeEnum(attachment.type); buf.writeLong(attachment.amount);
                encodeStackWithCount(buf, attachment.stack); buf.writeUtf(attachment.value, 256);
                buf.writeUtf(attachment.label, 128); buf.writeBoolean(attachment.claimed);
            }
        }
        buf.writeVarInt(Math.min(MAX_MESSAGES, packet.sentMessages.size()));
        for (SentView sent : packet.sentMessages.stream().limit(MAX_MESSAGES).toList()) {
            buf.writeUUID(sent.id); buf.writeUtf(sent.title, 256); buf.writeUtf(sent.recipients, 1024);
            buf.writeUtf(sent.payloadJson, 16384);
            buf.writeLong(sent.createdAtEpochMillis); buf.writeBoolean(sent.retracted);
        }
        buf.writeVarInt(packet.unreadCount); buf.writeVarInt(packet.mailboxCount); buf.writeVarInt(packet.mailboxLimit);
        buf.writeBoolean(packet.canCompose); buf.writeBoolean(packet.openScreen);
        buf.writeUUID(packet.selectedMailId == null ? EMPTY_UUID : packet.selectedMailId);
        buf.writeUtf(packet.resultMessage == null ? "" : packet.resultMessage, 256);
        buf.writeBoolean(packet.success); buf.writeLong(packet.resultValue);
    }

    private static MailSyncPacket decode(RegistryFriendlyByteBuf buf) {
        int count = Math.min(MAX_MESSAGES, buf.readVarInt());
        List<MessageView> messages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            String recipientName = buf.readUtf(64); String senderName = buf.readUtf(64);
            String title = buf.readUtf(256); String text = buf.readUtf(8192);
            long created = buf.readLong(); boolean read = buf.readBoolean();
            int attachmentCount = Math.min(64, buf.readVarInt());
            List<AttachmentView> attachments = new ArrayList<>(attachmentCount);
            for (int j = 0; j < attachmentCount; j++) {
                attachments.add(new AttachmentView(buf.readUUID(), buf.readEnum(MailAttachment.Type.class),
                    buf.readLong(), decodeStackWithCount(buf), buf.readUtf(256), buf.readUtf(128), buf.readBoolean()));
            }
            messages.add(new MessageView(id, recipientName, senderName, title, text, created, read,
                List.copyOf(attachments)));
        }
        int sentCount = Math.min(MAX_MESSAGES, buf.readVarInt());
        List<SentView> sentMessages = new ArrayList<>(sentCount);
        for (int i = 0; i < sentCount; i++) {
            sentMessages.add(new SentView(buf.readUUID(), buf.readUtf(256), buf.readUtf(1024),
                buf.readUtf(16384), buf.readLong(), buf.readBoolean()));
        }
        int unread = buf.readVarInt(); int mailboxCount = buf.readVarInt(); int mailboxLimit = buf.readVarInt();
        boolean canCompose = buf.readBoolean(); boolean open = buf.readBoolean();
        UUID selected = buf.readUUID(); if (EMPTY_UUID.equals(selected)) selected = null;
        return new MailSyncPacket(List.copyOf(messages), List.copyOf(sentMessages), unread,
            mailboxCount, mailboxLimit, canCompose, open, selected,
            buf.readUtf(256), buf.readBoolean(), buf.readLong());
    }

    public static MailSyncPacket snapshot(ServerPlayer player, boolean openScreen, UUID selectedMailId,
                                          String resultMessage, boolean success, long resultValue) {
        MailData data = MailData.get(player.server);
        List<MessageView> messages = data.inbox(player.getUUID()).stream().map(MailSyncPacket::view).toList();
        List<SentView> sentMessages = data.sent(player.getUUID()).stream().map(value -> new SentView(
            value.id(), value.title(), value.recipients(), value.payloadJson(),
            value.createdAtEpochMillis(), value.retracted())).toList();
        return new MailSyncPacket(messages, sentMessages, data.unreadCount(player.getUUID()), data.mailboxSize(player.getUUID()),
            data.mailboxLimit(), player.isCreative(), openScreen,
            selectedMailId, resultMessage == null ? "" : resultMessage, success, resultValue);
    }

    private static MessageView view(MailMessage message) {
        return new MessageView(message.id(), message.recipientName(), message.senderName(), message.title(),
            message.text(), message.createdAtEpochMillis(), message.read(),
            message.attachments().stream().map(MailSyncPacket::view).toList());
    }

    private static AttachmentView view(MailAttachment attachment) {
        return new AttachmentView(attachment.id(), attachment.type(), attachment.amount(), attachment.stack().copy(),
            attachment.value(), attachment.label(), attachment.claimed());
    }

    private static void encodeStackWithCount(RegistryFriendlyByteBuf buf, ItemStack stack) {
        int count = stack == null || stack.isEmpty() ? 0 : stack.getCount();
        buf.writeVarInt(Math.max(0, count));
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, count <= 0 ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    private static ItemStack decodeStackWithCount(RegistryFriendlyByteBuf buf) {
        int count = Math.max(0, buf.readVarInt());
        ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        if (!stack.isEmpty() && count > 0) stack.setCount(count);
        return stack;
    }

    public static void handle(MailSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> MailClientState.INSTANCE.update(packet));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
