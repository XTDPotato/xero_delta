package com.xtdpotato.xero_delta.trading;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.*;

/** Captured on the server thread from all registered recipe types, including modded ones. */
final class RecipeSupplyIndex {
    final RecipeSupplyGraph graph = new RecipeSupplyGraph();
    final Map<String, ItemStack> outputs = new LinkedHashMap<>();
    private final Map<Item, List<Binding>> uses = new HashMap<>();

    static String key(ItemStack stack) {
        ItemStack identity = stack.copyWithCount(1);
        if (identity.isDamageableItem()) identity.setDamageValue(0);
        identity.remove(ModDataComponents.GRID_CONTENTS.get());
        identity.remove(ModDataComponents.GRID_CONTAINERS.get());
        identity.remove(DataComponents.CONTAINER);
        identity.remove(DataComponents.BUNDLE_CONTENTS);
        return ModDataStorage.getKey(identity);
    }

    static RecipeSupplyIndex capture(MinecraftServer server) {
        return capture(server.getRecipeManager().getRecipes(), server.registryAccess());
    }

    static RecipeSupplyIndex capture(Collection<RecipeHolder<?>> registered, HolderLookup.Provider registries) {
        RecipeSupplyIndex index = new RecipeSupplyIndex();
        List<Recipe> recipes = new ArrayList<>();
        Map<Item, List<ItemStack>> outputVariants = new HashMap<>();
        int unavailable = 0;
        for (var holder : registered) {
            try {
                var recipe = holder.value();
                ItemStack result = recipe.getResultItem(registries);
                if (result.isEmpty()) { unavailable++; continue; }
                String output = key(result);
                if (!index.outputs.containsKey(output)) {
                    ItemStack sample = result.copyWithCount(1);
                    index.outputs.put(output, sample);
                    outputVariants.computeIfAbsent(sample.getItem(), ignored -> new ArrayList<>()).add(sample);
                }
                recipes.add(new Recipe(output, List.copyOf(recipe.getIngredients())));
            } catch (RuntimeException | LinkageError exception) {
                unavailable++;
            }
        }
        for (Recipe recipe : recipes) {
            Set<String> inputs = new LinkedHashSet<>();
            for (Ingredient ingredient : recipe.ingredients()) {
                try {
                    Set<Item> itemTypes = new HashSet<>();
                    for (ItemStack option : ingredient.getItems()) {
                        if (option.isEmpty()) continue;
                        inputs.add(key(option));
                        itemTypes.add(option.getItem());
                    }
                    for (Item item : itemTypes) {
                        index.uses.computeIfAbsent(item, ignored -> new ArrayList<>())
                            .add(new Binding(ingredient, recipe.output()));
                        // A tag or ordinary ingredient can accept component-bearing mod outputs too.
                        for (ItemStack variant : outputVariants.getOrDefault(item, List.of())) {
                            if (ingredient.test(variant)) inputs.add(key(variant));
                        }
                    }
                } catch (RuntimeException | LinkageError exception) {
                    unavailable++;
                }
            }
            index.graph.addRecipe(recipe.output(), inputs);
        }
        if (unavailable > 0) org.slf4j.LoggerFactory.getLogger(XeroDelta.MOD_ID).info(
            "Recipe market: {} dynamic/unreadable recipe entries had no usable static item result or ingredient", unavailable);
        return index;
    }

    Set<String> related(ItemStack obtained) {
        Set<String> exact = graph.outputsFor(key(obtained));
        if (!exact.isEmpty()) return exact;
        Set<String> related = new LinkedHashSet<>();
        for (Binding binding : uses.getOrDefault(obtained.getItem(), List.of())) {
            try {
                if (binding.ingredient().test(obtained)) related.addAll(graph.outputsFor(binding.output()));
            } catch (RuntimeException | LinkageError ignored) { }
        }
        return related;
    }

    private record Recipe(String output, List<Ingredient> ingredients) {}
    private record Binding(Ingredient ingredient, String output) {}
}
