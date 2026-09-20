package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradingUiScaleTest {
    @Test
    void guiScaleLevelsAdjustWithinBounds() {
        assertEquals(1, TradingUiScale.adjustLevel(0, 1));
        assertEquals(-1, TradingUiScale.adjustLevel(0, -1));
        assertEquals(TradingUiScale.MAX_LEVEL,
            TradingUiScale.adjustLevel(TradingUiScale.MAX_LEVEL, 1));
        assertEquals(TradingUiScale.MIN_LEVEL,
            TradingUiScale.adjustLevel(TradingUiScale.MIN_LEVEL, -1));
    }

    @Test
    void viewportScalesLogicalCanvasAndMouseCoordinates() {
        TradingUiScale.Viewport viewport = TradingUiScale.viewport(1920, 1080, 2);
        assertEquals(1.25F, viewport.scale());
        assertEquals(1536, viewport.logicalWidth());
        assertEquals(864, viewport.logicalHeight());
        assertEquals(768, viewport.mouseXDouble(960.0D), 0.01D);
        assertEquals(432, viewport.mouseYDouble(540.0D), 0.01D);
    }

    @Test
    void zoomInReducesColumnsAndZoomOutAddsColumns() {
        assertEquals(2, TradingUiScale.adjustColumns(3, 1, 1, 10));
        assertEquals(4, TradingUiScale.adjustColumns(3, -1, 1, 10));
    }

    @Test
    void clampsColumnZoomAtSupportedBounds() {
        assertEquals(1, TradingUiScale.adjustColumns(1, 1, 1, 10));
        assertEquals(10, TradingUiScale.adjustColumns(10, -1, 1, 10));
    }
}
