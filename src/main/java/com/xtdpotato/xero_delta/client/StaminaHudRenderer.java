package com.xtdpotato.xero_delta.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Continuous Delta-style stamina bar shown only while the restricted layout is active. */
public final class StaminaHudRenderer {
    private static final int WIDTH = 164;
    private static final int HEIGHT = 5;
    private static final float ANIMATION_SECONDS = 0.16F;
    private static final float DAMAGE_TRAIL_SECONDS = 0.70F;
    private static final long RENDER_GAP_RESET_MS = 500L;
    private static float animatedFraction = -1.0F;
    private static float damageTrailFraction = -1.0F;
    private static long lastFrameMs;
    private static final VisibilityTracker VISIBILITY = new VisibilityTracker();

    private StaminaHudRenderer() {}

    public static StatusEffectHudRenderer.Bounds render(GuiGraphics graphics, Minecraft minecraft) {
        if (!PlayerStatusClientState.INSTANCE.layoutEnabled() || minecraft.player == null) {
            reset();
            return StatusEffectHudRenderer.Bounds.EMPTY;
        }
        long now = Util.getMillis();
        if (lastFrameMs > 0L && now - lastFrameMs > RENDER_GAP_RESET_MS) {
            animatedFraction = -1.0F;
            damageTrailFraction = -1.0F;
            VISIBILITY.reset();
        }
        float target = StaminaClientState.INSTANCE.fraction();
        update(target, now);
        if (!VISIBILITY.update(target, now)) {
            return StatusEffectHudRenderer.Bounds.EMPTY;
        }
        return renderBar(graphics, minecraft, animatedFraction, damageTrailFraction,
            StaminaClientState.INSTANCE.exhausted());
    }

    public static StatusEffectHudRenderer.Bounds renderPreview(GuiGraphics graphics,
                                                                 Minecraft minecraft) {
        return renderBar(graphics, minecraft, 0.35F, 0.35F, false);
    }

    private static StatusEffectHudRenderer.Bounds renderBar(GuiGraphics graphics,
                                                              Minecraft minecraft,
                                                              float fraction, float trailFraction,
                                                              boolean exhausted) {
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        float scale = StatusEffectHudState.staminaScale();
        int scaledWidth = Math.round(WIDTH * scale);
        int scaledHeight = Math.round((HEIGHT + 4) * scale);
        int defaultX = (screenWidth - scaledWidth) / 2;
        int defaultY = Math.max(12, screenHeight - 87);
        int[] position = StatusEffectHudState.staminaPosition(defaultX, defaultY,
            screenWidth, screenHeight, scaledWidth, scaledHeight);

        graphics.pose().pushPose();
        graphics.pose().translate(position[0], position[1], 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        float opacity = StatusEffectHudState.staminaOpacity();
        int x = 0;
        int y = 2;
        graphics.fill(x - 2, y - 2, x + WIDTH + 2, y + HEIGHT + 2,
            withOpacity(0x720A1012, opacity));
        graphics.renderOutline(x - 1, y - 1, WIDTH + 2, HEIGHT + 2,
            withOpacity(0xA06D7A7D, opacity));
        graphics.fill(x, y, x + WIDTH, y + HEIGHT,
            withOpacity(0x8F192225, opacity));
        int filled = Math.round(WIDTH * Math.max(0.0F, Math.min(1.0F, fraction)));
        int trailFilled = Math.round(WIDTH * Math.max(0.0F, Math.min(1.0F, trailFraction)));
        if (trailFilled > filled) {
            graphics.fill(x + filled, y, x + trailFilled, y + HEIGHT,
                withOpacity(0xFFE04A4F, opacity));
        }
        if (filled > 0) {
            int color = exhausted || fraction <= 0.25F ? 0xFFD94B50 : 0xFFE6ECE9;
            graphics.fill(x, y, x + filled, y + HEIGHT, withOpacity(color, opacity));
        }
        graphics.pose().popPose();
        return new StatusEffectHudRenderer.Bounds(position[0], position[1],
            scaledWidth, scaledHeight);
    }

    static float approach(float current, float target, float deltaSeconds) {
        float alpha = Math.max(0.0F, Math.min(1.0F, deltaSeconds / ANIMATION_SECONDS));
        return Math.max(0.0F, Math.min(1.0F, current + (target - current) * alpha));
    }

    static float damageTrailApproach(float current, float target, float deltaSeconds) {
        float alpha = Math.max(0.0F, Math.min(1.0F, deltaSeconds / DAMAGE_TRAIL_SECONDS));
        return Math.max(0.0F, Math.min(1.0F, current + (target - current) * alpha));
    }

    static int withOpacity(int color, float opacity) {
        int sourceAlpha = color >>> 24;
        int adjusted = Math.max(0, Math.min(255,
            Math.round(sourceAlpha * Math.max(0.0F, Math.min(1.0F, opacity)))));
        return adjusted << 24 | color & 0x00FFFFFF;
    }

    private static void update(float target, long now) {
        if (animatedFraction < 0.0F || lastFrameMs == 0L) {
            animatedFraction = target;
            damageTrailFraction = target;
            lastFrameMs = now;
            return;
        }
        float deltaSeconds = Math.min(0.10F,
            Math.max(0.0F, (now - lastFrameMs) / 1_000.0F));
        lastFrameMs = now;
        float previousTarget = animatedFraction;
        animatedFraction = approach(animatedFraction, target, deltaSeconds);
        // Keep a visible red trail whenever stamina drops, including sprint drain.
        if (target < previousTarget - 0.0001F) {
            damageTrailFraction = Math.max(damageTrailFraction, previousTarget);
        }
        damageTrailFraction = damageTrailApproach(damageTrailFraction, target, deltaSeconds);
        damageTrailFraction = Math.max(damageTrailFraction, animatedFraction);
    }

    private static void reset() {
        animatedFraction = -1.0F;
        damageTrailFraction = -1.0F;
        lastFrameMs = 0L;
        VISIBILITY.reset();
    }

    static final class VisibilityTracker {
        static final long FULL_IDLE_HIDE_DELAY_MS = 1_000L;
        private static final float EPSILON = 0.0001F;
        private float lastFraction = -1.0F;
        private long fullStableSinceMs = -1L;

        boolean update(float fraction, long nowMs) {
            float normalized = Math.max(0.0F, Math.min(1.0F, fraction));
            if (lastFraction < 0.0F || Math.abs(normalized - lastFraction) > EPSILON) {
                lastFraction = normalized;
                fullStableSinceMs = isFull(normalized) ? nowMs : -1L;
            }
            if (!isFull(normalized)) {
                fullStableSinceMs = -1L;
                return true;
            }
            if (fullStableSinceMs < 0L) fullStableSinceMs = nowMs;
            long stableForMs = Math.max(0L, nowMs - fullStableSinceMs);
            return stableForMs < FULL_IDLE_HIDE_DELAY_MS;
        }

        void reset() {
            lastFraction = -1.0F;
            fullStableSinceMs = -1L;
        }

        private static boolean isFull(float fraction) {
            return fraction >= 1.0F - EPSILON;
        }
    }
}
