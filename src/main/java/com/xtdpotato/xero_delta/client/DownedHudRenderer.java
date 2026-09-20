package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Configurable downed countdown and control hints for both red and yellow stages. */
public final class DownedHudRenderer {
    private static final ResourceLocation MOUSE_LEFT = inputIcon("mouse_left.png");
    private static final ResourceLocation MOUSE_RIGHT = inputIcon("mouse_right.png");
    private static final ResourceLocation KEY_SPACE = inputIcon("key_space.png");
    private static final ResourceLocation KEY_E = inputIcon("key_e.png");
    private static final int WIDTH = 420;
    private static final int HEIGHT = 47;
    private static final int MAIN_WIDTH = 260;
    private static final int BAR_X = 0;
    private static final int BAR_Y = 22;
    private static final int BAR_WIDTH = MAIN_WIDTH;
    private static final int BAR_HEIGHT = 6;
    private static final int SWEEP_WIDTH = 56;

    private DownedHudRenderer() {
    }

    public static StatusEffectHudRenderer.Bounds render(GuiGraphics graphics,
                                                          Minecraft minecraft) {
        if (minecraft.player == null || !DownedClientState.INSTANCE.downed()) {
            return StatusEffectHudRenderer.Bounds.EMPTY;
        }
        boolean abandoning = DownedClientState.INSTANCE.redDown()
            && DownedAbandonClientState.INSTANCE.holding();
        float progress = abandoning ? DownedAbandonClientState.INSTANCE.progress()
            : DownedClientState.INSTANCE.progress();
        RescueRequestClientState.Sample request =
            RescueRequestClientState.INSTANCE.sample(minecraft.player.getUUID());
        return renderConfigured(graphics, minecraft, DownedClientState.INSTANCE.yellowDown(),
            abandoning, request.active(), request.progress(),
            DownedClientState.INSTANCE.remainingTicks(), progress);
    }

    public static StatusEffectHudRenderer.Bounds renderPreview(GuiGraphics graphics,
                                                                 Minecraft minecraft) {
        return renderConfigured(graphics, minecraft, true, false,
            false, 0.0F, 159 * 20, 0.72F);
    }

    private static StatusEffectHudRenderer.Bounds renderConfigured(GuiGraphics graphics,
                                                                     Minecraft minecraft,
                                                                     boolean yellow,
                                                                     boolean abandoning,
                                                                     boolean calling,
                                                                     float callingProgress,
                                                                     int remainingTicks,
                                                                     float progress) {
        float scale = StatusEffectHudState.downedScale();
        int scaledWidth = Math.round(WIDTH * scale);
        int scaledHeight = Math.round(HEIGHT * scale);
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int defaultX = (screenWidth - scaledWidth) / 2;
        int defaultY = screenHeight - scaledHeight - 12;
        int[] position = StatusEffectHudState.downedPosition(defaultX, defaultY,
            screenWidth, screenHeight, scaledWidth, scaledHeight);
        float opacity = StatusEffectHudState.downedOpacity();

        graphics.pose().pushPose();
        graphics.pose().translate(position[0], position[1], 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);

        Component status = calling
            ? Component.translatable("downed.xero_delta.calling")
            : abandoning
                ? Component.translatable("downed.xero_delta.abandon_holding")
                : Component.literal(Component.translatable("downed.xero_delta.waiting").getString()
                    + "  " + DownedHudMath.formatTime(remainingTicks));
        graphics.drawCenteredString(minecraft.font, status, MAIN_WIDTH / 2, 4,
            alpha(0xFFF2F4F3, opacity));

        drawGlassBar(graphics, opacity);
        int filled = filledWidth(progress);
        if (filled > 0) {
            int color = abandoning ? 0xFFD23B3B
                : yellow ? 0xFFB8C0BD : 0xFFE8EBE9;
            graphics.fill(BAR_X, BAR_Y, BAR_X + filled, BAR_Y + BAR_HEIGHT,
                alpha(color, opacity));
        }
        if (calling) drawCallingSweep(graphics, callingProgress, opacity);

        if (yellow) {
            drawHint(graphics, minecraft, MOUSE_LEFT,
                "downed.xero_delta.request", 275, calling, opacity);
            drawHint(graphics, minecraft, MOUSE_RIGHT,
                "downed.xero_delta.switch_spectator", 327, false, opacity);
            drawHint(graphics, minecraft, KEY_E,
                "downed.xero_delta.exit_spectator", 379, false, opacity);
        } else {
            drawHint(graphics, minecraft, MOUSE_LEFT,
                "downed.xero_delta.request", 292, calling, opacity);
            drawHint(graphics, minecraft, KEY_SPACE,
                "downed.xero_delta.abandon", 371, abandoning, opacity);
        }
        graphics.pose().popPose();
        return new StatusEffectHudRenderer.Bounds(position[0], position[1],
            scaledWidth, scaledHeight);
    }

