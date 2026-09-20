package com.xtdpotato.xero_delta.data;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class KnifeSkinRules {
    public static final int SLOT = 3;
    private KnifeSkinRules() {}

    public static boolean locked(ItemStack stack) {
        return TaczCompatibilityRules.isLrTacticalMelee(stack);
    }

    /**
     * Knife skins are protected from normal survival-inventory actions, while
     * creative players retain vanilla access for testing and loadout editing.
     * A missing player is handled conservatively as a protected action.
     */
    public static boolean blocksPlayerInventoryAction(Player player, ItemStack stack) {
        return locked(stack) && (player == null || !player.isCreative());
    }

    public static boolean matches(ItemStack actual, ItemStack expected) {
        if (actual.isEmpty() || expected.isEmpty()) return false;
        ItemStack first = actual.copy();
        ItemStack second = expected.copy();
        first.remove(DataComponents.DAMAGE);
        second.remove(DataComponents.DAMAGE);
        return ItemStack.isSameItemSameComponents(first, second);
    }
}
