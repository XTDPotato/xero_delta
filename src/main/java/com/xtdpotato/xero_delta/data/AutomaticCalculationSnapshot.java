package com.xtdpotato.xero_delta.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Immutable registry and recipe input used by the dedicated automatic-rule worker. */
public record AutomaticCalculationSnapshot(List<Item> items, List<RecipeData> recipes) {
    public AutomaticCalculationSnapshot {
        items = List.copyOf(items);
        recipes = List.copyOf(recipes);
    }

    public static AutomaticCalculationSnapshot capture(MinecraftServer server) {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) items.add(item);

        List<RecipeData> recipes = new ArrayList<>();
        for (var holder : server.getRecipeManager().getRecipes()) {
            try {
                var recipe = holder.value();
                ItemStack output = recipe.getResultItem(server.registryAccess());
                if (output.isEmpty()) continue;
                List<List<Item>> ingredients = new ArrayList<>();
                int nonEmptyIngredients = 0;
                for (var ingredient : recipe.getIngredients()) {
                    if (!ingredient.isEmpty()) nonEmptyIngredients++;
                    LinkedHashSet<Item> options = new LinkedHashSet<>();
                    for (ItemStack option : ingredient.getItems()) {
                        if (!option.isEmpty()) options.add(option.getItem());
                    }
                    ingredients.add(List.copyOf(options));
                }
                ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
                recipes.add(new RecipeData(output.getItem(), Math.max(1, output.getCount()),
                    List.copyOf(ingredients), nonEmptyIngredients,
                    RecipeValueMultiplier.forTypeId(typeId == null ? null : typeId.toString())));
            } catch (RuntimeException | LinkageError ignored) {
                // A broken third-party recipe must not abort every automatic rule calculation.
            }
        }
        return new AutomaticCalculationSnapshot(items, recipes);
    }

    public record RecipeData(Item output, int outputCount, List<List<Item>> ingredients,
                             int nonEmptyIngredientCount, double multiplier) {
    }
}
