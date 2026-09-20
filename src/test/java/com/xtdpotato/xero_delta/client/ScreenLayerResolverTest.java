package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenLayerResolverTest {
    @Test
    void finalPassClearsInventoryOverlaysWithoutCrossingGuiNearPlane() {
        float finalPass = ScreenLayerResolver.finalPass();

        assertTrue(finalPass > 800.0F);
        assertTrue(finalPass + ScreenLayerResolver.tooltipRenderOffset()
            + ScreenLayerResolver.tooltip() < 10_000.0F);
    }

    @Test
    void detailLayersRemainOrderedWithinFinalPass() {
        assertTrue(ScreenLayerResolver.foreground()
            < ScreenLayerResolver.dialog());
        assertTrue(ScreenLayerResolver.dialog()
            < ScreenLayerResolver.tooltip());
    }
}
