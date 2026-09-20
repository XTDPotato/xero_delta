package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.TaczCompatibilityRules;
import com.xtdpotato.xero_delta.network.CombatFeedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Delta-style red-down and successful-rescue feed in the upper-right corner. */
public final class CombatFeedHudRenderer {
    private static final ResourceLocation DOWNED = icon("downed.png");
    private static final ResourceLocation HEADSHOT = icon("headshot.png");
    private static final ResourceLocation RESCUE = icon("rescue.png");
    private static final ResourceLocation KNIFE = icon("knife.png");
    private static final int ALLY = 0xFF62B9E9;
    private static final int ENEMY = 0xFFE34D45;
    private static final int RESCUED = 0xFFE7C878;

    private CombatFeedHudRenderer() {
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.player == null) return;
        List<CombatFeedClientState.Entry> entries =
            CombatFeedClientState.INSTANCE.activeEntries();
        int right = minecraft.getWindow().getGuiScaledWidth() - 12;
        int y = 28;
        for (CombatFeedClientState.Entry entry : entries) {
            renderEntry(graphics, minecraft, entry.packet(), right, y);
            y += 21;
        }
    }

    private static void renderEntry(GuiGraphics graphics, Minecraft minecraft,
                                    CombatFeedPacket packet, int right, int y) {
        int actorWidth = minecraft.font.width(packet.actorName());
        int targetWidth = minecraft.font.width(packet.targetName());
        int iconWidth = packet.eventType() == CombatFeedPacket.RESCUED ? 18
            : weaponWidth(packet.weapon()) + 18;
        int x = right - actorWidth - targetWidth - iconWidth - 8;

        graphics.drawString(minecraft.font, packet.actorName(), x, y + 5,
            packet.actorAlly() ? ALLY : ENEMY, true);
        x += actorWidth + 5;
        if (packet.eventType() == CombatFeedPacket.RESCUED) {
            drawIcon(graphics, RESCUE, x, y + 1, 16, 16);
            x += 19;
            graphics.drawString(minecraft.font, packet.targetName(), x, y + 5,
                RESCUED, true);
            return;
        }

        x += drawWeapon(graphics, packet.weapon(), x, y);
        drawIcon(graphics, packet.headshot() ? HEADSHOT : DOWNED,
            x, y + 1, 16, 16);
        x += 19;
        graphics.drawString(minecraft.font, packet.targetName(), x, y + 5,
            packet.targetAlly() ? ALLY : ENEMY, true);
    }

    private static int weaponWidth(ItemStack stack) {
        if (stack != null && !stack.isEmpty() && TaczCompatibilityRules.isLrTacticalMelee(stack)) {
            return 25;
        }
        WeaponSlotTextureResolver.Icon icon = WeaponSlotTextureResolver.resolve(stack);
        if (icon == null) return 19;
        return fittedWidth(icon, 42, 16) + 3;
    }

    private static int drawWeapon(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (stack != null && !stack.isEmpty() && TaczCompatibilityRules.isLrTacticalMelee(stack)) {
            drawIcon(graphics, KNIFE, x, y, 22, 18);
            return 25;
        }
        WeaponSlotTextureResolver.Icon icon = WeaponSlotTextureResolver.resolve(stack);
        if (icon != null) {
            int width = fittedWidth(icon, 42, 16);
            int height = Math.max(1, Math.round(width * icon.height() / (float) icon.width()));
            int drawY = y + (18 - height) / 2;
            graphics.blit(icon.texture(), x, drawY, width, height,
                0, 0, icon.width(), icon.height(), icon.width(), icon.height());
            return width + 3;
        }
        if (stack != null && !stack.isEmpty()) graphics.renderItem(stack, x, y + 1);
        return 19;
    }

    private static int fittedWidth(WeaponSlotTextureResolver.Icon icon,
                                   int maximumWidth, int maximumHeight) {
        float aspect = icon.width() / (float) icon.height();
        return Math.max(1, Math.min(maximumWidth, Math.round(maximumHeight * aspect)));
    }

    private static void drawIcon(GuiGraphics graphics, ResourceLocation texture,
                                 int x, int y, int width, int height) {
        graphics.blit(texture, x, y, width, height, 0, 0, 32, 32, 32, 32);
    }

    private static ResourceLocation icon(String file) {
        return ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID,
            "textures/gui/combat/" + file);
    }
}
