package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LockIconLayoutTest {
    @Test
    void centersSquareTextureInsideVerticalFootprint() {
        assertEquals(new LockIconLayout.Bounds(10, 29, 36, 36),
            LockIconLayout.contain(10, 20, 36, 54, 16, 16));
    }

    @Test
    void centersSquareTextureInsideHorizontalFootprint() {
        assertEquals(new LockIconLayout.Bounds(19, 20, 36, 36),
            LockIconLayout.contain(10, 20, 54, 36, 16, 16));
    }

    @Test
    void preservesNonSquareTextureAspectRatio() {
        assertEquals(new LockIconLayout.Bounds(4, 8, 60, 30),
            LockIconLayout.contain(4, 8, 60, 30, 16, 8));
    }
}
