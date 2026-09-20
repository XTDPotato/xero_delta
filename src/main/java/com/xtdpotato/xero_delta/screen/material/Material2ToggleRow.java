package com.xtdpotato.xero_delta.screen.material;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** Full-width Material 3 setting row with a trailing switch. */
public final class Material2ToggleRow extends AbstractButton {
    private final BooleanSupplier checked;
    private final Runnable toggle;
    private final int primary;
    private final int surface;
    private final int text;
    private final int outline;
    private boolean pointerPressed;
    private Material2Icon icon;

    public Material2ToggleRow(int x, int y, int width, Component message,
                              BooleanSupplier checked, Runnable toggle, int primary,
                              int surface, int text, int outline) {
        super(x, y, width, 20, message);
        this.checked = checked;
        this.toggle = toggle;
        this.primary = primary;
        this.surface = surface;
        this.text = text;
        this.outline = outline;
    }

    public Material2ToggleRow icon(Material2Icon value) {
        icon = value;
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
        toggle.run();
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
        int labelX = getX() + (icon == null ? 10 : 28);
        String label = Minecraft.getInstance().font.plainSubstrByWidth(getMessage().getString(),
            Math.max(1, getX() + getWidth() - 46 - labelX));
        if (icon != null) icon.render(graphics, getX() + 14, getY() + getHeight() / 2, text);
        graphics.drawString(Minecraft.getInstance().font, label, labelX,
            getY() + (getHeight() - Minecraft.getInstance().font.lineHeight) / 2, text, false);

        boolean selected = checked.getAsBoolean();
        int switchX = getX() + getWidth() - 36;
        int switchY = getY() + 2;
        int switchColor = selected ? primary : surface;
        Material2Drawing.roundedRect(graphics, switchX, switchY, 28, 16,
            Material3Theme.RADIUS_FULL, switchColor);
        if (!selected) Material2Drawing.outlineRoundedRect(graphics, switchX, switchY,
            28, 16, Material3Theme.RADIUS_FULL, 1.0F, outline);
        int knobX = selected ? switchX + 20 : switchX + 8;
        if (isMouseOver(mouseX, mouseY)) Material2Drawing.circle(graphics, knobX,
            switchY + 8, pointerPressed ? 9 : 8, Material3Theme.alpha(primary, 26));
        Material2Drawing.circle(graphics, knobX, switchY + 8, selected ? 6 : 5,
            selected ? Material3Theme.ON_PRIMARY : outline);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

