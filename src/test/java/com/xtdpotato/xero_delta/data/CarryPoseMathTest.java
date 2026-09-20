package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CarryPoseMathTest {
    @Test
    void rightShoulderTracksTheCarrierYaw() {
        var south = CarryPoseMath.shoulderOffset(0.0F);
        assertEquals(-0.48D, south.x(), 0.0001D);
        assertEquals(0.0D, south.z(), 0.0001D);

        var west = CarryPoseMath.shoulderOffset(90.0F);
        assertEquals(0.0D, west.x(), 0.0001D);
        assertEquals(-0.48D, west.z(), 0.0001D);
    }

    @Test
    void dropTargetIsExactlyHalfABlockInFront() {
        var south = CarryPoseMath.dropOffset(0.0F);
        assertEquals(0.0D, south.x(), 0.0001D);
        assertEquals(0.50D, south.z(), 0.0001D);

        var west = CarryPoseMath.dropOffset(90.0F);
        assertEquals(-0.50D, west.x(), 0.0001D);
        assertEquals(0.0D, west.z(), 0.0001D);
    }
}
