package com.xtdpotato.xero_delta.screen;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CorpseLootViewportTest {
    private final CorpseLootViewport viewport = new CorpseLootViewport(1, 34, 236, 312);

    @Test
    void clippedPartsCannotBeClickedOverHeaderFooterOrScrollbar() {
        assertFalse(viewport.contains(50, 33.99));
        assertFalse(viewport.contains(50, 312));
        assertFalse(viewport.contains(236, 100));
        assertFalse(viewport.contains(0, 100));
        assertTrue(viewport.contains(1, 34));
        assertTrue(viewport.contains(235.99, 311.99));
    }

    @Test
    void slotAndBackgroundPassesClipToTheSameScreenRectangle() {
        Matrix4f nativePose = new Matrix4f().translate(100, 20, 0)
            .scale(1.5F, 0.75F, 1).translate(-100, -20, 0);
        CorpseLootViewport background = viewport.screenBounds(nativePose, 100, 20);
        CorpseLootViewport slots = viewport.screenBounds(
            new Matrix4f(nativePose).translate(100, 20, 0), 0, 0);
        assertEquals(background, slots);
        assertEquals(new CorpseLootViewport(102, 46, 454, 254), background);
    }

    @Test
    void openingAnimationMovesTheClipWithTheContent() {
        assertEquals(new CorpseLootViewport(12, 57, 247, 335),
            viewport.screenBounds(new Matrix4f().translate(11, 23, 0), 0, 0));
    }
}
