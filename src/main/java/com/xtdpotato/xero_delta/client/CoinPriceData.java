package com.xtdpotato.xero_delta.client;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Tooltip component carrying coin price data. */
public record CoinPriceData(long price) implements TooltipComponent {
}
