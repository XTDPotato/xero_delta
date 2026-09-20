package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.data.ItemSize;

import java.util.ArrayList;
import java.util.List;

/** Builds a deterministic row-major plan for splitting 1x1 units into a vacated footprint. */
public final class GridOriginFillPlan {
    public record Cell(int x, int y) {
    }

    private GridOriginFillPlan() {
    }

    public static List<Cell> unitCells(int originX, int originY, ItemSize footprint, int itemCount) {
        if (footprint.width() <= 0 || footprint.height() <= 0 || itemCount <= 0) return List.of();
        int limit = Math.min(itemCount, footprint.width() * footprint.height());
        List<Cell> cells = new ArrayList<>(limit);
        for (int y = 0; y < footprint.height() && cells.size() < limit; y++) {
            for (int x = 0; x < footprint.width() && cells.size() < limit; x++) {
                cells.add(new Cell(originX + x, originY + y));
            }
        }
        return List.copyOf(cells);
    }
}
