package com.xtdpotato.xero_delta.screen.material;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Compact Material 3 command button shared by Xero Delta screens. */
public class Material2Button extends AbstractButton {
    public enum Variant { FILLED, TONAL, OUTLINED, TEXT, ROW }

    private final Runnable action;
    private final Variant variant;
    private boolean pointerPressed;
    private int primary = Material3Theme.PRIMARY;
    private int onPrimary = Material3Theme.ON_PRIMARY;
    private int surface = Material3Theme.SECONDARY_CONTAINER;
    private int text = Material3Theme.TEXT;
    private int outline = Material3Theme.OUTLINE;
    private boolean customColors;
    private boolean chevron;
    private Material2Icon icon;
    private boolean smooth;

    public Material2Button(int x, int y, int width, Component message,
                           Variant variant, Runnable action) {
        this(x, y, width, 20, message, variant, action);
    }

    protected Material2Button(int x, int y, int width, int height, Component message,
                              Variant variant, Runnable action) {
        super(x, y, width, height, message);
        this.variant = variant == null ? Variant.TEXT : variant;
        this.action = action == null ? () -> { } : action;
    }

    public Material2Button colors(int primary, int onPrimary, int surface,
                                  int text, int outline) {
        this.primary = primary;
        this.onPrimary = onPrimary;
        this.surface = surface;
        this.text = text;
        this.outline = outline;
        this.customColors = true;
        return this;
    }

    public Material2Button chevron(boolean value) {
        chevron = value;
        return this;
    }

    public Material2Button icon(Material2Icon value) {
        icon = value;
        return this;
    }

    public Material2Button smooth(boolean value) {
        smooth = value;
        return this;
    }

    public void cancelPointerGesture() {
        pointerPressed = false;
    }

    public boolean isPointerPressed() {
        return pointerPressed;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        pointerPressed = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || !pointerPressed) return false;
        pointerPressed = false;
        if (active && visible && isMouseOver(mouseX, mouseY)) onPress();
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = active && isMouseOver(mouseX, mouseY);
        int currentPrimary = customColors ? primary : Material3Theme.PRIMARY;
        int currentOnPrimary = customColors ? onPrimary : Material3Theme.ON_PRIMARY;
        int currentSurface = customColors ? surface : Material3Theme.SECONDARY_CONTAINER;
        int currentText = customColors ? text : Material3Theme.TEXT;
        int currentOutline = customColors ? outline : Material3Theme.OUTLINE;
        int enabledText = active ? currentText
            : Material3Theme.alpha(currentText, Material3Theme.DISABLED_CONTENT_ALPHA);
        int enabledPrimary = active ? currentPrimary
            : Material3Theme.alpha(currentText, Material3Theme.DISABLED_CONTENT_ALPHA);
        int background = switch (variant) {
            case FILLED -> active ? currentPrimary
                : Material3Theme.alpha(currentText, Material3Theme.DISABLED_CONTAINER_ALPHA);
            case TONAL, ROW -> active ? currentSurface
                : Material3Theme.alpha(currentText, Material3Theme.DISABLED_CONTAINER_ALPHA);
            case OUTLINED, TEXT -> 0;
        };
        if (background != 0) {
            if (smooth) Material2Drawing.smoothRoundedRect(graphics, getX(), getY(),
                getWidth(), getHeight(), Material3Theme.RADIUS_FULL, background);
            else Material2Drawing.roundedRect(graphics, getX(), getY(),
                getWidth(), getHeight(), Material3Theme.RADIUS_FULL, background);
        }
        if (variant == Variant.OUTLINED) Material2Drawing.outlineRoundedRect(graphics,
            getX(), getY(), getWidth(), getHeight(), Material3Theme.RADIUS_FULL, 1.0F,
            active ? currentOutline
                : Material3Theme.alpha(currentOutline, Material3Theme.DISABLED_CONTENT_ALPHA));
        if (hovered || pointerPressed) {
            int layerColor = variant == Variant.FILLED ? currentOnPrimary : enabledPrimary;
            int layerAlpha = pointerPressed ? Material3Theme.STATE_PRESS_ALPHA : Material3Theme.STATE_HOVER_ALPHA;
            if (smooth) Material2Drawing.smoothRoundedRect(graphics, getX(), getY(),
                getWidth(), getHeight(), Material3Theme.RADIUS_FULL,
                Material3Theme.alpha(layerColor, layerAlpha));
            else Material2Drawing.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(),
                Material3Theme.RADIUS_FULL, Material3Theme.alpha(layerColor, layerAlpha));
        }

        int foreground = active && variant == Variant.FILLED ? currentOnPrimary : enabledText;
        int labelX = getX() + (icon == null ? 10 : 28);
        int labelRight = getX() + getWidth() - (chevron ? 20 : 10);
        String label = Minecraft.getInstance().font.plainSubstrByWidth(getMessage().getString(),
            Math.max(1, labelRight - labelX));
        if (icon != null) icon.render(graphics, getX() + 14, getY() + getHeight() / 2, foreground);
        graphics.drawString(Minecraft.getInstance().font, label, labelX,
            getY() + (getHeight() - Minecraft.getInstance().font.lineHeight) / 2,
            foreground, false);
        if (chevron) Material2Icon.CHEVRON_RIGHT.render(graphics,
            getX() + getWidth() - 11, getY() + getHeight() / 2, enabledPrimary);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

