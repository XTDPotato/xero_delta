package com.xtdpotato.xero_delta.data.size;

import com.xtdpotato.xero_delta.data.ItemSizeRule;
import com.xtdpotato.xero_delta.data.size.provider.DefaultItemSizeProvider;
import com.xtdpotato.xero_delta.data.size.provider.RecipeItemSizeProvider;
import com.xtdpotato.xero_delta.data.size.provider.SophisticatedBackpacksItemSizeProvider;
import com.xtdpotato.xero_delta.data.size.provider.TaczItemSizeProvider;
import com.xtdpotato.xero_delta.data.size.provider.VanillaItemSizeProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ItemSizeProviders {
    private static final List<ItemSizeProvider> PROVIDERS = new ArrayList<>();

    static {
        register(new SophisticatedBackpacksItemSizeProvider());
        register(new TaczItemSizeProvider());
        register(new VanillaItemSizeProvider());
        register(new RecipeItemSizeProvider());
        register(new DefaultItemSizeProvider());
    }

    private ItemSizeProviders() {
    }

    public static synchronized void register(ItemSizeProvider provider) {
        PROVIDERS.removeIf(existing -> existing.id().equals(provider.id()));
        PROVIDERS.add(provider);
        PROVIDERS.sort(Comparator.comparing(ItemSizeProvider::tier));
    }

    public static ItemSizeRule resolveCompatibility(ItemStack stack) {
        return resolve(stack, null, ItemSizeProvider.Tier.MOD_COMPATIBILITY);
    }

    public static ItemSizeRule resolveAutomatic(ItemStack stack, Integer recipeIngredientCount) {
        ItemSizeRule automatic = resolve(stack, recipeIngredientCount, ItemSizeProvider.Tier.AUTOMATIC);
        return automatic != null ? automatic : resolve(stack, recipeIngredientCount, ItemSizeProvider.Tier.DEFAULT);
    }

    public static boolean hasCompatibilityProvider(ItemStack stack) {
        return resolveCompatibility(stack) != null;
    }

    private static ItemSizeRule resolve(ItemStack stack, Integer recipeIngredientCount, ItemSizeProvider.Tier tier) {
        if (stack.isEmpty()) return null;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (ItemSizeProvider provider : List.copyOf(PROVIDERS)) {
            if (provider.tier() != tier) continue;
            ItemSizeRule result = provider.resolve(stack, itemId, recipeIngredientCount);
            if (result != null) return result;
        }
        return null;
    }
}
