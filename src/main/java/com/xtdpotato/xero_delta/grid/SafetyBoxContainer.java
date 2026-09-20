package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.ModDataComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public class SafetyBoxContainer extends GridContainer {
    private ItemStack boxStack;
    private final int gridWidth, gridHeight;
    private final NonNullList<ItemStack> items;
    private SafetyBoxMenu menu;

    public SafetyBoxContainer(ItemStack boxStack, int gw, int gh) {
        this.boxStack = boxStack;
        this.gridWidth = gw; this.gridHeight = gh;
        this.items = NonNullList.withSize(gw * gh, ItemStack.EMPTY);
    }

    public void setMenu(SafetyBoxMenu m) { this.menu = m; }
    public SafetyBoxMenu getMenu() { return menu; }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { for (var s : items) if (!s.isEmpty()) return false; return true; }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) { var r = items.get(slot).split(amount); if (!r.isEmpty()) setChanged(); return r; }
    @Override public ItemStack removeItemNoUpdate(int slot) { var r = items.get(slot); items.set(slot, ItemStack.EMPTY); return r; }
    @Override public void setItem(int slot, ItemStack stack) { if (slot >= 0 && slot < items.size()) { items.set(slot, stack); setChanged(); } }
    @Override public void setChanged() { saveToComponent(); }
    @Override public boolean stillValid(Player p) { return true; }
    @Override public void clearContent() { for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY); }

    public int getGridWidth() { return gridWidth; }
    public int getGridHeight() { return gridHeight; }
    public ItemStack getBoxStack() { return boxStack; }

    public void loadFromComponent() {
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
        List<ItemStack> stored = boxStack.getOrDefault(ModDataComponents.GRID_CONTENTS.get(), List.of());
        for (int i = 0; i < items.size() && i < stored.size(); i++)
            items.set(i, stored.get(i).copy());
    }

    public void saveToComponent() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack s : items) copy.add(s.copy());
        if (boxStack != null && !boxStack.isEmpty()) boxStack.set(ModDataComponents.GRID_CONTENTS.get(), copy);
    }

    @Override public void startOpen(Player p) { loadFromComponent(); }
    @Override public void stopOpen(Player p) { saveToComponent(); }
}