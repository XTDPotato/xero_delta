package com.xtdpotato.xero_delta.trading;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public record TradingListing(UUID id, UUID sellerId, String sellerName, ItemStack stack,
                             long price, long itemValue, long createdAt, String publicId,
                             boolean virtualSupply, long durationTicks) {
    public TradingListing(UUID id, UUID sellerId, String sellerName, ItemStack stack,
                          long price, long itemValue, long createdAt) {
        this(id, sellerId, sellerName, stack, price, itemValue, createdAt, "", false,
            TradingRules.LISTING_LIFETIME_TICKS);
    }

    public TradingListing(UUID id, UUID sellerId, String sellerName, ItemStack stack,
                          long price, long itemValue, long createdAt, String publicId,
                          boolean virtualSupply) {
        this(id, sellerId, sellerName, stack, price, itemValue, createdAt, publicId, virtualSupply,
            TradingRules.LISTING_LIFETIME_TICKS);
    }

    public TradingListing copy() {
        return new TradingListing(id, sellerId, sellerName, stack.copy(), price, itemValue, createdAt,
            publicId, virtualSupply, durationTicks);
    }
}
