package com.xtdpotato.xero_delta.client;

import java.text.NumberFormat;
import java.util.Locale;

/** Minecraft-independent formatter for trading prices and player balances. */
public final class TradingCurrencyFormatter {
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);

    private TradingCurrencyFormatter() {
    }

    public static String formatPrice(long value) {
        return compact(value, false);
    }

    public static String formatBalance(long value) {
        return compact(value, true);
    }

    public static String formatDetailed(long value) {
        return NUMBER.format(Math.max(0L, value));
    }

    private static String compact(long value, boolean allowBillions) {
        value = Math.max(0L, value);
        if (allowBillions && value >= 1_000_000_000L) return value / 1_000_000_000L + "B";
        if (value >= 100_000_000L) return value / 1_000_000L + "M";
        if (value >= 1_000_000L) return value / 1_000L + "K";
        return NUMBER.format(value);
    }
}
