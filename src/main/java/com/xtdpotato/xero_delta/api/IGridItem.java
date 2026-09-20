package com.xtdpotato.xero_delta.api;

import net.minecraft.world.item.ItemStack;

public interface IGridItem {
    int getGridWidth(ItemStack stack);
    int getGridHeight(ItemStack stack);
    default boolean canRotate() { return true; }
}