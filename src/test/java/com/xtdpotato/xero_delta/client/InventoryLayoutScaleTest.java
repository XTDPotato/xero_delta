package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryLayoutScaleTest {
    @Test
    void combinesGlobalScaleWithPerScreenScale() {
        assertEquals(1.5D, InventoryLayoutScale.combine(1.0D, 1.5F), 0.0001D);
        assertEquals(2.25D, InventoryLayoutScale.combine(1.5D, 1.5F), 0.0001D);
    }

    @Test
    void clampsInvalidScaleInputs() {
        assertEquals(0.6D, InventoryLayoutScale.combine(0.0D, 0.0F), 0.0001D);
        assertEquals(5.0D, InventoryLayoutScale.combine(Double.NaN, 9.0F), 0.0001D);
    }

    @Test
    void cellScaleChangesTheActualGridEdgeAroundTheDefault() {
        assertEquals(18, InventoryLayoutScale.cellSize(18, 1.5F));
        assertEquals(12, InventoryLayoutScale.cellSize(18, 1.0F));
        assertEquals(24, InventoryLayoutScale.cellSize(18, 2.0F));
    }

    @Test
    void embeddedGridScaleIncludesViewportAndConfiguredCellSize() {
        assertEquals(0.5F,
            InventoryLayoutScale.embeddedGridScale(0.5F, 18, 18), 0.0001F);
        assertEquals(2.0F / 3.0F,
            InventoryLayoutScale.embeddedGridScale(0.5F, 24, 18), 0.0001F);
        assertEquals(1.0F,
            InventoryLayoutScale.embeddedGridScale(Float.NaN, 18, 18), 0.0001F);
    }

    @Test
    void viewportScaleHonorsTheConfiguredSizeWithoutCroppingSmallScreens() {
        assertEquals(1.3F / 1.5F,
            InventoryLayoutScale.fitViewportScale(640, 371, 720, 372, 1.3F), 0.0001F);
        assertEquals(640.0F / 720.0F,
            InventoryLayoutScale.fitViewportScale(640, 371, 720, 372, 3.0F), 0.0001F);
        assertEquals(1.0F,
            InventoryLayoutScale.fitViewportScale(1920, 1080, 720, 372, 1.5F), 0.0001F);
    }
}
