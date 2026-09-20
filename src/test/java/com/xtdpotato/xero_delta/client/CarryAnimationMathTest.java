package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarryAnimationMathTest {
    @Test
    void estimatesUnsynchronizedCarryTicksFromElapsedTime() {
        assertEquals(40.0F, CarryAnimationMath.estimatedRemaining(40, 0L), 0.001F);
        assertEquals(30.0F, CarryAnimationMath.estimatedRemaining(40, 500L), 0.001F);
        assertEquals(0.0F, CarryAnimationMath.estimatedRemaining(40, 3_000L), 0.001F);
    }

    @Test
    void oneSecondAnimationFinishesBeforeTheTwoSecondActionLock() {
        assertEquals(0.0F, CarryAnimationMath.progress((byte) 1, 40.0F, 40, 20), 0.001F);
        assertEquals(0.5F, CarryAnimationMath.progress((byte) 1, 30.0F, 40, 20), 0.001F);
        assertEquals(1.0F, CarryAnimationMath.progress((byte) 1, 20.0F, 40, 20), 0.001F);
        assertEquals(1.0F, CarryAnimationMath.progress((byte) 1, 0.0F, 40, 20), 0.001F);
        assertEquals(1.0F, CarryAnimationMath.progress((byte) 3, 40.0F, 40, 20), 0.001F);
        assertEquals(0.5F, CarryAnimationMath.progress((byte) 3, 30.0F, 40, 20), 0.001F);
        assertEquals(0.0F, CarryAnimationMath.progress((byte) 3, 20.0F, 40, 20), 0.001F);
        assertEquals(0.0F, CarryAnimationMath.progress((byte) 3, 0.0F, 40, 20), 0.001F);
    }

    @Test
    void smootherStepStartsAndEndsWithoutAnAbruptSlope() {
        float nearStart = CarryAnimationMath.smootherStep(0.05F);
        float nearEnd = CarryAnimationMath.smootherStep(0.95F);
        assertTrue(nearStart < 0.01F);
        assertTrue(nearEnd > 0.99F);
    }
}
