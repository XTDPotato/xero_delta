package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.BooleanSupplier;

/** Selectable corpse entity row with an actual entity-model preview. */
final class CorpseEntityRuleRow extends AbstractButton {
    private static final int ROW_HEIGHT = 32;
    private final Font font;
    private final String entityId;
    private final BooleanSupplier selected;
    private final Runnable selectAction;
    private final Runnable rightClickAction;
    private final int surface;
    private final int surfaceSelected;
    private final int accent;
    private final int textColor;
    private final Component entityName;
    private final LivingEntity previewEntity;

    CorpseEntityRuleRow(Font font, int x, int y, int width, EntityType<?> entityType,
                        String entityId, BooleanSupplier selected, Runnable selectAction,
                        Runnable editAction,
                        int surface, int surfaceSelected, int accent, int textColor) {
        this(font, x, y, width, entityType, entityId, selected, selectAction, editAction,
            editAction, surface, surfaceSelected, accent, textColor);
    }

    CorpseEntityRuleRow(Font font, int x, int y, int width, EntityType<?> entityType,
                        String entityId, BooleanSupplier selected, Runnable selectAction,
                        Runnable editAction, Runnable rightClickAction,
                        int surface, int surfaceSelected, int accent, int textColor) {
        super(x, y, width, ROW_HEIGHT, Component.literal(entityId));
        this.font = font;
        this.entityId = entityId;
        this.selected = selected;
        this.selectAction = selectAction;
        this.rightClickAction = rightClickAction;
        this.surface = surface;
        this.surfaceSelected = surfaceSelected;
        this.accent = accent;
        this.textColor = textColor;
        this.entityName = entityType == null ? Component.literal(entityId)
            : entityType.getDescription();
        this.previewEntity = EntityModelPreview.createPreviewEntity(entityType);
    }

    String entityId() {
        return entityId;
    }

    @Override
    public void onPress() {
        selectAction.run();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && active && visible && isMouseOver(mouseX, mouseY)) {
            if (rightClickAction != null) rightClickAction.run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
                                float partialTick) {
        boolean isSelected = selected.getAsBoolean();
        boolean hovered = isMouseOver(mouseX, mouseY);
        int background = isSelected ? surfaceSelected : surface;
        if (hovered && !isSelected) background = Material2Drawing.alpha(accent, 24);
        Material2Drawing.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(),
            5, background);
        if (isSelected) {
            Material2Drawing.outlineRoundedRect(graphics, getX(), getY(), getWidth(),
                getHeight(), 5, 1.0F, accent);
        }
        drawPreview(graphics);
        String name = entityName.getString();
        int textWidth = Math.max(1, getWidth() - 42);
        graphics.drawString(font, font.plainSubstrByWidth(name, textWidth), getX() + 36,
            getY() + 3, textColor, false);
        graphics.drawString(font, font.plainSubstrByWidth(entityId, textWidth), getX() + 36,
            getY() + 3 + font.lineHeight, Material2Drawing.alpha(textColor, 175), false);
    }

    private void drawPreview(GuiGraphics graphics) {
        int left = getX() + 2;
        int top = getY() + 2;
        int right = getX() + 32;
        int bottom = getY() + getHeight() - 2;
        if (previewEntity != null) {
            InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, left, top, right,
                bottom, 22, 0.0625F, 0.0F, 0.0F, previewEntity);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

