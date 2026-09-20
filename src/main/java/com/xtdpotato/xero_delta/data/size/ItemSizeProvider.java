package com.xtdpotato.xero_delta.data.size;

import com.xtdpotato.xero_delta.data.ItemSizeRule;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public interface ItemSizeProvider {
    enum Tier {
        MOD_COMPATIBILITY,
        AUTOMATIC,
        DEFAULT
    }

    String id();

    Tier tier();

    ItemSizeRule resolve(ItemStack stack, ResourceLocation itemId, Integer recipeIngredientCount);
}
