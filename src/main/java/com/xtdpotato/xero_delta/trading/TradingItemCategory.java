package com.xtdpotato.xero_delta.trading;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Minecraft-aware category adapter kept separate from the pure category enum. */
public final class TradingItemCategory {
    private TradingItemCategory() {
    }

    public static TradingCategory classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return TradingCategory.COLLECTIBLES;
        String itemId = stack.getItemHolder().getKey().location().toString();
        return TradingCategory.classify(itemId, stack.getItem() instanceof BlockItem);
    }

    public static boolean matches(TradingCategory category, ItemStack stack, boolean favorite) {
        if (category == TradingCategory.ALL) return true;
        if (category == TradingCategory.FAVORITES) return favorite;
        return classify(stack) == category;
    }
}
