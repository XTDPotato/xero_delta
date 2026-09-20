package com.xtdpotato.xero_delta.client;

/** Shared player-panel metrics, independent of the menu hosting the inventory. */
public final class DeltaInventoryLayout {
    public static final int WIDTH = 404;
    public static final int HEIGHT = 356;
    public static final int HEADER = 30;
    public static final int GAP = 24;
    /**
     * Allow the shared panel to grow on high-resolution displays.  The
     * viewport fit calculation still caps the result for small windows, so
     * this only removes the artificial 404px ceiling that made the layout
     * appear tiny at normal 1080p/4K GUI sizes.
     */
    public static final float MAX_SCALE = 2.0F;

    private DeltaInventoryLayout() {}

    public static float fitScale(int availableWidth, int availableHeight, double contentFactor) {
        double widthFit = Math.max(1, availableWidth) / (WIDTH * Math.max(1.0D, contentFactor));
        double heightFit = Math.max(1, availableHeight) / (double) HEIGHT;
        return (float) Math.max(0.01D, Math.min(MAX_SCALE, Math.min(widthFit, heightFit)));
    }
}
