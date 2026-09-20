package com.xtdpotato.xero_delta.data;

/** Proportional health-cap penalties for trauma, weakness and yellow-down rescue. */
public final class HealthPenaltyRule {
    public static final double DEFAULT_CHEST_TRAUMA_PENALTY = 0.10D;
    public static final double SEVERE_TRAUMA_PENALTY = 0.20D;
    public static final double DEFAULT_YELLOW_RESCUE_PENALTY = 0.20D;

    private HealthPenaltyRule() {
    }

    public static double fraction(boolean painRelief, boolean weaknessEffect,
                                  float head, float chest, float abdomen) {
        return fraction(painRelief, weaknessEffect, false, head, chest, abdomen,
            DEFAULT_CHEST_TRAUMA_PENALTY, DEFAULT_YELLOW_RESCUE_PENALTY);
    }

    public static double fraction(boolean painRelief, boolean weaknessEffect,
                                  boolean yellowRescuePenalty,
                                  float head, float chest, float abdomen) {
        return fraction(painRelief, weaknessEffect, yellowRescuePenalty,
            head, chest, abdomen, DEFAULT_CHEST_TRAUMA_PENALTY,
            DEFAULT_YELLOW_RESCUE_PENALTY);
    }

    public static double fraction(boolean painRelief, boolean weaknessEffect,
                                  boolean yellowRescuePenalty,
                                  float head, float chest, float abdomen,
                                  double chestPenalty, double yellowPenalty) {
        if (weaknessEffect) return SEVERE_TRAUMA_PENALTY;
        if (yellowRescuePenalty) return clamp(yellowPenalty);
        if (painRelief) return 0.0D;
        if (head >= 100.0F || abdomen >= 100.0F) return SEVERE_TRAUMA_PENALTY;
        if (chest >= 100.0F) return clamp(chestPenalty);
        return 0.0D;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0D;
        return Math.max(0.0D, Math.min(0.90D, value));
    }
}