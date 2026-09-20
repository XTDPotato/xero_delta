package com.xtdpotato.xero_delta.trading;

import java.util.Optional;
import java.util.random.RandomGenerator;

/** Parses absolute prices and item-value-relative price offsets used by trading commands. */
public record TradingPriceSpec(Mode mode, long minimum, long maximum) {
    public enum Mode {
        ABSOLUTE,
        OFFSET
    }

    public static Optional<TradingPriceSpec> parse(String input) {
        if (input == null) return Optional.empty();
        String value = input.trim().replace("_", "");
        if (value.isEmpty()) return Optional.empty();
        if (value.contains("..")) return parseOffsetRange(value);
        if (value.startsWith("+") || value.startsWith("-")) {
            Long offset = parseBounded(value, true);
            return offset == null
                ? Optional.empty()
                : Optional.of(new TradingPriceSpec(Mode.OFFSET, offset, offset));
        }
        Long absolute = parseBounded(value, false);
        return absolute == null
            ? Optional.empty()
            : Optional.of(new TradingPriceSpec(Mode.ABSOLUTE, absolute, absolute));
    }

    private static Optional<TradingPriceSpec> parseOffsetRange(String value) {
        String[] parts = value.split("\\.\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) return Optional.empty();
        Long first = parseBounded(parts[0], true);
        Long second = parseBounded(parts[1], true);
        if (first == null || second == null) return Optional.empty();
        return Optional.of(new TradingPriceSpec(Mode.OFFSET,
            Math.min(first, second), Math.max(first, second)));
    }

    private static Long parseBounded(String value, boolean offset) {
        try {
            long parsed = Long.parseLong(value);
            if (offset) {
                return parsed < -TradingRules.MAX_CONFIG_CURRENCY
                    || parsed > TradingRules.MAX_CONFIG_CURRENCY ? null : parsed;
            }
            return parsed < 1L || parsed > TradingRules.MAX_CONFIG_CURRENCY ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public long resolve(long itemValue, RandomGenerator random) {
        long selected = minimum >= maximum ? minimum : random.nextLong(minimum, maximum + 1L);
        if (mode == Mode.ABSOLUTE) return Math.max(1L, Math.min(TradingRules.MAX_CURRENCY, selected));
        long base = TradingRules.normalizeItemValue(itemValue);
        if (selected > 0L && base > TradingRules.MAX_CURRENCY - selected) {
            return TradingRules.MAX_CURRENCY;
        }
        return Math.max(1L, Math.min(TradingRules.MAX_CURRENCY, base + selected));
    }
}
