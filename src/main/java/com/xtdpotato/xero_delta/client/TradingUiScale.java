package com.xtdpotato.xero_delta.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Local GUI-scale controls for the trading, recycling, and selling screens. */
public final class TradingUiScale {
    public static final int MIN_LEVEL = -2;
    public static final int MAX_LEVEL = 4;
    public static final int DEFAULT_LEVEL = 0;
    private static final int BUTTON_Y = 8;
    private static final int BUTTON_SIZE = 22;
    private static final int BUTTON_GAP = 4;
    private static final int CLOSE_BUTTON_X_OFFSET = 30;
    private static final int BUTTON_HEIGHT = 22;

    private TradingUiScale() {
    }

    public static int adjustLevel(int current, int direction) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, current + Integer.signum(direction)));
    }

    public static float factor(int level) {
        int clamped = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
        return switch (clamped) {
            case -2 -> 0.75F;
            case -1 -> 0.875F;
            case 1 -> 1.125F;
            case 2 -> 1.25F;
            case 3 -> 1.375F;
            case 4 -> 1.5F;
            default -> 1.0F;
        };
    }

    public static Viewport viewport(int physicalWidth, int physicalHeight, int level) {
        float scale = factor(level);
        int logicalWidth = Math.max(1, (int) Math.floor(physicalWidth / scale));
        int logicalHeight = Math.max(1, (int) Math.floor(physicalHeight / scale));
        int scaledWidth = Math.round(logicalWidth * scale);
        int scaledHeight = Math.round(logicalHeight * scale);
        return new Viewport(physicalWidth, physicalHeight, logicalWidth, logicalHeight, scale,
            (physicalWidth - scaledWidth) / 2, (physicalHeight - scaledHeight) / 2);
    }

    public static int adjustColumns(int current, int zoomDirection, int minimum, int maximum) {
        int next = current - Integer.signum(zoomDirection);
        return Math.max(minimum, Math.min(maximum, next));
    }

    public static void drawControls(GuiGraphics graphics, Font font, int screenWidth,
                                    int mouseX, int mouseY,
                                    boolean canZoomOut, boolean canZoomIn) {
        int minusX = minusX(screenWidth);
        int plusX = plusX(screenWidth);
        drawControl(graphics, font, minusX, "-", canZoomOut, mouseX, mouseY);
        drawControl(graphics, font, plusX, "+", canZoomIn, mouseX, mouseY);
    }

    public static boolean zoomOutClicked(double mouseX, double mouseY, int screenWidth) {
        return inside(mouseX, mouseY, minusX(screenWidth), BUTTON_Y, BUTTON_SIZE, BUTTON_HEIGHT);
    }

    public static boolean zoomInClicked(double mouseX, double mouseY, int screenWidth) {
        return inside(mouseX, mouseY, plusX(screenWidth), BUTTON_Y, BUTTON_SIZE, BUTTON_HEIGHT);
    }

    private static void drawControl(GuiGraphics graphics, Font font, int x, String label,
                                    boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, BUTTON_Y, BUTTON_SIZE, BUTTON_HEIGHT);
        graphics.fill(x, BUTTON_Y, x + BUTTON_SIZE, BUTTON_Y + BUTTON_HEIGHT,
            !enabled ? 0xCC202C30 : hovered ? 0xFF5B7077 : 0xCC2C3B40);
        graphics.renderOutline(x, BUTTON_Y, BUTTON_SIZE, BUTTON_HEIGHT,
            hovered ? 0xFFF2F6F4 : 0xFF52676E);
        graphics.drawCenteredString(font, label, x + BUTTON_SIZE / 2,
            BUTTON_Y + 7, enabled ? 0xFFF2F6F4 : 0xFF718084);
    }

    private static int plusX(int screenWidth) {
        return screenWidth - CLOSE_BUTTON_X_OFFSET - BUTTON_GAP - BUTTON_SIZE;
    }

    private static int minusX(int screenWidth) {
        return plusX(screenWidth) - BUTTON_GAP - BUTTON_SIZE;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public record Viewport(int physicalWidth, int physicalHeight, int logicalWidth, int logicalHeight,
                           float scale, int offsetX, int offsetY) {
        public void apply(GuiGraphics graphics) {
            graphics.pose().translate(offsetX, offsetY, 0);
            graphics.pose().scale(scale, scale, 1.0F);
        }

        public int mouseX(double physicalX) {
            return (int) Math.floor((physicalX - offsetX) / scale);
        }

        public int mouseY(double physicalY) {
            return (int) Math.floor((physicalY - offsetY) / scale);
        }

        public double mouseXDouble(double physicalX) {
            return (physicalX - offsetX) / scale;
        }

        public double mouseYDouble(double physicalY) {
            return (physicalY - offsetY) / scale;
        }

        public double deltaX(double physicalDelta) {
            return physicalDelta / scale;
        }

        public double deltaY(double physicalDelta) {
            return physicalDelta / scale;
        }

        /**
         * GuiGraphics scissor coordinates are not affected by pose scaling. Convert the local
         * trading-screen coordinates back into the original screen coordinate space first.
         */
        public void enableScissor(GuiGraphics graphics, int left, int top, int right, int bottom) {
            int scaledLeft = clamp((int) Math.floor(offsetX + left * scale), 0, physicalWidth);
            int scaledTop = clamp((int) Math.floor(offsetY + top * scale), 0, physicalHeight);
            int scaledRight = clamp((int) Math.ceil(offsetX + right * scale), 0, physicalWidth);
            int scaledBottom = clamp((int) Math.ceil(offsetY + bottom * scale), 0, physicalHeight);
            graphics.enableScissor(scaledLeft, scaledTop,
                Math.max(scaledLeft, scaledRight), Math.max(scaledTop, scaledBottom));
        }

        private static int clamp(int value, int minimum, int maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }
    }
}
