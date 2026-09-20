package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;


/** Shared capacity header and hover-only help used by every Delta storage surface. */
public final class StorageSectionHeaderRenderer {
    private static final int TEXT = 0xFFE5EAE8;
    private static final int MUTED = 0xFF8E9A98;
    private static final ResourceLocation INFO = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "textures/gui/action/info.png");

    private StorageSectionHeaderRenderer() {
    }

    /**
     * Draws {@code name: used/total ?}. The total part is deliberately two
     * pixels smaller than the normal Minecraft font. Returns the hovered help
     * translation key, or {@code null} when the help diamond is not hovered.
     */
    public static String render(GuiGraphics graphics, Font font, Component name,
                                int used, int total, String helpKey,
                                int x, int y, float scale,
                                double mouseX, double mouseY) {
        float safeScale = Math.max(0.25F, scale);
        float suffixScale = safeScale * 7.0F / 9.0F;
        String prefix = name.getString() + ": " + Math.max(0, used);
        String suffix = "/" + Math.max(0, total);

        drawScaled(graphics, font, prefix, x, y, safeScale, TEXT);
        int prefixWidth = Math.round(font.width(prefix) * safeScale);
        int suffixX = x + prefixWidth;
        int suffixY = y + Math.max(1, Math.round(2.0F * safeScale));
        drawScaled(graphics, font, suffix, suffixX, suffixY, suffixScale, MUTED);

        int suffixWidth = Math.round(font.width(suffix) * suffixScale);
        int iconSize = Math.max(8, Math.round(10.0F * safeScale));
        int iconX = suffixX + suffixWidth + Math.max(3, Math.round(4.0F * safeScale));
        int iconY = y - Math.max(0, Math.round(1.0F * safeScale));
        boolean hovered = mouseX >= iconX && mouseX < iconX + iconSize
            && mouseY >= iconY && mouseY < iconY + iconSize;
        drawHelpIcon(graphics, iconX, iconY, iconSize, hovered);
        return hovered ? helpKey : null;
    }

    /** Draws a section title followed by the shared PNG help icon. */
    public static String renderTitleWithHelp(GuiGraphics graphics, Font font,
                                             Component title, String helpKey,
                                             int x, int y, float scale,
                                             double mouseX, double mouseY) {
        float safeScale = Math.max(0.25F, scale);
        String text = title.getString();
        drawScaled(graphics, font, text, x, y, safeScale, TEXT);
        int iconSize = Math.max(8, Math.round(10.0F * safeScale));
        int iconX = x + Math.round(font.width(text) * safeScale)
            + Math.max(3, Math.round(4.0F * safeScale));
        int iconY = y - Math.max(0, Math.round(1.0F * safeScale));
        boolean hovered = mouseX >= iconX && mouseX < iconX + iconSize
            && mouseY >= iconY && mouseY < iconY + iconSize;
        drawHelpIcon(graphics, iconX, iconY, iconSize, hovered);
        return hovered ? helpKey : null;
    }

    public static void renderTooltip(GuiGraphics graphics, Font font,
                                     String translationKey, int mouseX, int mouseY) {
        if (translationKey == null || translationKey.isBlank()) return;
        List<FormattedCharSequence> lines = font.split(
            Component.translatable(translationKey), 250);
        if (lines.isEmpty()) return;
        int textWidth = 0;
        for (FormattedCharSequence line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        int lineHeight = Math.max(10, font.lineHeight + 1);
        int boxWidth = textWidth + 8;
        int boxHeight = lines.size() * lineHeight + 8;
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = mouseX + 12;
        if (x + boxWidth > screenWidth - 4) x = mouseX - boxWidth - 12;
        x = Math.max(4, Math.min(x, Math.max(4, screenWidth - boxWidth - 4)));
        int y = Math.max(4, Math.min(mouseY - 12,
            Math.max(4, screenHeight - boxHeight - 4)));

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, ScreenLayerResolver.tooltip());
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF0100714);
        graphics.renderOutline(x, y, boxWidth, boxHeight, 0xFF6D248E);
        if (boxWidth > 2 && boxHeight > 2) {
            graphics.renderOutline(x + 1, y + 1,
                boxWidth - 2, boxHeight - 2, 0xFF2A1235);
        }
        graphics.pose().translate(0.0F, 0.0F, 1.0F);
        int textY = y + 4;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x + 4, textY, TEXT, false);
            textY += lineHeight;
        }
        graphics.pose().popPose();
    }

    public static int usedCells(GridBackingStore store) {
        if (store == null) return 0;
        int used = 0;
        for (int row = 0; row < store.getHeight(); row++) {
            for (int column = 0; column < store.getWidth(); column++) {
                if (store.isCellOccupied(column, row)) used++;
            }
        }
        return used;
    }

    public static int usedSlots(IItemHandler handler) {
        if (handler == null) return 0;
        int used = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) used++;
        }
        return used;
    }

    private static void drawScaled(GuiGraphics graphics, Font font, String text,
                                   int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 420.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawHelpIcon(GuiGraphics graphics,
                                     int x, int y, int size, boolean hovered) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 425.0F);
        graphics.setColor(1.0F, 1.0F, 1.0F, hovered ? 1.0F : 0.78F);
        graphics.blit(INFO, x, y, size, size, 0, 0, 16, 16, 16, 16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }
}
