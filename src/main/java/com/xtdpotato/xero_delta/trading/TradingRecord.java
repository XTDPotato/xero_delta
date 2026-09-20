package com.xtdpotato.xero_delta.trading;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public record TradingRecord(UUID listingId, UUID sellerId, String sellerName, UUID buyerId,
                            String buyerName, ItemStack stack, long price, long completedAt,
                            String publicId, long completedEpochMillis) {
    public TradingRecord(UUID listingId, UUID sellerId, String sellerName, UUID buyerId,
                         String buyerName, ItemStack stack, long price, long completedAt) {
        this(listingId, sellerId, sellerName, buyerId, buyerName, stack, price, completedAt,
            "", System.currentTimeMillis());
    }

    public TradingRecord copy() {
        return new TradingRecord(listingId, sellerId, sellerName, buyerId, buyerName,
            stack.copy(), price, completedAt, publicId, completedEpochMillis);
    }
}
