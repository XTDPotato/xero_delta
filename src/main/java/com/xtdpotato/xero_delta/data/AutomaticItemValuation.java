package com.xtdpotato.xero_delta.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class AutomaticItemValuation {
    private static final long MAX_VALUE = 999_999_999L;

    public record Result(Map<String, Long> prices, Map<String, String> qualities,
                         int recipeDerived, int fallbackDerived) {
    }

    private AutomaticItemValuation() {
    }

    public static Result calculate(MinecraftServer server, Map<String, Long> existingPrices) {
        return calculate(AutomaticCalculationSnapshot.capture(server), existingPrices, true, null);
    }

    public static Result calculate(MinecraftServer server, Map<String, Long> existingPrices,
                                   boolean useConfiguredQualities) {
        return calculate(AutomaticCalculationSnapshot.capture(server), existingPrices,
            useConfiguredQualities, null);
    }

    public static Result calculate(AutomaticCalculationSnapshot snapshot, Map<String, Long> existingPrices,
                                   boolean useConfiguredQualities) {
        return calculate(snapshot, existingPrices, useConfiguredQualities, null);
    }

    public static Result calculate(AutomaticCalculationSnapshot snapshot, Map<String, Long> existingPrices,
                                   boolean useConfiguredQualities,
                                   Map<String, ItemSizeRule> automaticSizesOverride) {
        Map<Item, Long> values = new HashMap<>();
        Set<Item> manualItems = new HashSet<>();
        Set<Item> fixedItems = new HashSet<>();
        Set<Item> catalogueItems = new HashSet<>();
        for (var entry : existingPrices.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null || entry.getValue() <= 0 || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            values.put(item, clamp(entry.getValue()));
            manualItems.add(item);
            fixedItems.add(item);
        }

        // Explicit catalogue values are exceptions. Everything else remains recipe-derived.
        for (Item item : snapshot.items()) {
            if (item == Items.AIR || fixedItems.contains(item)) continue;
            BuiltinItemRuleCatalog.Rule rule = BuiltinItemRuleCatalog.explicit(item.getDefaultInstance());
            if (rule == null) continue;
            values.put(item, rule.price());
            fixedItems.add(item);
            catalogueItems.add(item);
        }

        var recipes = snapshot.recipes();
        Set<Item> recipeOutputs = new HashSet<>();
        for (AutomaticCalculationSnapshot.RecipeData recipe : recipes) recipeOutputs.add(recipe.output());

        int fallbackDerived = 0;
        for (Item item : snapshot.items()) {
            if (item == Items.AIR || fixedItems.contains(item) || recipeOutputs.contains(item)) continue;
            values.put(item, fallbackValue(item));
            fallbackDerived++;
        }
        int recipeDerived = resolveRecipes(recipes, values, fixedItems);
        for (Item item : snapshot.items()) {
            if (item == Items.AIR || values.containsKey(item)) continue;
            values.put(item, fallbackValue(item));
            fallbackDerived++;
        }
        recipeDerived += resolveRecipes(recipes, values, fixedItems);

        Map<String, Long> rawPrices = new HashMap<>();
        for (var entry : values.entrySet()) rawPrices.put(itemId(entry.getKey()), clamp(entry.getValue()));
        Map<String, String> qualities = new HashMap<>(calculateQualities(rawPrices));
        Map<String, ItemSizeRule> automaticSizes = automaticSizesOverride == null
            ? AutomaticItemSizing.calculate(snapshot).sizes()
            : automaticSizesOverride;
        Map<String, Long> result = new HashMap<>();
        for (var entry : values.entrySet()) {
            Item item = entry.getKey();
            String itemId = itemId(item);
            ItemStack stack = item.getDefaultInstance();
            String quality = qualities.getOrDefault(itemId, "gray");
            if (useConfiguredQualities) {
                String configuredQuality = ServerItemRules.getConfiguredManualQualityFor(stack);
                if (configuredQuality != null) quality = ModDataStorage.normalizeQuality(configuredQuality);
            }
            qualities.put(itemId, quality);
            // Manual prices are inviolable. The two world-artifact values are
            // intentionally fixed; all other automatic values gain an offset.
            if (manualItems.contains(item) || hasFixedAutomaticPrice(item)) {
                result.put(itemId, clamp(entry.getValue()));
                continue;
            }
            // Explicit catalogue prices are authored values, not recipe
            // estimates. Do not inflate small materials (for example nuggets)
            // to the quality tier's per-slot minimum. They still receive the
            // configured random market offset below unless explicitly exempt.
            if (catalogueItems.contains(item)) {
                result.put(itemId, clamp(entry.getValue()));
                continue;
            }
            ItemSizeRule configuredSize = ServerItemRules.getConfiguredSizeRuleFor(stack);
            ItemSizeRule sizeRule = configuredSize != null
                ? configuredSize : automaticSizes.getOrDefault(itemId, ItemSizeRule.DEFAULT);
            int slots = sizeRule.size().width() * sizeRule.size().height();
            result.put(itemId, rangedPrice(entry.getValue(), quality, slots));
        }
        applyReversibleMaterialPrices(recipes, result, fixedItems);
        for (Item item : values.keySet()) {
            if (manualItems.contains(item) || hasFixedAutomaticPrice(item)) continue;
            String itemId = itemId(item);
            result.computeIfPresent(itemId, (ignored, price) -> addRandomOffset(price));
        }

        Map<String, String> finalQualities = new HashMap<>(calculateQualities(result));
        applyRecipeQualityFloors(recipes, finalQualities);
        for (Item item : snapshot.items()) {
            if (item == Items.AIR) continue;
            BuiltinItemRuleCatalog.Rule builtin = BuiltinItemRuleCatalog.explicit(item.getDefaultInstance());
            if (builtin != null) finalQualities.put(itemId(item), builtin.quality());
        }
        if (useConfiguredQualities) {
            for (Item item : snapshot.items()) {
                if (item == Items.AIR) continue;
                String itemId = itemId(item);
                String configuredQuality = ServerItemRules.getConfiguredManualQualityFor(item.getDefaultInstance());
                if (configuredQuality != null) {
                    finalQualities.put(itemId, ModDataStorage.normalizeQuality(configuredQuality));
                }
            }
        }
        // Safety box tiers are product definitions rather than price-derived qualities.
        finalQualities.putAll(safetyBoxQualities());
        return new Result(Map.copyOf(result), Map.copyOf(finalQualities), recipeDerived, fallbackDerived);
    }

    /**
     * A crafted output may not fall below the highest-quality ingredient used
     * by its recipe. Alternatives use their lowest known quality so broad tags
     * do not inflate a recipe merely because one expensive option exists.
     * Manual quality rules are applied afterwards and remain authoritative.
     */
    private static void applyRecipeQualityFloors(Iterable<AutomaticCalculationSnapshot.RecipeData> recipes,
                                                 Map<String, String> qualities) {
        for (int pass = 0; pass < 32; pass++) {
            boolean changed = false;
            for (AutomaticCalculationSnapshot.RecipeData recipe : recipes) {
                int recipeFloor = 0;
                boolean hasIngredient = false;
                for (List<Item> alternatives : recipe.ingredients()) {
                    int alternativeFloor = Integer.MAX_VALUE;
                    for (Item alternative : alternatives) {
                        String quality = qualities.get(itemId(alternative));
                        if (quality != null) alternativeFloor = Math.min(alternativeFloor, qualityRank(quality));
                    }
                    if (alternativeFloor == Integer.MAX_VALUE) continue;
                    recipeFloor = Math.max(recipeFloor, alternativeFloor);
                    hasIngredient = true;
                }
                if (!hasIngredient) continue;
                String outputId = itemId(recipe.output());
                String current = qualities.getOrDefault(outputId, "gray");
                if (qualityRank(current) >= recipeFloor) continue;
                qualities.put(outputId, qualityForRank(recipeFloor));
                changed = true;
            }
            if (!changed) break;
        }
    }

    private static boolean hasManualPrice(Map<String, Long> manualPrices, ItemStack stack, String itemId) {
        if (manualPrices.containsKey(itemId) || manualPrices.containsKey(ModDataStorage.getKey(stack))) return true;
        for (String key : manualPrices.keySet()) {
            if (key.startsWith(ModDataStorage.PREFIX_WILDCARD)
                && ModDataStorage.matchesWildcard(key.substring(ModDataStorage.PREFIX_WILDCARD.length()), itemId)) {
                return true;
            }
            if (key.startsWith(ModDataStorage.PREFIX_TAG)) {
                String tagId = key.substring(ModDataStorage.PREFIX_TAG.length());
                if (stack.getTags().anyMatch(tag -> tag.location().toString().equals(tagId))) return true;
            }
        }
        return false;
    }

    public static Map<String, String> calculateQualities(Map<String, Long> prices) {
        List<Long> sorted = prices.values().stream().filter(value -> value > 0).sorted().toList();
        if (sorted.isEmpty()) return safetyBoxQualities();
        long green = quantile(sorted, 0.35);
        long blue = quantile(sorted, 0.65);
        long purple = quantile(sorted, 0.85);
        long gold = quantile(sorted, 0.96);
        long red = quantile(sorted, 0.995);
        Map<String, String> result = new HashMap<>();
        for (var entry : prices.entrySet()) {
            ResourceLocation itemId = ResourceLocation.tryParse(entry.getKey());
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) continue;
            Item item = BuiltInRegistries.ITEM.get(itemId);
            long value = entry.getValue();
            String quality = value >= red ? "red"
                : value >= gold ? "gold"
                : value >= purple ? "purple"
                : value >= blue ? "blue"
                : value >= green ? "green" : "gray";
            quality = higherQuality(quality, rarityFloor(item.getDefaultInstance().getRarity()));
            boolean explicitQuality = false;
            String modNameQuality = RuleFormulaConfig.modQualityFromName(item.getDefaultInstance());
            if (modNameQuality != null) {
                quality = ModDataStorage.normalizeQuality(modNameQuality);
                explicitQuality = true;
            } else {
                String forced = forcedQuality(item, itemId);
                if (forced != null) {
                    quality = forced;
                    explicitQuality = true;
                } else {
                    String material = materialQuality(itemId);
                    if (material != null) {
                        quality = material;
                        explicitQuality = true;
                    }
                }
                BuiltinItemRuleCatalog.Rule explicit = BuiltinItemRuleCatalog.explicit(item.getDefaultInstance());
                if (explicit != null) {
                    quality = explicit.quality();
                    explicitQuality = true;
                }
            }
            if (!explicitQuality) {
                // Vanilla name color remains a fallback; mod item name color is
                // handled first because one item id may represent many variants.
                String nameColorQuality = RuleFormulaConfig.qualityFromName(item.getDefaultInstance());
                if (nameColorQuality != null) quality = ModDataStorage.normalizeQuality(nameColorQuality);
            }
            result.put(entry.getKey(), quality);
        }
        result.putAll(safetyBoxQualities());
        return Map.copyOf(result);
    }

    public static Map<String, String> safetyBoxQualities() {
        return Map.of(
            "xero_delta:safety_box_2x1", "green",
            "xero_delta:safety_box_2x2", "blue",
            "xero_delta:safety_box_3x2", "purple",
            "xero_delta:safety_box_3x3", "gold",
            "xero_delta:safety_box_4x2", "purple"
        );
    }

    private static String forcedQuality(Item item, ResourceLocation itemId) {
        String path = itemId.getPath().toLowerCase(Locale.ROOT);
        String requested = VanillaQualityOverrides.find(path);
        if (requested != null) return requested;
        if (item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP
            || item == Items.TOTEM_OF_UNDYING || item == Items.NAUTILUS_SHELL) {
            return "gold";
        }
        if (item == Items.MACE || item == Items.ENCHANTED_GOLDEN_APPLE
            || item == Items.HEART_OF_THE_SEA || item == Items.NETHER_STAR
            || itemId.getPath().contains("netherite")) {
            return "red";
        }
        return null;
    }

    private static String rarityFloor(Rarity rarity) {
        if (rarity == Rarity.EPIC) return "purple";
        if (rarity == Rarity.RARE) return "blue";
        if (rarity == Rarity.UNCOMMON) return "green";
        return "gray";
    }

    private static String materialQuality(ResourceLocation itemId) {
        String path = itemId.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith("_spawn_egg")) return null;
        if (path.contains("diamond")) return "gold";
        if (isIronMaterial(path)) return "purple";
        if (isGoldMaterial(path)) return "blue";
        if (isCommonWood(path)) return "gray";
        if (isCommonStone(path)) return "green";
        return null;
    }

    private static boolean isIronMaterial(String path) {
        return path.startsWith("iron_") || path.contains("_iron_")
            || path.equals("raw_iron") || path.equals("raw_iron_block");
    }

    private static boolean isGoldMaterial(String path) {
        return path.startsWith("gold_") || path.startsWith("golden_") || path.contains("_gold_")
            || path.equals("raw_gold") || path.equals("raw_gold_block");
    }

    private static boolean isCommonWood(String path) {
        return path.startsWith("wooden_") || path.contains("_wooden_")
            || path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_planks")
            || path.endsWith("_stem") || path.endsWith("_hyphae");
    }

    private static boolean isCommonStone(String path) {
        return path.equals("stone") || path.startsWith("stone_") || path.endsWith("_stone_bricks")
            || path.equals("cobblestone") || path.endsWith("_cobblestone")
            || path.equals("smooth_stone") || path.equals("granite") || path.equals("diorite")
            || path.equals("andesite") || path.equals("deepslate") || path.equals("cobbled_deepslate")
            || path.endsWith("sandstone");
    }

    private static String higherQuality(String first, String second) {
        return qualityRank(first) >= qualityRank(second) ? first : second;
    }

    private static int qualityRank(String quality) {
        return switch (quality) {
            case "red" -> 5;
            case "gold" -> 4;
            case "purple" -> 3;
            case "blue" -> 2;
            case "green" -> 1;
            default -> 0;
        };
    }

    private static String qualityForRank(int rank) {
        return switch (Math.max(0, Math.min(5, rank))) {
            case 5 -> "red";
            case 4 -> "gold";
            case 3 -> "purple";
            case 2 -> "blue";
            case 1 -> "green";
            default -> "gray";
        };
    }

    private static int resolveRecipes(Iterable<AutomaticCalculationSnapshot.RecipeData> recipes,
                                      Map<Item, Long> values, Set<Item> fixedItems) {
        int changedItems = 0;
        for (int pass = 0; pass < 32; pass++) {
            int changedThisPass = 0;
            for (AutomaticCalculationSnapshot.RecipeData recipe : recipes) {
                if (fixedItems.contains(recipe.output())) continue;
                List<List<Item>> ingredients = recipe.ingredients();
                if (ingredients.isEmpty()) continue;
                long total = 0;
                boolean complete = true;
                for (List<Item> ingredient : ingredients) {
                    long ingredientValue = cheapestIngredient(ingredient, values);
                    if (ingredientValue <= 0) {
                        complete = false;
                        break;
                    }
                    total = clamp(total + ingredientValue);
                }
                if (!complete || total <= 0) continue;
                long calculated = clamp(Math.max(1L,
                    Math.round(total * recipe.multiplier() / recipe.outputCount())));
                Long previous = values.get(recipe.output());
                if (previous == null || calculated < previous) {
                    values.put(recipe.output(), calculated);
                    changedThisPass++;
                }
            }
            changedItems += changedThisPass;
            if (changedThisPass == 0) break;
        }
        return changedItems;
    }

    private static void applyReversibleMaterialPrices(Iterable<AutomaticCalculationSnapshot.RecipeData> recipes,
                                                      Map<String, Long> prices, Set<Item> fixedItems) {
        List<MaterialRecipe> materialRecipes = new ArrayList<>();
        for (AutomaticCalculationSnapshot.RecipeData recipe : recipes) {
            List<List<Item>> ingredients = recipe.ingredients();
            Item ingredient = singleIngredientItem(ingredients);
            if (ingredient == null || ingredients.isEmpty() || ingredient == recipe.output()) continue;
            materialRecipes.add(new MaterialRecipe(ingredient, ingredients.size(), recipe.output(), recipe.outputCount()));
        }

        Map<Item, List<MaterialConversion>> graph = new HashMap<>();
        for (MaterialRecipe recipe : materialRecipes) {
            if (!hasBalancedReverse(recipe, materialRecipes)) continue;
            addConversion(graph, recipe.ingredient(), recipe.output(), recipe.ingredientCount(), recipe.outputCount());
            addConversion(graph, recipe.output(), recipe.ingredient(), recipe.outputCount(), recipe.ingredientCount());
        }

        Set<Item> visited = new HashSet<>();
        for (Item start : graph.keySet()) {
            if (!visited.add(start)) continue;
            Set<Item> component = new HashSet<>();
            Deque<Item> pending = new ArrayDeque<>();
            pending.add(start);
            while (!pending.isEmpty()) {
                Item current = pending.removeFirst();
                component.add(current);
                for (MaterialConversion conversion : graph.getOrDefault(current, List.of())) {
                    if (visited.add(conversion.to())) pending.addLast(conversion.to());
                }
            }

            Item anchor = chooseMaterialAnchor(component, fixedItems, prices);
            if (anchor == null) continue;
            long anchorPrice = getPrice(prices, anchor);
            Map<Item, UnitRatio> ratios = new HashMap<>();
            ratios.put(anchor, UnitRatio.ONE);
            pending.add(anchor);
            while (!pending.isEmpty()) {
                Item current = pending.removeFirst();
                UnitRatio currentRatio = ratios.get(current);
                for (MaterialConversion conversion : graph.getOrDefault(current, List.of())) {
                    if (ratios.containsKey(conversion.to())) continue;
                    ratios.put(conversion.to(), currentRatio.multiply(conversion.numerator(), conversion.denominator()));
                    pending.addLast(conversion.to());
                }
            }
            for (var entry : ratios.entrySet()) {
                if (fixedItems.contains(entry.getKey())) continue;
                prices.put(itemId(entry.getKey()), entry.getValue().apply(anchorPrice));
            }
        }
    }

    private static boolean hasBalancedReverse(MaterialRecipe recipe, List<MaterialRecipe> recipes) {
        for (MaterialRecipe reverse : recipes) {
            if (reverse.ingredient() != recipe.output() || reverse.output() != recipe.ingredient()) continue;
            if ((long) recipe.ingredientCount() * reverse.ingredientCount()
                == (long) recipe.outputCount() * reverse.outputCount()) return true;
        }
        return false;
    }

    private static void addConversion(Map<Item, List<MaterialConversion>> graph, Item from, Item to,
                                      int numerator, int denominator) {
        List<MaterialConversion> conversions = graph.computeIfAbsent(from, ignored -> new ArrayList<>());
        boolean exists = conversions.stream().anyMatch(conversion -> conversion.to() == to
            && conversion.numerator() == numerator && conversion.denominator() == denominator);
        if (!exists) conversions.add(new MaterialConversion(to, numerator, denominator));
    }

    private static Item chooseMaterialAnchor(Set<Item> component, Set<Item> fixedItems, Map<String, Long> prices) {
        Comparator<Item> comparator = Comparator
            .comparingInt(AutomaticItemValuation::materialAnchorPriority)
            .thenComparing(AutomaticItemValuation::itemId);
        return component.stream()
            .filter(item -> fixedItems.contains(item) && getPrice(prices, item) > 0)
            .max(comparator)
            .orElseGet(() -> component.stream().filter(item -> getPrice(prices, item) > 0).max(comparator).orElse(null));
    }

    private static int materialAnchorPriority(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.endsWith("_nugget")) return 10;
        if (path.endsWith("_block")) return 20;
        if (path.startsWith("raw_") && path.endsWith("_block")) return 15;
        if (path.startsWith("raw_")) return 80;
        if (path.endsWith("_ingot") || path.endsWith("_gem") || path.endsWith("_dust")
            || path.endsWith("_fragment") || path.endsWith("_shard")) return 100;
        return 90;
    }

    private static long getPrice(Map<String, Long> prices, Item item) {
        return prices.getOrDefault(itemId(item), 0L);
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static Item singleIngredientItem(List<List<Item>> ingredients) {
        Item item = null;
        for (List<Item> ingredient : ingredients) {
            if (ingredient.size() != 1) return null;
            Item candidate = ingredient.getFirst();
            if (item == null) item = candidate;
            else if (item != candidate) return null;
        }
        return item;
    }

    private static long cheapestIngredient(List<Item> ingredient, Map<Item, Long> values) {
        long cheapest = Long.MAX_VALUE;
        for (Item option : ingredient) {
            Long value = values.get(option);
            if (value != null && value > 0) cheapest = Math.min(cheapest, value);
        }
        return cheapest == Long.MAX_VALUE ? -1 : cheapest;
    }

    private static long fallbackValue(Item item) {
        ItemStack stack = item.getDefaultInstance();
        String id = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase(Locale.ROOT);
        long value = item instanceof BlockItem ? 25 : 40;
        value += Math.max(0, stack.getMaxDamage()) * 2L;
        if (stack.getMaxStackSize() == 1) value += 180;
        Rarity rarity = stack.getRarity();
        value *= switch (rarity) {
            case UNCOMMON -> 2;
            case RARE -> 5;
            case EPIC -> 12;
            default -> 1;
        };
        value = Math.max(value, keywordFloor(id));
        if (!id.startsWith("minecraft:")) value = Math.round(value * 1.35);
        return clamp(Math.max(1, value));
    }

    private static long keywordFloor(String id) {
        if (id.contains("netherite")) return 50_000;
        if (id.contains("diamond")) return 10_000;
        if (id.contains("emerald")) return 6_000;
        if (id.contains("gold")) return 1_500;
        if (id.contains("iron")) return 500;
        if (id.contains("copper")) return 150;
        if (id.contains("lapis") || id.contains("redstone") || id.contains("quartz")) return 100;
        if (id.contains("coal")) return 80;
        if (id.contains("log") || id.contains("wood") || id.contains("plank")) return 20;
        if (id.contains("stone") || id.contains("cobble") || id.contains("dirt") || id.contains("sand")) return 5;
        return 1;
    }

    private static boolean hasFixedAutomaticPrice(Item item) {
        if (item == Items.HEART_OF_THE_SEA || item == Items.NETHER_STAR) return true;
        BuiltinItemRuleCatalog.Rule builtin = BuiltinItemRuleCatalog.explicit(item.getDefaultInstance());
        return builtin != null && builtin.fixedAutomaticPrice();
    }

    private static long rangedPrice(long calculated, String quality, int slots) {
        PriceRange range = priceRange(quality);
        long minimum = clamp(range.minimumPerSlot() * Math.max(1, slots));
        long maximum = clamp(Math.min(range.maximumTotal(), range.maximumPerSlot() * Math.max(1, slots)));
        long bounded = Math.max(minimum, Math.min(maximum, calculated));
        return bounded;
    }

    private static PriceRange priceRange(String quality) {
        RuleFormulaConfig.Range configured = RuleFormulaConfig.range(quality);
        return new PriceRange(configured.minimumPerSlot, configured.maximumPerSlot, configured.maximumTotal);
    }

    private static long addRandomOffset(long value) {
        RuleFormulaConfig.FormulaData formula = RuleFormulaConfig.get();
        long minimum = Math.max(0L, formula.randomOffsetMinimum);
        long maximum = Math.max(minimum, formula.randomOffsetMaximum);
        if (maximum == 0L) return value;
        long offset = minimum == maximum ? minimum : ThreadLocalRandom.current().nextLong(minimum, maximum + 1L);
        return clamp(value > MAX_VALUE - offset ? MAX_VALUE : value + offset);
    }

    private static long quantile(List<Long> values, double fraction) {
        int index = (int) Math.floor((values.size() - 1) * fraction);
        return values.get(Math.max(0, Math.min(values.size() - 1, index)));
    }

    private static long clamp(long value) {
        return Math.max(1L, Math.min(MAX_VALUE, value));
    }

    private record MaterialRecipe(Item ingredient, int ingredientCount, Item output, int outputCount) {
    }

    private record MaterialConversion(Item to, long numerator, long denominator) {
    }

    private record UnitRatio(long numerator, long denominator) {
        private static final UnitRatio ONE = new UnitRatio(1L, 1L);

        private UnitRatio multiply(long factorNumerator, long factorDenominator) {
            long commonFirst = greatestCommonDivisor(factorNumerator, denominator);
            long commonSecond = greatestCommonDivisor(numerator, factorDenominator);
            long reducedNumerator = factorNumerator / commonFirst;
            long reducedDenominator = factorDenominator / commonSecond;
            long nextNumerator = clampMultiplication(numerator / commonSecond, reducedNumerator);
            long nextDenominator = clampMultiplication(denominator / commonFirst, reducedDenominator);
            long common = greatestCommonDivisor(nextNumerator, nextDenominator);
            return new UnitRatio(nextNumerator / common, nextDenominator / common);
        }

        private long apply(long value) {
            long common = greatestCommonDivisor(value, denominator);
            long scaledValue = value / common;
            long scaledDenominator = denominator / common;
            long product = clampMultiplication(scaledValue, numerator);
            return clamp(Math.max(1L, Math.round((double) product / scaledDenominator)));
        }

        private static long clampMultiplication(long first, long second) {
            if (first <= 0 || second <= 0) return 1L;
            if (first > MAX_VALUE / second) return MAX_VALUE;
            return first * second;
        }

        private static long greatestCommonDivisor(long first, long second) {
            while (second != 0) {
                long remainder = first % second;
                first = second;
                second = remainder;
            }
            return Math.max(1L, Math.abs(first));
        }
    }

    private record PriceRange(long minimumPerSlot, long maximumPerSlot, long maximumTotal) {
    }
}
