package com.xtdpotato.xero_delta.client;

/** Relative layers used only by final-pass detail surfaces. */
public final class ScreenLayerResolver {
    private static final float DETAIL_STEP = 10.0F;
    private static final float FINAL_PASS = 6_000.0F;
    private static final float TOOLTIP_RENDER_OFFSET = 2_800.0F;

    private ScreenLayerResolver() {
    }

    public static float foreground() {
        return DETAIL_STEP;
    }

    public static float dialog() {
        return DETAIL_STEP * 2.0F;
    }

    public static float tooltip() {
        return DETAIL_STEP * 3.0F;
    }

    /** High enough to clear inventory overlays while remaining inside the GUI depth range. */
    public static float finalPass() {
        return FINAL_PASS;
    }

    /** Keeps vanilla tooltip backgrounds and text above overlays without reaching z=10,000. */
    public static float tooltipRenderOffset() {
        return TOOLTIP_RENDER_OFFSET;
    }
}
