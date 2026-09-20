package com.xtdpotato.xero_delta.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record TooltipTitleData(Component title) implements TooltipComponent {
}
