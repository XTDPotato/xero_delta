package com.xtdpotato.xero_delta.trading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradingCategoryTest {
    @Test
    void classifiesOrdinaryBlockItemsAsBlocks() {
        assertEquals(TradingCategory.BLOCKS,
            TradingCategory.classify("minecraft:stone", true));
    }

    @Test
    void blockClassificationWinsOverMaterialKeywords() {
        assertEquals(TradingCategory.BLOCKS,
            TradingCategory.classify("minecraft:iron_ore", true));
        assertEquals(TradingCategory.MATERIALS,
            TradingCategory.classify("minecraft:iron_ingot", false));
    }

    @Test
    void keepsBannersInCollectibles() {
        assertEquals(TradingCategory.COLLECTIBLES,
            TradingCategory.classify("minecraft:white_banner", true));
    }

    @Test
    void keepsRedstoneBlocksInRedstoneCategory() {
        assertEquals(TradingCategory.REDSTONE,
            TradingCategory.classify("minecraft:repeater", true));
    }
}
