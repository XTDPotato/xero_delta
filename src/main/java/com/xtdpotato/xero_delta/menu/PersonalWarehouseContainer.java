package com.xtdpotato.xero_delta.menu;

import com.xtdpotato.xero_delta.data.PersonalWarehouseData;
import com.xtdpotato.xero_delta.data.WarehouseCategory;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class PersonalWarehouseContainer implements Container {
    private final PersonalWarehouseData data;
    private final PersonalWarehouseData.Bin bin;
    private final WarehouseCategory category;

    public PersonalWarehouseContainer(PersonalWarehouseData data,
                                      PersonalWarehouseData.Bin bin,
                                      WarehouseCategory category) {
        this.data = data;
        this.bin = bin;
        this.category = category;
    }

    @Override public int getContainerSize() { return bin.items().size(); }
    @Override public boolean isEmpty() { return bin.items().stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return bin.items().get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(bin.items(), slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = bin.items().get(slot);
        bin.items().set(slot, ItemStack.EMPTY);
        if (!stack.isEmpty()) setChanged();
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!canPlaceItem(slot, stack)) return;
        bin.items().set(slot, stack);
        if (stack.getCount() > getMaxStackSize(stack)) {
            stack.setCount(getMaxStackSize(stack));
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return category.accepts(stack);
    }

    @Override public void setChanged() { data.changed(); }
    @Override public boolean stillValid(Player player) { return player.isAlive(); }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < bin.items().size(); slot++) {
            bin.items().set(slot, ItemStack.EMPTY);
        }
        setChanged();
    }
}
