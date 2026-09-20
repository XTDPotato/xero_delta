package com.xtdpotato.xero_delta.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.xtdpotato.xero_delta.screen.material.Material2Icon;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import java.math.BigDecimal;

/** Compact draggable summary that opens mail or the full character/health screen. */
public final class PlayerStatusPanelRenderer {
    public static final int WIDTH = 154;
    public static final int HEIGHT = 94;
    public static final int HEADER_HEIGHT = 28;
    public static final int COLLAPSED_WIDTH = 32;
    public static final int COLLAPSED_HEIGHT = 22;
    public static final int COMPACT_WIDTH = CreativeRuleEditorLayout.WIDTH;
    public static final int COMPACT_HEIGHT = 42;

    private PlayerStatusPanelRenderer() {
    }

    public static Layout renderCompact(GuiGraphics graphics, Font font, int x, int y,
                                        int mouseX, int mouseY) {
        Material3Theme.refreshFromConfig();
        var status = PlayerStatusClientState.INSTANCE;
        graphics.fill(x, y, x + COMPACT_WIDTH, y + COMPACT_HEIGHT, Material3Theme.SURFACE_CONTAINER);
        border(graphics, x, y, COMPACT_WIDTH, COMPACT_HEIGHT, Material3Theme.OUTLINE_VARIANT);
        Material2Icon.MAIL.render(graphics, x + 13, y + 10, Material3Theme.PRIMARY);
        if (MailClientState.INSTANCE.unreadCount() > 0) {
            graphics.fill(x + 18, y + 3, x + 21, y + 6, Material3Theme.ERROR);
        }
        graphics.drawString(font, Component.translatable("status.xero_delta.panel_title"),
            x + 26, y + 6, Material3Theme.TEXT, false);
        int foldX = x + COMPACT_WIDTH - 22;
        graphics.drawCenteredString(font, "-", foldX + 8, y + 5, Material3Theme.TEXT);
        String weight = font.plainSubstrByWidth(formatWeight(status.weightKg()) + " / 88KG", COMPACT_WIDTH / 2);
        int weightWidth = font.width(weight);
        int balanceWidth = COMPACT_WIDTH - weightWidth - 26;
        graphics.enableScissor(x + 5, y + 22, x + 5 + Math.max(1, balanceWidth), y + 36);
        TradingUi.drawBalance(graphics, font, status.balance(), x + 7, y + 24, Material3Theme.TEXT);
        graphics.disableScissor();
        graphics.drawString(font, weight, x + COMPACT_WIDTH - weightWidth - 8, y + 24,
            status.overloaded() ? Material3Theme.ERROR : Material3Theme.TEXT_MUTED, false);
        if (inside(mouseX, mouseY, x + 5, y + 22, balanceWidth, 14)) {
            graphics.renderTooltip(font, Component.literal(TradingUi.formatDetailed(status.balance())), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, x + 5, y + 21, COMPACT_WIDTH - 10, 17)) {
            graphics.renderTooltip(font, Component.translatable("status.xero_delta.open_detail"), mouseX, mouseY);
        }
        return new Layout(x, y, COMPACT_WIDTH, COMPACT_HEIGHT, x + 5, y + 2, 16, 16,
            foldX, y + 2, 16, 16, false);
    }

    public static Layout render(GuiGraphics graphics, Font font, int x, int y,
                                int mouseX, int mouseY, boolean collapsed) {
        if (collapsed) return renderCollapsed(graphics, font, x, y, mouseX, mouseY);
        PlayerStatusClientState status = PlayerStatusClientState.INSTANCE;
        boolean hovered = inside(mouseX, mouseY, x, y, WIDTH, HEIGHT);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, hovered ? 0xF0253034 : 0xED1B2427);
        border(graphics, x, y, WIDTH, HEIGHT, hovered ? 0xFF72868D : 0xFF46575D);
        graphics.fill(x, y, x + WIDTH, y + HEADER_HEIGHT, 0xF02A363A);
        graphics.fill(x, y + HEADER_HEIGHT - 1, x + WIDTH, y + HEADER_HEIGHT, 0xFF55C99A);

        int mailX = x + 4;
        int mailY = y + 3;
        MailOverlayRenderer.render(graphics, font, mailX, mailY,
            MailClientState.INSTANCE.unreadCount(), mouseX, mouseY);
        graphics.drawString(font, Component.translatable("status.xero_delta.panel_title"),
            x + 32, y + 10, 0xFFF0F4F2, false);
        int foldX = x + WIDTH - 23;
        boolean foldHovered = inside(mouseX, mouseY, foldX, y + 5, 18, 18);
        graphics.fill(foldX, y + 5, foldX + 18, y + 23, foldHovered ? 0xFF53666D : 0x66344247);
        graphics.drawCenteredString(font, "-", foldX + 9, y + 10, 0xFFF0F4F2);

