package com.xtdpotato.xero_delta.screen;

public final class WarehouseScrollLayout {
    static final int GRID_TOP = 28;
    static final int FOOTER_HEIGHT = 22;
    static final int ROW_HEIGHT = 18;

    private WarehouseScrollLayout() {
    }

    public static int visibleRowsForHeight(int panelHeight, int totalRows) {
        int available = Math.max(ROW_HEIGHT,
            panelHeight - GRID_TOP - FOOTER_HEIGHT);
        return Math.max(1, Math.min(totalRows, available / ROW_HEIGHT));
    }

    public static double maximumScrollPixels(int totalRows, int visibleRows) {
        return Math.max(0, totalRows - visibleRows) * (double) ROW_HEIGHT;
    }

    public static int thumbHeight(int trackHeight, int contentHeight) {
        if (trackHeight <= 0) return 0;
        int proportional = trackHeight * trackHeight / Math.max(1, contentHeight);
        return Math.min(trackHeight, Math.max(18, proportional));
    }

    public static int thumbOffset(double scrollPixels, double maximumScrollPixels,
                                  int travelPixels) {
        if (maximumScrollPixels <= 0.0D || travelPixels <= 0) return 0;
        double ratio = Math.max(0.0D, Math.min(1.0D,
            scrollPixels / maximumScrollPixels));
        return (int) Math.round(ratio * travelPixels);
    }

    public static double scrollPixelsForThumbOffset(double offsetPixels,
                                                     int travelPixels,
                                                     double maximumScrollPixels) {
        if (travelPixels <= 0 || maximumScrollPixels <= 0.0D) return 0.0D;
        double ratio = Math.max(0.0D, Math.min(1.0D,
            offsetPixels / travelPixels));
        return ratio * maximumScrollPixels;
    }

    static int playerSlotX(int inventoryIndex) {
        return 10 + inventoryIndex % 9 * ROW_HEIGHT;
    }

    static int playerSlotY(int inventoryIndex, int mainTop) {
        return inventoryIndex < 9
            ? mainTop + ROW_HEIGHT * 3 + 8
            : mainTop + (inventoryIndex - 9) / 9 * ROW_HEIGHT;
    }
}
