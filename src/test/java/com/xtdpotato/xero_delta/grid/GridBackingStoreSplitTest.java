package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.data.ItemSize;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GridBackingStoreSplitTest {
    @Test
    void adjacentFootprintsHaveEqualDistanceRegardlessOfShape() {
        ItemSize wide = new ItemSize(2, 1);

        assertEquals(1, GridBackingStore.footprintDistance(0, 0, wide, 2, 0));
        assertEquals(1, GridBackingStore.footprintDistance(0, 0, wide, 0, 1));
    }

    @Test
    void fartherFootprintsHaveIncreasingDistance() {
        ItemSize square = new ItemSize(2, 2);

        assertEquals(1, GridBackingStore.footprintDistance(1, 1, square, 3, 1));
        assertEquals(2, GridBackingStore.footprintDistance(1, 1, square, 4, 1));
        assertEquals(2, GridBackingStore.footprintDistance(1, 1, square, 3, 3));
    }

    @Test
    void splitCountsAlwaysConserveTheOriginalStack() {
        GridBackingStore.SplitCounts counts = GridBackingStore.splitCounts(9, 4);

        assertEquals(5, counts.remainder());
        assertEquals(4, counts.split());
        assertEquals(9, counts.remainder() + counts.split());
    }

    @Test
    void oversizedSplitStillLeavesOneItemAtTheSource() {
        GridBackingStore.SplitCounts counts = GridBackingStore.splitCounts(9, 99);

        assertEquals(1, counts.remainder());
        assertEquals(8, counts.split());
    }

    @Test
    void singleItemsAndInvalidAmountsAreNoOps() {
        assertNull(GridBackingStore.splitCounts(1, 1));
        assertNull(GridBackingStore.splitCounts(9, 0));
    }
}
