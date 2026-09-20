package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.data.ItemSize;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record ItemSizeTooltipData(ItemStack stack, ItemSize size, double weight,
                                  Config.TooltipSizeMode mode)
    implements TooltipComponent {
}
