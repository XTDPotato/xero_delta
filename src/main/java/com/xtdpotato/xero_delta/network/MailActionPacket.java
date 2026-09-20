package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.mail.MailData;
import com.xtdpotato.xero_delta.mail.MailPayloadParser;
import com.xtdpotato.xero_delta.mail.MailService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record MailActionPacket(Action action, UUID mailId, UUID attachmentId,
                               String recipients, String json) implements CustomPacketPayload {
    public enum Action { OPEN, REFRESH, READ, CLAIM, CLAIM_ALL, CLAIM_SELECTED, CLAIM_INBOX,
        DELETE, DELETE_READ, RETRACT, RESEND_SENT, DELETE_SENT, SEND }
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);
    public static final Type<MailActionPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "mail_action"));
    public static final StreamCodec<FriendlyByteBuf, MailActionPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeEnum(packet.action); buf.writeUUID(packet.mailId == null ? EMPTY_UUID : packet.mailId);
            buf.writeUUID(packet.attachmentId == null ? EMPTY_UUID : packet.attachmentId);
            buf.writeUtf(packet.recipients == null ? "" : packet.recipients, 1024);
            buf.writeUtf(packet.json == null ? "" : packet.json, 16384);
        }, buf -> new MailActionPacket(buf.readEnum(Action.class), nullable(buf.readUUID()), nullable(buf.readUUID()),
            buf.readUtf(1024), buf.readUtf(16384)));

    public static MailActionPacket open() { return new MailActionPacket(Action.OPEN, null, null, "", ""); }
    public static MailActionPacket refresh() { return new MailActionPacket(Action.REFRESH, null, null, "", ""); }
    public static MailActionPacket read(UUID mailId) { return new MailActionPacket(Action.READ, mailId, null, "", ""); }
    public static MailActionPacket claim(UUID mailId, UUID attachmentId) {
        return new MailActionPacket(Action.CLAIM, mailId, attachmentId, "", "");
    }
    public static MailActionPacket claimAll(UUID mailId) {
        return new MailActionPacket(Action.CLAIM_ALL, mailId, null, "", "");
    }
    public static MailActionPacket claimSelected(UUID mailId, java.util.Collection<UUID> attachmentIds) {
        String ids = attachmentIds == null ? "" : attachmentIds.stream().limit(64)
            .map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
        return new MailActionPacket(Action.CLAIM_SELECTED, mailId, null, "", ids);
    }
    public static MailActionPacket claimInbox() {
        return new MailActionPacket(Action.CLAIM_INBOX, null, null, "", "");
    }
    public static MailActionPacket delete(UUID mailId) {
        return new MailActionPacket(Action.DELETE, mailId, null, "", "");
    }
    public static MailActionPacket deleteRead() {
        return new MailActionPacket(Action.DELETE_READ, null, null, "", "");
    }
    public static MailActionPacket retract(UUID mailId) {
        return new MailActionPacket(Action.RETRACT, mailId, null, "", "");
    }
    public static MailActionPacket resendSent(UUID mailId) {
        return new MailActionPacket(Action.RESEND_SENT, mailId, null, "", "");
    }
    public static MailActionPacket deleteSent(UUID mailId) {
        return new MailActionPacket(Action.DELETE_SENT, mailId, null, "", "");
    }
    public static MailActionPacket send(String recipients, String json) {
        return new MailActionPacket(Action.SEND, null, null, recipients, json);
    }

    private static UUID nullable(UUID value) { return EMPTY_UUID.equals(value) ? null : value; }

    public static void handle(MailActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            MailData data = MailData.get(player.server);
            MailData.Result result = MailData.Result.ok("");
            boolean open = packet.action == Action.OPEN;
            UUID selected = packet.mailId;
            switch (packet.action) {
                case OPEN, REFRESH -> { }
                case READ -> {
                    if (packet.mailId == null || !data.markRead(player.getUUID(), packet.mailId)) {
                        result = MailData.Result.fail("mail.xero_delta.error.missing");
                    }
                }
                case CLAIM -> result = packet.mailId == null || packet.attachmentId == null
                    ? MailData.Result.fail("mail.xero_delta.error.attachment_missing")
                    : data.claim(player, packet.mailId, packet.attachmentId);
                case CLAIM_ALL -> result = packet.mailId == null
                    ? MailData.Result.fail("mail.xero_delta.error.missing") : data.claimAll(player, packet.mailId);
                case CLAIM_SELECTED -> result = packet.mailId == null
                    ? MailData.Result.fail("mail.xero_delta.error.missing")
                    : data.claimSelected(player, packet.mailId, parseIds(packet.json));
                case CLAIM_INBOX -> result = data.claimInbox(player);
                case DELETE -> result = packet.mailId == null
                    ? MailData.Result.fail("mail.xero_delta.error.missing")
                    : data.delete(player.getUUID(), packet.mailId);
                case DELETE_READ -> result = data.deleteRead(player.getUUID());
                case RETRACT -> result = packet.mailId == null
                    ? MailData.Result.fail("mail.xero_delta.error.sent_missing")
                    : data.retract(player.getUUID(), packet.mailId);
                case RESEND_SENT -> {
                    int sent = packet.mailId == null ? 0 : MailService.resendFromHistory(player, packet.mailId);
                    result = sent > 0 ? MailData.Result.ok("mail.xero_delta.success.sent", sent)
                        : MailData.Result.fail("mail.xero_delta.error.sent_missing");
                }
                case DELETE_SENT -> result = packet.mailId == null
                    ? MailData.Result.fail("mail.xero_delta.error.sent_missing")
                    : data.deleteSent(player.getUUID(), packet.mailId);
                case SEND -> {
                    if (!player.isCreative()) {
                        result = MailData.Result.fail("mail.xero_delta.error.compose_permission");
                        break;
                    }
                    try {
                        var draft = MailPayloadParser.parse(packet.json, player.getGameProfile().getName());
                        int sent = MailService.sendFromComposer(player, packet.recipients, draft, packet.json);
                        result = sent > 0 ? MailData.Result.ok("mail.xero_delta.success.sent", sent)
                            : MailData.Result.fail("mail.xero_delta.error.recipient");
                    } catch (RuntimeException error) {
                        result = MailData.Result.fail("mail.xero_delta.error.invalid_json");
                    }
                }
            }
            PacketDistributor.sendToPlayer(player, MailSyncPacket.snapshot(player, open, selected,
                result.message(), result.success(), result.value()));
        });
    }

    private static java.util.List<UUID> parseIds(String value) {
        if (value == null || value.isBlank()) return java.util.List.of();
        java.util.List<UUID> result = new java.util.ArrayList<>();
        for (String token : value.split(",")) {
            try { result.add(UUID.fromString(token.trim())); }
            catch (IllegalArgumentException ignored) { }
        }
        return java.util.List.copyOf(result);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
