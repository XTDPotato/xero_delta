package com.xtdpotato.xero_delta.data.size.provider;

import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.ItemSizeRule;
import com.xtdpotato.xero_delta.data.RuleFormulaConfig;
import com.xtdpotato.xero_delta.data.size.ItemSizeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class RecipeItemSizeProvider implements ItemSizeProvider {
    @Override
    public String id() {
        return "recipe";
    }

    @Override
    public Tier tier() {
        return Tier.AUTOMATIC;
    }

    @Override
    public ItemSizeRule resolve(ItemStack stack, ResourceLocation itemId, Integer ingredientCount) {
        if (!RuleFormulaConfig.get().recipeSizeEnabled) return null;
        if (ingredientCount == null || ingredientCount <= 0) return null;
        ItemSize size;
        if (ingredientCount <= 2) size = ItemSize.ONE;
        else if (ingredientCount <= 4) size = new ItemSize(2, 1);
        else if (ingredientCount <= 6) size = new ItemSize(2, 2);
        else if (ingredientCount <= 8) size = new ItemSize(2, 3);
        else size = new ItemSize(3, 3);
        return new ItemSizeRule(size, true, false);
    }
}
