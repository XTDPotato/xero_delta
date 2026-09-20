package com.xtdpotato.xero_delta.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattlefieldMedicalKitRulesTest {
    @Test
    void usesThreeAndHalfSecondStartup() {
        assertEquals(70, BattlefieldMedicalKitRules.STARTUP_TICKS);
        assertEquals(25, BattlefieldMedicalKitRules.WOUND_DURABILITY);
        assertEquals(25, BattlefieldMedicalKitRules.PAIN_RELIEF_DURABILITY);
        assertEquals(1200, BattlefieldMedicalKitRules.PAIN_RELIEF_TICKS);
    }

    @Test
    void healsThirtyHealthAtOneSecondIntervals() {
        assertEquals(30.0F, BattlefieldMedicalKitRules.HEAL_PER_SECOND, 0.0001F);
        assertEquals(20, BattlefieldMedicalKitRules.HEAL_INTERVAL_TICKS);
        assertEquals(4, BattlefieldMedicalKitRules.healingPulseCount(100.0F));
        assertEquals(90, BattlefieldMedicalKitRules.healingPulseTick(0));
        assertEquals(110, BattlefieldMedicalKitRules.healingPulseTick(1));
        assertEquals(130, BattlefieldMedicalKitRules.healingPulseTick(2));
        assertEquals(150, BattlefieldMedicalKitRules.healingPulseTick(3));
        assertEquals(1, BattlefieldMedicalKitRules.healingPulseCount(0.5F));
        assertEquals(1.5F, BattlefieldMedicalKitRules.healingPerTick(), 0.0001F);
    }

    @Test
    void estimatesStartupPlusOnlyTheTimeNeededForMissingHealth() {
        assertEquals(70, BattlefieldMedicalKitRules.estimatedDurationTicks(20.0F, 20.0F));
        assertEquals(137, BattlefieldMedicalKitRules.estimatedDurationTicks(0.0F, 20.0F));
        assertEquals(104, BattlefieldMedicalKitRules.estimatedDurationTicks(10.0F, 20.0F));
        assertEquals(137, BattlefieldMedicalKitRules.estimatedDurationTicks(0.0F, 100.0F));
        assertEquals(71, BattlefieldMedicalKitRules.estimatedDurationTicks(19.9F, 20.0F));
        assertEquals(90, BattlefieldMedicalKitRules.estimatedDurationTicks(10.0F, 16.0F, 20.0F));
    }
}
