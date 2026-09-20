package com.xtdpotato.xero_delta.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Shared body-part icons for the compact panel and full status screen. */
public final class PlayerStatusUi {
    public enum Part {
        HEAD("status.xero_delta.part.head"), CHEST("status.xero_delta.part.chest"),
        LEFT_ARM("status.xero_delta.part.left_arm"), RIGHT_ARM("status.xero_delta.part.right_arm"),
        ABDOMEN("status.xero_delta.part.abdomen"),
        LEFT_LEG("status.xero_delta.part.left_leg"), RIGHT_LEG("status.xero_delta.part.right_leg");
        private final String key;
        Part(String key) { this.key = key; }
        public Component label() { return Component.translatable(key); }
    }

    private PlayerStatusUi() { /* utility */ }

    public static float value(PlayerStatusClientState state, Part part) {
        return switch (part) {
            case HEAD -> state.head(); case CHEST -> state.chest();
            case LEFT_ARM -> state.leftArm(); case RIGHT_ARM -> state.rightArm();
            case ABDOMEN -> state.abdomen();
            case LEFT_LEG -> state.leftLeg(); case RIGHT_LEG -> state.rightLeg();
        };
    }

    public static void drawIcon(GuiGraphics g, int x, int y, int size, Part part,
                                float injury, boolean hovered) {
        g.fill(x, y, x + size, y + size, hovered ? 0xD02D3B40 : 0xB0141D20);
        g.renderOutline(x, y, size, size, hovered ? 0xFFF1F5F3 : 0xFF53666C);
        ResourceLocation texture = iconTexture(part);
        int inset = Math.max(2, size / 8);
        int drawSize = size - inset * 2;
        g.blit(texture, x + inset, y + inset, drawSize, drawSize,
            0, 0, 16, 16, 16, 16);
        if (injury >= 100.0F) {
            g.setColor(1.0F, 0.08F, 0.08F, 1.0F);
            g.blit(texture, x + inset, y + inset, drawSize, drawSize,
                0, 0, 16, 16, 16, 16);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static ResourceLocation iconTexture(Part part) {
        String path = switch (part) {
            case HEAD -> "head";
            case CHEST -> "chest";
            case LEFT_ARM -> "left_arm";
            case RIGHT_ARM -> "right_arm";
            case ABDOMEN -> "abdomen";
            case LEFT_LEG -> "left_leg";
            case RIGHT_LEG -> "right_leg";
        };
        return ResourceLocation.fromNamespaceAndPath(
            "xero_delta", "textures/gui/status/" + path + ".png");
    }
    public static void renderTooltip(GuiGraphics g, Font font, Part part, float injury, int mouseX, int mouseY) {
        String text = part.label().getString() + Character.toString(32) + Math.round(Mth.clamp(injury, 0.0F, 100.0F)) + Character.toString(37);
        g.renderTooltip(font, Component.literal(text), mouseX, mouseY);
    }

    public static int injuryColor(float injury) {
        float p = Mth.clamp(injury / 100.0F, 0.0F, 1.0F);
        return 0xFF000000 | Math.round(112 + 143 * p) << 16
            | Math.round(211 * (1.0F - p)) << 8 | Math.round(173 * (1.0F - p));
    }
}
