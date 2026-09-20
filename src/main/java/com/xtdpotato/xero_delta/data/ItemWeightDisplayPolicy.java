package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.world.item.ItemStack;

/** Rules for item-level weight labels in client inventory surfaces. */
public final class ItemWeightDisplayPolicy {
    private ItemWeightDisplayPolicy() {
    }

    /**
     * Knife skins and safety boxes are utility carriers rather than carried cargo,
     * so their individual inventory labels stay clear while their contained items
     * continue to contribute to the player's total carried weight.
     */
    public static boolean shouldShow(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && !stack.is(ModTags.SAFETY_BOX)
            && !KnifeSkinRules.locked(stack);
    }
}
