package com.xtdpotato.xero_delta.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Top-layer item name and ammunition text for Delta loadout cards. */
public final class LoadoutLabelRenderer {
    private static final float BASE_TEXT_SCALE = 0.62F;
    private static final float LABEL_Z = 650.0F;

    private LoadoutLabelRenderer() {
    }

    public static void render(GuiGraphics graphics, Font font, ItemStack stack,
                              String keyLabel, int x, int y, int width, int height) {
        float scale = BASE_TEXT_SCALE * StatusEffectHudState.inventoryLabelScale();
        int padding = 3;
        int keyAdvance = keyLabel == null || keyLabel.isBlank()
            ? 0 : 17;
        if (keyAdvance > 0) {
            renderKeyCap(graphics, font, keyLabel, x + padding, y + 2, 1.0F);
        }
        if (stack == null || stack.isEmpty()) return;
        int textX = x + padding + keyAdvance;
        int available = Math.max(1, width - padding - (textX - x));
        String ammunition = TaczLoadoutText.ammunitionName(stack);
        int logicalWidth = Math.max(1, (int) Math.floor(available / scale));
        int logicalHeight = Math.max(font.lineHeight,
            (int) Math.floor(Math.max(1, height - padding * 2) / scale));
        int maximumLines = Math.max(1, logicalHeight / font.lineHeight);
        List<FormattedCharSequence> nameLines = font.split(
            stack.getHoverName(), logicalWidth);
        List<FormattedCharSequence> ammunitionLines = ammunition.isBlank()
            ? List.of() : font.split(Component.literal(ammunition), logicalWidth);
        int ammunitionLimit = ammunitionLines.isEmpty() ? 0
            : Math.min(ammunitionLines.size(), Math.max(0, maximumLines - 1));
        int nameLimit = Math.min(nameLines.size(), maximumLines - ammunitionLimit);

        graphics.pose().pushPose();
        graphics.pose().translate(textX, y + padding, LABEL_Z);
        graphics.pose().scale(scale, scale, 1.0F);
        int line = drawLines(graphics, font, nameLines, nameLimit,
            0, 0xFFF1F4F2);
        drawLines(graphics, font, ammunitionLines,
            Math.min(ammunitionLines.size(), maximumLines - line), line,
            0xFF929C9A);
        graphics.pose().popPose();
    }

    /** F-interaction-style key cap used for the 1/2 weapon shortcuts. */
    public static void renderKeyCap(GuiGraphics graphics, Font font,
                                    String key, int x, int y, float scale) {
        if (key == null || key.isBlank() || scale <= 0.0F) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, LABEL_Z + 1.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.fill(0, 0, 14, 11, 0xFFE5E9E8);
        graphics.renderOutline(0, 0, 14, 11, 0xFF687275);
        graphics.drawString(font, key, 7 - font.width(key) / 2, 2,
            0xFF172023, false);
        graphics.pose().popPose();
    }

    private static int drawLines(GuiGraphics graphics, Font font,
                                 List<FormattedCharSequence> lines, int limit,
                                 int firstLine, int color) {
        int count = Math.min(limit, lines.size());
        for (int index = 0; index < count; index++) {
            graphics.drawString(font, lines.get(index), 0,
                (firstLine + index) * font.lineHeight, color, false);
        }
        return firstLine + count;
    }
}
