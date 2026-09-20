package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.grid.GridGeometry;
import net.minecraft.client.gui.GuiGraphics;

/** Shared cell chrome for every Delta inventory and safety-box surface. */
public final class DeltaGridCellRenderer {
    private static final int FILL = DeltaInventoryTheme.CELL;
    private static final int BORDER_RGB = DeltaInventoryTheme.BORDER & 0x00FFFFFF;
    private static final int HOVER_FILL = 0x55FFFFFF;
    private static final int HOVER_BORDER = DeltaInventoryTheme.ACCENT;

    private DeltaGridCellRenderer() {
    }

    public static void render(GuiGraphics graphics, int x, int y, int size) {
        render(graphics, x, y, size, size);
    }

    public static void render(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, FILL);
        // Older configs could contain zero. Keep every visible grid cell
        // outlined even when that stale value is loaded after an upgrade.
        double configured = Math.max(0.1D, Config.INSTANCE.gridBorderThickness.get());
        int thickness = Math.max(1, (int) Math.ceil(configured));
        int alpha = Math.min(255, Math.max(0,
            (int) Math.round(Math.min(1.0D, configured) * 255.0D)));
        int border = (alpha << 24) | BORDER_RGB;
        thickness = Math.min(thickness, Math.max(1, Math.min(width, height) / 2));
        graphics.fill(x, y, x + width, y + thickness, border);
        graphics.fill(x, y + height - thickness, x + width, y + height, border);
        graphics.fill(x, y + thickness, x + thickness, y + height - thickness, border);
        graphics.fill(x + width - thickness, y + thickness,
            x + width, y + height - thickness, border);
    }

    public static void renderGrid(GuiGraphics graphics, GridGeometry geometry) {
        for (int row = 0; row < geometry.rows(); row++) {
            for (int column = 0; column < geometry.cols(); column++) {
                if (!geometry.isValidCell(column, row)) continue;
                render(graphics, geometry.cellX(column), geometry.cellY(row),
                    geometry.cellSize());
            }
        }
    }

    public static void renderHover(GuiGraphics graphics, int x, int y,
                                   int width, int height) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, HOVER_FILL);
        graphics.renderOutline(x, y, width, height, HOVER_BORDER);
    }
}
