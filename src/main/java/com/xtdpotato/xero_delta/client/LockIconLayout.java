package com.xtdpotato.xero_delta.client;

/** Aspect-ratio preserving placement of the lock inside a complete item footprint. */
final class LockIconLayout {
    private LockIconLayout() {
    }

    record Bounds(int x, int y, int width, int height) {
    }

    static Bounds contain(int x, int y, int width, int height, int textureWidth, int textureHeight) {
        int availableWidth = Math.max(1, width);
        int availableHeight = Math.max(1, height);
        int sourceWidth = Math.max(1, textureWidth);
        int sourceHeight = Math.max(1, textureHeight);
        double scale = Math.min(availableWidth / (double) sourceWidth,
            availableHeight / (double) sourceHeight);
        int drawWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        return new Bounds(x + (availableWidth - drawWidth) / 2,
            y + (availableHeight - drawHeight) / 2, drawWidth, drawHeight);
    }
}
