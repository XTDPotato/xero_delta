package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthHudMathTest {
    @Test
    void lowHealthEdgesDeepenBelowThirtyPercentAndCapAtEightyPercent() {
        assertEquals(0.0F, HealthHudMath.lowHealthEdgeOpacity(1.0F), 0.001F);
        assertEquals(0.0F, HealthHudMath.lowHealthEdgeOpacity(0.30F), 0.001F);
        assertEquals(0.40F, HealthHudMath.lowHealthEdgeOpacity(0.20F), 0.001F);
        assertEquals(0.80F, HealthHudMath.lowHealthEdgeOpacity(0.10F), 0.001F);
        assertEquals(0.80F, HealthHudMath.lowHealthEdgeOpacity(0.05F), 0.001F);
        assertEquals(0.0F, HealthHudMath.lowHealthEdgeOpacity(Float.NaN), 0.001F);
    }

    @Test
    void supportsMaximumHealthAboveTwentyUsingPercentages() {
        assertEquals(45.0F, HealthHudMath.baselineMax(36.0F, 0.20D), 0.001F);
        assertEquals(0.40F, HealthHudMath.currentFraction(18.0F, 36.0F, 0.20D), 0.001F);
    }

    @Test
    void reservesTheRightmostTwentyPercentForWeakness() {
        assertEquals(112, HealthHudMath.availablePixels(140, 0.20D));
        assertEquals(140, HealthHudMath.availablePixels(140, 0.0D));
    }
}
