package com.xtdpotato.xero_delta.grid;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Minimal Container backed by NBT "Items" tag for server-side use.
 * Uses vanilla ItemStack NBT directly.
 */
public class NBTInventoryWrapper implements Container {
    private final ItemStack boxStack;
    private final HolderLookup.Provider registryAccess;
    private final int gridWidth, gridHeight;
    private final int size;

    public NBTInventoryWrapper(ItemStack boxStack, HolderLookup.Provider registryAccess, int gw, int gh) {
        this.boxStack = boxStack;
        this.registryAccess = registryAccess;
        this.gridWidth = gw;
        this.gridHeight = gh;
        this.size = gw * gh;
    }

    public int getGridWidth() { return gridWidth; }
    public int getGridHeight() { return gridHeight; }
    public ItemStack getBoxStack() { return boxStack; }

    @Override public int getContainerSize() { return size; }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < size; i++)
            if (!getItem(i).isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= size) return ItemStack.EMPTY;
        return ItemStack.parseOptional(registryAccess,
            getOrCreateTag().getList("Items", 10).size() > slot
                ? getOrCreateTag().getList("Items", 10).getCompound(slot)
                : new net.minecraft.nbt.CompoundTag());
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= size) return;
        var tag = getOrCreateTag();
        var list = tag.getList("Items", 10);
        while (list.size() <= slot) list.add(new net.minecraft.nbt.CompoundTag());
        if (stack.isEmpty())
            list.set(slot, new net.minecraft.nbt.CompoundTag());
        else
            list.set(slot, (net.minecraft.nbt.CompoundTag) stack.save(registryAccess));
        tag.put("Items", list);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack cur = getItem(slot);
        if (cur.isEmpty()) return ItemStack.EMPTY;
        int take = Math.min(amount, cur.getCount());
        ItemStack result = cur.copyWithCount(take);
        ItemStack rest = cur.copy(); rest.shrink(take);
        setItem(slot, rest);
        return result;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack cur = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return cur;
    }

    @Override
    public void clearContent() {
        var tag = getOrCreateTag();
        tag.remove("Items");
    }

    @Override public void setChanged() {}
    @Override public boolean stillValid(Player p) { return !boxStack.isEmpty() && p.isAlive(); }
    @Override public int getMaxStackSize() { return 64; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return true; }

    private net.minecraft.nbt.CompoundTag getOrCreateTag() {
        var cd = boxStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (cd == null) cd = new net.minecraft.nbt.CompoundTag();
        return cd;
    }

    public int countItems() {
        int c = 0;
        for (int i = 0; i < size; i++) if (!getItem(i).isEmpty()) c++;
        return c;
    }
}
