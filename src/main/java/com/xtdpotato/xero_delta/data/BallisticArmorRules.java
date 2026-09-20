package com.xtdpotato.xero_delta.data;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Quality-based projectile wear against gray through red armor tiers. */
public final class BallisticArmorRules {
    public static final int TIER_COUNT = 6;
    private static final List<Double> GRAY = List.of(0.60D, 0.60D, 0.40D, 0.30D, 0.20D, 0.20D);
    private static final List<Double> GREEN = List.of(0.70D, 0.70D, 0.70D, 0.50D, 0.40D, 0.30D);
    private static final List<Double> BLUE = List.of(0.90D, 0.90D, 0.90D, 0.90D, 0.50D, 0.40D);
    private static final List<Double> PURPLE = List.of(1.00D, 1.00D, 1.00D, 1.00D, 1.00D, 0.60D);
    private static final List<Double> GOLD = List.of(1.10D, 1.10D, 1.10D, 1.10D, 1.10D, 1.10D);
    private static final List<Double> RED = List.of(1.20D, 1.20D, 1.20D, 1.20D, 1.20D, 1.20D);
    private static volatile Map<String, List<Double>> clientRules = Map.of();

    private BallisticArmorRules() {
    }

    public static List<Double> defaultsForProjectileQuality(String quality) {
        return switch (normalize(quality)) {
            case "green" -> GREEN;
            case "blue" -> BLUE;
            case "purple" -> PURPLE;
            case "gold" -> GOLD;
            case "red" -> RED;
            default -> GRAY;
        };
    }

    public static double multiplier(String projectileQuality, String armorQuality,
                                    List<Double> custom) {
        List<Double> values = valid(custom) ? custom : defaultsForProjectileQuality(projectileQuality);
        return values.get(tierIndex(armorQuality));
    }

    public static int durabilityWear(int maxDurability, float baseDamage, double multiplier) {
        if (maxDurability <= 1 || baseDamage <= 0.0F || multiplier <= 0.0D) return 0;
        double damageFraction = Math.min(1.0D, baseDamage / 100.0D);
        return Math.max(1, (int) Math.ceil(maxDurability * damageFraction * multiplier));
    }

    public static List<Double> resolve(Map<String, List<Double>> source, ItemStack stack) {
        if (source == null || source.isEmpty() || stack == null || stack.isEmpty()) return null;
        String[] exactKeys = {
            ServerItemRules.exactKey(ModDataStorage.getKey(stack)),
            ServerItemRules.exactKey(ModDataStorage.getTypeKey(stack)),
            ServerItemRules.exactKey(ModDataStorage.getIdOnlyKey(stack))
        };
        for (String key : exactKeys) {
            List<Double> value = source.get(key);
            if (valid(value)) return value;
        }
        String durabilityKey = null;
        for (String key : source.keySet()) {
            if (DurabilityRange.matches(key, stack)
                && (durabilityKey == null || key.compareTo(durabilityKey) < 0)) {
                durabilityKey = key;
            }
        }
        if (durabilityKey != null && valid(source.get(durabilityKey))) return source.get(durabilityKey);
        String id = ModDataStorage.getIdOnlyKey(stack);
        String bestPrefix = null;
        List<Double> best = null;
        for (var entry : source.entrySet()) {
            if (!entry.getKey().startsWith("type:") || !valid(entry.getValue())) continue;
            String prefix = entry.getKey().substring("type:".length());
            if (id.startsWith(prefix) && (bestPrefix == null || prefix.length() > bestPrefix.length())) {
                bestPrefix = prefix;
                best = entry.getValue();
            }
        }
        if (best != null) return best;
        List<Double> global = source.get("*");
        return valid(global) ? global : null;
    }

    public static void updateClientRules(Map<String, List<Double>> rules) {
        clientRules = immutableRules(rules);
    }

    public static List<Double> clientRule(ItemStack stack) {
        return resolve(clientRules, stack);
    }

    public static Map<String, List<Double>> immutableRules(Map<String, List<Double>> source) {
        if (source == null || source.isEmpty()) return Map.of();
        var result = new java.util.LinkedHashMap<String, List<Double>>();
        source.forEach((key, value) -> {
            if (key != null && !key.isBlank() && valid(value)) result.put(key, List.copyOf(value));
        });
        return Map.copyOf(result);
    }

    public static boolean valid(List<Double> values) {
        if (values == null || values.size() != TIER_COUNT) return false;
        for (Double value : values) {
            if (value == null || !Double.isFinite(value) || value < 0.0D || value > 10.0D) return false;
        }
        return true;
    }

    public static int tierIndex(String quality) {
        return switch (normalize(quality)) {
            case "green" -> 1;
            case "blue" -> 2;
            case "purple" -> 3;
            case "gold" -> 4;
            case "red" -> 5;
            default -> 0;
        };
    }

    private static String normalize(String quality) {
        String normalized = quality == null ? "gray" : quality.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "red", "gold", "purple", "blue", "green", "gray" -> normalized;
            default -> "gray";
        };
    }
}