package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;

/** Renders the coin icon in tooltips */
public record CoinTooltipComponent() implements ClientTooltipComponent {
    private static final ResourceLocation COIN_TEX = ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "textures/quality/coin.png");

    @Override public int getHeight() { return 9; }
    @Override public int getWidth(Font font) { return 10; }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        g.setColor(1f, 1f, 1f, 1f);
        g.blit(COIN_TEX, x, y, 0, 0, 9, 9, 9, 9);
    }
}
