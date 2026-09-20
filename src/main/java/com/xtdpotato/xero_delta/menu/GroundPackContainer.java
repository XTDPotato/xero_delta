package com.xtdpotato.xero_delta.menu;

import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.grid.GridContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Mutable view of the contents stored directly on a ground carrier item. */
public final class GroundPackContainer extends GridContainer {
    private final ItemEntity entity;
    private ItemStack carrier;
    private final int width;
    private final int height;
    private final NonNullList<ItemStack> items;

    public GroundPackContainer(ItemEntity entity, ItemStack fallback,
                               int width, int height) {
        this.entity = entity;
        this.carrier = entity != null && !entity.getItem().isEmpty()
            ? entity.getItem().copy() : fallback.copy();
        this.width = width;
        this.height = height;
        this.items = NonNullList.withSize(width * height, ItemStack.EMPTY);
        load();
    }

    public int width() { return width; }
    public int height() { return height; }
    public ItemStack carrier() { return carrier; }
    public int carrierSlot() { return items.size(); }
    public ItemEntity entity() { return entity; }

    @Override public int getContainerSize() { return items.size() + 1; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == carrierSlot()) return carrier;
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot == carrierSlot()) {
            if (carrier.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            ItemStack result = carrier.copyWithCount(Math.min(amount, carrier.getCount()));
            carrier.shrink(result.getCount());
            if (carrier.isEmpty()) clearStoredItems();
            updateEntity();
            return result;
        }
        if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
        ItemStack result = items.get(slot).split(amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot == carrierSlot()) {
            ItemStack result = carrier;
            carrier = ItemStack.EMPTY;
            clearStoredItems();
            updateEntity();
            return result;
        }
        if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
        ItemStack result = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == carrierSlot()) {
            carrier = stack == null ? ItemStack.EMPTY : stack;
            if (carrier.isEmpty()) clearStoredItems();
            else load();
            updateEntity();
            return;
        }
        if (slot < 0 || slot >= items.size()) return;
        items.set(slot, stack);
        setChanged();
    }

    @Override public void setChanged() { save(); }

    @Override
    public boolean stillValid(Player player) {
        return !carrier.isEmpty() && (entity == null || entity.isAlive()
            && player.distanceToSqr(entity) <= 100.0D);
    }

    @Override
    public void clearContent() {
        for (int index = 0; index < items.size(); index++) {
            items.set(index, ItemStack.EMPTY);
        }
        setChanged();
    }

    @Override public void startOpen(Player player) { load(); }
    @Override public void stopOpen(Player player) { save(); }

    private void load() {
        List<ItemStack> stored = carrier.getOrDefault(
            ModDataComponents.GRID_CONTENTS.get(), List.of());
        for (int index = 0; index < items.size(); index++) {
            items.set(index, index < stored.size()
                ? stored.get(index).copy() : ItemStack.EMPTY);
        }
    }

    private void save() {
        if (carrier.isEmpty()) {
            updateEntity();
            return;
        }
        List<ItemStack> stored = new ArrayList<>(items.size());
        for (ItemStack stack : items) stored.add(stack.copy());
        carrier.set(ModDataComponents.GRID_CONTENTS.get(), stored);
        updateEntity();
    }

    private void updateEntity() {
        if (entity != null && entity.isAlive()) entity.setItem(carrier.copy());
    }

    private void clearStoredItems() {
        for (int index = 0; index < items.size(); index++) {
            items.set(index, ItemStack.EMPTY);
        }
    }
}
