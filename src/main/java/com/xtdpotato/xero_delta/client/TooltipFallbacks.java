package com.xtdpotato.xero_delta.client;

import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public final class TooltipFallbacks {
    private static final ClientTooltipComponent NOOP_COMPONENT = new ClientTooltipComponent() {
        @Override
        public int getHeight() {
            return 0;
        }

        @Override
        public int getWidth(Font font) {
            return 0;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        }
    };

    private static final Function<TooltipComponent, ClientTooltipComponent> NOOP_FACTORY = ignored -> NOOP_COMPONENT;

    private TooltipFallbacks() {
    }

    public static Object getFactoryOrFallback(Map<?, ?> factories, Object key) {
        Object factory = factories.get(key);
        return factory != null ? factory : NOOP_FACTORY;
    }
}
