package com.xtdpotato.xero_delta.data;

/** Pure boundary rule kept separate so it can be verified without a game runtime. */
public final class FallInjuryRule {
    private FallInjuryRule() {
    }

    public static boolean shouldBreakLeg(float distance, double roll) {
        return distance > 5.0F && roll < 0.90D;
    }
}
