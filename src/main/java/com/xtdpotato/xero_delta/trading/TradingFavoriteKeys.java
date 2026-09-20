package com.xtdpotato.xero_delta.trading;

import java.util.HashSet;
import java.util.Set;

/** Compatibility helpers for component-sensitive market favorites. */
public final class TradingFavoriteKeys {
    private TradingFavoriteKeys() {
    }

    public static boolean isVariantKey(String key) {
        if (key == null) return false;
        int separator = key.indexOf('{');
        return separator > key.indexOf(':') + 1 && key.endsWith("}");
    }

    public static String baseItemId(String key) {
        if (key == null) return "";
        int separator = key.indexOf('{');
        return separator < 0 ? key : key.substring(0, separator);
    }

    /**
     * Removes broad item-id favorites left by builds that did not distinguish
     * component variants. A base key is only removed when the same favorite set
     * already contains at least one concrete variant, so ordinary favorites are
     * preserved.
     */
    public static boolean normalize(Set<String> favorites) {
        if (favorites == null || favorites.isEmpty()) return false;
        Set<String> variantBases = new HashSet<>();
        for (String key : favorites) {
            if (isVariantKey(key)) variantBases.add(baseItemId(key));
        }
        if (variantBases.isEmpty()) return false;
        return favorites.removeIf(key -> !isVariantKey(key) && variantBases.contains(key));
    }

    /** Removes the old broad key before a concrete variant is selected. */
    public static boolean removeLegacyBaseForVariant(Set<String> favorites, String key) {
        return favorites != null && isVariantKey(key) && favorites.remove(baseItemId(key));
    }
}
