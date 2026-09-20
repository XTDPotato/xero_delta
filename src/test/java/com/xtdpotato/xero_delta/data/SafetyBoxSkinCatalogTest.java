package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyBoxSkinCatalogTest {
    @Test
    void rejectsUnknownSkinIdsAndFallsBackToDefault() {
        assertFalse(SafetyBoxSkinCatalog.isKnown("missing"));
        assertEquals(SafetyBoxSkinCatalog.DEFAULT_SKIN,
            SafetyBoxSkinCatalog.normalize("missing"));
        assertEquals(SafetyBoxSkinCatalog.DEFAULT_SKIN,
            SafetyBoxSkinCatalog.normalize(null));
    }

    @Test
    void exposesKnownPreviewSkinsButOnlyAllowsDefaultUnlocks() {
        assertTrue(SafetyBoxSkinCatalog.isKnown("wheel_of_fate"));
        assertTrue(SafetyBoxSkinCatalog.isUnlockedByDefault("default"));
        assertTrue(SafetyBoxSkinCatalog.isUnlockedByDefault("ZERO_PLAYER"));
        assertFalse(SafetyBoxSkinCatalog.isUnlockedByDefault("gilded_glow"));
    }

    @Test
    void keepsTheUltimateBoxAsTheOnlyCustomizableBox() {
        assertEquals("xero_delta:safety_box_3x3", SafetyBoxSkinCatalog.TOP_BOX_ID);
        assertEquals(6, SafetyBoxSkinCatalog.skins().size());
    }
}
