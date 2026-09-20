package com.xtdpotato.xero_delta.data;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthEffectScalingRuleTest {
    @Test
    void difficultyUsesOneTwoAndFourScaling() {
        assertEquals(10, HealthEffectScalingRule.scaleInjuryPoints(10, 1.0D, 1));
        assertEquals(20, HealthEffectScalingRule.scaleInjuryPoints(10, 1.0D, 2));
        assertEquals(40, HealthEffectScalingRule.scaleInjuryPoints(10, 1.0D, 3));
    }

    @Test
    void configuredMultiplierCombinesWithDifficulty() {
        assertEquals(10, HealthEffectScalingRule.scaleInjuryPoints(10, 0.5D, 2));
        assertEquals(40, HealthEffectScalingRule.scaleInjuryPoints(10, 2.0D, 2));
        assertEquals(0, HealthEffectScalingRule.scaleInjuryPoints(10, 0.0D, 3));
    }

    @Test
    void peacefulDoesNotAccumulateInjuries() {
        assertEquals(0, HealthEffectScalingRule.scaleInjuryPoints(10, 10.0D, 0));
    }

    @Test
    void explosionAlwaysHasAVisibleStunWindow() {
        assertEquals(40, HealthEffectScalingRule.explosionDizzinessTicks(0.0D, 1));
        assertEquals(60, HealthEffectScalingRule.explosionDizzinessTicks(1.0D, 1));
        assertEquals(120, HealthEffectScalingRule.explosionDizzinessTicks(1.0D, 2));
        assertEquals(200, HealthEffectScalingRule.explosionDizzinessTicks(1.0D, 3));
    }

    @Test
    void explosionInjuryFallsOffWithDistance() {
        assertEquals(0.95D, HealthEffectScalingRule.explosionInjuryChance(0.0D), 0.001D);
        assertTrue(HealthEffectScalingRule.explosionInjuryChance(4.0D)
            > HealthEffectScalingRule.explosionInjuryChance(64.0D));
        assertEquals(0, HealthEffectScalingRule.explosionInjuryChance(100.0D), 0.001D);
    }

    @Test
    void closeBlastDoesMoreRegionalInjury() {
        assertTrue(HealthEffectScalingRule.explosionRegionPoints(10, 1.0D)
            > HealthEffectScalingRule.explosionRegionPoints(10, 81.0D));
    }
}
