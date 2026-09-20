package com.xtdpotato.xero_delta.trading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingForbiddenItemPathsTest {
    @Test
    void rejectsVanillaCreativeAndOperatorOnlyItems() {
        assertFalse(canList("command_block"));
        assertFalse(canList("command_block_minecart"));
        assertFalse(canList("barrier"));
        assertFalse(canList("bedrock"));
        assertFalse(canList("zombie_spawn_egg"));
    }

    @Test
    void keepsNormalAndModdedItemsEligible() {
        assertTrue(canList("dragon_egg"));
        assertTrue(canList("diamond"));
        assertTrue(TradingForbiddenItemPaths.canList("examplemod", "command_block"));
    }

    private static boolean canList(String path) {
        return TradingForbiddenItemPaths.canList("minecraft", path);
    }
}
