package com.xtdpotato.xero_delta.mail;

import com.xtdpotato.xero_delta.trading.TradingMarketData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** World-global mailbox storage. All mutations happen on the logical server thread. */
public final class MailData extends SavedData {
    private static final String DATA_NAME = "xero_delta_mail";
    private static final UUID BROADCAST_MAILBOX_ID = UUID.nameUUIDFromBytes(
        "xero_delta:broadcast_mailbox".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    public static final int DEFAULT_MAIL_LIMIT = 200;
    public static final int MAX_MAIL_LIMIT = 2_000;

    public record Result(boolean success, String message, long value) {
        public static Result ok(String message) { return new Result(true, message, 0L); }
        public static Result ok(String message, long value) { return new Result(true, message, value); }
        public static Result fail(String message) { return new Result(false, message, 0L); }
    }

    public record SentMail(UUID id, UUID senderId, String title, String recipients,
                           String payloadJson, long createdAtEpochMillis, boolean retracted) {
        public SentMail {
            title = title == null ? "" : title;
            recipients = recipients == null ? "" : recipients;
            payloadJson = payloadJson == null ? "" : payloadJson;
        }
    }

    private final Map<UUID, Deque<MailMessage>> mailboxes = new HashMap<>();
    private final Map<UUID, Deque<SentMail>> sentMail = new HashMap<>();
    private final Map<UUID, Set<UUID>> deliveredBroadcasts = new HashMap<>();
    private int mailboxLimit = DEFAULT_MAIL_LIMIT;

    public static MailData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(MailData::new, MailData::load), DATA_NAME);
    }

    public synchronized MailMessage send(UUID recipientId, String recipientName, String senderName,
                                         String title, String text, List<MailAttachment> attachments) {
        return sendWithId(UUID.randomUUID(), recipientId, recipientName, senderName, title, text, attachments);
    }

    public synchronized MailMessage sendWithId(UUID messageId, UUID recipientId, String recipientName,
                                               String senderName, String title, String text,
                                               List<MailAttachment> attachments) {
        MailMessage message = new MailMessage(messageId, recipientId, recipientName, senderName,
            title, text, System.currentTimeMillis(), false, attachments);
        Deque<MailMessage> mailbox = mailboxes.computeIfAbsent(recipientId, ignored -> new ArrayDeque<>());
        mailbox.addFirst(message);
        pruneToLimit(mailbox);
        setDirty();
        return message.copy();
    }

    public synchronized MailMessage sendBroadcast(String senderName, String title, String text,
                                                   List<MailAttachment> attachments) {
        return send(BROADCAST_MAILBOX_ID, "*", senderName, title, text, attachments);
    }

    public synchronized MailMessage sendBroadcast(UUID messageId, String senderName, String title,
                                                   String text, List<MailAttachment> attachments) {
        return sendWithId(messageId, BROADCAST_MAILBOX_ID, "*", senderName, title, text, attachments);
    }

    public synchronized void recordSent(UUID senderId, UUID messageId, String title,
                                        String recipients, String payloadJson) {
        Deque<SentMail> values = sentMail.computeIfAbsent(senderId, ignored -> new ArrayDeque<>());
        values.addFirst(new SentMail(messageId, senderId, title == null ? "" : title,
            recipients == null ? "" : recipients, payloadJson,
            System.currentTimeMillis(), false));
        while (values.size() > mailboxLimit) values.removeLast();
        setDirty();
    }

    public synchronized List<SentMail> sent(UUID senderId) {
        Deque<SentMail> values = sentMail.get(senderId);
        return values == null ? List.of() : values.stream().limit(mailboxLimit).toList();
    }

    public synchronized SentMail sent(UUID senderId, UUID messageId) {
        Deque<SentMail> values = sentMail.get(senderId);
        if (values == null || messageId == null) return null;
        return values.stream().filter(value -> value.id().equals(messageId)).findFirst().orElse(null);
    }

    public synchronized Result deleteSent(UUID senderId, UUID messageId) {
        Deque<SentMail> values = sentMail.get(senderId);
        if (values == null || messageId == null || !values.removeIf(value -> value.id().equals(messageId))) {
            return Result.fail("mail.xero_delta.error.sent_missing");
        }
        setDirty();
        return Result.ok("mail.xero_delta.success.sent_deleted");
    }

    public synchronized Result retract(UUID senderId, UUID messageId) {
        Deque<SentMail> values = sentMail.get(senderId);
        if (values == null) return Result.fail("mail.xero_delta.error.sent_missing");
        boolean owned = values.stream().anyMatch(value -> value.id().equals(messageId) && !value.retracted());
        if (!owned) return Result.fail("mail.xero_delta.error.sent_missing");
        List<SentMail> replacement = values.stream().map(value -> value.id().equals(messageId)
            ? new SentMail(value.id(), value.senderId(), value.title(), value.recipients(),
                value.payloadJson(), value.createdAtEpochMillis(), true) : value).toList();
        values.clear(); values.addAll(replacement);
        for (Deque<MailMessage> mailbox : mailboxes.values()) {
            mailbox.removeIf(message -> message.id().equals(messageId));
        }
        setDirty();
        return Result.ok("mail.xero_delta.success.retracted");
    }

    /** Delivers every active all-player message once when a player joins. */
    public synchronized int deliverBroadcasts(UUID recipientId, String recipientName) {
        if (BROADCAST_MAILBOX_ID.equals(recipientId)) return 0;
        Deque<MailMessage> templates = mailboxes.get(BROADCAST_MAILBOX_ID);
        if (templates == null || templates.isEmpty()) return 0;
        Deque<MailMessage> mailbox = mailboxes.computeIfAbsent(recipientId, ignored -> new ArrayDeque<>());
        Set<UUID> delivered = deliveredBroadcasts.computeIfAbsent(recipientId, ignored -> new HashSet<>());
        // Upgrade old worlds: any broadcast id already present was previously delivered.
        mailbox.stream().map(MailMessage::id).forEach(delivered::add);
        List<MailMessage> missing = new ArrayList<>();
        for (MailMessage template : templates) {
            if (delivered.add(template.id())) {
                missing.add(new MailMessage(template.id(), recipientId, recipientName, template.senderName(),
                    template.title(), template.text(), template.createdAtEpochMillis(), false,
                    template.attachments()));
            }
        }
        for (int i = missing.size() - 1; i >= 0; i--) mailbox.addFirst(missing.get(i));
        if (!missing.isEmpty()) {
            pruneToLimit(mailbox); setDirty();
        }
        return missing.size();
    }

    public synchronized List<MailMessage> inbox(UUID recipientId) {
        Deque<MailMessage> mailbox = mailboxes.get(recipientId);
        if (mailbox == null || mailbox.isEmpty()) return List.of();
        return mailbox.stream().limit(mailboxLimit).map(MailMessage::copy).toList();
    }

    public synchronized int mailboxSize(UUID recipientId) {
        Deque<MailMessage> mailbox = mailboxes.get(recipientId);
        return mailbox == null ? 0 : mailbox.size();
    }

    public synchronized int mailboxLimit() {
        return mailboxLimit;
    }

    public synchronized int setMailboxLimit(int value) {
        mailboxLimit = Math.max(1, Math.min(MAX_MAIL_LIMIT, value));
        for (Deque<MailMessage> mailbox : mailboxes.values()) pruneToLimit(mailbox);
        for (Deque<SentMail> values : sentMail.values()) {
            while (values.size() > mailboxLimit) values.removeLast();
        }
        setDirty();
        return mailboxLimit;
    }

    public synchronized int unreadCount(UUID recipientId) {
        Deque<MailMessage> mailbox = mailboxes.get(recipientId);
        if (mailbox == null) return 0;
        int count = 0;
        for (MailMessage message : mailbox) if (!message.read()) count++;
        return count;
    }

    public synchronized MailMessage getMessage(UUID recipientId, UUID mailId) {
        Deque<MailMessage> mailbox = mailboxes.get(recipientId);
        if (mailbox == null) return null;
        for (MailMessage message : mailbox) if (message.id().equals(mailId)) return message.copy();
        return null;
    }

    /** Moves mail sent to a command-created unresolved account when that player next logs in. */
    public synchronized void migrateMailbox(UUID fromId, UUID toId, String recipientName) {
        if (fromId == null || toId == null || fromId.equals(toId)) return;
        Deque<MailMessage> pending = mailboxes.remove(fromId);
        if (pending == null || pending.isEmpty()) return;
        Deque<MailMessage> target = mailboxes.computeIfAbsent(toId, ignored -> new ArrayDeque<>());
        List<MailMessage> moved = new ArrayList<>(pending.size());
        for (MailMessage message : pending) {
            moved.add(new MailMessage(message.id(), toId, recipientName, message.senderName(),
                message.title(), message.text(), message.createdAtEpochMillis(), message.read(),
                message.attachments()));
        }
        for (int i = moved.size() - 1; i >= 0; i--) target.addFirst(moved.get(i));
        pruneToLimit(target);
        setDirty();
    }

    public synchronized boolean markRead(UUID recipientId, UUID mailId) {
        Deque<MailMessage> mailbox = mailboxes.get(recipientId);
        if (mailbox == null) return false;
        List<MailMessage> replacement = new ArrayList<>(mailbox.size());
        boolean found = false;
        for (MailMessage message : mailbox) {
            if (message.id().equals(mailId)) {
                replacement.add(message.withRead(true));
                found = true;
            } else replacement.add(message);
        }
        if (found) {
            mailbox.clear();
            mailbox.addAll(replacement);
            setDirty();
        }
        return found;
    }

    public Result claim(ServerPlayer player, UUID mailId, UUID attachmentId) {
        MailAttachment attachment;
        synchronized (this) {
            MailMessage message = getMessage(player.getUUID(), mailId);
            if (message == null) return Result.fail("mail.xero_delta.error.missing");
            attachment = message.attachments().stream()
                .filter(value -> value.id().equals(attachmentId)).findFirst().orElse(null);
            if (attachment == null) return Result.fail("mail.xero_delta.error.attachment_missing");
            if (!attachment.isClaimable()) return Result.fail("mail.xero_delta.error.not_claimable");
            if (attachment.claimed()) return Result.fail("mail.xero_delta.error.already_claimed");
        }

        Result awarded = award(player, attachment);
        if (!awarded.success()) return awarded;
        synchronized (this) {
            if (!replaceAttachment(player.getUUID(), mailId, attachmentId, true)) {
                return Result.fail("mail.xero_delta.error.stale");
            }
        }
        return awarded;
    }

    public Result claimAll(ServerPlayer player, UUID mailId) {
        MailMessage message = getMessage(player.getUUID(), mailId);
        if (message == null) return Result.fail("mail.xero_delta.error.missing");
        int claimed = 0;
        for (MailAttachment attachment : message.attachments()) {
            if (!attachment.isClaimable() || attachment.claimed()) continue;
            Result result = claim(player, mailId, attachment.id());
            if (result.success()) claimed++;
        }
        return claimed > 0 ? Result.ok("mail.xero_delta.success.claimed_all", claimed)
            : Result.fail("mail.xero_delta.error.nothing_to_claim");
    }

    public Result claimSelected(ServerPlayer player, UUID mailId, List<UUID> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return Result.fail("mail.xero_delta.error.nothing_to_claim");
        }
        int claimed = 0;
        for (UUID attachmentId : attachmentIds.stream().distinct().limit(64).toList()) {
            if (claim(player, mailId, attachmentId).success()) claimed++;
        }
        return claimed > 0 ? Result.ok("mail.xero_delta.success.claimed_all", claimed)
            : Result.fail("mail.xero_delta.error.nothing_to_claim");
    }

    public Result claimInbox(ServerPlayer player) {
        int claimed = 0;
        for (MailMessage message : inbox(player.getUUID())) {
            for (MailAttachment attachment : message.attachments()) {
                if (!attachment.isClaimable() || attachment.claimed()) continue;
                if (claim(player, message.id(), attachment.id()).success()) claimed++;
            }
        }
        return claimed > 0 ? Result.ok("mail.xero_delta.success.claimed_all", claimed)
            : Result.fail("mail.xero_delta.error.nothing_to_claim");
    }

    public synchronized Result delete(UUID recipientId, UUID mailId) {
        Deque<MailMessage> mailbox = mailboxes.get(recipientId);
        if (mailbox == null || !mailbox.removeIf(message -> message.id().equals(mailId))) {
            return Result.fail("mail.xero_delta.error.missing");
        }
        setDirty();
        return Result.ok("mail.xero_delta.success.deleted");
    }

    public synchronized Result deleteRead(UUID recipientId) {
        Deque<MailMessage> mailbox = mailboxes.get(recipientId);
        if (mailbox == null) return Result.fail("mail.xero_delta.error.nothing_to_delete");
        int before = mailbox.size();
        mailbox.removeIf(MailMessage::read);
        int deleted = before - mailbox.size();
        if (deleted <= 0) return Result.fail("mail.xero_delta.error.nothing_to_delete");
        setDirty();
        return Result.ok("mail.xero_delta.success.deleted_count", deleted);
    }

    private Result award(ServerPlayer player, MailAttachment attachment) {
        return switch (attachment.type()) {
            case CURRENCY -> awardCurrency(player, attachment.amount());
            case EXPERIENCE_POINTS -> {
                if (attachment.amount() > Integer.MAX_VALUE) yield Result.fail("mail.xero_delta.error.invalid_attachment");
                player.giveExperiencePoints((int) attachment.amount());
                yield Result.ok("mail.xero_delta.success.claimed", attachment.amount());
            }
            case EXPERIENCE_LEVELS -> {
                if (attachment.amount() > Integer.MAX_VALUE) yield Result.fail("mail.xero_delta.error.invalid_attachment");
                player.giveExperienceLevels((int) attachment.amount());
                yield Result.ok("mail.xero_delta.success.claimed", attachment.amount());
            }
            case ITEM -> awardItem(player, attachment.stack());
            case RECIPE, FTB_TASK -> Result.fail("mail.xero_delta.error.not_claimable");
        };
    }

    private static Result awardCurrency(ServerPlayer player, long amount) {
        if (amount <= 0L) return Result.fail("mail.xero_delta.error.invalid_attachment");
        TradingMarketData market = TradingMarketData.get(player.server);
        long balance = market.balance(player.getUUID());
        if (balance > market.maxCurrency() - amount) return Result.fail("mail.xero_delta.error.wallet_full");
        long credited = market.credit(player.getUUID(), amount);
        return credited == amount ? Result.ok("mail.xero_delta.success.claimed", credited)
            : Result.fail("mail.xero_delta.error.wallet_full");
    }

    private static Result awardItem(ServerPlayer player, ItemStack stored) {
        if (stored == null || stored.isEmpty()) return Result.fail("mail.xero_delta.error.invalid_attachment");
        ItemStack stack = stored.copy();
        if (!canFit(player, stack)) return Result.fail("mail.xero_delta.error.inventory_full");
        int remaining = stack.getCount();
        int max = Math.max(1, stack.getMaxStackSize());
        while (remaining > 0) {
            int amount = Math.min(max, remaining);
            ItemStack part = stack.copyWithCount(amount);
            if (!player.getInventory().add(part) || !part.isEmpty()) return Result.fail("mail.xero_delta.error.inventory_full");
            remaining -= amount;
        }
        player.getInventory().setChanged();
        return Result.ok("mail.xero_delta.success.claimed", stack.getCount());
    }

    private static boolean canFit(ServerPlayer player, ItemStack incoming) {
        int remaining = incoming.getCount();
        int slotCapacity = Math.max(1, Math.min(incoming.getMaxStackSize(), player.getInventory().getMaxStackSize()));
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) remaining -= slotCapacity;
            else if (ItemStack.isSameItemSameComponents(stack, incoming)) {
                remaining -= Math.max(0, Math.min(stack.getMaxStackSize(), player.getInventory().getMaxStackSize()) - stack.getCount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private synchronized boolean replaceAttachment(UUID recipientId, UUID mailId, UUID attachmentId, boolean claimed) {
        Deque<MailMessage> mailbox = mailboxes.get(recipientId);
        if (mailbox == null) return false;
        List<MailMessage> messages = new ArrayList<>(mailbox.size());
        boolean changed = false;
        for (MailMessage message : mailbox) {
            if (!message.id().equals(mailId)) {
                messages.add(message);
                continue;
            }
            List<MailAttachment> attachments = new ArrayList<>(message.attachments().size());
            for (MailAttachment attachment : message.attachments()) {
                if (attachment.id().equals(attachmentId) && attachment.claimed() != claimed) {
                    attachments.add(attachment.withClaimed(claimed));
                    changed = true;
                } else attachments.add(attachment);
            }
            messages.add(message.withRead(true).withAttachments(attachments));
        }
        if (changed) {
            mailbox.clear();
            mailbox.addAll(messages);
            setDirty();
        }
        return changed;
    }

    private void pruneToLimit(Deque<MailMessage> mailbox) {
        while (mailbox.size() > mailboxLimit) mailbox.removeLast();
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag messages = new ListTag();
        for (var mailbox : mailboxes.entrySet()) {
            for (MailMessage message : mailbox.getValue()) {
                CompoundTag value = new CompoundTag();
                value.putUUID("id", message.id()); value.putUUID("recipient", message.recipientId());
                value.putString("recipient_name", message.recipientName()); value.putString("sender", message.senderName());
                value.putString("title", message.title()); value.putString("text", message.text());
                value.putLong("created", message.createdAtEpochMillis()); value.putBoolean("read", message.read());
                ListTag attachments = new ListTag();
                for (MailAttachment attachment : message.attachments()) {
                    CompoundTag attachmentTag = new CompoundTag();
                    attachmentTag.putUUID("id", attachment.id()); attachmentTag.putString("type", attachment.type().name());
                    attachmentTag.putLong("amount", attachment.amount()); attachmentTag.putString("value", attachment.value());
                    attachmentTag.putString("label", attachment.label()); attachmentTag.putBoolean("claimed", attachment.claimed());
                    if (!attachment.stack().isEmpty()) {
                        attachmentTag.putInt("stack_count", attachment.stack().getCount());
                        attachmentTag.put("stack", attachment.stack().copyWithCount(1).save(registries));
                    }
                    attachments.add(attachmentTag);
                }
                value.put("attachments", attachments);
                messages.add(value);
            }
        }
        tag.put("messages", messages);
        ListTag sentValues = new ListTag();
        for (Deque<SentMail> mailbox : sentMail.values()) {
            for (SentMail sent : mailbox) {
                CompoundTag value = new CompoundTag();
                value.putUUID("id", sent.id()); value.putUUID("sender", sent.senderId());
                value.putString("title", sent.title()); value.putString("recipients", sent.recipients());
                value.putString("payload", sent.payloadJson());
                value.putLong("created", sent.createdAtEpochMillis()); value.putBoolean("retracted", sent.retracted());
                sentValues.add(value);
            }
        }
        tag.put("sent", sentValues);
        ListTag deliveryValues = new ListTag();
        for (var entry : deliveredBroadcasts.entrySet()) {
            for (UUID messageId : entry.getValue()) {
                CompoundTag value = new CompoundTag();
                value.putUUID("recipient", entry.getKey());
                value.putUUID("message", messageId);
                deliveryValues.add(value);
            }
        }
        tag.put("broadcast_deliveries", deliveryValues);
        tag.putInt("mailbox_limit", mailboxLimit);
        return tag;
    }

    public static MailData load(CompoundTag tag, HolderLookup.Provider registries) {
        MailData data = new MailData();
        if (tag.contains("mailbox_limit")) {
            data.mailboxLimit = Math.max(1, Math.min(MAX_MAIL_LIMIT, tag.getInt("mailbox_limit")));
        }
        for (var raw : tag.getList("messages", 10)) {
            CompoundTag value = (CompoundTag) raw;
            if (!value.hasUUID("id") || !value.hasUUID("recipient")) continue;
            List<MailAttachment> attachments = new ArrayList<>();
            for (var attachmentRaw : value.getList("attachments", 10)) {
                CompoundTag attachmentTag = (CompoundTag) attachmentRaw;
                MailAttachment.Type type;
                try { type = MailAttachment.Type.valueOf(attachmentTag.getString("type")); }
                catch (IllegalArgumentException ignored) { continue; }
                ItemStack stack = attachmentTag.contains("stack")
                    ? ItemStack.parseOptional(registries, attachmentTag.getCompound("stack")) : ItemStack.EMPTY;
                if (!stack.isEmpty() && attachmentTag.contains("stack_count")) stack.setCount(Math.max(1, attachmentTag.getInt("stack_count")));
                attachments.add(new MailAttachment(
                    attachmentTag.hasUUID("id") ? attachmentTag.getUUID("id") : UUID.randomUUID(), type,
                    attachmentTag.getLong("amount"), stack, attachmentTag.getString("value"),
                    attachmentTag.getString("label"), attachmentTag.getBoolean("claimed")));
            }
            UUID recipientId = value.getUUID("recipient");
            MailMessage message = new MailMessage(value.getUUID("id"), recipientId,
                value.getString("recipient_name"), value.getString("sender"), value.getString("title"),
                value.getString("text"), value.getLong("created"), value.getBoolean("read"), attachments);
            data.mailboxes.computeIfAbsent(recipientId, ignored -> new ArrayDeque<>()).addLast(message);
        }
        for (var raw : tag.getList("sent", 10)) {
            CompoundTag value = (CompoundTag) raw;
            if (!value.hasUUID("id") || !value.hasUUID("sender")) continue;
            UUID senderId = value.getUUID("sender");
            data.sentMail.computeIfAbsent(senderId, ignored -> new ArrayDeque<>()).addLast(new SentMail(
                value.getUUID("id"), senderId, value.getString("title"), value.getString("recipients"),
                value.getString("payload"), value.getLong("created"), value.getBoolean("retracted")));
        }
        for (var raw : tag.getList("broadcast_deliveries", 10)) {
            CompoundTag value = (CompoundTag) raw;
            if (!value.hasUUID("recipient") || !value.hasUUID("message")) continue;
            data.deliveredBroadcasts.computeIfAbsent(value.getUUID("recipient"),
                ignored -> new HashSet<>()).add(value.getUUID("message"));
        }
        // Worlds saved before delivery tracking must not re-send deleted historical broadcasts.
        for (var mailbox : data.mailboxes.entrySet()) {
            for (MailMessage message : mailbox.getValue()) {
                data.deliveredBroadcasts.computeIfAbsent(mailbox.getKey(),
                    ignored -> new HashSet<>()).add(message.id());
            }
        }
        for (Deque<MailMessage> mailbox : data.mailboxes.values()) data.pruneToLimit(mailbox);
        return data;
    }
}
