package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.DeltaGridCellRenderer;
import com.xtdpotato.xero_delta.grid.SafetyBoxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SafetyBoxScreen extends AbstractContainerScreen<SafetyBoxMenu> {
    private final int gridW, gridH;

    public SafetyBoxScreen(SafetyBoxMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.gridW = menu.gridW;
        this.gridH = menu.gridH;
        this.imageWidth = Math.max(176, gridW * 18 + 16);
        this.imageHeight = gridH * 18 + 16 + 14 + 3 * 18 + 4 + 18 + 7;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = this.leftPos, y = this.topPos;
        int totalW = this.imageWidth, totalH = this.imageHeight;

        // Main background panel (dark, vanilla-like)
        g.fill(x, y, x + totalW, y + totalH, Material3Theme.OUTLINE_VARIANT);
        g.fill(x + 4, y + 4, x + totalW - 4, y + totalH - 4,
            Material3Theme.SURFACE_CONTAINER);

        // Grid area background
        int gx = x + 8, gy = y + 18;
        int gridPixelW = gridW * 18;
        int gridPixelH = gridH * 18;
        g.fill(gx - 1, gy - 1, gx + gridPixelW + 1, gy + gridPixelH + 1,
            Material3Theme.OUTLINE_VARIANT);
        g.fill(gx, gy, gx + gridPixelW, gy + gridPixelH, Material3Theme.SURFACE_CONTAINER_HIGH);

        // Grid slot sprites (vanilla style)
        for (int cy = 0; cy < gridH; cy++)
            for (int cx = 0; cx < gridW; cx++) {
                int sx = gx + cx * 18;
                int sy = gy + cy * 18;
                drawCell(g, sx, sy, 18);
            }

        // Player inventory slot sprites
        int ix = x + 8, iy = gy + gridPixelH + 14;
        int hotY = iy + 3 * 18 + 4;
        if (com.xtdpotato.xero_delta.client.DeltaContainerLayoutController.isEmbedded(this)) return;
        g.fill(ix - 1, iy - 1, ix + 9 * 18 + 1, hotY + 18 + 1, Material3Theme.OUTLINE_VARIANT);
        g.fill(ix, iy, ix + 9 * 18, hotY + 18, Material3Theme.SURFACE_CONTAINER_HIGH);

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawCell(g, ix + col * 18, iy + row * 18, 18);

        for (int col = 0; col < 9; col++)
            drawCell(g, ix + col * 18, hotY, 18);
    }

    private static void drawCell(GuiGraphics graphics, int x, int y, int size) {
        DeltaGridCellRenderer.render(graphics, x, y, size);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, this.title, 8, 6, Material3Theme.TEXT);
        if (!com.xtdpotato.xero_delta.client.DeltaContainerLayoutController.isEmbedded(this)) {
            g.drawString(this.font, this.playerInventoryTitle, 8, this.imageHeight - 94, Material3Theme.TEXT_MUTED);
        }
    }
}

