package com.xtdpotato.xero_delta.trading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradingRulesTest {
    @Test
    void capsListingsAtThreeTimesServerValue() {
        assertEquals(300_000L, TradingRules.maxListingPrice(100_000L));
        assertTrue(TradingRules.isListingPriceAllowed(100_000L, 300_000L));
        assertFalse(TradingRules.isListingPriceAllowed(100_000L, 300_001L));
        assertEquals(10_000L, TradingRules.minListingPrice(100_000L));
        assertTrue(TradingRules.isListingPriceAllowed(100_000L, 10_000L));
        assertFalse(TradingRules.isListingPriceAllowed(100_000L, 9_999L));
        assertEquals(3L, TradingRules.maxListingPrice(0L));
        assertTrue(TradingRules.isListingPriceAllowed(0L, 1L));
        assertFalse(TradingRules.isListingPriceAllowed(0L, 4L));
    }

    @Test
    void appliesTaxAndTenDayExpiry() {
        assertEquals(10_000L, TradingRules.tax(100_000L));
        assertEquals(3_000L, TradingRules.guarantee(100_000L));
        assertEquals(13_000L, TradingRules.marketDeductions(100_000L));
        assertEquals(87_000L, TradingRules.sellerProceeds(100_000L));
        assertFalse(TradingRules.isExpired(1_000L,
            1_000L + TradingRules.LISTING_LIFETIME_TICKS - 1L));
        assertTrue(TradingRules.isExpired(1_000L,
            1_000L + TradingRules.LISTING_LIFETIME_TICKS));
    }

    @Test
    void preventsCurrencyOverflow() {
        assertEquals(TradingRules.MAX_CURRENCY,
            TradingRules.maxListingPrice(TradingRules.MAX_CURRENCY));
        assertEquals(TradingRules.MAX_CURRENCY,
            TradingRules.addBalance(TradingRules.MAX_CURRENCY - 2L, 10L));
    }

    @Test
    void appliesDecimalPriceAdjustments() {
        assertEquals(110L, TradingRules.adjustByPercent(100L, 10.0D));
        assertEquals(221L, TradingRules.adjustByPercent(200L, 10.5D));
        assertEquals(89L, TradingRules.adjustByPercent(100L, -10.5D));
        assertEquals(1L, TradingRules.adjustByPercent(100L, -100.0D));
        assertEquals(100L, TradingRules.adjustByPercent(100L, Double.NaN));
        assertEquals(TradingRules.MAX_CURRENCY,
            TradingRules.adjustByPercent(TradingRules.MAX_CURRENCY, 10_000.0D));
    }

    @Test
    void safelyCalculatesWholeStackRecycleValue() {
        assertEquals(320_000L, TradingMarketService.safeMultiply(5_000L, 64));
        assertEquals(TradingRules.MAX_CURRENCY,
            TradingMarketService.safeMultiply(TradingRules.MAX_CURRENCY, 64));
        assertEquals(0L, TradingMarketService.safeMultiply(5_000L, 0));
        assertEquals(34L, TradingMarketService.proportionalCeil(100L, 1, 3));
        assertEquals(67L, TradingMarketService.proportionalCeil(100L, 2, 3));
        assertEquals(100L, TradingMarketService.proportionalCeil(100L, 3, 3));
    }

    @Test
    void publicListingIdsAreUniqueAndCarryMinuteTimestamp() {
        long now = System.currentTimeMillis();
        String first = TradingListingId.format(now, 1L);
        String second = TradingListingId.format(now, 2L);
        assertTrue(first.matches("\\d{12}_000001"));
        assertTrue(second.matches("\\d{12}_000002"));
        assertNotEquals(first, second);
        long parsed = TradingListingId.createdAtMillis(first);
        assertTrue(parsed >= 0L);
        assertTrue(Math.abs(now - parsed) < 60_000L);
    }

    @Test
    void classifiesRepresentativeItems() {
        assertEquals(TradingCategory.GUNS, TradingCategory.classify("tacz:modern_kinetic_gun"));
        assertEquals(TradingCategory.AMMO, TradingCategory.classify("tacz:ammo"));
        assertEquals(TradingCategory.ATTACHMENTS, TradingCategory.classify("tacz:scope"));
        assertEquals(TradingCategory.KEYS, TradingCategory.classify("minecraft:filled_map"));
        assertEquals(TradingCategory.CONSUMABLES, TradingCategory.classify("minecraft:healing_potion"));
        assertEquals(TradingCategory.EQUIPMENT, TradingCategory.classify("minecraft:diamond_chestplate"));
        assertEquals(TradingCategory.BLOCKS, TradingCategory.classify("minecraft:oak_planks", true));
        assertEquals(TradingCategory.REDSTONE, TradingCategory.classify("minecraft:comparator"));
        assertEquals(TradingCategory.MATERIALS, TradingCategory.classify("minecraft:iron_ingot"));
        assertEquals(TradingCategory.TOOLS, TradingCategory.classify("minecraft:diamond_pickaxe"));
    }

    @Test
    void definesPlayerListingSlotDefaults() {
        assertEquals(6, TradingRules.DEFAULT_PLAYER_LISTING_SLOTS);
        assertEquals(12, TradingRules.DEFAULT_MAX_PLAYER_LISTING_SLOTS);
        assertEquals(5, TradingRules.DEFAULT_LISTING_SLOT_LEVEL_COST);
        assertTrue(TradingRules.MAX_CONFIG_PLAYER_LISTING_SLOTS
            >= TradingRules.DEFAULT_MAX_PLAYER_LISTING_SLOTS);
    }
}
