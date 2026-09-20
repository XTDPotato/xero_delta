package com.xtdpotato.xero_delta.screen.material;

/** Geometry uses Minecraft GUI pixels, independently of native window resolution. */
public record MaterialPageLayout(int contentX, int contentWidth, int bodyTop, int bodyBottom) {
    public static MaterialPageLayout of(int width, int height, boolean navigation) {
        int left = navigation ? (width >= 480 ? 150 : 110) : 16;
        return new MaterialPageLayout(left, Math.max(1, width - left - 16), 36, height - 36);
    }

    public int viewportHeight() { return Math.max(1, bodyBottom - bodyTop); }
    public int maxScroll(int contentHeight) { return Math.max(0, contentHeight - viewportHeight()); }
    public double clampScroll(double value, int contentHeight) {
        return Math.max(0, Math.min(value, maxScroll(contentHeight)));
    }
}
