package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthPenaltyRuleTest {
    @Test
    void severeHealthSystemTraumaReducesMaximumHealthByTwentyPercent() {
        assertEquals(0.20D, HealthPenaltyRule.fraction(false, false,
            100.0F, 0.0F, 0.0F), 0.0001D);
    }

    @Test
    void painReliefMasksTraumaButNotAnExternalWeaknessEffect() {
        assertEquals(0.0D, HealthPenaltyRule.fraction(true, false,
            100.0F, 100.0F, 100.0F), 0.0001D);
        assertEquals(0.20D, HealthPenaltyRule.fraction(true, true,
            0.0F, 0.0F, 0.0F), 0.0001D);
    }

    @Test
    void multipleSeverePartsStillUseOneTwentyPercentReservedRegion() {
        assertEquals(0.20D, HealthPenaltyRule.fraction(false, false,
            100.0F, 100.0F, 100.0F), 0.0001D);
    }

    @Test
    void severeChestTraumaTemporarilyReservesTenPercent() {
        assertEquals(0.10D, HealthPenaltyRule.fraction(false, false,
            0.0F, 100.0F, 0.0F), 0.0001D);
    }

    @Test
    void yellowRescuePenaltyReservesTwentyPercentUntilDeath() {
        assertEquals(0.20D, HealthPenaltyRule.fraction(false, false, true,
            0.0F, 0.0F, 0.0F), 0.0001D);
        assertEquals(0.20D, HealthPenaltyRule.fraction(true, false, true,
            0.0F, 100.0F, 0.0F), 0.0001D);
    }}
