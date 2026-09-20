package com.xtdpotato.xero_delta.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure trading constraints shared by the server service and unit tests. */
public final class TradingRules {
    public static final long MAX_CONFIG_CURRENCY = 2_000_000_000L;
    public static volatile long MAX_CURRENCY = MAX_CONFIG_CURRENCY;
    public static final long DEFAULT_STARTING_BALANCE = 1_000_000L;
    public static final int DEFAULT_LISTING_DAYS = 72;
    public static final int DEFAULT_MAX_MARKET_LISTINGS = 255;
    public static final int DEFAULT_PLAYER_LISTING_SLOTS = 6;
    public static final int DEFAULT_MAX_PLAYER_LISTING_SLOTS = 12;
    public static final int DEFAULT_LISTING_SLOT_LEVEL_COST = 5;
    public static final int MAX_CONFIG_LISTING_DAYS = 3_650;
    public static final int MAX_CONFIG_MARKET_LISTINGS = 4_096;
    public static final int MAX_CONFIG_PLAYER_LISTING_SLOTS = 256;
    public static final int MAX_CONFIG_LISTING_SLOT_LEVEL_COST = 10_000;
    public static final int MAX_STACKS_PER_LISTING = 3;
    public static final int MAX_CREATIVE_LISTING_AMOUNT = 4_096;
    public static final int MAX_LISTINGS_PER_PLAYER = DEFAULT_MAX_MARKET_LISTINGS;
    public static final int MAX_HISTORY = 200;
    public static final int MAX_SYNC_LISTINGS = MAX_CONFIG_MARKET_LISTINGS;
    public static final int PRICE_MULTIPLIER = 3;
    public static final int MIN_PRICE_PERCENT = 10;
    public static final int TAX_PERCENT = 10;
    public static final int GUARANTEE_PERCENT = 3;
    public static final long LISTING_LIFETIME_TICKS = listingLifetimeTicks(DEFAULT_LISTING_DAYS);
    public static final long MIN_ITEM_VALUE = 1L;

    private TradingRules() {
    }

    public static void setMaxCurrency(long value) {
        MAX_CURRENCY = Math.max(1L, Math.min(MAX_CONFIG_CURRENCY, value));
    }

    public static long maxListingPrice(long itemValue) {
        itemValue = normalizeItemValue(itemValue);
        if (itemValue > MAX_CURRENCY / PRICE_MULTIPLIER) return MAX_CURRENCY;
        return Math.min(MAX_CURRENCY, itemValue * PRICE_MULTIPLIER);
    }

    public static long minListingPrice(long itemValue) {
        itemValue = normalizeItemValue(itemValue);
        return Math.max(1L, (itemValue * MIN_PRICE_PERCENT + 99L) / 100L);
    }

    public static long normalizeItemValue(long itemValue) {
        return Math.max(MIN_ITEM_VALUE, Math.min(MAX_CURRENCY, itemValue));
    }

    /** Applies a decimal percentage such as 10 or 10.5 and rounds down to whole currency. */
    public static long adjustByPercent(long value, double percentage) {
        if (!Double.isFinite(percentage)) return normalizeItemValue(value);
        BigDecimal factor = BigDecimal.valueOf(100.0D).add(BigDecimal.valueOf(percentage));
        if (factor.signum() <= 0) return MIN_ITEM_VALUE;
        BigDecimal adjusted = BigDecimal.valueOf(normalizeItemValue(value))
            .multiply(factor)
            .divide(BigDecimal.valueOf(100L), 0, RoundingMode.DOWN);
        if (adjusted.compareTo(BigDecimal.valueOf(MAX_CURRENCY)) >= 0) return MAX_CURRENCY;
        return Math.max(MIN_ITEM_VALUE, adjusted.longValue());
    }

    public static boolean isListingPriceAllowed(long itemValue, long requestedPrice) {
        return requestedPrice >= minListingPrice(itemValue) && requestedPrice <= maxListingPrice(itemValue);
    }

    public static long tax(long grossPrice) {
        if (grossPrice <= 0) return 0L;
        return grossPrice * TAX_PERCENT / 100L;
    }

    public static long guarantee(long grossPrice) {
        if (grossPrice <= 0) return 0L;
        return grossPrice * GUARANTEE_PERCENT / 100L;
    }

    public static long marketDeductions(long grossPrice) {
        return Math.min(Math.max(0L, grossPrice), tax(grossPrice) + guarantee(grossPrice));
    }

    public static long sellerProceeds(long grossPrice) {
        return Math.max(0L, grossPrice - marketDeductions(grossPrice));
    }

    public static boolean isExpired(long createdAt, long currentGameTime) {
        return isExpired(createdAt, currentGameTime, LISTING_LIFETIME_TICKS);
    }

    public static boolean isExpired(long createdAt, long currentGameTime, long durationTicks) {
        return currentGameTime - createdAt >= Math.max(1L, durationTicks);
    }

    public static long expiresAt(long createdAt) {
        return expiresAt(createdAt, LISTING_LIFETIME_TICKS);
    }

    public static long expiresAt(long createdAt, long durationTicks) {
        durationTicks = Math.max(1L, durationTicks);
        if (createdAt > Long.MAX_VALUE - durationTicks) return Long.MAX_VALUE;
        return createdAt + durationTicks;
    }

    public static long listingLifetimeTicks(int days) {
        return Math.max(1L, Math.min(MAX_CONFIG_LISTING_DAYS, days)) * 24_000L;
    }

    public static int maxAmountPerListing(int maxStackSize) {
        return Math.max(1, maxStackSize) * MAX_STACKS_PER_LISTING;
    }

    public static long clampBalance(long value) {
        return Math.max(0L, Math.min(MAX_CURRENCY, value));
    }

    public static long addBalance(long current, long delta) {
        if (delta <= 0) return clampBalance(current);
        if (current > MAX_CURRENCY - delta) return MAX_CURRENCY;
        return current + delta;
    }
}
