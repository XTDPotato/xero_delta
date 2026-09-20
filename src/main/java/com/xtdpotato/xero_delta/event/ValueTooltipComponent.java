package com.xtdpotato.xero_delta.event;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Data-side tooltip component for item value display.
 * Attached via ItemTooltipEvent, NOT written to DataComponent/ItemStack.
 */
public record ValueTooltipComponent(long value) implements TooltipComponent {
}
