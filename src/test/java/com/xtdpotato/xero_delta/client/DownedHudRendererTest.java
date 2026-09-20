package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DownedHudRendererTest {
    @Test
    void yellowCountdownUsesMinutesAndZeroPaddedSeconds() {
        assertEquals("02:39", DownedHudMath.formatTime(159 * 20));
        assertEquals("00:01", DownedHudMath.formatTime(1));
        assertEquals("00:00", DownedHudMath.formatTime(0));
    }

    @Test
    void remainingBarWidthIsClamped() {
        assertEquals(0, DownedHudMath.filledWidth(-1.0F, 182));
        assertEquals(91, DownedHudMath.filledWidth(0.5F, 182));
        assertEquals(182, DownedHudMath.filledWidth(2.0F, 182));
    }

    @Test
    void callingSweepMovesAcrossAndFadesAtItsEdges() {
        assertEquals(-20, DownedHudMath.sweepStart(0.0F, 100, 20));
        assertEquals(40, DownedHudMath.sweepStart(0.5F, 100, 20));
        assertEquals(100, DownedHudMath.sweepStart(1.0F, 100, 20));
        assertEquals(0, DownedHudMath.sweepAlpha(39, 40, 20));
        assertEquals(0, DownedHudMath.sweepAlpha(60, 40, 20));
        assertEquals(199, DownedHudMath.sweepAlpha(49, 40, 20));
    }
}
