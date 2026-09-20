package com.xtdpotato.xero_delta.data;


/** Pure health-effect scaling shared by combat handling and automated tests. */
public final class HealthEffectScalingRule {
    private static final int MIN_EXPLOSION_DIZZINESS_TICKS = 40;
    private static final int BASE_EXPLOSION_DIZZINESS_TICKS = 60;
    private static final int MAX_EXPLOSION_DIZZINESS_TICKS = 200;

    private HealthEffectScalingRule() {
    }

    public static double difficultyMultiplier(int difficultyId) {
        return switch (difficultyId) {
            case 0 -> 0.0D;
            case 1 -> 1.0D;
            case 2 -> 2.0D;
            case 3 -> 4.0D;
            default -> 1.0D;
        };
    }

    public static double combinedMultiplier(double configuredMultiplier, int difficultyId) {
        double configured = Double.isFinite(configuredMultiplier)
            ? Math.max(0.0D, Math.min(10.0D, configuredMultiplier))
            : HealthSystemRulesData.DEFAULT_EFFECT_MULTIPLIER;
        return configured * difficultyMultiplier(difficultyId);
    }

    public static int scaleInjuryPoints(int basePoints, double configuredMultiplier,
                                        int difficultyId) {
        if (basePoints <= 0) return 0;
        return Math.max(0, Math.min(100, (int) Math.round(
            basePoints * combinedMultiplier(configuredMultiplier, difficultyId))));
    }

    /** Explosions always stun for at least two seconds, even when injury accumulation is disabled. */
    public static int explosionDizzinessTicks(double configuredMultiplier, int difficultyId) {
        double difficultyScale = Math.max(1.0D, difficultyMultiplier(difficultyId));
        double configured = Double.isFinite(configuredMultiplier)
            ? Math.max(0.0D, Math.min(10.0D, configuredMultiplier))
            : HealthSystemRulesData.DEFAULT_EFFECT_MULTIPLIER;
        int ticks = (int) Math.round(BASE_EXPLOSION_DIZZINESS_TICKS
            * configured * difficultyScale);
        return Math.max(MIN_EXPLOSION_DIZZINESS_TICKS,
            Math.min(MAX_EXPLOSION_DIZZINESS_TICKS, ticks));
    }

    /**
     * Returns the probability that a body region is injured by blast pressure.
     * The value falls off smoothly with distance and is deliberately high at
     * close range, where a blast should affect several regions at once.
     */
    public static double explosionInjuryChance(double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0D) return 0.0D;
        double distance = Math.sqrt(distanceSquared);
        return Math.max(0.0D, Math.min(0.95D, 0.95D - distance * 0.105D));
    }

    /** Blast pressure contributes additional injury to each region it hits. */
    public static int explosionRegionPoints(int impact, double distanceSquared) {
        if (impact <= 0) return 0;
        double distance = Math.sqrt(Math.max(0.0D, distanceSquared));
        double scale = Math.max(0.20D, 1.0D - distance / 10.0D);
        return Math.max(1, Math.min(100, (int) Math.round(impact * 0.65D * scale)));
    }
}
