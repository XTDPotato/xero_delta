package com.xtdpotato.xero_delta.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutdoorMedicalKitRulesTest {
    @Test
    void usesFourSecondStartupAndThreeHundredFiftyDurability() {
        assertEquals(80, OutdoorMedicalKitRules.STARTUP_TICKS);
        assertEquals(350, OutdoorMedicalKitRules.MAX_DURABILITY);
        assertEquals(25, OutdoorMedicalKitRules.WOUND_DURABILITY);
        assertEquals(25, OutdoorMedicalKitRules.PAIN_RELIEF_DURABILITY);
        assertEquals(600, OutdoorMedicalKitRules.PAIN_RELIEF_TICKS);
    }

    @Test
    void healsTwentyHealthAtOneSecondIntervals() {
        assertEquals(20.0F, OutdoorMedicalKitRules.HEAL_PER_SECOND, 0.0001F);
        assertEquals(20, OutdoorMedicalKitRules.HEAL_INTERVAL_TICKS);
        assertEquals(5, OutdoorMedicalKitRules.healingPulseCount(100.0F));
        assertEquals(100, OutdoorMedicalKitRules.healingPulseTick(0));
        assertEquals(120, OutdoorMedicalKitRules.healingPulseTick(1));
        assertEquals(140, OutdoorMedicalKitRules.healingPulseTick(2));
        assertEquals(160, OutdoorMedicalKitRules.healingPulseTick(3));
        assertEquals(180, OutdoorMedicalKitRules.healingPulseTick(4));
        assertEquals(1, OutdoorMedicalKitRules.healingPulseCount(0.5F));
        assertEquals(1.0F, OutdoorMedicalKitRules.healingPerTick(), 0.0001F);
    }

    @Test
    void estimatesStartupPlusOnlyTheTimeNeededForMissingHealth() {
        assertEquals(80, OutdoorMedicalKitRules.estimatedDurationTicks(20.0F, 20.0F));
        assertEquals(180, OutdoorMedicalKitRules.estimatedDurationTicks(0.0F, 20.0F));
        assertEquals(130, OutdoorMedicalKitRules.estimatedDurationTicks(10.0F, 20.0F));
        assertEquals(180, OutdoorMedicalKitRules.estimatedDurationTicks(0.0F, 100.0F));
        assertEquals(81, OutdoorMedicalKitRules.estimatedDurationTicks(19.9F, 20.0F));
        assertEquals(110, OutdoorMedicalKitRules.estimatedDurationTicks(10.0F, 16.0F, 20.0F));
    }
}
