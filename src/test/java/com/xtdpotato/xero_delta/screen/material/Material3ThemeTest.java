package com.xtdpotato.xero_delta.screen.material;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Material3ThemeTest {
    @Test
    void primaryButtonContentRemainsReadable() {
        assertTrue(contrast(Material3Theme.PRIMARY, Material3Theme.ON_PRIMARY) >= 4.5D);
    }

    @Test
    void defaultTextRemainsReadableAcrossCoreSurfaces() {
        assertTrue(contrast(Material3Theme.TEXT, Material3Theme.BACKGROUND) >= 7.0D);
        assertTrue(contrast(Material3Theme.TEXT, Material3Theme.SURFACE_CONTAINER) >= 7.0D);
        assertTrue(contrast(Material3Theme.TEXT_MUTED, Material3Theme.SURFACE_CONTAINER) >= 4.5D);
    }

    @Test
    void surfaceHierarchyIsOrderedFromDarkToLight() {
        assertTrue(luminance(Material3Theme.BACKGROUND) < luminance(Material3Theme.SURFACE));
        assertTrue(luminance(Material3Theme.SURFACE) < luminance(Material3Theme.SURFACE_CONTAINER));
        assertTrue(luminance(Material3Theme.SURFACE_CONTAINER)
            < luminance(Material3Theme.SURFACE_CONTAINER_HIGH));
    }

    private static double contrast(int first, int second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05D) / (darker + 0.05D);
    }

    private static double luminance(int color) {
        return 0.2126D * channel((color >>> 16) & 255)
            + 0.7152D * channel((color >>> 8) & 255)
            + 0.0722D * channel(color & 255);
    }

    private static double channel(int value) {
        double normalized = value / 255.0D;
        return normalized <= 0.04045D ? normalized / 12.92D
            : Math.pow((normalized + 0.055D) / 1.055D, 2.4D);
    }
}

