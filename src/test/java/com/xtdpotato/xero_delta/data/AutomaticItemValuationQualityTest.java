package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutomaticItemValuationQualityTest {
    @Test
    void unknownRecipeTypeUsesGenericMultiplier() {
        assertEquals(1.10, RecipeValueMultiplier.forTypeId(null), 0.0001);
    }

    @Test
    void appliesRequestedVanillaQualityOverrides() {
        assertQuality("blue", "slime_ball");
        assertQuality("red", "zombie_spawn_egg");
        assertQuality("purple", "wind_charge");
        assertQuality("gold", "spectral_arrow");
        assertQuality("blue", "tripwire_hook");
        assertQuality("purple", "redstone_lamp");
        assertQuality("gold", "anvil");
        assertQuality("purple", "chipped_anvil");
        assertQuality("blue", "damaged_anvil");
        assertQuality("gray", "small_amethyst_bud");
        assertQuality("green", "medium_amethyst_bud");
        assertQuality("blue", "large_amethyst_bud");
        assertQuality("purple", "amethyst_cluster");
    }

    @Test
    void keepsSafetyBoxQualitiesAtTheirDesignedTiers() {
        var qualities = AutomaticItemValuation.safetyBoxQualities();

        assertEquals("green", qualities.get("xero_delta:safety_box_2x1"), "基础安全箱");
        assertEquals("blue", qualities.get("xero_delta:safety_box_2x2"), "进阶安全箱");
        assertEquals("purple", qualities.get("xero_delta:safety_box_3x2"), "高级安全箱");
        assertEquals("purple", qualities.get("xero_delta:safety_box_4x2"), "扩展安全箱");
        assertEquals("gold", qualities.get("xero_delta:safety_box_3x3"), "顶级安全箱");
    }

    private static void assertQuality(String expected, String path) {
        assertEquals(expected, VanillaQualityOverrides.find(path), path);
    }
}
