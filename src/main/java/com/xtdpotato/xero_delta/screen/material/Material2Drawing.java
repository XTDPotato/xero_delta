package com.xtdpotato.xero_delta.screen.material;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Small dependency-free drawing primitives used by the shared Material 3 widgets. */
public final class Material2Drawing {
    private Material2Drawing() {
    }

    public static void roundedRect(GuiGraphics graphics, int x, int y, int width,
                                   int height, int radius, int color) {
        roundedRect(graphics, (float) x, (float) y, (float) width, (float) height,
            (float) radius, color);
    }

    public static void roundedRect(GuiGraphics graphics, float x, float y, float width,
                                   float height, float radius, int color) {
        int left = Math.round(x);
        int top = Math.round(y);
        int right = Math.round(x + width);
        int bottom = Math.round(y + height);
        if (right <= left || bottom <= top || (color >>> 24) == 0) return;
        int r = Math.max(0, Math.min(Math.round(radius), Math.min(right - left, bottom - top) / 2));
        for (int py = top; py < bottom; py++) {
            int inset = rowInset(py, top, bottom, r);
            graphics.fill(left + inset, py, right - inset, py + 1, color);
        }
    }

    /** Draws rounded geometry at the physical GUI resolution before scaling it back. */
    public static void smoothRoundedRect(GuiGraphics graphics, int x, int y, int width,
                                         int height, int radius, int color) {
        int scale = Math.max(1, Math.min(4,
            (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale())));
        if (scale == 1) {
            roundedRect(graphics, x, y, width, height, radius, color);
            return;
        }
        graphics.pose().pushPose();
        float inverse = 1.0F / scale;
        graphics.pose().scale(inverse, inverse, 1.0F);
        roundedRect(graphics, x * scale, y * scale, width * scale,
            height * scale, radius * scale, color);
        graphics.pose().popPose();
    }

    public static void smoothRoundedPanel(GuiGraphics graphics, int x, int y, int width,
                                          int height, int radius, int border, int surface) {
        smoothRoundedRect(graphics, x, y, width, height, radius, border);
        if (width > 2 && height > 2) {
            smoothRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2,
                Math.max(0, radius - 1), surface);
        }
    }

    public static void circle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        circle(graphics, (float) centerX, (float) centerY, (float) radius, color);
    }

    public static void circle(GuiGraphics graphics, float centerX, float centerY,
                              float radius, int color) {
        int r = Math.max(0, Math.round(radius));
        if (r == 0) return;
        int top = Math.round(centerY) - r;
        int bottom = Math.round(centerY) + r;
        for (int py = top; py < bottom; py++) {
            double dy = py + 0.5D - centerY;
            int half = (int) Math.floor(Math.sqrt(Math.max(0.0D, radius * radius - dy * dy)));
            graphics.fill(Math.round(centerX) - half, py, Math.round(centerX) + half + 1, py + 1, color);
        }
    }

    public static void line(GuiGraphics graphics, float x1, float y1, float x2, float y2,
                            float thickness, int color) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        if (length <= 0.001D || thickness <= 0.0F) return;
        int steps = Math.max(1, (int) Math.ceil(length));
        float radius = Math.max(0.5F, thickness / 2.0F);
        for (int step = 0; step <= steps; step++) {
            float progress = step / (float) steps;
            circle(graphics, x1 + (x2 - x1) * progress,
                y1 + (y2 - y1) * progress, radius, color);
        }
    }

    public static void clippedCircle(GuiGraphics graphics, float centerX, float centerY,
                                     float circleRadius, float x, float y, float width,
                                     float height, float cornerRadius, int color) {
        rasterCircle(graphics, centerX, centerY, circleRadius, circleRadius,
            x, y, width, height, cornerRadius, color);
    }

    public static void clippedRadialCircle(GuiGraphics graphics, float centerX, float centerY,
                                           float innerRadius, float outerRadius, float x, float y,
                                           float width, float height, float cornerRadius, int color) {
        rasterCircle(graphics, centerX, centerY, Math.max(0.0F, innerRadius),
            Math.max(innerRadius, outerRadius), x, y, width, height, cornerRadius, color);
    }

    public static void outlineRoundedRect(GuiGraphics graphics, int x, int y, int width,
                                          int height, int radius, float thickness, int color) {
        int amount = Math.max(1, Math.round(thickness));
        int right = x + width;
        int bottom = y + height;
        int innerLeft = x + amount;
        int innerTop = y + amount;
        int innerRight = right - amount;
        int innerBottom = bottom - amount;
        int outerRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        int innerRadius = Math.max(0, outerRadius - amount);
        for (int py = y; py < bottom; py++) {
            int runStart = Integer.MIN_VALUE;
            for (int px = x; px < right; px++) {
                boolean outer = insideRounded(px + 0.5F, py + 0.5F,
                    x, y, width, height, outerRadius);
                boolean inner = innerRight > innerLeft && innerBottom > innerTop
                    && insideRounded(px + 0.5F, py + 0.5F, innerLeft, innerTop,
                    innerRight - innerLeft, innerBottom - innerTop, innerRadius);
                boolean draw = outer && !inner;
                if (draw && runStart == Integer.MIN_VALUE) runStart = px;
                if ((!draw || px == right - 1) && runStart != Integer.MIN_VALUE) {
                    int runEnd = draw && px == right - 1 ? px + 1 : px;
                    graphics.fill(runStart, py, runEnd, py + 1, color);
                    runStart = Integer.MIN_VALUE;
                }
            }
        }
    }

    public static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | color & 0x00FFFFFF;
    }

    private static int rowInset(int py, int top, int bottom, int radius) {
        if (radius <= 0) return 0;
        double centerY;
        if (py < top + radius) centerY = top + radius;
        else if (py >= bottom - radius) centerY = bottom - radius;
        else return 0;
        double dy = py + 0.5D - centerY;
        return Math.max(0, radius - (int) Math.floor(
            Math.sqrt(Math.max(0.0D, radius * radius - dy * dy))));
    }

    private static boolean insideRounded(float px, float py, float x, float y,
                                         float width, float height, float radius) {
        if (px < x || py < y || px >= x + width || py >= y + height) return false;
        float r = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));
        float cx = Math.max(x + r, Math.min(x + width - r, px));
        float cy = Math.max(y + r, Math.min(y + height - r, py));
        float dx = px - cx;
        float dy = py - cy;
        return dx * dx + dy * dy <= r * r + 0.01F;
    }

    private static void rasterCircle(GuiGraphics graphics, float centerX, float centerY,
                                     float innerRadius, float outerRadius, float x, float y,
                                     float width, float height, float cornerRadius, int color) {
        if (outerRadius <= 0.0F || width <= 0.0F || height <= 0.0F) return;
        int left = (int) Math.floor(x);
        int top = (int) Math.floor(y);
        int right = (int) Math.ceil(x + width);
        int bottom = (int) Math.ceil(y + height);
        int baseAlpha = color >>> 24;
        for (int py = top; py < bottom; py++) for (int px = left; px < right; px++) {
            if (!insideRounded(px + 0.5F, py + 0.5F, x, y, width, height, cornerRadius)) continue;
            double distance = Math.hypot(px + 0.5D - centerX, py + 0.5D - centerY);
            if (distance > outerRadius) continue;
            float fade = outerRadius <= innerRadius || distance <= innerRadius ? 1.0F
                : 1.0F - (float) ((distance - innerRadius) / (outerRadius - innerRadius));
            graphics.fill(px, py, px + 1, py + 1,
                alpha(color, Math.round(baseAlpha * Math.max(0.0F, Math.min(1.0F, fade)))));
        }
    }
}

