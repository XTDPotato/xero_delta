package com.xtdpotato.xero_delta.client;

import java.util.Locale;

/** Pure formatting and geometry helpers that do not load Minecraft client classes. */
public final class DownedHudMath {
    private DownedHudMath() {
    }

    public static int filledWidth(float progress, int width) {
        return Math.round(Math.max(0, width)
            * Math.max(0.0F, Math.min(1.0F, progress)));
    }

    public static String formatTime(int remainingTicks) {
        int seconds = Math.max(0, remainingTicks + 19) / 20;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    public static int sweepStart(float progress, int width, int sweepWidth) {
        int safeWidth = Math.max(0, width);
        int safeSweepWidth = Math.max(0, sweepWidth);
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        return Math.round((safeWidth + safeSweepWidth) * clamped) - safeSweepWidth;
    }

    public static int sweepAlpha(int pixel, int start, int sweepWidth) {
        if (sweepWidth <= 0 || pixel < start || pixel >= start + sweepWidth) return 0;
        float center = start + (sweepWidth - 1) * 0.5F;
        float radius = Math.max(1.0F, sweepWidth * 0.5F);
        float distance = Math.min(1.0F, Math.abs(pixel - center) / radius);
        float intensity = 1.0F - distance;
        return Math.round(220.0F * intensity * intensity);
    }
}
