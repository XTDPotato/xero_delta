package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.ModDataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Helpers for the binding state owned by one concrete item stack. */
public final class BoundItemPolicy {
    private BoundItemPolicy() {
    }

    public static boolean isBound(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && (stack.getOrDefault(ModDataComponents.ITEM_BOUND.get(), false)
                || !stack.getOrDefault(ModDataComponents.ITEM_BOUND_OWNER.get(), "").isBlank());
    }

    public static void setBound(ItemStack stack, boolean bound) {
        if (stack == null || stack.isEmpty()) return;
        if (bound) stack.set(ModDataComponents.ITEM_BOUND.get(), true);
        else {
            stack.remove(ModDataComponents.ITEM_BOUND.get());
            stack.remove(ModDataComponents.ITEM_BOUND_OWNER.get());
        }
    }

    public static void setBound(ItemStack stack, UUID owner) {
        if (stack == null || stack.isEmpty() || owner == null) return;
        stack.set(ModDataComponents.ITEM_BOUND.get(), true);
        stack.set(ModDataComponents.ITEM_BOUND_OWNER.get(), owner.toString());
    }

    public static String owner(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ""
            : stack.getOrDefault(ModDataComponents.ITEM_BOUND_OWNER.get(), "");
    }
}
