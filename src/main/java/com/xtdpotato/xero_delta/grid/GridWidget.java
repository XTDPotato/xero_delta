package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.client.DeltaGridCellRenderer;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class GridWidget {
    public static final int CELL_SIZE = 18;

    private GridBackingStore store;
    private GridGeometry geometry;
    private int screenX, screenY;
    private int gridW, gridH;
    private int hoverCellX = -1, hoverCellY = -1;

    public GridWidget(int w, int h) {
        this.store = null;
        this.gridW = w; this.gridH = h;
    }

    public GridWidget(GridGeometry geom) {
        this.store = null;
        this.geometry = geom;
        this.gridW = geom.cols();
        this.gridH = geom.rows();
    }

    public GridWidget(GridBackingStore store) {
        this.store = store;
        this.gridW = store.getWidth();
        this.gridH = store.getHeight();
    }

    public void setGeometry(GridGeometry geom) {
        this.geometry = geom;
        if (geom != null) { this.gridW = geom.cols(); this.gridH = geom.rows(); }
    }

    public GridGeometry getGeometry() { return geometry; }

    public void setPosition(int x, int y) {
        this.screenX = x;
        this.screenY = y;
    }

    public int getX() { return geometry != null ? geometry.gridX() : screenX; }
    public int getY() { return geometry != null ? geometry.gridY() : screenY; }
    public int getWidth() { return geometry != null ? geometry.pixelWidth() : gridW * CELL_SIZE; }
    public int getHeight() { return geometry != null ? geometry.pixelHeight() : gridH * CELL_SIZE; }
    public int getGridWidth() { return gridW; }
    public int getGridHeight() { return gridH; }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (geometry != null) return geometry.isMouseOver(mouseX, mouseY);
        return mouseX >= screenX && mouseX < screenX + gridW * CELL_SIZE
            && mouseY >= screenY && mouseY < screenY + gridH * CELL_SIZE;
    }

    public int[] getCellAt(double mouseX, double mouseY) {
        if (geometry != null) return geometry.getCellAt(mouseX, mouseY);
        int cx = (int)((mouseX - screenX) / CELL_SIZE);
        int cy = (int)((mouseY - screenY) / CELL_SIZE);
        if (cx >= 0 && cx < gridW && cy >= 0 && cy < gridH) {
            return new int[]{cx, cy};
        }
        return null;
    }

    public void updateHover(double mouseX, double mouseY) {
        int[] cell = getCellAt(mouseX, mouseY);
        if (cell != null) {
            hoverCellX = cell[0];
            hoverCellY = cell[1];
        } else {
            hoverCellX = -1;
            hoverCellY = -1;
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        updateHover(mouseX, mouseY);
        int x = screenX;
        int y = screenY;
        int totalW = gridW * CELL_SIZE;
        int totalH = gridH * CELL_SIZE;

        // Dark background panel
        graphics.fill(x - 4, y - 4, x + totalW + 4, y + totalH + 4, 0xC0101010);
        // Border
        graphics.fill(x - 4, y - 4, x + totalW + 4, y - 3, 0xFF373737);
        graphics.fill(x - 4, y + totalH + 3, x + totalW + 4, y + totalH + 4, 0xFF373737);
        graphics.fill(x - 4, y - 4, x - 3, y + totalH + 4, 0xFF373737);
        graphics.fill(x + totalW + 3, y - 4, x + totalW + 4, y + totalH + 4, 0xFF373737);

        // Draw cell backgrounds
        for (int cx = 0; cx < gridW; cx++) {
            for (int cy = 0; cy < gridH; cy++) {
                int cellX = x + cx * CELL_SIZE;
                int cellY = y + cy * CELL_SIZE;
                DeltaGridCellRenderer.render(graphics, cellX, cellY, CELL_SIZE);
                
            }
        }

        // Hover highlight for empty cells
        if (hoverCellX >= 0 && hoverCellY >= 0 && !store.isCellOccupied(hoverCellX, hoverCellY)) {
            int hx = x + hoverCellX * CELL_SIZE;
            int hy = y + hoverCellY * CELL_SIZE;
            graphics.fill(hx + 1, hy + 1, hx + CELL_SIZE - 1, hy + CELL_SIZE - 1, 0x30FFFFFF);
        }

        // Draw placed items
        var mc = Minecraft.getInstance();
        for (int cy = 0; cy < gridH; cy++) {
            for (int cx = 0; cx < gridW; cx++) {
                ItemStack stack = store.getItemRaw(cx, cy);
                if (stack.isEmpty()) continue;
                // Cell background for occupied cells
                int hx = x + cx * CELL_SIZE + 1;
                int hy = y + cy * CELL_SIZE + 1;
                graphics.fill(hx, hy, hx + CELL_SIZE - 2, hy + CELL_SIZE - 2, 0x40000000);

                // Render item centered
                var pose = graphics.pose();
                pose.pushPose();
                float centerX = x + cx * CELL_SIZE + CELL_SIZE / 2f;
                float centerY = y + cy * CELL_SIZE + CELL_SIZE / 2f;
                pose.translate(centerX, centerY, 0);
                graphics.renderItem(stack, -8, -8);
                graphics.renderItemDecorations(mc.font, stack, -8, -8);
                pose.popPose();
            }
        }

        // Highlight hovered occupied cell
        if (hoverCellX >= 0 && hoverCellY >= 0 && store.isCellOccupied(hoverCellX, hoverCellY)) {
            int hx = x + hoverCellX * CELL_SIZE;
            int hy = y + hoverCellY * CELL_SIZE;
            graphics.fill(hx, hy, hx + CELL_SIZE, hy + CELL_SIZE, 0x80FFFFFF);
        }

        // Placement preview based on carried item
        ItemStack carried = mc.player.containerMenu.getCarried();
        if (!carried.isEmpty() && hoverCellX >= 0 && hoverCellY >= 0) {
            boolean canPlace = store.canPlace(hoverCellX, hoverCellY, carried);
            boolean cursorBlocked = carried.is(ModTags.SAFETY_BOX)
                || com.xtdpotato.xero_delta.Config.INSTANCE.isBlacklisted(carried);
            boolean isOccupied = store.isCellOccupied(hoverCellX, hoverCellY);
            boolean isSwap = isOccupied && !cursorBlocked;
            int color;
            if (isSwap) {
                color = 0x80FFAA00;
            } else if (canPlace && !cursorBlocked) {
                color = 0x8000CC00;
            } else {
                color = 0x80FF2222;
            }

            int px = x + hoverCellX * CELL_SIZE;
            int py = y + hoverCellY * CELL_SIZE;
            graphics.fill(px, py, px + CELL_SIZE, py + CELL_SIZE, color);
        }
    }

    public GridBackingStore getStore() { return store; }
    public boolean hasStore() { return store != null; }
}
