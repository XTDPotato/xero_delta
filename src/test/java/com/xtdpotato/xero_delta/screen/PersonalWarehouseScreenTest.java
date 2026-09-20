package com.xtdpotato.xero_delta.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonalWarehouseScreenTest {
    @Test
    void visibleRowsFollowAvailablePanelHeight() {
        assertEquals(8, WarehouseScrollLayout.visibleRowsForHeight(194, 35));
        assertEquals(18, WarehouseScrollLayout.visibleRowsForHeight(374, 35));
    }

    @Test
    void visibleRowsNeverExceedWarehouseRows() {
        assertEquals(6, WarehouseScrollLayout.visibleRowsForHeight(374, 6));
    }

    @Test
    void scrollRangeUsesPixelsInsteadOfWholeRows() {
        assertEquals(486.0D,
            WarehouseScrollLayout.maximumScrollPixels(35, 8));
        assertEquals(0.0D,
            WarehouseScrollLayout.maximumScrollPixels(6, 8));
    }

    @Test
    void scrollbarMapsSmoothPixelsInBothDirections() {
        int track = 324;
        int thumb = WarehouseScrollLayout.thumbHeight(track, 630);
        int travel = track - thumb;
        double maximum = WarehouseScrollLayout.maximumScrollPixels(35, 18);
        int offset = WarehouseScrollLayout.thumbOffset(maximum / 2.0D, maximum, travel);

        assertEquals(maximum / 2.0D,
            WarehouseScrollLayout.scrollPixelsForThumbOffset(offset, travel, maximum),
            1.0D);
        assertEquals(0.0D,
            WarehouseScrollLayout.scrollPixelsForThumbOffset(-20, travel, maximum));
        assertEquals(maximum,
            WarehouseScrollLayout.scrollPixelsForThumbOffset(travel + 20, travel, maximum));
    }

    @Test
    void vanillaPlayerInventoryUsesTheLeftPanelGrid() {
        int mainTop = 90;

        assertEquals(10, WarehouseScrollLayout.playerSlotX(9));
        assertEquals(154, WarehouseScrollLayout.playerSlotX(17));
        assertEquals(mainTop, WarehouseScrollLayout.playerSlotY(9, mainTop));
        assertEquals(mainTop + 36,
            WarehouseScrollLayout.playerSlotY(35, mainTop));

        assertEquals(10, WarehouseScrollLayout.playerSlotX(0));
        assertEquals(154, WarehouseScrollLayout.playerSlotX(8));
        assertEquals(mainTop + 62,
            WarehouseScrollLayout.playerSlotY(0, mainTop));
        assertEquals(mainTop + 62,
            WarehouseScrollLayout.playerSlotY(8, mainTop));
    }
}
