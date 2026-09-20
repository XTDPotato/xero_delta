package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.data.size.ItemSizeProviders;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public final class AutomaticItemSizing {
    public record Result(Map<String, ItemSizeRule> sizes, int recipeDerived, int categoryDerived) {
    }

    private AutomaticItemSizing() {
    }

    public static Result calculate(MinecraftServer server) {
        return calculate(server, true, false, 0, "all_type", null);
    }

    public static Result calculate(MinecraftServer server, boolean rotateTexture, boolean stretchTexture,
                                   int proportionalScale, String ruleType, String durabilityRange) {
        return calculate(AutomaticCalculationSnapshot.capture(server), rotateTexture, stretchTexture,
            proportionalScale, ruleType, durabilityRange);
    }

    public static Result calculate(AutomaticCalculationSnapshot snapshot) {
        return calculate(snapshot, true, false, 0, "all_type", null);
    }

    public static Result calculate(AutomaticCalculationSnapshot snapshot, boolean rotateTexture,
                                   boolean stretchTexture, int proportionalScale, String ruleType,
                                   String durabilityRange) {
        Map<Item, Integer> ingredientCounts = recipeIngredientCounts(snapshot);
        Map<String, ItemSizeRule> sizes = new HashMap<>();
        int recipeDerived = 0;
        int categoryDerived = 0;
        for (Item item : snapshot.items()) {
            if (item == Items.AIR || isProtected(item)) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            ItemStack stack = item.getDefaultInstance();
            Integer ingredientCount = ingredientCounts.get(item);
            BuiltinItemRuleCatalog.Rule builtin = BuiltinItemRuleCatalog.explicit(stack);
            ItemSizeRule compatibility = ItemSizeProviders.resolveCompatibility(stack);
            ItemSizeRule calculated = builtin != null
                ? new ItemSizeRule(builtin.size(), true, builtin.stretchTexture())
                : compatibility != null
                    ? compatibility
                    : ItemSizeProviders.resolveAutomatic(stack, ingredientCount);
            String key = automaticRuleKey(stack, id, ruleType, durabilityRange);
            if (key == null) continue;
            if (builtin != null || compatibility != null || ingredientCount == null) categoryDerived++;
            else recipeDerived++;
            // Compatibility providers still supply component-sensitive
            // dimensions at lookup time; this stored rule also supplies the
            // texture policy selected by /xero_size auto.
            sizes.put(key, new ItemSizeRule(calculated.size(), rotateTexture,
                stretchTexture || calculated.stretchTexture(), proportionalScale));
        }
        return new Result(Map.copyOf(sizes), recipeDerived, categoryDerived);
    }

    private static String automaticRuleKey(ItemStack stack, ResourceLocation id, String ruleType,
                                           String durabilityRange) {
        if ("this_type".equalsIgnoreCase(ruleType)) return ModDataStorage.getTypeKey(stack);
        if ("this_durability".equalsIgnoreCase(ruleType)) {
            if (!stack.isDamageableItem() || DurabilityRange.parse(durabilityRange, stack.getMaxDamage()) == null) {
                return null;
            }
            return DurabilityRange.key(id.toString(), durabilityRange);
        }
        return id.toString();
    }

    public static boolean isProtected(Item item) {
        return item instanceof SafetyBoxItem
            || item == Items.HEART_OF_THE_SEA
            || item == Items.NETHER_STAR;
    }

    private static Map<Item, Integer> recipeIngredientCounts(AutomaticCalculationSnapshot snapshot) {
        Map<Item, Integer> counts = new HashMap<>();
        for (AutomaticCalculationSnapshot.RecipeData recipe : snapshot.recipes()) {
            if (recipe.nonEmptyIngredientCount() > 0) {
                counts.merge(recipe.output(), recipe.nonEmptyIngredientCount(), Math::min);
            }
        }
        return counts;
    }

}
