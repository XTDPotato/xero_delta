package com.xtdpotato.xero_delta.data.size.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VanillaItemSizeProviderTest {
    @Test
    void appliesRequestedOneCellExceptionsBeforeGenericBlocks() {
        assertSize(1, 1, "ender_pearl", false);
        assertSize(1, 1, "redstone", false);
        assertSize(1, 1, "string", false);
        assertSize(1, 1, "egg", false);
        assertSize(1, 1, "fermented_spider_eye", false);
        assertSize(1, 1, "globe_banner_pattern", false);
        assertSize(1, 1, "mushroom_stew", false);
        assertSize(1, 1, "tipped_arrow", false);
        assertSize(1, 1, "oak_pressure_plate", true);
        assertSize(1, 1, "oak_trapdoor", true);
        assertSize(1, 1, "glow_lichen", true);
        assertSize(1, 1, "painting", false);
        assertSize(1, 1, "player_head", true);
        assertSize(1, 1, "snow", true);
        assertSize(1, 1, "pointed_dripstone", true);
        assertSize(1, 1, "large_amethyst_bud", true);
        assertSize(1, 1, "oak_sapling", true);
        assertSize(1, 1, "flowering_azalea", true);
        assertSize(1, 1, "red_mushroom", true);
        assertSize(1, 1, "warped_fungus", true);
        assertSize(1, 1, "spore_blossom", true);
        assertSize(1, 1, "bamboo", true);
        assertSize(1, 1, "wheat_seeds", false);
        assertSize(1, 1, "sculk_vein", true);
    }

    @Test
    void appliesRequestedMultiCellShapes() {
        assertSize(2, 2, "leather", false);
        assertSize(1, 2, "honey_bottle", false);
        assertSize(1, 2, "ominous_bottle", false);
        assertSize(2, 3, "diamond_sword", false);
        assertSize(2, 2, "diamond_horse_armor", false);
        assertSize(2, 2, "hopper_minecart", false);
        assertSize(2, 2, "oak_boat", false);
        assertSize(2, 2, "bamboo_chest_raft", false);
        assertSize(1, 2, "chain", true);
        assertSize(1, 2, "end_rod", true);
        assertSize(3, 2, "red_bed", true);
        assertSize(1, 2, "lilac", true);
        assertSize(2, 2, "amethyst_cluster", true);
    }

    private static void assertSize(int width, int height, String path, boolean blockItem) {
        var rule = VanillaItemSizeProvider.classifyPath(path, blockItem);
        assertEquals(width, rule.size().width(), path + " width");
        assertEquals(height, rule.size().height(), path + " height");
    }
}
