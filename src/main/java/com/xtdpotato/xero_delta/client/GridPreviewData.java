package com.xtdpotato.xero_delta.client;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Tooltip component carrying grid preview data for safety box items. */
public record GridPreviewData(ItemStack boxStack, List<ItemStack> items, int gridWidth, int gridHeight) implements TooltipComponent {
}
