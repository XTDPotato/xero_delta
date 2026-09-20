package com.xtdpotato.xero_delta.data.size.provider;

import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.ItemSizeRule;
import com.xtdpotato.xero_delta.data.RuleFormulaConfig;
import com.xtdpotato.xero_delta.data.size.ItemSizeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class DefaultItemSizeProvider implements ItemSizeProvider {
    @Override
    public String id() {
        return "default";
    }

    @Override
    public Tier tier() {
        return Tier.DEFAULT;
    }

    @Override
    public ItemSizeRule resolve(ItemStack stack, ResourceLocation itemId, Integer recipeIngredientCount) {
        RuleFormulaConfig.FormulaData formula = RuleFormulaConfig.get();
        if (formula.defaultWidth != 1 || formula.defaultHeight != 1) {
            return new ItemSizeRule(new ItemSize(formula.defaultWidth, formula.defaultHeight), true, false);
        }
        ItemSize size;
        if (stack.getMaxDamage() > 0 || stack.getMaxStackSize() == 1) size = new ItemSize(2, 2);
        else if (stack.getMaxStackSize() <= 16) size = new ItemSize(2, 1);
        else size = ItemSize.ONE;
        return new ItemSizeRule(size, true, false);
    }
}
