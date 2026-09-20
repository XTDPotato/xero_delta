package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.data.ItemSize;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridPackingPlanTest {
    @Test
    void packsLargeAndSmallItemsWithoutOverlap() {
        var plan = GridPackingPlan.pack(rectangle(3, 3), List.of(
            new GridPackingPlan.Entry(0, new ItemSize(2, 2), false),
            new GridPackingPlan.Entry(1, new ItemSize(1, 1), false),
            new GridPackingPlan.Entry(2, new ItemSize(1, 1), false),
            new GridPackingPlan.Entry(3, new ItemSize(1, 1), false)
        ));

        assertTrue(plan.isPresent());
        Set<GridPackingPlan.Cell> occupied = new HashSet<>();
        plan.orElseThrow().forEach(placement ->
            placement.cells().forEach(cell -> assertTrue(occupied.add(cell), "overlap at " + cell)));
        assertEquals(7, occupied.size());
    }

    @Test
    void rotatesNonSquareFootprintWhenRequired() {
        var plan = GridPackingPlan.pack(rectangle(2, 3), List.of(
            new GridPackingPlan.Entry(0, new ItemSize(3, 2), false)
        ));

        assertTrue(plan.isPresent());
        assertTrue(plan.orElseThrow().getFirst().rotated());
        assertEquals(6, plan.orElseThrow().getFirst().cells().size());
    }

    @Test
    void packsMultipleNonSquareItemsWithoutTreatingCoveredCellsAsEmpty() {
        var plan = GridPackingPlan.pack(rectangle(3, 3), List.of(
            new GridPackingPlan.Entry(0, new ItemSize(2, 3), false),
            new GridPackingPlan.Entry(1, new ItemSize(3, 1), false)
        ));

        assertTrue(plan.isPresent());
        assertTrue(plan.orElseThrow().get(1).rotated());
        Set<GridPackingPlan.Cell> occupied = new HashSet<>();
        plan.orElseThrow().forEach(placement ->
            placement.cells().forEach(cell -> assertTrue(occupied.add(cell), "overlap at " + cell)));
        assertEquals(9, occupied.size());
    }

    @Test
    void rejectsPlanInsteadOfOverlappingWhenGridIsFull() {
        var plan = GridPackingPlan.pack(rectangle(2, 2), List.of(
            new GridPackingPlan.Entry(0, new ItemSize(2, 2), false),
            new GridPackingPlan.Entry(1, new ItemSize(1, 1), false)
        ));

        assertTrue(plan.isEmpty());
    }

    @Test
    void assignsEveryDisplacedItemToADistinctOriginCell() {
        var plan = GridPackingPlan.pack(rectangle(2, 2), List.of(
            new GridPackingPlan.Entry(0, ItemSize.ONE, false),
            new GridPackingPlan.Entry(1, ItemSize.ONE, false),
            new GridPackingPlan.Entry(2, ItemSize.ONE, false),
            new GridPackingPlan.Entry(3, ItemSize.ONE, false)
        ));

        assertTrue(plan.isPresent());
        Set<GridPackingPlan.Cell> occupied = new HashSet<>();
        plan.orElseThrow().forEach(placement ->
            placement.cells().forEach(cell -> assertTrue(occupied.add(cell), "overlap at " + cell)));
        assertEquals(4, occupied.size());
    }

    private static Set<GridPackingPlan.Cell> rectangle(int width, int height) {
        Set<GridPackingPlan.Cell> cells = new HashSet<>();
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                cells.add(new GridPackingPlan.Cell(column, row));
            }
        }
        return cells;
    }
}
