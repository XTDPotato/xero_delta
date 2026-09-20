package com.xtdpotato.xero_delta.data;

import net.minecraft.world.item.ItemStack;

/** Shared parser for xero durability ranges and their persisted rule keys. */
public record DurabilityRange(String source, int min, int max) {
    public static final String PREFIX = "durability|";

    public static DurabilityRange parse(String source, int maxDamage) {
        if (source == null || !source.contains("..")) return null;
        String[] parts = source.trim().toLowerCase(java.util.Locale.ROOT).split("\\.\\.", 2);
        if (parts.length != 2 || maxDamage <= 0) return null;
        Integer min = resolve(parts[0], maxDamage, true);
        Integer max = resolve(parts[1], maxDamage, false);
        if (min == null || max == null || min < 0 || max < min || max > maxDamage) return null;
        return new DurabilityRange(source, min, max);
    }

    public static String key(String itemId, String range) {
        return PREFIX + itemId + "|" + range;
    }

    public static boolean matches(String key, ItemStack stack) {
        if (!key.startsWith(PREFIX) || stack.isEmpty() || !stack.isDamageableItem()) return false;
        String[] parts = key.split("\\|", 3);
        if (parts.length != 3 || !ModDataStorage.getIdOnlyKey(stack).equals(parts[1])) return false;
        DurabilityRange range = parse(parts[2], stack.getMaxDamage());
        return range != null && stack.getDamageValue() >= range.min && stack.getDamageValue() <= range.max;
    }

    private static Integer resolve(String value, int maxDamage, boolean lowerBound) {
        return switch (value.trim()) {
            case "min" -> 0;
            case "max" -> maxDamage;
            case "1_3" -> maxDamage / 3;
            case "2_3" -> maxDamage * 2 / 3;
            default -> {
                try { yield Integer.parseInt(value.trim()); }
                catch (NumberFormatException ignored) { yield null; }
            }
        };
    }
}
