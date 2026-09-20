package com.xtdpotato.xero_delta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared two-stage screen transition. The backdrop is revealed first, then
 * the screen controls enter from the right.
 */
public final class ScreenTransition {
    public static final long BACKGROUND_MILLIS = 70L;
    public static final long CONTENT_MILLIS = 250L;
    public static final long DURATION_MILLIS = BACKGROUND_MILLIS + CONTENT_MILLIS;
    private final long openedAt = System.currentTimeMillis();
    private final boolean enabled;
    private long closingAt;
    private Runnable closeAction;
    private boolean completed;

    public ScreenTransition() {
        this(true);
    }

    public ScreenTransition(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean closing() {
        return closingAt > 0L;
    }

    public void beginClose(Runnable action) {
        if (!enabled) {
            if (action != null) action.run();
            return;
        }
        if (closing()) return;
        closingAt = System.currentTimeMillis();
        closeAction = action;
    }

    public void tick(Minecraft minecraft) {
        if (!closing() || completed
            || System.currentTimeMillis() - closingAt < DURATION_MILLIS) return;
        completed = true;
        Runnable action = closeAction;
        closeAction = null;
        if (action != null) action.run();
    }

    public void push(GuiGraphics graphics) {
        if (!enabled) {
            graphics.pose().pushPose();
            return;
        }
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int backgroundAlpha = backgroundAlpha();
        if (backgroundAlpha > 0) {
            graphics.pose().pushPose();
            graphics.pose().last().pose().identity();
            graphics.fill(0, 0, width, height,
                backgroundAlpha << 24 | 0x00080D0F);
            graphics.pose().popPose();
        }
        pushContent(graphics);
    }

    /** Applies only the moving content transform without redrawing the backdrop. */
    public void pushContent(GuiGraphics graphics) {
        graphics.pose().pushPose();
        if (enabled) graphics.pose().translate(horizontalOffset(), 0.0F, 0.0F);
    }

    public void pop(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    public void drawFade(GuiGraphics graphics, int width, int height) {
        // Kept for source compatibility. The backdrop is drawn before the
        // transformed content so it remains stationary while controls enter.
    }

    public double horizontalOffset() {
        if (!enabled) return 0.0D;
        long now = System.currentTimeMillis();
        int width = Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        if (closing()) {
            double progress = clamp((now - closingAt) / (double) CONTENT_MILLIS);
            return Math.round((width + 24.0D) * easeInCubic(progress));
        }
        long elapsed = now - openedAt;
        if (elapsed <= BACKGROUND_MILLIS) return width + 24.0D;
        double progress = clamp((elapsed - BACKGROUND_MILLIS) / (double) CONTENT_MILLIS);
        return Math.round((width + 24.0D) * (1.0D - easeOutCubic(progress)));
    }

    private int backgroundAlpha() {
        if (!enabled) return 0;
        long now = System.currentTimeMillis();
        if (closing()) {
            long fadeElapsed = now - closingAt - CONTENT_MILLIS;
            if (fadeElapsed <= 0L) return 232;
            double progress = clamp(fadeElapsed / (double) BACKGROUND_MILLIS);
            return (int) Math.round(232.0D * (1.0D - easeOutCubic(progress)));
        }
        double progress = clamp((now - openedAt) / (double) BACKGROUND_MILLIS);
        return (int) Math.round(232.0D * easeOutCubic(progress));
    }

    private static double easeInCubic(double value) {
        return value * value * value;
    }

    private static double easeOutCubic(double value) {
        double remaining = 1.0D - value;
        return 1.0D - remaining * remaining * remaining;
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
