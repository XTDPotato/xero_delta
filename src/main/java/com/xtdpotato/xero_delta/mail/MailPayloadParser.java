package com.xtdpotato.xero_delta.mail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses JSON accepted by /xero_mail and the creative mail composer. */
public final class MailPayloadParser {
    public record Draft(String title, String sender, String text, List<MailAttachment> attachments) {}

    private MailPayloadParser() {}

    public static Draft parse(String json, String fallbackSender) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String title = limited(string(root, "title", "(无标题)"), 256);
        String sender = limited(string(root, "sender", fallbackSender), 64);
        String text = limited(string(root, "text", ""), 8192);
        List<MailAttachment> attachments = new ArrayList<>();
        JsonElement rawAttachments = root.has("attachment") ? root.get("attachment") : root.get("attachments");
        if (rawAttachments instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (attachments.size() >= 64) break;
                MailAttachment attachment = parseAttachment(element);
                if (attachment != null) attachments.add(attachment);
            }
        }
        return new Draft(title, sender, text, List.copyOf(attachments));
    }

    private static MailAttachment parseAttachment(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return parseStringAttachment(element.getAsString());
        }
        if (!element.isJsonObject()) return null;
        JsonObject value = element.getAsJsonObject();
        String type = string(value, "type", "item").toLowerCase(Locale.ROOT);
        long amount = Math.max(0L, longValue(value, "amount", longValue(value, "count", 1L)));
        String label = limited(string(value, "label", ""), 128);
        return switch (type) {
            case "currency", "coin", "money" -> amount > 0L
                ? MailAttachment.currency(amount).withLabel(label) : null;
            case "experience", "xp", "experience_points" -> amount > 0L
                ? MailAttachment.experience(amount, false).withLabel(label) : null;
            case "level", "levels", "experience_levels" -> amount > 0L
                ? MailAttachment.experience(amount, true).withLabel(label) : null;
            case "item" -> parseItem(string(value, "item", string(value, "id", "")),
                (int) Math.min(9999L, amount));
            case "recipe", "rei", "jei" -> {
                String itemId = limited(string(value, "item", string(value, "value", "")), 256);
                yield MailAttachment.action(MailAttachment.Type.RECIPE, itemId,
                    limited(string(value, "label", "查看配方"), 128), icon(value, itemId));
            }
            case "ftb_task", "task" -> MailAttachment.action(MailAttachment.Type.FTB_TASK,
                limited(string(value, "task", string(value, "value", "")), 256),
                limited(string(value, "label", "打开任务"), 128),
                icon(value, "minecraft:writable_book"));
            default -> null;
        };
    }

    private static MailAttachment parseStringAttachment(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        int separator = value.indexOf(':');
        if (separator > 0) {
            String type = value.substring(0, separator).toLowerCase(Locale.ROOT);
            String payload = value.substring(separator + 1);
            try {
                long amount = Long.parseLong(payload);
                if ("currency".equals(type) || "coin".equals(type)) return MailAttachment.currency(amount);
                if ("xp".equals(type)) return MailAttachment.experience(amount, false);
                if ("level".equals(type) || "levels".equals(type)) return MailAttachment.experience(amount, true);
            } catch (NumberFormatException ignored) {
            }
        }
        int count = 1;
        int star = value.lastIndexOf('*');
        if (star > 0) {
            try {
                count = Math.max(1, Math.min(9999, Integer.parseInt(value.substring(star + 1))));
                value = value.substring(0, star);
            } catch (NumberFormatException ignored) {
            }
        }
        return parseItem(value, count);
    }

    private static MailAttachment parseItem(String id, int count) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) return null;
        Item item = BuiltInRegistries.ITEM.get(location);
        if (item == Items.AIR) return null;
        return MailAttachment.item(new ItemStack(item, Math.max(1, count)));
    }

    private static ItemStack icon(JsonObject value, String fallbackId) {
        String id = string(value, "icon", string(value, "item", fallbackId));
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(location);
        return item == Items.AIR ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return fallback;
        try { return value.getAsLong(); } catch (RuntimeException ignored) { return fallback; }
    }

    private static String limited(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