        TradingUi.drawBalance(graphics, font, status.balance(), x + 8, y + 34, 0xFFE8ECEA);
        int balanceWidth = TradingUi.balanceWidth(font, status.balance());
        if (inside(mouseX, mouseY, x + 8, y + 32, balanceWidth, 13)) {
            graphics.renderTooltip(font, Component.literal(TradingUi.formatDetailed(status.balance())), mouseX, mouseY);
        }
        String weight = formatWeight(status.weightKg()) + " / 88KG";
        int weightColor = status.overloaded() ? 0xFFFF665C
            : status.encumbered() ? 0xFFFFC857 : 0xFFB8D7CB;
        graphics.drawString(font, weight, x + 8, y + 49, weightColor, false);
        int barX = x + 8;
        int barY = y + 61;
        int barWidth = WIDTH - 16;
        graphics.fill(barX, barY, barX + barWidth, barY + 4, 0xFF101719);
        int fill = (int) Math.round(barWidth * Math.min(1.0D, status.weightKg() / 88.0D));
        graphics.fill(barX, barY, barX + fill, barY + 4, weightColor);

        PlayerStatusUi.Part[] parts = PlayerStatusUi.Part.values();
        int iconY = y + 70;
        for (int index = 0; index < parts.length; index++) {
            int iconX = x + 8 + index * 19;
            float value = PlayerStatusUi.value(status, parts[index]);
            boolean partHovered = inside(mouseX, mouseY, iconX, iconY, 17, 17);
            PlayerStatusUi.drawIcon(graphics, iconX, iconY, 17, parts[index], value, partHovered);
            if (partHovered) PlayerStatusUi.renderTooltip(
                graphics, font, parts[index], value, mouseX, mouseY);
        }
        /*

        String[] labels = {"头", "胸", "左臂", "右臂", "左腿", "右腿", "全"};
        byte[] values = {status.head(), status.chest(), status.leftArm(), status.rightArm(),
            status.leftLeg(), status.rightLeg(), status.wholeBody()};
        int cellWidth = 19;
        int healthX = x + 8;
        int healthY = y + 69;
        for (int index = 0; index < labels.length; index++) {
            int cellX = healthX + index * cellWidth;
            int color = injuryColor(values[index]);
            graphics.fill(cellX, healthY, cellX + 17, healthY + 14, 0xB0131B1E);
            border(graphics, cellX, healthY, 17, 14, color);
            graphics.drawCenteredString(font, labels[index], cellX + 8, healthY + 3, color);
        }
        */
        if (hovered && !foldHovered
            && !inside(mouseX, mouseY, mailX, mailY, MailOverlayRenderer.SIZE, MailOverlayRenderer.SIZE)
            && !inside(mouseX, mouseY, x + 8, y + 32, balanceWidth, 13)
            && !inside(mouseX, mouseY, x + 8, iconY, 7 * 19, 17)) {
            graphics.renderTooltip(font,
                Component.translatable("status.xero_delta.open_detail"), mouseX, mouseY);
        }
        return new Layout(x, y, WIDTH, HEIGHT, mailX, mailY, MailOverlayRenderer.SIZE,
            MailOverlayRenderer.SIZE, foldX, y + 5, 18, 18, false);
    }

    private static int injuryColor(byte value) {
        if (value >= 2) return 0xFFFF665C;
        if (value == 1) return 0xFFFFC857;
        return 0xFF72D6AD;
    }

    private static String formatWeight(double kilograms) {
        return BigDecimal.valueOf(kilograms).setScale(1, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString() + "KG";
    }

    private static Layout renderCollapsed(GuiGraphics graphics, Font font, int x, int y,
                                          int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, COLLAPSED_WIDTH, COLLAPSED_HEIGHT);
        graphics.fill(x, y, x + COLLAPSED_WIDTH, y + COLLAPSED_HEIGHT,
            hovered ? 0xF03C4C51 : 0xED1B2427);
        border(graphics, x, y, COLLAPSED_WIDTH, COLLAPSED_HEIGHT,
            hovered ? 0xFFF0F4F2 : 0xFF52656B);
        graphics.drawCenteredString(font, "...", x + COLLAPSED_WIDTH / 2, y + 6, 0xFFF0F4F2);
        if (hovered) graphics.renderTooltip(font,
            Component.translatable("status.xero_delta.open_detail"), mouseX, mouseY);
        return new Layout(x, y, COLLAPSED_WIDTH, COLLAPSED_HEIGHT,
            0, 0, 0, 0, 0, 0, 0, 0, true);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    public record Layout(int x, int y, int width, int height,
                         int mailX, int mailY, int mailWidth, int mailHeight,
                         int foldX, int foldY, int foldWidth, int foldHeight,
                         boolean collapsed) {
        public boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, x, y, width, height);
        }

        public boolean headerContains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, x, y, width,
                height == COMPACT_HEIGHT ? 20 : Math.min(height, HEADER_HEIGHT));
        }

        public boolean mailContains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, mailX, mailY, mailWidth, mailHeight);
        }

        public boolean foldContains(double mouseX, double mouseY) {
            return !collapsed && inside(mouseX, mouseY, foldX, foldY, foldWidth, foldHeight);
        }
    }
}
