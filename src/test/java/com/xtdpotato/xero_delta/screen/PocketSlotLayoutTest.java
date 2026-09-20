package com.xtdpotato.xero_delta.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketSlotLayoutTest {
    @Test
    void scaledPocketBoundsKeepRenderAndHitSizeIdentical() {
        PocketSlotLayout.Bounds bounds = PocketSlotLayout.bounds(100, 40, 27, 2, 4);
        assertEquals(27, bounds.width());
        assertEquals(27, bounds.height());
        assertTrue(bounds.contains(bounds.x(), bounds.y()));
        assertTrue(bounds.contains(bounds.x() + 26.99D, bounds.y() + 26.99D));
        assertFalse(bounds.contains(bounds.x() + 27.0D, bounds.y() + 10.0D));
    }

    @Test
    void adjacentPocketCellsUseConfiguredSizeAndGap() {
        PocketSlotLayout.Bounds first = PocketSlotLayout.bounds(10, 20, 24, 2, 0);
        PocketSlotLayout.Bounds second = PocketSlotLayout.bounds(10, 20, 24, 2, 1);
        assertEquals(first.x() + first.width() + 2, second.x());
        assertEquals(first.y(), second.y());
    }
}
