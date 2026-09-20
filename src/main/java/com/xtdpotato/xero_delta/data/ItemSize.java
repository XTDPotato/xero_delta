package com.xtdpotato.xero_delta.data;

public record ItemSize(int width, int height) {
    public static final ItemSize ONE = new ItemSize(1, 1);

    public ItemSize {
        width = Math.max(1, Math.min(10, width));
        height = Math.max(1, Math.min(10, height));
    }

    public ItemSize rotated() {
        return new ItemSize(height, width);
    }

    public long pack() {
        return ((long) width << 32) | (height & 0xFFFFFFFFL);
    }

    public static ItemSize unpack(long packed) {
        return new ItemSize((int) (packed >> 32), (int) packed);
    }
}
