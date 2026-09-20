package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class TradingUi {
    private static final int MARQUEE_GAP = 14;
    private static final long MARQUEE_MILLIS_PER_PIXEL = 45L;
    private static final int TOOLTIP_Z = 1_000;
    private static final ResourceLocation COIN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "textures/quality/coin.png");
    private TradingUi() {
    }

    public static String format(long value) {
        return TradingCurrencyFormatter.formatPrice(value);
    }

    public static String formatMarket(long value) {
        return TradingCurrencyFormatter.formatPrice(value);
    }

    public static String formatBalance(long value) {
        return TradingCurrencyFormatter.formatBalance(value);
    }

    public static String formatDetailed(long value) {
        return TradingCurrencyFormatter.formatDetailed(value);
    }

    public static int amountWidth(Font font, long value) {
        return 11 + font.width(format(value));
    }

    public static void drawAmount(GuiGraphics graphics, Font font, long value, int x, int y, int color) {
        graphics.blit(COIN_TEXTURE, x, y, 0, 0, 9, 9, 9, 9);
        graphics.drawString(font, format(value), x + 12, y + 1, color, false);
    }

    public static int balanceWidth(Font font, long value) {
        return 11 + font.width(formatBalance(value));
    }

    public static void drawBalance(GuiGraphics graphics, Font font, long value, int x, int y, int color) {
        graphics.blit(COIN_TEXTURE, x, y, 0, 0, 9, 9, 9, 9);
        graphics.drawString(font, formatBalance(value), x + 12, y + 1, color, false);
    }

    public static void renderBalanceTooltipIfHovered(GuiGraphics graphics, Font font, long value,
                                                      int screenWidth, int y, int mouseX, int mouseY) {
        int balanceWidth = balanceWidth(font, value);
        int x = screenWidth - balanceWidth - 88;
        if (mouseX < x || mouseX >= x + balanceWidth || mouseY < y || mouseY >= y + 11) return;
        renderBalanceTooltip(graphics, font, value, mouseX, mouseY);
    }

    public static void renderBalanceTooltip(GuiGraphics graphics, Font font, long value,
                                            int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, TOOLTIP_Z);
        graphics.renderTooltip(font, Component.literal(formatDetailed(value)), mouseX, mouseY);
        graphics.flush();
        graphics.pose().popPose();
    }

    public static int detailedAmountWidth(Font font, long value) {
        return 11 + font.width(formatDetailed(value));
    }

    public static void drawDetailedAmount(GuiGraphics graphics, Font font, long value,
                                          int x, int y, int color) {
        graphics.blit(COIN_TEXTURE, x, y, 0, 0, 9, 9, 9, 9);
        graphics.drawString(font, formatDetailed(value), x + 12, y + 1, color, false);
    }

    public static void drawMarquee(GuiGraphics graphics, Font font, String text,
                                   int x, int y, int availableWidth, int color, long phase) {
        drawMarquee(graphics, font, text, x, y, availableWidth, color, phase, null);
    }

    public static void drawMarquee(GuiGraphics graphics, Font font, String text,
                                   int x, int y, int availableWidth, int color, long phase,
                                   TradingUiScale.Viewport viewport) {
        if (text == null || text.isEmpty() || availableWidth <= 0) return;
        int textWidth = font.width(text);
        if (textWidth <= availableWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        int period = textWidth + MARQUEE_GAP;
        int offset = (int) ((System.currentTimeMillis() / MARQUEE_MILLIS_PER_PIXEL + phase) % period);
        if (viewport == null) {
            graphics.enableScissor(x, y - 1, x + availableWidth, y + 10);
        } else {
            viewport.enableScissor(graphics, x, y - 1, x + availableWidth, y + 10);
        }
        graphics.drawString(font, text, x - offset, y, color, false);
        graphics.drawString(font, text, x - offset + period, y, color, false);
        graphics.disableScissor();
    }

    public static void drawBackButton(GuiGraphics graphics, Font font, int screenWidth,
                                      int mouseX, int mouseY) {
        int x = screenWidth - 30;
        boolean hovered = mouseX >= x && mouseX < x + 22 && mouseY >= 8 && mouseY < 30;
        graphics.fill(x, 8, x + 22, 30, hovered ? 0xFF5B7077 : 0xCC2C3B40);
        graphics.drawCenteredString(font, "×", x + 11, 15, 0xFFF2F6F4);
    }

    public static boolean backButtonClicked(double mouseX, double mouseY, int screenWidth) {
        int x = screenWidth - 30;
        return mouseX >= x && mouseX < x + 22 && mouseY >= 8 && mouseY < 30;
    }

    public static void drawOpeningFade(GuiGraphics graphics, int width, int height, long openedAt) {
        long elapsed = System.currentTimeMillis() - openedAt;
        if (elapsed >= 180L) return;
        int alpha = (int) (150L * (180L - Math.max(0L, elapsed)) / 180L);
        graphics.fill(0, 0, width, height, alpha << 24);
    }
}
