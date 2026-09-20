package com.xtdpotato.xero_delta.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import com.xtdpotato.xero_delta.XeroDelta;

import java.text.NumberFormat;
import java.util.Locale;

/** Renders a coin icon followed by the formatted price in tooltips. */
public class CoinPriceComponent implements ClientTooltipComponent {
    private static final ResourceLocation COIN_TEX = ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "textures/quality/coin.png");
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final String priceText;

    public CoinPriceComponent(CoinPriceData data) {
        this.priceText = FORMAT.format(data.price());
    }

    @Override
    public int getHeight() {
        return 10;
    }

    @Override
    public int getWidth(Font font) {
        return 10 + font.width(priceText) + 2;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        g.setColor(1f, 1f, 1f, 1f);
        g.blit(COIN_TEX, x, y + 1, 0, 0, 9, 9, 9, 9);
        g.drawString(font, priceText, x + 12, y + 1, 0xFFDAA520, false);
    }
}
