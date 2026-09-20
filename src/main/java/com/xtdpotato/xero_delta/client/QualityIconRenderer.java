package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/** Draws the shipped raster quality badges without synthesizing replacement shapes. */
public final class QualityIconRenderer {
    private static final int TEXTURE_SIZE = 16;

    private QualityIconRenderer() {
    }

    public static void render(GuiGraphics graphics, String quality,
                              int x, int y, int size) {
        String normalized = normalize(quality);
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
            XeroDelta.MOD_ID, "textures/quality/" + normalized + ".png");
        graphics.blit(texture, x, y, size, size, 0, 0,
            TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private static String normalize(String quality) {
        if (quality == null) return "gray";
        return switch (quality.toLowerCase(Locale.ROOT)) {
            case "green", "blue", "purple", "gold", "red" ->
                quality.toLowerCase(Locale.ROOT);
            default -> "gray";
        };
    }
}