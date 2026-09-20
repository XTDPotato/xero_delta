package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.screen.SafetyBoxLayoutPack;
import java.util.*;

/**
 * Pure layout calculator for safety box grid.
 * No rendering, no mouse state. Shared between overlay and preview.
 *
 * validCells uses bit-packed (row << 16 | col) for future non-rectangular layouts.
 */
public class GridGeometry {
    public static final int CELL_SIZE = 18;
    public static final int SPLIT_GAP = 12;

    private final int cols, rows;
    private final int gridX, gridY;
    private final float scale;
    private final float gridScale;
    private final Set<Long> validCells;
    private final int pixelW, pixelH;
    private final int cellSz, splitGap;

    /** Rectangular constructor (all cells valid) */
    public GridGeometry(int cols, int rows, int gridX, int gridY, float scale, float gridScale) {
        this.cols = cols;
        this.rows = rows;
        this.gridX = gridX;
        this.gridY = gridY;
        this.scale = scale;
        this.gridScale = gridScale;
        this.validCells = null; // null means all cells valid (rectangular)
        this.cellSz = Math.max(1, (int)(CELL_SIZE * scale * gridScale));
        this.splitGap = isSplit(cols, rows) ? Math.max(1, (int)(SPLIT_GAP * scale * gridScale)) : 0;
        this.pixelW = cols * cellSz + splitGap;
        this.pixelH = rows * cellSz;
    }

    /** Exact-cell constructor used by embedded inventory surfaces. */
    public static GridGeometry exactCellSize(int cols, int rows, int gridX, int gridY,
                                             int cellSize) {
        return new GridGeometry(cols, rows, gridX, gridY, Math.max(1, cellSize));
    }

    private GridGeometry(int cols, int rows, int gridX, int gridY, int exactCellSize) {
        this.cols = cols;
        this.rows = rows;
        this.gridX = gridX;
        this.gridY = gridY;
        this.scale = exactCellSize / (float) CELL_SIZE;
        this.gridScale = 1.0F;
        this.validCells = null;
        this.cellSz = Math.max(1, exactCellSize);
        this.splitGap = isSplit(cols, rows)
            ? Math.max(1, Math.round(SPLIT_GAP * this.scale)) : 0;
        this.pixelW = cols * cellSz + splitGap;
        this.pixelH = rows * cellSz;
    }
    /** Non-rectangular constructor (future: L-shape, custom layouts) */
    public GridGeometry(int cols, int rows, int gridX, int gridY, float scale, float gridScale, Set<Long> validCells) {
        this.cols = cols;
        this.rows = rows;
        this.gridX = gridX;
        this.gridY = gridY;
        this.scale = scale;
        this.gridScale = gridScale;
        this.validCells = validCells;
        this.cellSz = Math.max(1, (int)(CELL_SIZE * scale * gridScale));
        this.splitGap = isSplit(cols, rows) ? Math.max(1, (int)(SPLIT_GAP * scale * gridScale)) : 0;
        this.pixelW = cols * cellSz + splitGap;
        this.pixelH = rows * cellSz;
    }

    public int cols() { return cols; }
    public int rows() { return rows; }
    public int gridX() { return gridX; }
    public int gridY() { return gridY; }
    public float scale() { return scale; }
    public float gridScale() { return gridScale; }
    public int pixelWidth() { return pixelW; }
    public int pixelHeight() { return pixelH; }
    public int cellSize() { return cellSz; }
    public int splitGap() { return splitGap; }
    public boolean isSplit() { return splitGap > 0; }

    public int cellX(int col) { return gridX + col * cellSize() + (isSplit() && col >= 3 ? splitGap : 0); }
    public int cellY(int row) { return gridY + row * cellSize(); }
    public int cellCenterX(int col) { return cellX(col) + cellSize() / 2; }
    public int cellCenterY(int row) { return cellY(row) + cellSize() / 2; }
    public int separatorX() { return isSplit() ? gridX + 3 * cellSize() + splitGap / 2 : -1; }

    public boolean isValidCell(int col, int row) {
        if (col < 0 || col >= cols || row < 0 || row >= rows) return false;
        if (validCells == null) return true;
        return validCells.contains(packCell(col, row));
    }

    public int cellIndex(int col, int row) { return row * cols + col; }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= gridX && mouseX < gridX + pixelW
            && mouseY >= gridY && mouseY < gridY + pixelH;
    }

    public int[] getCellAt(double mouseX, double mouseY) {
        if (mouseY < gridY || mouseY >= gridY + pixelH) return null;
        for (int cy = 0; cy < rows; cy++) {
            for (int cx = 0; cx < cols; cx++) {
                int x = cellX(cx), y = cellY(cy);
                if (mouseX >= x && mouseX < x + cellSize()
                    && mouseY >= y && mouseY < y + cellSize()
                    && isValidCell(cx, cy)) {
                    return new int[]{cx, cy};
                }
            }
        }
        return null;
    }

    private static boolean isSplit(int cols, int rows) {
        return cols == 4 && rows == 2;
    }

    public static long packCell(int col, int row) { return ((long)row << 16) | (col & 0xFFFF); }

    /** Build from layout config 闁?used by both overlay and preview */
    public static GridGeometry fromLayout(SafetyBoxLayoutPack.LayoutData ld, int gw, int gh,
                                           float scale, int gridPixelX, int gridPixelY) {
        float gs = (float) ld.gridScale;
        int cs = (int)(CELL_SIZE * scale * gs);
        return new GridGeometry(gw, gh, gridPixelX, gridPixelY, scale, gs);
    }

    /** Build from layout config with anchor 闁?calculates pixel position from layout direction */

}
