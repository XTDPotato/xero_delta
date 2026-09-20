package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material2Icon;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Material 3 text field that keeps vanilla cursor, selection, and clipboard behavior. */
public class MaterialEditBox extends EditBox {
    private static final int MIN_HEIGHT = 26;
    private int fill = Material3Theme.SURFACE_CONTAINER_HIGH;
    private int text = Material3Theme.TEXT;
    private int muted = Material3Theme.TEXT_MUTED;
    private int accent = Material3Theme.PRIMARY;
    private int line = Material3Theme.OUTLINE;
    private boolean customColors;
    private Material2Icon icon;
    private final Font materialFont;

    public MaterialEditBox(Font font, int x, int y, int width, int height, Component message) {
        this(font, x, y, width, height, message, MIN_HEIGHT);
    }

    protected MaterialEditBox(Font font, int x, int y, int width, int height,
                              Component message, int minimumHeight) {
        super(font, x, y, width, Math.max(minimumHeight, height), message);
        materialFont = font;
        setBordered(false);
        setMaxLength(Integer.MAX_VALUE);
        setTextColor(text);
        setTextColorUneditable(muted);
        setTextShadow(false);
    }

    public MaterialEditBox icon(Material2Icon value) {
        icon = value;
        return this;
    }

    public MaterialEditBox colors(int fill, int text, int muted, int accent, int line) {
        this.fill = fill;
        this.text = text;
        this.muted = muted;
        this.accent = accent;
        this.line = line;
        this.customColors = true;
        setTextColor(text);
        setTextColorUneditable(muted);
        return this;
    }

    protected boolean supportsFloatingLabel() {
        return true;
    }

    @Override
    public int getInnerWidth() {
        return Math.max(1, getWidth() - (icon == null ? 20 : 34));
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX - (icon == null ? 8 : 26), mouseY);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int currentFill = customColors ? fill : Material3Theme.SURFACE_CONTAINER_HIGH;
        int currentText = customColors ? text : Material3Theme.TEXT;
        int currentMuted = customColors ? muted : Material3Theme.TEXT_MUTED;
        int currentAccent = customColors ? accent : Material3Theme.PRIMARY;
        int currentLine = customColors ? line : Material3Theme.OUTLINE;
        setTextColor(currentText);
        setTextColorUneditable(currentMuted);
        Material2Drawing.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(),
            Material3Theme.RADIUS_EXTRA_SMALL, currentFill);
        int underlineHeight = isFocused() ? 2 : 1;
        graphics.fill(getX(), getY() + getHeight() - underlineHeight,
            getX() + getWidth(), getY() + getHeight(), isFocused() ? currentAccent : currentLine);

        int contentX = icon == null ? 8 : 26;
        if (icon != null) icon.render(graphics, getX() + 13,
            getY() + getHeight() / 2, active ? currentText : currentMuted);
        boolean floating = supportsFloatingLabel()
            && !getMessage().getString().isEmpty()
            && (isFocused() || !getValue().isEmpty());
        boolean compactPlaceholder = !supportsFloatingLabel()
            && getValue().isEmpty() && !isFocused();
        if (!getMessage().getString().isEmpty()
            && (supportsFloatingLabel() || compactPlaceholder)) {
            int labelY = floating ? getY() + 3
                : getY() + (getHeight() - 9) / 2;
            int labelColor = isFocused() ? currentAccent : currentMuted;
            graphics.drawString(materialFont, materialFont.plainSubstrByWidth(
                    getMessage().getString(), getInnerWidth()),
                getX() + contentX, labelY, labelColor, false);
        }
        if (floating || getMessage().getString().isEmpty()
            || !supportsFloatingLabel() && !compactPlaceholder) {
            int textY = supportsFloatingLabel()
                ? getY() + getHeight() - materialFont.lineHeight - 3
                : getY() + (getHeight() - materialFont.lineHeight) / 2;
            graphics.pose().pushPose();
            graphics.pose().translate(contentX, textY - getY(), 0.0F);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        }
    }
}

