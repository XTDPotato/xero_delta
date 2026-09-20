package com.xtdpotato.xero_delta.grid;

/**
 * Manages floating panel position and layout for safety box slots.
 * Does NOT handle item rendering, hover, tooltips, or click logic.
 * Those are handled by real Slot objects via vanilla AbstractContainerScreen.
 */
public class FloatingSlotLayout {
    public static final int CELL_SIZE = 18;

    private int screenX, screenY;
    private final int gridW, gridH;

    public FloatingSlotLayout(int gridWidth, int gridHeight) {
        this.gridW = gridWidth;
        this.gridH = gridHeight;
    }

    public void setPosition(int x, int y) {
        this.screenX = x;
        this.screenY = y;
    }

    public int getX() { return screenX; }
    public int getY() { return screenY; }
    public int getWidth() { return gridW * CELL_SIZE; }
    public int getHeight() { return gridH * CELL_SIZE; }
    public int getGridWidth() { return gridW; }
    public int getGridHeight() { return gridH; }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= screenX && mouseX < screenX + getWidth()
            && mouseY >= screenY && mouseY < screenY + getHeight();
    }

    public int[] getCellAt(double mouseX, double mouseY) {
        int cx = (int)((mouseX - screenX) / CELL_SIZE);
        int cy = (int)((mouseY - screenY) / CELL_SIZE);
        if (cx >= 0 && cx < gridW && cy >= 0 && cy < gridH) {
            return new int[]{cx, cy};
        }
        return null;
    }
}
