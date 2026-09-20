package com.xtdpotato.xero_delta.data;

/** Pure force-to-injury conversion used by server combat events and tests. */
public final class InjuryImpactRule {
    private InjuryImpactRule() {
    }

    public static int points(float finalDamage, double forceRoll) {
        if (!Float.isFinite(finalDamage) || finalDamage <= 0.0F) return 0;
        double roll = Math.max(0.0D, Math.min(1.0D, forceRoll));
        double force = 0.75D + roll * 1.25D;
        return Math.max(1, Math.min(20, (int) Math.round(finalDamage * force)));
    }
}
