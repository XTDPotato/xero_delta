package com.xtdpotato.xero_delta.grid;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Abstract base for grid-based containers.
 * Provides common interface for future extensions (4x4, 5x5, multi-page, etc.).
 */
public abstract class GridContainer implements Container {

    @Override
    public abstract int getContainerSize();

    @Override
    public abstract boolean isEmpty();

    @Override
    public abstract ItemStack getItem(int slot);

    @Override
    public abstract ItemStack removeItem(int slot, int amount);

    @Override
    public abstract ItemStack removeItemNoUpdate(int slot);

    @Override
    public abstract void setItem(int slot, ItemStack stack);

    @Override
    public abstract void setChanged();

    @Override
    public abstract boolean stillValid(Player player);

    @Override
    public abstract void clearContent();

    @Override
    public int getMaxStackSize() { return 64; }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
    }

    @Override
    public void startOpen(Player player) {}

    @Override
    public void stopOpen(Player player) {}
}
