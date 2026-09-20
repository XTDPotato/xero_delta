package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaminaHudRendererTest {
    @Test
    void interpolationReachesTargetInTwoTenthsOfASecond() {
        assertEquals(0.5F, StaminaHudRenderer.approach(1.0F, 0.0F, 0.1F), 0.001F);
        assertEquals(0.0F, StaminaHudRenderer.approach(1.0F, 0.0F, 0.2F), 0.001F);
    }

    @Test
    void interpolationNeverLeavesNormalizedRange() {
        assertEquals(1.0F, StaminaHudRenderer.approach(0.9F, 2.0F, 0.2F), 0.001F);
        assertEquals(0.0F, StaminaHudRenderer.approach(0.1F, -2.0F, 0.2F), 0.001F);
    }

    @Test
    void damageTrailLagsBehindAJumpCostAndCatchesUpInAboutFourTenths() {
        assertEquals(0.75F, StaminaHudRenderer.damageTrailApproach(1.0F, 0.0F, 0.105F), 0.001F);
        assertEquals(0.0F, StaminaHudRenderer.damageTrailApproach(1.0F, 0.0F, 0.42F), 0.001F);
        assertEquals(1.0F, StaminaHudRenderer.damageTrailApproach(0.2F, 1.0F, 0.42F), 0.001F);
    }

    @Test
    void configuredOpacityMultipliesEverySourceAlpha() {
        assertEquals(0x40ABCDEF, StaminaHudRenderer.withOpacity(0x80ABCDEF, 0.5F));
        assertEquals(0x00ABCDEF, StaminaHudRenderer.withOpacity(0x80ABCDEF, 0.0F));
        assertEquals(0x80ABCDEF, StaminaHudRenderer.withOpacity(0x80ABCDEF, 1.0F));
    }

    @Test
    void fullStaminaHidesAfterOneStableSecond() {
        var tracker = new StaminaHudRenderer.VisibilityTracker();
        assertTrue(tracker.update(1.0F, 1_000L));
        assertTrue(tracker.update(1.0F, 1_999L));
        assertFalse(tracker.update(1.0F, 2_000L));
    }

    @Test
    void staminaBelowFullAlwaysRemainsVisible() {
        var tracker = new StaminaHudRenderer.VisibilityTracker();
        assertTrue(tracker.update(0.75F, 1_000L));
        assertTrue(tracker.update(0.75F, 20_000L));
    }

    @Test
    void aChangeShowsTheBarAgainAndRestartsTheFullTimer() {
        var tracker = new StaminaHudRenderer.VisibilityTracker();
        assertTrue(tracker.update(1.0F, 1_000L));
        assertFalse(tracker.update(1.0F, 2_000L));
        assertTrue(tracker.update(0.9F, 2_001L));
        assertTrue(tracker.update(1.0F, 2_500L));
        assertTrue(tracker.update(1.0F, 3_499L));
        assertFalse(tracker.update(1.0F, 3_500L));
    }
}
