package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/** Clickable item row used by the corpse carrier weighted-list editor. */
final class CorpseCarrierCandidateRow extends AbstractButton {
    private final Font font;
    private final ItemStack stack;
    private final String itemId;
    private final int surface;
    private final int accentColor;
    private final Runnable selectAction;
    private final Consumer<String> copyAction;
    private final DoubleSupplier probabilitySupplier;

    CorpseCarrierCandidateRow(Font font, int x, int y, int width, ItemStack stack,
                              String itemId, int surface, int accentColor,
                              Runnable selectAction, Consumer<String> copyAction,
                              DoubleSupplier probabilitySupplier) {
        super(x, y, width, 32, Component.literal(itemId));
        this.font = font;
        this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
        this.itemId = itemId;
        this.surface = surface;
        this.accentColor = accentColor;
        this.selectAction = selectAction;
        this.copyAction = copyAction;
        this.probabilitySupplier = probabilitySupplier;
    }

    ItemStack stack() {
        return stack;
    }

    String itemId() {
        return itemId;
    }

    @Override
    public void onPress() {
        selectAction.run();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        if (button == 1) {
            copyAction.accept(itemId);
            return true;
        }
        return button == 0 && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
                                float partialTick) {
        int color = isMouseOver(mouseX, mouseY)
            ? Material2Drawing.alpha(accentColor, 38) : surface;
        Material2Drawing.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(),
            5, color);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, getX() + 5, getY() + 8);
            graphics.renderItemDecorations(font, stack, getX() + 5, getY() + 8);
        }
        String probability = String.format(java.util.Locale.ROOT, "%.1f%%",
            Math.max(0.0D, Math.min(1.0D, probabilitySupplier.getAsDouble())) * 100.0D);
        int probabilityWidth = font.width(probability);
        graphics.drawString(font, probability,
            getX() + getWidth() - probabilityWidth - 7,
            getY() + (getHeight() - font.lineHeight) / 2, accentColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

