package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerInjuryManagerTest {
    @Test
    void fiveBlocksNeverBreaksALeg() {
        assertFalse(FallInjuryRule.shouldBreakLeg(5.0F, 0.0D));
    }

    @Test
    void moreThanFiveBlocksUsesStrictNinetyPercentBoundary() {
        assertTrue(FallInjuryRule.shouldBreakLeg(5.01F, 0.8999D));
        assertFalse(FallInjuryRule.shouldBreakLeg(5.01F, 0.90D));
    }

    @Test
    void secondFallTargetsTheRemainingHealthyLeg() {
        assertEquals(LegInjuryRule.Leg.RIGHT,
            LegInjuryRule.selectBreakTarget(true, false, true));
        assertEquals(LegInjuryRule.Leg.LEFT,
            LegInjuryRule.selectBreakTarget(false, true, false));
        assertEquals(LegInjuryRule.Leg.NONE,
            LegInjuryRule.selectBreakTarget(true, true, true));
    }

    @Test
    void oneBrokenLegIsAtLeastSlownessTwoStrength() {
        assertEquals(0.30D, LegInjuryRule.movementPenalty(true, false, false), 0.0001D);
        assertEquals(0.60D, LegInjuryRule.movementPenalty(true, true, false), 0.0001D);
    }

    @Test
    void jumpingAddsHalfSecondPenaltyWhenALegIsBroken() {
        assertEquals(10, LegInjuryRule.JUMP_SLOW_TICKS);
        assertEquals(0.45D, LegInjuryRule.movementPenalty(true, false, true), 0.0001D);
        assertEquals(0.0D, LegInjuryRule.movementPenalty(false, false, true), 0.0001D);
    }
    @Test
    void painReliefSuppressesInjuryMovementPenaltyWithoutHealingTheInjury() {
        assertEquals(0.0D,
            LegInjuryRule.movementPenalty(true, false, false, true), 0.0001D);
        assertEquals(0.30D,
            LegInjuryRule.movementPenalty(true, false, false, false), 0.0001D);
    }
}
