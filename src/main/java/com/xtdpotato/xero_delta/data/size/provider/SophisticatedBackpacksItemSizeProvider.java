package com.xtdpotato.xero_delta.data.size.provider;

import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.ItemSizeRule;
import com.xtdpotato.xero_delta.data.size.ItemSizeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Size compatibility for Sophisticated Backpacks and Sophisticated Storage items. */
public final class SophisticatedBackpacksItemSizeProvider implements ItemSizeProvider {
    @Override
    public String id() {
        return "sophisticated_backpacks";
    }

    @Override
    public Tier tier() {
        return Tier.MOD_COMPATIBILITY;
    }

    @Override
    public ItemSizeRule resolve(ItemStack stack, ResourceLocation itemId, Integer recipeIngredientCount) {
        return classify(itemId.getNamespace(), itemId.getPath());
    }

    static ItemSizeRule classify(String namespace, String itemPath) {
        if (!"sophisticatedbackpacks".equals(namespace) && !"sophisticatedstorage".equals(namespace)) return null;
        String path = itemPath.toLowerCase(Locale.ROOT);
        // Upgrades are small modules, independent of their level or effect.
        if (path.contains("upgrade")) return rule(1, 1);
        if (!"sophisticatedbackpacks".equals(namespace) || !path.contains("backpack")) return null;
        if (path.contains("netherite") || path.contains("diamond")) return rule(3, 3);
        return rule(2, 2);
    }

    private static ItemSizeRule rule(int width, int height) {
        return new ItemSizeRule(new ItemSize(width, height), true, false);
    }
}
