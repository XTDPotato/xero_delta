package com.xtdpotato.xero_delta.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import com.xtdpotato.xero_delta.tag.ModTags;

import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic fallback weight calculation used by /xero_weight auto. */
public final class AutomaticItemWeight {
    private AutomaticItemWeight() {
    }

    public static Map<String, Double> calculateAll() {
        Map<String, Double> result = new LinkedHashMap<>();
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (stack.isEmpty() || isBackpackLike(stack)) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            result.put(ServerItemRules.exactKey(itemId), estimate(stack));
        }
        return Map.copyOf(result);
    }

    public static double estimate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0D;
        var medical = com.xtdpotato.xero_delta.item.ConsumableProfile.get(stack);
        if (medical != null) return medical.weight();
        if (isBackpackLike(stack)) return 0.0D;
        ItemSize size = ServerItemRules.getSizeFor(stack);
        int cells = Math.max(1, size.width() * size.height());
        double weight;
        if (stack.getItem() instanceof BlockItem) {
            weight = estimateBlockWeight(cells, stack.getMaxStackSize());
        } else if (stack.isDamageableItem()) {
            weight = 0.18D + cells * 0.24D;
        } else if (stack.getMaxStackSize() <= 1) {
            weight = 0.12D + cells * 0.18D;
        } else {
            weight = 0.05D + cells * 0.11D;
            if (stack.getMaxStackSize() <= 16) weight *= 1.15D;
        }
        return Math.max(0.001D, Math.round(weight * 1000.0D) / 1000.0D);
    }

    /** Default weight for one block item, unless an explicit item-weight rule overrides it. */
    static double estimateBlockWeight(int cells, int maxStackSize) {
        return 0.10D;
    }

    public static boolean isBackpackLike(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(ModTags.SAFETY_BOX)) return true;
        if (stack.getItem() instanceof com.xtdpotato.xero_delta.item.DeltaPackItem) return true;
        try {
            if (stack.getCapability(Capabilities.ItemHandler.ITEM) != null) return true;
        } catch (RuntimeException ignored) {
        }
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return namespace.contains("sophisticatedbackpack")
            || namespace.contains("travelersbackpack");
    }
}
