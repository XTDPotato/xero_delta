package com.xtdpotato.xero_delta.mail;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Immutable, individually claimable attachment stored in a {@link MailMessage}. */
public record MailAttachment(UUID id, Type type, long amount, ItemStack stack,
                             String value, String label, boolean claimed) {
    public enum Type {
        CURRENCY,
        EXPERIENCE_POINTS,
        EXPERIENCE_LEVELS,
        ITEM,
        RECIPE,
        FTB_TASK
    }

    public MailAttachment {
        id = id == null ? UUID.randomUUID() : id;
        type = type == null ? Type.CURRENCY : type;
        amount = Math.max(0L, amount);
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
        value = value == null ? "" : value;
        label = label == null ? "" : label;
    }

    public static MailAttachment currency(long amount) {
        return new MailAttachment(UUID.randomUUID(), Type.CURRENCY, amount, ItemStack.EMPTY, "", "", false);
    }

    public static MailAttachment experience(long amount, boolean levels) {
        return new MailAttachment(UUID.randomUUID(),
            levels ? Type.EXPERIENCE_LEVELS : Type.EXPERIENCE_POINTS,
            amount, ItemStack.EMPTY, "", "", false);
    }

    public static MailAttachment item(ItemStack stack) {
        return new MailAttachment(UUID.randomUUID(), Type.ITEM, 0L, stack, "", "", false);
    }

    public static MailAttachment action(Type type, String value, String label) {
        return action(type, value, label, ItemStack.EMPTY);
    }

    public static MailAttachment action(Type type, String value, String label, ItemStack icon) {
        if (type != Type.RECIPE && type != Type.FTB_TASK) {
            throw new IllegalArgumentException("Action attachment must be RECIPE or FTB_TASK");
        }
        return new MailAttachment(UUID.randomUUID(), type, 0L, icon, value, label, false);
    }

    public boolean isClaimable() {
        return type == Type.CURRENCY || type == Type.EXPERIENCE_POINTS
            || type == Type.EXPERIENCE_LEVELS || type == Type.ITEM;
    }

    public MailAttachment withClaimed(boolean value) {
        return new MailAttachment(id, type, amount, stack, this.value, label, value);
    }

    public MailAttachment withLabel(String value) {
        return new MailAttachment(id, type, amount, stack, this.value, value, claimed);
    }

    public MailAttachment copy() {
        return new MailAttachment(id, type, amount, stack, value, label, claimed);
    }
}
