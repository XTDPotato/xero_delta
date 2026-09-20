package com.xtdpotato.xero_delta.data;

/** Pure rules for independent leg fractures and their movement penalties. */
public final class LegInjuryRule {
    public static final int JUMP_SLOW_TICKS = 10;
    private static final double BROKEN_LEG_PENALTY = 0.30D;
    private static final double JUMP_PENALTY = 0.15D;

    private LegInjuryRule() {
    }

    public static Leg selectBreakTarget(boolean leftBroken, boolean rightBroken, boolean chooseLeft) {
        if (leftBroken && rightBroken) return Leg.NONE;
        if (leftBroken) return Leg.RIGHT;
        if (rightBroken) return Leg.LEFT;
        return chooseLeft ? Leg.LEFT : Leg.RIGHT;
    }

    public static double movementPenalty(boolean leftBroken, boolean rightBroken, boolean jumpSlowActive) {
        return movementPenalty(leftBroken, rightBroken, jumpSlowActive, false);
    }

    public static double movementPenalty(boolean leftBroken, boolean rightBroken,
                                         boolean jumpSlowActive, boolean painReliefActive) {
        if (painReliefActive) return 0.0D;
        double penalty = (leftBroken ? BROKEN_LEG_PENALTY : 0.0D)
            + (rightBroken ? BROKEN_LEG_PENALTY : 0.0D);
        if (jumpSlowActive && (leftBroken || rightBroken)) penalty += JUMP_PENALTY;
        return penalty;
    }

    public enum Leg {
        LEFT,
        RIGHT,
        NONE
    }
}
