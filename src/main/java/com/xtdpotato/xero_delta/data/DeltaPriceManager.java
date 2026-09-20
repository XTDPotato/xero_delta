package com.xtdpotato.xero_delta.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DeltaPriceManager {
    private static final Map<String, Integer> rarityMap = new HashMap<>();
    private static final Map<String, Long> computedCache = new HashMap<>();
    private static boolean rarityLoaded = false;

    private static final long[] RARITY_MULTIPLIER = {0, 1, 2, 4, 8, 16, 32};

    public static long getPrice(ItemStack stack, ModDataStorage data, ServerLevel level) {
        if (stack.isEmpty()) return 0;
        // Only return manually set prices; no auto-computation
        return data.getPriceFor(stack);
    }

    private static long computePrice(ItemStack stack, ModDataStorage data, ServerLevel level) {
        String itemId = ModDataStorage.getIdOnlyKey(stack);
        int rarity = getRarity(itemId);
        long basePrice = computeRecipePrice(itemId, data, level, new HashSet<>());
        if (basePrice == 0) basePrice = 100;
        long price = basePrice * RARITY_MULTIPLIER[Math.min(rarity, 6)];
        return Math.max(1, price);
    }

    private static long computeRecipePrice(String itemId, ModDataStorage data, ServerLevel level, Set<String> visited) {
        if (visited.contains(itemId)) return 0;
        visited.add(itemId);

        var server = level.getServer();
        var recipes = server.getRecipeManager();
        long best = 0;

        for (RecipeHolder<?> holder : recipes.getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            String resultId = ModDataStorage.getIdOnlyKey(result);
            if (!resultId.equals(itemId)) continue;

            long sum = 0;
            if (recipe instanceof ShapelessRecipe shapeless) {
                for (Ingredient ing : shapeless.getIngredients()) {
                    ItemStack[] items = ing.getItems();
                    if (items.length > 0) {
                        String ingId = ModDataStorage.getIdOnlyKey(items[0]);
                        long ingPrice = data.getPrice(ingId);
                        if (ingPrice == 0) ingPrice = computeRecipePrice(ingId, data, level, new HashSet<>(visited));
                        sum += ingPrice;
                    }
                }
            } else if (recipe instanceof ShapedRecipe shaped) {
                for (Ingredient ing : shaped.getIngredients()) {
                    ItemStack[] items = ing.getItems();
                    if (items.length > 0) {
                        String ingId = ModDataStorage.getIdOnlyKey(items[0]);
                        long ingPrice = data.getPrice(ingId);
                        if (ingPrice == 0) ingPrice = computeRecipePrice(ingId, data, level, new HashSet<>(visited));
                        sum += ingPrice;
                    }
                }
            }
            if (sum > best) best = sum;
        }
        return best;
    }

    private static int getRarity(String itemId) {
        ensureRarityLoaded();
        return rarityMap.getOrDefault(itemId, 1);
    }

    private static void ensureRarityLoaded() {
        if (rarityLoaded) return;
        rarityLoaded = true;
        try {
            Path path = Path.of("config", "raritycore", "auto", "auto_rarity.json");
            if (!Files.exists(path)) return;
            try (Reader r = Files.newBufferedReader(path)) {
                JsonElement root = JsonParser.parseReader(r);
                if (root.isJsonObject()) {
                    for (var entry : root.getAsJsonObject().entrySet()) {
                        int rv = entry.getValue().getAsInt();
                        if (rv >= 1 && rv <= 6) rarityMap.put(entry.getKey(), rv);
                    }
                }
            }
            XeroDelta.LOGGER.info("[Price] Loaded {} rarity entries", rarityMap.size());
        } catch (Exception e) {
            XeroDelta.LOGGER.warn("[Price] Rarity load failed: {}", e.getMessage());
        }
    }

    public static void clearCache() { computedCache.clear(); }
    public static void reloadRarity() { rarityLoaded = false; rarityMap.clear(); ensureRarityLoaded(); }
}