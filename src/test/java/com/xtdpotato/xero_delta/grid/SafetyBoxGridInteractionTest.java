package com.xtdpotato.xero_delta.grid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SafetyBoxGridInteractionTest {
    @Test
    void resolvesEveryCellInsideSafetyBoxGrid() {
        var target = SafetyBoxGridInteraction.targetAt(
            151.0D, 91.0D, 100, 40, 18, 3, 3);

        assertEquals(2, target.column());
        assertEquals(2, target.row());
        assertEquals(8, target.cell());
    }

    @Test
    void preservesCursorFractionForAutomaticRotation() {
        var target = SafetyBoxGridInteraction.targetAt(
            109.0D, 44.5D, 100, 40, 18, 3, 3);

        assertEquals(0.5D, target.fractionX(), 0.0001D);
        assertEquals(0.25D, target.fractionY(), 0.0001D);
    }

    @Test
    void rejectsPixelsOutsideOverlayInsteadOfFallingThrough() {
        assertNull(SafetyBoxGridInteraction.targetAt(
            154.0D, 50.0D, 100, 40, 18, 3, 3));
        assertNull(SafetyBoxGridInteraction.targetAt(
            99.9D, 50.0D, 100, 40, 18, 3, 3));
    }
}
