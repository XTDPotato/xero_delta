package com.xtdpotato.xero_delta.trading;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingFavoriteKeysTest {
    private static final String BASE = "lrtactical:melee";
    private static final String FIRST = BASE + "{v2:Zmlyc3Q}";
    private static final String SECOND = BASE + "{v2:c2Vjb25k}";

    @Test
    void migratesLegacyBaseWhenConcreteVariantExists() {
        Set<String> favorites = new HashSet<>(Set.of(BASE, FIRST));

        assertTrue(TradingFavoriteKeys.normalize(favorites));
        assertEquals(Set.of(FIRST), favorites);
    }

    @Test
    void keepsMultipleConcreteVariantsIndependent() {
        Set<String> favorites = new HashSet<>(Set.of(BASE, FIRST, SECOND));

        assertTrue(TradingFavoriteKeys.normalize(favorites));
        assertEquals(Set.of(FIRST, SECOND), favorites);
    }

    @Test
    void preservesOrdinaryComponentlessFavorite() {
        Set<String> favorites = new HashSet<>(Set.of("minecraft:iron_ingot"));

        assertFalse(TradingFavoriteKeys.normalize(favorites));
        assertEquals(Set.of("minecraft:iron_ingot"), favorites);
    }

    @Test
    void selectingVariantRemovesOnlyItsLegacyBase() {
        Set<String> favorites = new HashSet<>(Set.of(BASE, "minecraft:iron_ingot"));

        assertTrue(TradingFavoriteKeys.removeLegacyBaseForVariant(favorites, FIRST));
        assertEquals(Set.of("minecraft:iron_ingot"), favorites);
    }

    @Test
    void recognizesLegacyHashedVariantKeys() {
        String legacy = BASE + "{12ab34cd}";

        assertTrue(TradingFavoriteKeys.isVariantKey(legacy));
        assertEquals(BASE, TradingFavoriteKeys.baseItemId(legacy));
    }
}
