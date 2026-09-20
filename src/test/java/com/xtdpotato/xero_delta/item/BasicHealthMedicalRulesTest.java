package com.xtdpotato.xero_delta.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BasicHealthMedicalRulesTest {
    @Test
    void convertsOneRealGameplaySecondToOneServerPulse() {
        assertEquals(1.0D, BasicHealthMedicalRules.PULSE_SECONDS, 0.0001D);
        assertEquals(20, BasicHealthMedicalRules.PULSE_INTERVAL_TICKS);
    }

    @Test
    void spreadsTheLegacyQuarterHealthBudgetAcrossThreeSeconds() {
        assertEquals(25.0F, BasicHealthMedicalRules.totalHealing(100.0F), 0.0001F);
        assertEquals(10.0F, BasicHealthMedicalRules.healingPerSecond(100.0F), 0.0001F);
        assertEquals(60, BasicHealthMedicalRules.durationTicks(100.0F));
    }

    @Test
    void scalesHealingWithThePlayersEffectiveMaximumHealth() {
        assertEquals(20.0F, BasicHealthMedicalRules.totalHealing(80.0F), 0.0001F);
        assertEquals(8.0F, BasicHealthMedicalRules.healingPerSecond(80.0F), 0.0001F);
        assertEquals(60, BasicHealthMedicalRules.durationTicks(80.0F));
    }
}
