package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.data.ItemSize;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GridOriginFillPlanTest {
    @Test
    void fillsFourUnitItemsAcrossVacatedTwoByTwoFootprint() {
        assertEquals(List.of(
                new GridOriginFillPlan.Cell(3, 4),
                new GridOriginFillPlan.Cell(4, 4),
                new GridOriginFillPlan.Cell(3, 5),
                new GridOriginFillPlan.Cell(4, 5)),
            GridOriginFillPlan.unitCells(3, 4, new ItemSize(2, 2), 4));
    }

    @Test
    void neverPlansMoreUnitsThanTheOriginalFootprintCanHold() {
        assertEquals(4, GridOriginFillPlan.unitCells(0, 0, new ItemSize(2, 2), 64).size());
    }
}