    private static void drawGlassBar(GuiGraphics graphics, float opacity) {
        graphics.fill(BAR_X - 2, BAR_Y - 2, BAR_X + BAR_WIDTH + 2,
            BAR_Y + BAR_HEIGHT + 2, alpha(0x5A101518, opacity));
        graphics.fill(BAR_X, BAR_Y, BAR_X + BAR_WIDTH,
            BAR_Y + BAR_HEIGHT, alpha(0xB3262B2E, opacity));
        graphics.fill(BAR_X, BAR_Y, BAR_X + BAR_WIDTH,
            BAR_Y + 1, alpha(0x708B9496, opacity));
        graphics.renderOutline(BAR_X - 2, BAR_Y - 2, BAR_WIDTH + 4,
            BAR_HEIGHT + 4, alpha(0x8A697579, opacity));
    }

    private static void drawCallingSweep(GuiGraphics graphics, float progress,
                                         float opacity) {
        int start = DownedHudMath.sweepStart(progress, BAR_WIDTH, SWEEP_WIDTH);
        int from = Math.max(0, start);
        int to = Math.min(BAR_WIDTH, start + SWEEP_WIDTH);
        for (int pixel = from; pixel < to; pixel++) {
            int sweepAlpha = DownedHudMath.sweepAlpha(pixel, start, SWEEP_WIDTH);
            int adjusted = Math.round(sweepAlpha * opacity);
            graphics.fill(BAR_X + pixel, BAR_Y, BAR_X + pixel + 1,
                BAR_Y + BAR_HEIGHT, (adjusted << 24) | 0x00F6FAF8);
        }
    }

    private static void drawHint(GuiGraphics graphics, Minecraft minecraft,
                                 ResourceLocation icon, String key, int centerX,
                                 boolean active, float opacity) {
        Component label = Component.translatable(key);
        int iconSize = 18;
        int gap = 4;
        int contentWidth = iconSize + gap + minecraft.font.width(label);
        int x = centerX - contentWidth / 2;
        int y = 16;
        if (active) {
            graphics.fill(x - 6, y - 6, x + contentWidth + 6, y + 24,
                alpha(0x32151A1C, opacity));
            graphics.renderOutline(x - 6, y - 6, contentWidth + 12, 30,
                alpha(0x7A737E82, opacity));
        }
        drawIcon(graphics, icon, x, y - 3, iconSize, opacity);
        graphics.drawString(minecraft.font, label, x + iconSize + gap, y + 2,
            alpha(0xFFE9EDEB, opacity), false);
    }

    private static void drawIcon(GuiGraphics graphics, ResourceLocation icon,
                                 int x, int y, int size, float opacity) {
        graphics.setColor(1.0F, 1.0F, 1.0F, opacity);
        graphics.blit(icon, x, y, size, size, 0, 0, 32, 32, 32, 32);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    static int filledWidth(float progress) {
        return DownedHudMath.filledWidth(progress, BAR_WIDTH);
    }

    static String formatTime(int remainingTicks) {
        return DownedHudMath.formatTime(remainingTicks);
    }

    private static int alpha(int color, float opacity) {
        int source = color >>> 24;
        int adjusted = Math.max(0, Math.min(255, Math.round(source * opacity)));
        return (color & 0x00FFFFFF) | (adjusted << 24);
    }

    private static ResourceLocation inputIcon(String file) {
        return ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID,
            "textures/gui/input/" + file);
    }
}
