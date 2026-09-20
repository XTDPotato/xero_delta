package com.xtdpotato.xero_delta.data;

public final class WarehouseNameRules {
    private WarehouseNameRules() {}

    public static String sanitize(String input, int maximumCodePoints) {
        if (input == null || maximumCodePoints <= 0) return "";
        StringBuilder clean = new StringBuilder(Math.min(input.length(), maximumCodePoints));
        input.codePoints()
            .filter(codePoint -> !Character.isISOControl(codePoint))
            .limit(maximumCodePoints)
            .forEach(clean::appendCodePoint);
        return clean.toString().trim();
    }
}
