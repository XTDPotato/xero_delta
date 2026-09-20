package com.xtdpotato.xero_delta.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of safety box grid state.
 * Client only renders this — never mutates it.
 */
public class SafetyBoxSnapshot {
    private final List<net.minecraft.world.item.ItemStack> items;
    private final int version;
    private final int gridWidth, gridHeight;

    public SafetyBoxSnapshot(List<net.minecraft.world.item.ItemStack> items, int version, int gw, int gh) {
        this.items = new ArrayList<>(items);
        this.version = version;
        this.gridWidth = gw;
        this.gridHeight = gh;
    }

    public net.minecraft.world.item.ItemStack getItemAt(int x, int y) {
        int idx = y * gridWidth + x;
        if (idx < 0 || idx >= items.size()) return net.minecraft.world.item.ItemStack.EMPTY;
        return items.get(idx);
    }

    public int getGridWidth() { return gridWidth; }
    public int getGridHeight() { return gridHeight; }
    public int getVersion() { return version; }

    public boolean isEmpty() {
        for (var s : items) if (!s.isEmpty()) return false;
        return true;
    }
}
