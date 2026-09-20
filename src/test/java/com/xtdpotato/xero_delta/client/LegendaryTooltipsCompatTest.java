package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegendaryTooltipsCompatTest {
    @Test
    void keepsVanillaRareAndEpicDistinctInsideDefaultLevelZero() {
        assertEquals("blue", LegendaryTooltipQualityMap.resolve(0, "blue"));
        assertEquals("purple", LegendaryTooltipQualityMap.resolve(0, "purple"));
        assertEquals("red", LegendaryTooltipQualityMap.resolve(0, "gray"));
    }

    @Test
    void mapsConfiguredFrameLevelsToXeroQualities() {
        assertEquals("gold", LegendaryTooltipQualityMap.resolve(1, "gray"));
        assertEquals("purple", LegendaryTooltipQualityMap.resolve(2, "gray"));
        assertEquals("blue", LegendaryTooltipQualityMap.resolve(3, "gray"));
        assertEquals("green", LegendaryTooltipQualityMap.resolve(4, "gray"));
        assertEquals("gray", LegendaryTooltipQualityMap.resolve(5, "gray"));
    }

    @Test
    void standardCommonAndBlacklistedFramesFallBackToExistingFormula() {
        assertNull(LegendaryTooltipQualityMap.resolve(-1, "gray"));
        assertNull(LegendaryTooltipQualityMap.resolve(-2, "purple"));
        assertEquals("green", LegendaryTooltipQualityMap.resolve(-1, "green"));
    }
}
