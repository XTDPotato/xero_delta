package com.xtdpotato.xero_delta.trading;

import net.minecraft.world.item.ItemStack;
import java.util.List;

public interface TradingInventorySource {
    String id();

    String label();

    String groupId();

    ItemStack peek();

    ItemStack extract(int amount, boolean simulate);

    /** Restores a previously extracted stack to the same logical source. */
    default boolean restore(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    default boolean sellable() {
        return TradingInventorySources.canSellStack(peek());
    }

    default String blockedReason() {
        return "market.xero_delta.error.backpack_not_empty";
    }

    record Group(String id, ItemStack icon, int ordinal) {
        public Group {
            id = id == null ? "" : id;
            icon = icon == null || icon.isEmpty() ? ItemStack.EMPTY : new ItemStack(icon.getItem());
            ordinal = Math.max(1, ordinal);
        }
    }

    record View(String id, String label, String groupId, ItemStack stack, int availableCount,
                boolean sellable, String blockedReason) {
        public View {
            id = id == null ? "" : id;
            label = label == null ? "" : label;
            groupId = groupId == null ? "" : groupId;
            stack = stack.copy();
            availableCount = Math.max(stack.getCount(), availableCount);
            blockedReason = blockedReason == null ? "" : blockedReason;
        }
    }

    record Catalog(List<View> sources, List<Group> groups) {
        public Catalog {
            sources = List.copyOf(sources == null ? List.of() : sources);
            groups = List.copyOf(groups == null ? List.of() : groups);
        }
    }
}
