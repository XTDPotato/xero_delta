package com.xtdpotato.xero_delta.mail;

import java.util.List;
import java.util.UUID;

public record MailMessage(UUID id, UUID recipientId, String recipientName, String senderName,
                          String title, String text, long createdAtEpochMillis, boolean read,
                          List<MailAttachment> attachments) {
    public MailMessage {
        id = id == null ? UUID.randomUUID() : id;
        recipientName = recipientName == null ? "" : recipientName;
        senderName = senderName == null || senderName.isBlank() ? "Xero Delta" : senderName;
        title = title == null || title.isBlank() ? "(无标题)" : title;
        text = text == null ? "" : text;
        createdAtEpochMillis = createdAtEpochMillis <= 0L ? System.currentTimeMillis() : createdAtEpochMillis;
        attachments = attachments == null ? List.of()
            : attachments.stream().limit(64).map(MailAttachment::copy).toList();
    }

    public MailMessage withRead(boolean value) {
        return new MailMessage(id, recipientId, recipientName, senderName, title, text,
            createdAtEpochMillis, value, attachments);
    }

    public MailMessage withAttachments(List<MailAttachment> values) {
        return new MailMessage(id, recipientId, recipientName, senderName, title, text,
            createdAtEpochMillis, read, values);
    }

    public boolean hasUnclaimedAttachments() {
        return attachments.stream().anyMatch(value -> value.isClaimable() && !value.claimed());
    }

    public MailMessage copy() {
        return new MailMessage(id, recipientId, recipientName, senderName, title, text,
            createdAtEpochMillis, read, attachments);
    }
}
