package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class QualityItemBackground {
    private static int suppressedDepth;

    private QualityItemBackground() {
    }

    public static int color(ItemStack stack) {
        return Config.INSTANCE.getQualityColor(ClientDataCache.INSTANCE.getQuality(stack));
    }

    public static void render(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height) {
        if (stack.isEmpty() || suppressedDepth > 0) return;
        graphics.fill(x, y, x + width, y + height, color(stack));
    }

    public static void pushSuppress() {
        suppressedDepth++;
    }

    public static void popSuppress() {
        suppressedDepth = Math.max(0, suppressedDepth - 1);
    }

    public static boolean isSuppressed() {
        return suppressedDepth > 0;
    }
}
