package com.xtdpotato.xero_delta.client;

/** Shared responsive geometry for the tactical and medical radial wheels. */
public final class WheelLayout {
    public static final int DEFAULT_SCALE_PERCENT = 100;
    public static final int DEFAULT_CENTER_PERCENT = 50;

    private WheelLayout() {
    }

    public static int radius(int width, int height, int scalePercent) {
        int shortest = Math.max(1, Math.min(width, height));
        int base = Math.min(142, Math.max(82, shortest / 3));
        int maximum = Math.max(24, shortest / 2 - 4);
        int scaled = Math.round(base * clamp(scalePercent, 50, 150) / 100.0F);
        return clamp(scaled, Math.min(36, maximum), maximum);
    }

    public static int center(int span, int radius, int centerPercent) {
        if (span <= 0) return 0;
        int desired = Math.round(span * clamp(centerPercent, 0, 100) / 100.0F);
        int margin = Math.max(0, radius) + 4;
        if (span <= margin * 2) return span / 2;
        return clamp(desired, margin, span - margin);
    }

    public static int deadZone(int radius) {
        return Math.max(12, Math.round(Math.max(0, radius) * 0.13F));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

