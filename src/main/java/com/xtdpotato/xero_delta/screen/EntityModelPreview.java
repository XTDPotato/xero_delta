package com.xtdpotato.xero_delta.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** Draggable and zoomable living-entity preview used by corpse rule editors. */
final class EntityModelPreview extends AbstractWidget {
    private final int surface;
    private final int border;
    private final int accent;
    private LivingEntity entity;
    private boolean dragging;
    private int dragButton = -1;
    private float offsetX;
    private float offsetY;
    private float yaw;
    private float pitch;
    private float zoom = 1.0F;

    EntityModelPreview(int x, int y, int width, int height, EntityType<?> type,
                       int surface, int border, int accent) {
        super(x, y, width, height, Component.empty());
        this.surface = surface;
        this.border = border;
        this.accent = accent;
        setEntityType(type);
    }

    void setEntityType(EntityType<?> type) {
        entity = null;
        if (type == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        Entity created = minecraft.level == null ? null : createEntity(type, minecraft);
        if (created instanceof LivingEntity living) entity = living;
        yaw = 0.0F;
        pitch = 0.0F;
        zoom = 1.0F;
        offsetX = 0.0F;
        offsetY = 0.0F;
    }

    void resetView() {
        yaw = 0.0F;
        pitch = 0.0F;
        zoom = 1.0F;
        offsetX = 0.0F;
        offsetY = 0.0F;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
                                float partialTick) {
        int outline = dragging || isMouseOver(mouseX, mouseY) ? accent : border;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), surface);
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, outline);
        graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(),
            getY() + getHeight(), outline);
        graphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), outline);
        graphics.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(),
            getY() + getHeight(), outline);
        graphics.pose().pushPose();
        graphics.pose().translate(offsetX, offsetY, 0.0F);
        if (entity != null) {
            int inset = 8;
            int scale = Math.max(22, Math.round(Math.min(getWidth(), getHeight()) * 0.38F * zoom));
            InventoryScreen.renderEntityInInventoryFollowsAngle(graphics,
                getX() + inset, getY() + inset, getX() + getWidth() - inset,
                getY() + getHeight() - inset, scale, 0.0625F, yaw, pitch, entity);
        } else {
            Component message = Component.translatable("screen.xero_delta.corpse_rules.model_requires_world");
            graphics.drawCenteredString(Minecraft.getInstance().font,
                Minecraft.getInstance().font.plainSubstrByWidth(message.getString(), Math.max(1, getWidth() - 16)),
                getX() + getWidth() / 2, getY() + getHeight() / 2 - 4,
                accent);
        }
        graphics.pose().popPose();
    }

    static LivingEntity createPreviewEntity(EntityType<?> type) {
        Minecraft minecraft = Minecraft.getInstance();
        if (type == null || minecraft.level == null) return null;
        Entity created = createEntity(type, minecraft);
        if (!(created instanceof LivingEntity living)) return null;
        living.setYRot(180.0F);
        living.setXRot(0.0F);
        living.yRotO = 180.0F;
        living.xRotO = 0.0F;
        return living;
    }

    private static Entity createEntity(EntityType<?> type, Minecraft minecraft) {
        try {
            return type.create(minecraft.level);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button != 0 && button != 1) || !active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        dragging = true;
        dragButton = button;
        setFocused(true);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (!dragging || button != dragButton) return false;
        if (dragButton == 0) {
            // Horizontal input is intentionally reversed for the expected
            // model-view direction: drag left rotates the model left.
            yaw -= (float) dragX * 0.35F;
            pitch = Math.max(-35.0F, Math.min(35.0F, pitch - (float) dragY * 0.25F));
        } else {
            offsetX = Math.max(-getWidth() * 0.45F, Math.min(getWidth() * 0.45F,
                offsetX + (float) dragX));
            offsetY = Math.max(-getHeight() * 0.45F, Math.min(getHeight() * 0.45F,
                offsetY + (float) dragY));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != dragButton || !dragging) return false;
        dragging = false;
        dragButton = -1;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX,
                                 double scrollY) {
        if (!visible || !isMouseOver(mouseX, mouseY)) return false;
        float factor = (float) Math.pow(1.12D, scrollY);
        zoom = Math.max(0.10F, Math.min(20.0F, zoom * factor));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

