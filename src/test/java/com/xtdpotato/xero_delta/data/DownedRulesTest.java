package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownedRulesTest {
    @Test
    void usesDeltaDownedTimings() {
        assertEquals(1_000, DownedRules.redDuration(false));
        assertEquals(1_000, DownedRules.redDuration(true));
        assertEquals(3_600, DownedRules.YELLOW_WINDOW_TICKS);
        assertEquals(300, DownedRules.YELLOW_RESCUE_TICKS);
        assertEquals(300, DownedRules.RED_RESCUE_TICKS);
        assertEquals(40, DownedRules.CARRY_WINDUP_TICKS);
        assertEquals(40, DownedRules.CARRY_DROP_TICKS);
        assertEquals(20, DownedRules.CARRY_ANIMATION_TICKS);
        assertEquals(60, DownedRules.ABANDON_HOLD_TICKS);
    }

    @Test
    void incomingDamageShortensTheRedTimer() {
        assertEquals(500, DownedRules.subtractDamageTime(1_000, 1_000, 50.0F));
        assertEquals(900, DownedRules.subtractDamageTime(1_000, 1_000, 10.0F));
        assertEquals(0, DownedRules.subtractDamageTime(100, 1_000, 20.0F));
        assertEquals(200, DownedRules.subtractDamageTime(200, 1_000, 0.0F));
    }

    @Test
    void explosionsDealDoubleRedDownTimerDamage() {
        assertEquals(40.0F, DownedRules.redDownExplosionDamage(20.0F), 0.001F);
        assertEquals(0.0F, DownedRules.redDownExplosionDamage(0.0F), 0.001F);
    }

    @Test
    void pointBlankExplosionIsWithinTwoBlockRadius() {
        assertTrue(DownedRules.isCloseExplosion(0.0D));
        assertTrue(DownedRules.isCloseExplosion(4.0D));
        assertFalse(DownedRules.isCloseExplosion(4.01D));
    }

    @Test
    void progressIsClamped() {
        assertEquals(0.5F, DownedRules.progress(100, 200), 0.001F);
        assertEquals(0.0F, DownedRules.progress(-1, 200), 0.001F);
        assertEquals(1.0F, DownedRules.progress(300, 200), 0.001F);
    }
    @Test
    void abandonRequiresAContinuousThreeSecondHold() {
        assertEquals(0.5F, DownedRules.abandonProgress(30), 0.001F);
        assertEquals(1.0F, DownedRules.abandonProgress(60), 0.001F);
        assertEquals(1.0F, DownedRules.abandonProgress(80), 0.001F);
    }

    @Test
    void rescueRequestsHaveATenSecondCooldown() {
        long next = DownedRules.nextRescueRequestTime(1_000L);
        assertEquals(1_200L, next);
        assertEquals(false, DownedRules.canRequestRescue(1_199L, next));
        assertEquals(true, DownedRules.canRequestRescue(1_200L, next));
    }
    @Test
    void rescueRequiresAContinuousHoldHeartbeat() {
        assertEquals(true, DownedRules.rescueHeartbeatFresh(110L, 100L));
        assertEquals(false, DownedRules.rescueHeartbeatFresh(111L, 100L));
    }
}
