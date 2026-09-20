package com.xtdpotato.xero_delta.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shared empty-slot artwork for the player and corpse Delta loadouts. */
public final class LoadoutSlotBackgroundRenderer {
    public enum Kind {
        HELMET,
        CHESTPLATE,
        SIDEARM,
        PRIMARY
    }

    private static final ResourceLocation EMPTY_HELMET = ResourceLocation.withDefaultNamespace(
        "textures/item/empty_armor_slot_helmet.png");
    private static final ResourceLocation EMPTY_CHESTPLATE = ResourceLocation.withDefaultNamespace(
        "textures/item/empty_armor_slot_chestplate.png");
    private static final int ICON_COLOR = 0x775E6C6A;

    private LoadoutSlotBackgroundRenderer() {
    }

    public static void render(GuiGraphics graphics, int x, int y,
                              int width, int height, Kind kind) {
        switch (kind) {
            case HELMET -> renderVanillaArmorSlot(graphics, EMPTY_HELMET,
                x, y, width, height);
            case CHESTPLATE -> renderVanillaArmorSlot(graphics, EMPTY_CHESTPLATE,
                x, y, width, height);
            case SIDEARM -> renderWeapon(graphics, x, y, width, height, true);
            case PRIMARY -> renderWeapon(graphics, x, y, width, height, false);
        }
    }

    private static void renderVanillaArmorSlot(GuiGraphics graphics,
                                                ResourceLocation texture,
                                                int x, int y, int width, int height) {
        int size = Math.max(12, Math.min(16, Math.min(width, height) - 8));
        int iconX = x + (width - size) / 2;
        int iconY = y + (height - size) / 2;
        graphics.setColor(0.52F, 0.58F, 0.57F, 0.58F);
        graphics.blit(texture, iconX, iconY, size, size,
            0, 0, 16, 16, 16, 16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderWeapon(GuiGraphics graphics, int x, int y,
                                     int width, int height, boolean sidearm) {
        int centerY = y + height / 2;
        int bodyWidth = sidearm ? Math.min(17, width - 10) : Math.min(38, width - 18);
        int bodyX = x + (width - bodyWidth) / 2;
        graphics.fill(bodyX, centerY - 3, bodyX + bodyWidth, centerY + 2, ICON_COLOR);
        graphics.fill(bodyX + (sidearm ? 3 : 6), centerY + 2,
            bodyX + (sidearm ? 8 : 12), centerY + 10, ICON_COLOR);
        if (!sidearm) {
            graphics.fill(bodyX - 7, centerY - 1, bodyX, centerY + 2, ICON_COLOR);
            graphics.fill(bodyX + bodyWidth, centerY - 2,
                bodyX + bodyWidth + 7, centerY, ICON_COLOR);
        }
    }
}
