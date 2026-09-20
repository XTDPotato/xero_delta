package com.xtdpotato.xero_delta.screen;

/** Shared geometry for the five scaled pocket cells. */
public final class PocketSlotLayout {
    public static final int COUNT = 5;
    private static final int LEFT_INSET = 5;
    private static final int TOP_INSET = 23;

    private PocketSlotLayout() {
    }

    public static Bounds bounds(int sectionX, int sectionY, int cellSize, int gap, int index) {
        int safeSize = Math.max(1, cellSize);
        int safeGap = Math.max(0, gap);
        int safeIndex = Math.max(0, Math.min(COUNT - 1, index));
        return new Bounds(sectionX + LEFT_INSET + safeIndex * (safeSize + safeGap),
            sectionY + TOP_INSET, safeSize, safeSize);
    }

    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
        }
    }
}
