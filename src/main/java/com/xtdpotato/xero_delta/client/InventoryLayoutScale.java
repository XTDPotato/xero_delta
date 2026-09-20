package com.xtdpotato.xero_delta.client;

/** Pure scaling math for the shared Delta inventory layout. */
public final class InventoryLayoutScale {
    private InventoryLayoutScale() {
    }

    public static double combine(double screenScale, float globalScale) {
        double base = Double.isFinite(screenScale) && screenScale > 0.0D
            ? screenScale : 1.0D;
        double global = Float.isFinite(globalScale) ? Math.max(0.6D, Math.min(5.0D, globalScale)) : 1.0D;
        return base * global;
    }

    /** Converts the user percentage into a real Delta-grid cell edge. */
    public static int cellSize(int baseCell, float globalScale) {
        int base = Math.max(1, baseCell);
        float scale = Float.isFinite(globalScale) ? globalScale : 1.5F;
        return Math.max(8, Math.min(60, Math.round(base * scale / 1.5F)));
    }

    /** Scale used by physical foreground overlays embedded in a logical inventory viewport. */
    public static float embeddedGridScale(float viewportScale, int cellSize, int baseCell) {
        float viewport = Float.isFinite(viewportScale) && viewportScale > 0.0F
            ? viewportScale : 1.0F;
        return viewport * Math.max(1, cellSize) / Math.max(1.0F, baseCell);
    }

    /**
     * Returns the content factor represented by the inventory layout setting.
     * 150% is the reference layout size, so larger values consume more space.
     */
    public static float contentFactor(float globalScale) {
        float value = Float.isFinite(globalScale) ? globalScale : 1.5F;
        return Math.max(0.4F, Math.min(3.5F, value / 1.5F));
    }

    /** Fits a logical layout to the current Minecraft GUI viewport. */
    public static float fitViewportScale(int physicalWidth, int physicalHeight,
                                         int contentWidth, int contentHeight,
                                         float globalScale) {
        float requested = contentFactor(globalScale);
        float fit = Math.min(physicalWidth / (float) Math.max(1, contentWidth),
            physicalHeight / (float) Math.max(1, contentHeight));
        return Math.max(0.12F, Math.min(requested, fit));
    }
}
