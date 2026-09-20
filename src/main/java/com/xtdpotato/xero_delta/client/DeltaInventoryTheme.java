package com.xtdpotato.xero_delta.client;

import net.minecraft.client.gui.GuiGraphics;

/** Opaque colors sampled from the supplied Delta inventory reference. */
public final class DeltaInventoryTheme {
    public static final int BACKGROUND = 0xFF0E1417;
    public static final int STORAGE = 0xFF070B0C;
    public static final int CELL = 0xFF15181A;
    public static final int BORDER = 0xFF3D4042;
    public static final int ACCENT = 0xFF5CC793;
    public static final int TEXT = 0xFFCED2D1;
    public static final int MUTED = 0xFF858B8B;
    public static final int SELECTED = 0xFF192C29;

    private DeltaInventoryTheme() {}

    public static void section(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, STORAGE);
        g.fill(x, y, x + width, y + Math.min(16, height), CELL);
        g.renderOutline(x, y, width, Math.min(16, height), BORDER);
    }
}
