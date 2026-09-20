package com.xtdpotato.xero_delta.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MedicalHealthRulesTest {
    @Test
    void oneSecondOfTreatmentUsesDisplayPointsInsteadOfVanillaHearts() {
        for (float maximum : new float[]{20.0F, 40.0F, 100.0F}) {
            float outdoor = MedicalHealthRules.toEntityHealth(
                OutdoorMedicalKitRules.healingPerTick(), maximum) * 20;
            float battlefield = MedicalHealthRules.toEntityHealth(
                BattlefieldMedicalKitRules.healingPerTick(), maximum) * 20;
            assertEquals(maximum * 0.2F, outdoor, 0.0001F);
            assertEquals(maximum * 0.3F, battlefield, 0.0001F);
            assertEquals(20.0F, MedicalHealthRules.toDisplayHealth(outdoor, maximum), 0.0001F);
            assertEquals(30.0F, MedicalHealthRules.toDisplayHealth(battlefield, maximum), 0.0001F);
        }
    }

    @Test
    void treatmentDurationRespectsRemainingDurabilityAndPartialFinalTick() {
        assertEquals(7, MedicalHealthRules.healingTicks(10, 20, 7, 1));
        assertEquals(5, MedicalHealthRules.healingTicks(10, 20, 7, 1.5F));
        assertEquals(1, MedicalHealthRules.healingTicks(0.1F, 20, 350, 1));
        assertEquals(0, MedicalHealthRules.healingTicks(10, 20, 0, 1));
        assertEquals(0, MedicalHealthRules.healingTicks(-1, 20, 350, 1));
    }

    @Test
    void fullEffectiveHealthCapBlocksHealthItems() {
        assertFalse(MedicalHealthRules.hasMissingHealth(80.0F, 80.0F));
        assertFalse(MedicalHealthRules.hasMissingHealth(100.0F, 100.0F));
    }

    @Test
    void healthBelowEitherEffectiveCapAllowsHealthItems() {
        assertTrue(MedicalHealthRules.hasMissingHealth(79.0F, 80.0F));
        assertTrue(MedicalHealthRules.hasMissingHealth(99.0F, 100.0F));
    }

    @Test
    void tinyFloatingPointDifferencesStillCountAsFull() {
        assertFalse(MedicalHealthRules.hasMissingHealth(79.995F, 80.0F));
    }
}
