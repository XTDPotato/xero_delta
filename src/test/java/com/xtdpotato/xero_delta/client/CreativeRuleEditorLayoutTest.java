package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreativeRuleEditorLayoutTest {
    @Test
    void visibilityUsesCurrentCreativePermissionsRatherThanWeightOrCachedLayout() {
        assertTrue(CreativeRuleEditorLayout.eligible(true, true, true));
        assertFalse(CreativeRuleEditorLayout.eligible(false, true, true));
        assertFalse(CreativeRuleEditorLayout.eligible(true, false, true));
        assertFalse(CreativeRuleEditorLayout.eligible(true, true, false));
    }

    @Test
    void allTabsFitTheNormalPanelWithoutScrolling() {
        for (var tab : CreativeRuleEditorLayout.Tab.values()) {
            assertEquals(0, CreativeRuleEditorLayout.maxScroll(tab, CreativeRuleEditorLayout.HEIGHT));
            assertEquals(0, CreativeRuleEditorLayout.clampScroll(tab, CreativeRuleEditorLayout.HEIGHT, 1000));
        }
    }

    @Test
    void shortWindowsKeepNavigationAndSaveVisibleWhileOnlyTheBodyScrolls() {
        for (int height : new int[] {116, 130, 150, 180, 220, 240}) {
            int footer = CreativeRuleEditorLayout.footerTop(height);
            assertTrue(footer > CreativeRuleEditorLayout.BODY_TOP);
            assertEquals(height, footer + CreativeRuleEditorLayout.FOOTER_HEIGHT);
            for (var tab : CreativeRuleEditorLayout.Tab.values()) {
                int maximum = CreativeRuleEditorLayout.maxScroll(tab, height);
                assertEquals(maximum, CreativeRuleEditorLayout.clampScroll(tab, height, Integer.MAX_VALUE));
                assertEquals(0, CreativeRuleEditorLayout.clampScroll(tab, height, -100));
                assertTrue(tab.contentHeight - maximum <= footer - CreativeRuleEditorLayout.BODY_TOP);
            }
        }
    }

    @Test
    void slidersRemainSeparateAndInsideTheSizePage() {
        assertTrue(CreativeRuleEditorLayout.sliderTop(0) + 20 < CreativeRuleEditorLayout.sliderTop(1));
        assertTrue(CreativeRuleEditorLayout.sliderTop(1) + 20 < 54);
        assertTrue(CreativeRuleEditorLayout.sliderTop(2) > 100);
        assertTrue(CreativeRuleEditorLayout.sliderTop(2) + 20
            <= CreativeRuleEditorLayout.Tab.SIZE.contentHeight);
    }
}
