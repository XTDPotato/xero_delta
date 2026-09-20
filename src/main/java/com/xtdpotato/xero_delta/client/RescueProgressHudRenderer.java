package com.xtdpotato.xero_delta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Configurable rescue progress shown to both the rescuer and the rescued player. */
public final class RescueProgressHudRenderer {
    private static final int WIDTH = 196;
    private static final int HEIGHT = 25;
    private static final int BAR_X = 7;
    private static final int BAR_Y = 17;
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;

    private RescueProgressHudRenderer() {
    }

    public static StatusEffectHudRenderer.Bounds render(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.player == null || (!DownedClientState.INSTANCE.beingRescued()
            && !DownedClientState.INSTANCE.rescuing())) {
            return StatusEffectHudRenderer.Bounds.EMPTY;
        }
        String name = DownedClientState.INSTANCE.rescuerName().isBlank()
            ? Component.translatable("hud.xero_delta.rescue.unknown").getString()
            : DownedClientState.INSTANCE.rescuerName();
        return renderConfigured(graphics, minecraft, name,
            DownedClientState.INSTANCE.rescueRemainingSeconds(),
            DownedClientState.INSTANCE.rescueProgress(),
            DownedClientState.INSTANCE.rescuing()
                && !DownedClientState.INSTANCE.beingRescued());
    }

    public static StatusEffectHudRenderer.Bounds renderPreview(GuiGraphics graphics,
                                                                 Minecraft minecraft) {
        return renderConfigured(graphics, minecraft,
            Component.translatable("status_effect_hud.xero_delta.preview_rescuer_name").getString(),
            7.4F, 0.51F, false);
    }

    private static StatusEffectHudRenderer.Bounds renderConfigured(GuiGraphics graphics,
                                                                     Minecraft minecraft,
                                                                     String rescuerName,
                                                                     float remainingSeconds,
                                                                     float progress,
                                                                     boolean rescuerView) {
        float scale = StatusEffectHudState.rescueScale();
        int scaledWidth = Math.round(WIDTH * scale);
        int scaledHeight = Math.round(HEIGHT * scale);
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int defaultX = (screenWidth - scaledWidth) / 2;
        int defaultY = Math.max(28, Math.round(screenHeight * 0.70F));
        int[] position = StatusEffectHudState.rescuePosition(defaultX, defaultY,
            screenWidth, screenHeight, scaledWidth, scaledHeight);
        float opacity = StatusEffectHudState.rescueOpacity();

        graphics.pose().pushPose();
        graphics.pose().translate(position[0], position[1], 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        Component label = Component.translatable(rescuerView
                ? "hud.xero_delta.rescue.rescuing"
                : "hud.xero_delta.rescue.progress",
            rescuerName, String.format(Locale.ROOT, "%.1f", Math.max(0.0F, remainingSeconds)));
        graphics.drawCenteredString(minecraft.font, label, WIDTH / 2, 3,
            alpha(0xFFF2F4F3, opacity));
        graphics.fill(BAR_X - 1, BAR_Y - 1, BAR_X + BAR_WIDTH + 1,
            BAR_Y + BAR_HEIGHT + 1, alpha(0xC8192225, opacity));
        int filled = Math.round(BAR_WIDTH * Math.max(0.0F, Math.min(1.0F, progress)));
        if (filled > 0) {
            graphics.fill(BAR_X, BAR_Y, BAR_X + filled, BAR_Y + BAR_HEIGHT,
                alpha(0xFFE9EEE9, opacity));
        }
        graphics.renderOutline(BAR_X - 2, BAR_Y - 2, BAR_WIDTH + 4,
            BAR_HEIGHT + 4, alpha(0xA0667377, opacity));
        graphics.pose().popPose();
        return new StatusEffectHudRenderer.Bounds(position[0], position[1],
            scaledWidth, scaledHeight);
    }

    private static int alpha(int color, float opacity) {
        int source = color >>> 24;
        int adjusted = Math.max(0, Math.min(255, Math.round(source * opacity)));
        return (color & 0x00FFFFFF) | (adjusted << 24);
    }
}
