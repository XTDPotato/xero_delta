package com.xtdpotato.xero_delta.screen.material;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.function.IntConsumer;

/** Compact HSV and alpha picker implemented with Minecraft GUI primitives. */
public final class Material2ColorPicker extends AbstractWidget {
    private static final int PICKER_WIDTH = 184;
    private static final int PICKER_HEIGHT = 126;
    private static final int SV_X = 8;
    private static final int SV_Y = 8;
    private static final int SV_WIDTH = 142;
    private static final int SV_HEIGHT = 90;
    private static final int HUE_X = 156;
    private static final int ALPHA_X = 168;
    private final IntConsumer consumer;
    private float hue;
    private float saturation;
    private float brightness;
    private int alpha;
    private DragPart dragging = DragPart.NONE;

    public Material2ColorPicker(int argb, IntConsumer consumer) {
        super(0, 0, PICKER_WIDTH, PICKER_HEIGHT, Component.empty());
        this.consumer = consumer == null ? ignored -> { } : consumer;
        setColor(argb);
    }

    public void setColor(int argb) {
        alpha = argb >>> 24;
        float[] hsb = Color.RGBtoHSB(argb >>> 16 & 255, argb >>> 8 & 255, argb & 255, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    public int color() {
        return alpha << 24 | Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Material2Drawing.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(),
            Material3Theme.RADIUS_SMALL, Material3Theme.SURFACE_CONTAINER_HIGH);
        Material2Drawing.outlineRoundedRect(graphics, getX(), getY(), getWidth(), getHeight(),
            Material3Theme.RADIUS_SMALL, 1.0F, Material3Theme.OUTLINE_VARIANT);

        for (int row = 0; row < 18; row++) for (int column = 0; column < 28; column++) {
            float sat = column / 27.0F;
            float value = 1.0F - row / 17.0F;
            int left = getX() + SV_X + column * SV_WIDTH / 28;
            int right = getX() + SV_X + (column + 1) * SV_WIDTH / 28;
            int top = getY() + SV_Y + row * SV_HEIGHT / 18;
            int bottom = getY() + SV_Y + (row + 1) * SV_HEIGHT / 18;
            graphics.fill(left, top, right, bottom,
                0xFF000000 | Color.HSBtoRGB(hue, sat, value) & 0x00FFFFFF);
        }
        for (int row = 0; row < SV_HEIGHT; row++) {
            float selectedHue = row / (float) (SV_HEIGHT - 1);
            graphics.fill(getX() + HUE_X, getY() + SV_Y + row,
                getX() + HUE_X + 8, getY() + SV_Y + row + 1,
                0xFF000000 | Color.HSBtoRGB(selectedHue, 1.0F, 1.0F) & 0x00FFFFFF);
            int rowAlpha = Math.round(255.0F * (1.0F - row / (float) (SV_HEIGHT - 1)));
            int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
            graphics.fill(getX() + ALPHA_X, getY() + SV_Y + row,
                getX() + ALPHA_X + 8, getY() + SV_Y + row + 1, rowAlpha << 24 | rgb);
        }

        int markerX = getX() + SV_X + Math.round(saturation * SV_WIDTH);
        int markerY = getY() + SV_Y + Math.round((1.0F - brightness) * SV_HEIGHT);
        Material2Drawing.circle(graphics, markerX, markerY, 4, 0xFFFFFFFF);
        Material2Drawing.circle(graphics, markerX, markerY, 2, color());
        graphics.fill(getX() + HUE_X - 1,
            getY() + SV_Y + Math.round(hue * (SV_HEIGHT - 1)) - 1,
            getX() + HUE_X + 9,
            getY() + SV_Y + Math.round(hue * (SV_HEIGHT - 1)) + 1, 0xFFFFFFFF);
        graphics.fill(getX() + ALPHA_X - 1,
            getY() + SV_Y + Math.round((1.0F - alpha / 255.0F) * (SV_HEIGHT - 1)) - 1,
            getX() + ALPHA_X + 9,
            getY() + SV_Y + Math.round((1.0F - alpha / 255.0F) * (SV_HEIGHT - 1)) + 1,
            0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        dragging = partAt(mouseX, mouseY);
        if (dragging == DragPart.NONE) return false;
        updateFromPointer(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button != 0 || dragging == DragPart.NONE) return false;
        updateFromPointer(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || dragging == DragPart.NONE) return false;
        dragging = DragPart.NONE;
        return true;
    }

    private DragPart partAt(double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, getX() + SV_X, getY() + SV_Y, SV_WIDTH, SV_HEIGHT)) return DragPart.SV;
        if (inside(mouseX, mouseY, getX() + HUE_X, getY() + SV_Y, 8, SV_HEIGHT)) return DragPart.HUE;
        if (inside(mouseX, mouseY, getX() + ALPHA_X, getY() + SV_Y, 8, SV_HEIGHT)) return DragPart.ALPHA;
        return DragPart.NONE;
    }

    private void updateFromPointer(double mouseX, double mouseY) {
        switch (dragging) {
            case SV -> {
                saturation = clamp((float) ((mouseX - getX() - SV_X) / SV_WIDTH));
                brightness = 1.0F - clamp((float) ((mouseY - getY() - SV_Y) / SV_HEIGHT));
            }
            case HUE -> hue = clamp((float) ((mouseY - getY() - SV_Y) / SV_HEIGHT));
            case ALPHA -> alpha = Math.round(255.0F * (1.0F
                - clamp((float) ((mouseY - getY() - SV_Y) / SV_HEIGHT))));
            case NONE -> { }
        }
        consumer.accept(color());
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal("Color picker"));
    }

    private enum DragPart { NONE, SV, HUE, ALPHA }
}

