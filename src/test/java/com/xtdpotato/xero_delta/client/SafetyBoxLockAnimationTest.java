package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafetyBoxLockAnimationTest {
    @Test
    void usesRequestedFadeHoldAndFadeTimeline() {
        assertEquals(0.5F, LockFadeCurve.alpha(250_000_000L), 0.0001F);
        assertEquals(1.0F, LockFadeCurve.alpha(500_000_000L), 0.0001F);
        assertEquals(0.5F, LockFadeCurve.alpha(750_000_000L), 0.0001F);
        assertEquals(0.0F, LockFadeCurve.alpha(1_000_000_000L), 0.0F);
    }

    @Test
    void isTransparentOutsideAnimationWindow() {
        assertEquals(500_000_000L, LockFadeCurve.FADE_IN_NANOS);
        assertEquals(0L, LockFadeCurve.HOLD_NANOS);
        assertEquals(500_000_000L, LockFadeCurve.FADE_OUT_NANOS);
        assertEquals(0L, LockFadeCurve.COOLDOWN_NANOS);
        assertEquals(1_000_000_000L, LockFadeCurve.DURATION_NANOS);
        assertEquals(0.0F, LockFadeCurve.alpha(0), 0.0F);
        assertEquals(0.0F, LockFadeCurve.alpha(LockFadeCurve.DURATION_NANOS), 0.0F);
    }
}
