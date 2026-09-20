package com.xtdpotato.xero_delta.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PackRegionLayoutTest {
    private static final List<PackRegionLayout.LogicalRegion> DAR_REGIONS = List.of(
        new PackRegionLayout.LogicalRegion(0, 0, 2, 1),
        new PackRegionLayout.LogicalRegion(2, 0, 2, 1),
        new PackRegionLayout.LogicalRegion(0, 1, 2, 2),
        new PackRegionLayout.LogicalRegion(2, 1, 2, 2),
        new PackRegionLayout.LogicalRegion(0, 3, 1, 3),
        new PackRegionLayout.LogicalRegion(1, 3, 1, 3),
        new PackRegionLayout.LogicalRegion(2, 3, 2, 3));

    @Test
    void twoPixelRegionGapKeepsPouchesVisuallySeparated() {
        PackRegionLayout layout = new PackRegionLayout(DAR_REGIONS, 18, 2);

        assertEquals(new PackRegionLayout.Rect(38, 0, 36, 18),
            layout.regionBounds().get(1));
        assertEquals(new PackRegionLayout.Rect(40, 58, 36, 54),
            layout.regionBounds().get(6));
    }

    @Test
    void darPouchesHaveRealVisualGapsWithoutChangingLogicalCells() {
        PackRegionLayout layout = new PackRegionLayout(DAR_REGIONS, 18, 4);

        assertEquals(80, layout.width());
        assertEquals(116, layout.height());
        assertEquals(new PackRegionLayout.Rect(40, 0, 18, 18),
            layout.cellBounds(2, 0));
        assertEquals(new PackRegionLayout.Rect(22, 62, 18, 18),
            layout.cellBounds(1, 3));
        assertEquals(new PackRegionLayout.Rect(44, 62, 36, 54),
            layout.footprintBounds(2, 3, 2, 3));
    }

    @Test
    void hitTestingRejectsTheSpaceBetweenPouches() {
        PackRegionLayout layout = new PackRegionLayout(DAR_REGIONS, 18, 4);

        assertNull(layout.cellAt(38, 8));
        assertNull(layout.cellAt(20, 70));
        assertEquals(new PackRegionLayout.Cell(2, 0), layout.cellAt(41, 8));
        assertEquals(new PackRegionLayout.Cell(1, 3), layout.cellAt(23, 63));
    }
}
