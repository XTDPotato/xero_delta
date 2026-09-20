package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StaminaRulesTest {
    @Test
    void defaultsMatchDeltaLayoutRules() {
        assertEquals(180.0F, StaminaRules.DEFAULT_MAX, 0.001F);
        assertEquals(700, StaminaRules.SPRINT_DURATION_TICKS);
        assertEquals(10.0F, StaminaRules.JUMP_COST, 0.001F);
        assertEquals(40, StaminaRules.REGEN_DELAY_TICKS);
        assertEquals(20.0F, StaminaRules.REGEN_PER_TICK * 20.0F, 0.001F);
    }

    @Test
    void jumpAndSprintConsumptionNeverGoBelowZero() {
        assertEquals(170.0F,
            StaminaRules.consume(180.0F, StaminaRules.JUMP_COST), 0.001F);
        assertEquals(0.0F, StaminaRules.consume(4.0F, 10.0F), 0.001F);
        assertTrue(StaminaRules.exhausted(0.0F));
    }

    @Test
    void fullDefaultStaminaSupportsThirtyFiveSecondsOfSprinting() {
        float stamina = StaminaRules.DEFAULT_MAX;
        for (int tick = 0; tick < StaminaRules.SPRINT_DURATION_TICKS; tick++) {
            stamina = StaminaRules.consume(stamina, StaminaRules.SPRINT_COST_PER_TICK);
        }
        assertEquals(0.0F, stamina, 0.01F);
    }

    @Test
    void sprintLocksOnlyWhenEmptyAndUnlocksAboveFivePercent() {
        assertTrue(StaminaRules.shouldLockSprint(0.0F, 180.0F));
        assertFalse(StaminaRules.shouldLockSprint(0.01F, 180.0F));
        assertFalse(StaminaRules.canResumeSprint(9.0F, 180.0F));
        assertTrue(StaminaRules.canResumeSprint(9.01F, 180.0F));
    }

    @Test
    void sprintLockThresholdScalesWithMaximumStamina() {
        assertTrue(StaminaRules.shouldLockSprint(0.0F, 100.0F));
        assertFalse(StaminaRules.shouldLockSprint(0.01F, 100.0F));
        assertFalse(StaminaRules.canResumeSprint(5.0F, 100.0F));
        assertTrue(StaminaRules.canResumeSprint(5.01F, 100.0F));
    }

    @Test
    void hungerGateUsesThreeAndSixBars() {
        assertEquals(6, StaminaRules.EXHAUSTED_FOOD_LEVEL);
        assertEquals(12, StaminaRules.RECOVERED_FOOD_LEVEL);
    }

    @Test
    void regenerationIsContinuousAndClampedAtMaximum() {
        assertEquals(51.0F, StaminaRules.regenerate(50.0F, 180.0F), 0.001F);
        assertEquals(180.0F, StaminaRules.regenerate(180.0F, 180.0F), 0.001F);
    }
}
