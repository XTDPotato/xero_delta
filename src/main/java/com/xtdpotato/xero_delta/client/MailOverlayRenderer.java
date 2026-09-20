package com.xtdpotato.xero_delta.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.xtdpotato.xero_delta.screen.material.Material2Icon;

/** Independent mailbox button rendered beside the safety-box overlay. */
public final class MailOverlayRenderer {
    public static final int SIZE = 22;
    private MailOverlayRenderer() {}

    public static void render(GuiGraphics g, Font font, int x, int y, int unread, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
        g.fill(x, y, x + SIZE, y + SIZE, hovered ? 0xF05B7078 : 0xE1324147);
        border(g, x, y, SIZE, SIZE, unread > 0 ? 0xFFFFC857 : 0xFF71848A);
        Material2Icon.MAIL.render(g, x + SIZE / 2, y + SIZE / 2,
            hovered ? 0xFFFFFFFF : 0xFFE2F0EA);
        if (unread > 0) {
            String badge = unread > 99 ? "99+" : String.valueOf(unread);
            int badgeWidth = Math.max(10, font.width(badge) + 4);
            g.fill(x + SIZE - badgeWidth + 4, y - 4, x + SIZE + 4, y + 7, 0xFFE04E43);
            g.drawCenteredString(font, badge, x + SIZE - badgeWidth / 2 + 4, y - 2, 0xFFFFFFFF);
        }
        if (hovered) g.renderTooltip(font, Component.translatable("mail.xero_delta.open", unread), mouseX, mouseY);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
