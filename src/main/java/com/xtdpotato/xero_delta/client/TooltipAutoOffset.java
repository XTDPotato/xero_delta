package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.Config;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import java.util.ArrayList;
import java.util.List;

/** Applies automatic title padding after other mods have finalized tooltip components. */
public final class TooltipAutoOffset {
    private TooltipAutoOffset() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltipPre(RenderTooltipEvent.Pre event) {
        TooltipTitleComponent title = null;
        List<TooltipOverlapPadding.Metric> preceding = new ArrayList<>();
        for (ClientTooltipComponent component : event.getComponents()) {
            if (component instanceof TooltipTitleComponent tooltipTitle) {
                title = tooltipTitle;
                break;
            }
            preceding.add(new TooltipOverlapPadding.Metric(
                component.getWidth(event.getFont()), component.getHeight()));
        }
        if (title == null) return;
        title.setAutoPadding(TooltipOverlapPadding.NONE);
        if (!Config.INSTANCE.tooltipTitleAutoOffset.get()) return;
        title.setAutoPadding(TooltipOverlapPadding.resolve(preceding, title.contentHeight()));
    }
}
