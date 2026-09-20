package com.xtdpotato.xero_delta.data;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnifeSkinRulesTest {
    @Test
    void ordinaryItemsAreNotLocked() {
        assertFalse(KnifeSkinRules.locked(new ItemStack(Items.DIAMOND)));
        assertFalse(KnifeSkinRules.locked(ItemStack.EMPTY));
    }

    @Test
    void matchingIgnoresDurabilityDamage() {
        ItemStack expected = new ItemStack(Items.IRON_SWORD);
        ItemStack damaged = expected.copy();
        damaged.set(DataComponents.DAMAGE, 17);

        assertTrue(KnifeSkinRules.matches(damaged, expected));
        assertFalse(KnifeSkinRules.matches(ItemStack.EMPTY, expected));
        assertFalse(KnifeSkinRules.matches(new ItemStack(Items.DIAMOND), expected));
    }

    @Test
    void differentSkinsOnTheSameItemCanBeAppliedAgain() {
        ItemStack expected = new ItemStack(Items.IRON_SWORD);
        CompoundTag first = new CompoundTag();
        first.putString("MeleeId", "lr_tactical:combat_knife");
        expected.set(DataComponents.CUSTOM_DATA, CustomData.of(first));
        ItemStack other = expected.copy();
        CompoundTag second = new CompoundTag();
        second.putString("MeleeId", "lr_tactical:katana");
        other.set(DataComponents.CUSTOM_DATA, CustomData.of(second));

        assertTrue(KnifeSkinRules.locked(expected));
        assertFalse(com.xtdpotato.xero_delta.trading.TradingInventorySources.canSellStack(expected));
        Slot slot = new Slot(new SimpleContainer(expected), 0, 0, 0);
        assertFalse(slot.mayPickup(null));
        assertFalse(slot.mayPlace(new ItemStack(Items.DIAMOND)));
        Slot emptySlot = new Slot(new SimpleContainer(1), 0, 0, 0);
        assertFalse(emptySlot.mayPlace(expected));
        assertTrue(emptySlot.mayPlace(new ItemStack(Items.DIAMOND)));
        assertTrue(KnifeSkinRules.matches(expected.copy(), expected));
        assertFalse(KnifeSkinRules.matches(other, expected));
        assertFalse(KnifeSkinRules.matches(ItemStack.EMPTY, expected));
    }
}
