package com.xtdpotato.xero_delta.client;

/** Pure percentage math for the Delta health bar, independent of vanilla heart count. */
public final class HealthHudMath {
    private HealthHudMath() {
    }

    public static float clampPenalty(double penalty) {
        return (float) Math.max(0.0D, Math.min(0.90D, penalty));
    }

    public static float baselineMax(float effectiveMax, double penalty) {
        float safeMax = Math.max(1.0F, effectiveMax);
        return safeMax / (1.0F - clampPenalty(penalty));
    }

    public static float currentFraction(float health, float effectiveMax, double penalty) {
        float baseline = baselineMax(effectiveMax, penalty);
        return Math.max(0.0F, Math.min(1.0F, health / baseline));
    }

    public static int availablePixels(int width, double penalty) {
        return Math.max(0, Math.min(width,
            Math.round(width * (1.0F - clampPenalty(penalty)))));
    }

    public static float lowHealthEdgeOpacity(float fraction) {
        if (!Float.isFinite(fraction) || fraction >= 0.30F) return 0.0F;
        if (fraction <= 0.10F) return 0.80F;
        return 0.80F * (0.30F - fraction) / 0.20F;
    }
}
