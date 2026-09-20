package com.xtdpotato.xero_delta.item;

/** Health-cap-aware eligibility shared by every health-item use path. */
public final class MedicalHealthRules {
    private static final float EPSILON = 0.01F;

    private MedicalHealthRules() {
    }

    /** Medical descriptions and durability use the HUD's 100-point health scale. */
    public static float toDisplayHealth(float health, float baselineMaximumHealth) {
        return Math.max(0.0F, health) * 100.0F / Math.max(1.0F, baselineMaximumHealth);
    }

    public static float toEntityHealth(float displayHealth, float baselineMaximumHealth) {
        return Math.max(0.0F, displayHealth) * Math.max(1.0F, baselineMaximumHealth) / 100.0F;
    }

    public static int healingTicks(float missingHealth, float baselineMaximumHealth,
                                   float availableDurability, float displayHealthPerTick) {
        float points = Math.min(toDisplayHealth(missingHealth, baselineMaximumHealth),
            Math.max(0.0F, availableDurability));
        return (int) Math.ceil(points / displayHealthPerTick);
    }

    public static boolean hasMissingHealth(float health, float effectiveMaximumHealth) {
        return health + EPSILON < Math.max(0.0F, effectiveMaximumHealth);
    }
}
