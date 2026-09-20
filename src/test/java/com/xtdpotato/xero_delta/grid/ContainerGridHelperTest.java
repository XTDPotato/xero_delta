package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.data.ItemSize;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerGridHelperTest {
    @Test
    void evenFootprintsStayStableAroundTheirCenterIntersection() {
        assertEquals(1, ContainerGridHelper.anchorOffset(2, 0.1));
        assertEquals(0, ContainerGridHelper.anchorOffset(2, 0.5));
        assertEquals(0, ContainerGridHelper.anchorOffset(2, 0.9));
        assertEquals(2, ContainerGridHelper.anchorOffset(4, 0.1));
        assertEquals(1, ContainerGridHelper.anchorOffset(4, 0.9));

        int anchor = 10;
        assertEquals(anchor, 10 - ContainerGridHelper.anchorOffset(2, 0.75));
        assertEquals(anchor, 11 - ContainerGridHelper.anchorOffset(2, 0.25));
        assertEquals(anchor - 1, 10 - ContainerGridHelper.anchorOffset(2, 0.25));
        assertEquals(anchor + 1, 11 - ContainerGridHelper.anchorOffset(2, 0.75));
    }

    @Test
    void oddFootprintsStayCentered() {
        assertEquals(1, ContainerGridHelper.anchorOffset(3, 0.1));
        assertEquals(1, ContainerGridHelper.anchorOffset(3, 0.9));
    }

    @Test
    void slotHitboxIncludesTwoPixelGridGutters() {
        assertEquals(true, ContainerGridHelper.isWithinSlotHitbox(25.9, 37.9, 8, 20, true, true));
        assertEquals(false, ContainerGridHelper.isWithinSlotHitbox(26.0, 37.9, 8, 20, true, true));
        assertEquals(false, ContainerGridHelper.isWithinSlotHitbox(25.9, 38.0, 8, 20, true, true));
    }

    @Test
    void slotHitboxDoesNotExtendPastOuterGridEdge() {
        assertEquals(true, ContainerGridHelper.isWithinSlotHitbox(24.5, 30.0, 8, 20, true, false));
        assertEquals(false, ContainerGridHelper.isWithinSlotHitbox(24.5, 30.0, 8, 20, false, true));
        assertEquals(false, ContainerGridHelper.isWithinSlotHitbox(20.0, 36.5, 8, 20, true, false));
    }

    @Test
    void occupiedBoundarySpanChoosesExpansionDirection() {
        assertTrue(ContainerGridHelper.boundaryPrefersStart(0, 2));
        assertFalse(ContainerGridHelper.boundaryPrefersStart(1, 2));
        assertTrue(ContainerGridHelper.boundaryPrefersStart(0, 3));
        assertTrue(ContainerGridHelper.boundaryPrefersStart(1, 3));
        assertFalse(ContainerGridHelper.boundaryPrefersStart(2, 3));
    }

    @Test
    void clippedNonSquareFootprintsRequireRotationFallback() {
        assertFalse(ContainerGridHelper.isCompleteFootprint(4, new ItemSize(2, 3)));
        assertTrue(ContainerGridHelper.isCompleteFootprint(6, new ItemSize(2, 3)));
        assertFalse(ContainerGridHelper.isCompleteFootprint(7, new ItemSize(2, 4)));
        assertTrue(ContainerGridHelper.isCompleteFootprint(8, new ItemSize(2, 4)));
        assertFalse(ContainerGridHelper.isCompleteFootprint(14, new ItemSize(3, 5)));
        assertTrue(ContainerGridHelper.isCompleteFootprint(15, new ItemSize(3, 5)));
    }

    @Test
    void largerDisplacedItemCannotBeRefilledIntoSmallerOrigin() {
        assertFalse(ContainerGridHelper.canRefillOrigin(new ItemSize(1, 1), 6));
        assertFalse(ContainerGridHelper.canRefillOrigin(new ItemSize(2, 2), 6));
        assertTrue(ContainerGridHelper.canRefillOrigin(new ItemSize(2, 3), 6));
        assertTrue(ContainerGridHelper.canRefillOrigin(new ItemSize(2, 3), 4));
    }
}
