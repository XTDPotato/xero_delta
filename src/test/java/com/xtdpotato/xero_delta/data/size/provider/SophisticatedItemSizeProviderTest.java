package com.xtdpotato.xero_delta.data.size.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SophisticatedItemSizeProviderTest {
    @Test
    void fixesBackpackAndStorageUpgradesToOneCell() {
        assertSize(1, 1, "sophisticatedbackpacks", "stack_upgrade_tier_5");
        assertSize(1, 1, "sophisticatedbackpacks", "feeding_upgrade");
        assertSize(1, 1, "sophisticatedstorage", "smithing_upgrade");
        assertSize(3, 3, "sophisticatedbackpacks", "diamond_backpack");
        assertNull(SophisticatedBackpacksItemSizeProvider.classify("sophisticatedstorage", "chest"));
    }

    private static void assertSize(int width, int height, String namespace, String path) {
        var size = SophisticatedBackpacksItemSizeProvider.classify(namespace, path).size();
        assertEquals(width, size.width());
        assertEquals(height, size.height());
    }
}
