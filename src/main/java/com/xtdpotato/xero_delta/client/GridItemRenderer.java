package com.xtdpotato.xero_delta.client;

import com.mojang.math.Axis;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.BoundItemPolicy;
import com.xtdpotato.xero_delta.data.ItemSize;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class GridItemRenderer {
    public static final int ITEM_Z = 100;
    public static final int STACK_COUNT_Z = 300;
    public static final int BOUND_ICON_Z = 500;
    private static final ResourceLocation BOUND_ICON = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "textures/gui/item_bound.png");

    private GridItemRenderer() {
    }

    public static void renderSizedItem(GuiGraphics graphics, Font font, ItemStack stack,
                                       int x, int y, int width, int height,
                                       boolean rotated, boolean rotateTexture, boolean stretchTexture) {
        renderSizedItem(graphics, font, stack, x, y, width, height, rotated, rotateTexture, stretchTexture, 0);
    }

    public static void renderSizedItem(GuiGraphics graphics, Font font, ItemStack stack,
                                       int x, int y, int width, int height,
                                       boolean rotated, boolean rotateTexture, boolean stretchTexture,
                                       int proportionalScale) {
        if (stack.isEmpty()) return;
        if (!QualityItemBackground.isSuppressed()) {
            graphics.fill(x, y, x + width, y + height, QualityItemBackground.color(stack));
        }

        float availableWidth = Math.max(1.0F, width - 2.0F);
        float availableHeight = Math.max(1.0F, height - 2.0F);
        float textureAspect = ItemTextureAspectCache.get(stack);
        float displayedAspect = rotated && rotateTexture ? 1.0F / textureAspect : textureAspect;
        float renderWidth;
        float renderHeight;
        if (stretchTexture) {
            renderWidth = Math.max(16.0F, availableWidth);
            renderHeight = Math.max(16.0F, availableHeight);
        } else if (availableWidth / availableHeight > displayedAspect) {
            renderHeight = availableHeight;
            renderWidth = renderHeight * displayedAspect;
        } else {
            renderWidth = availableWidth;
            renderHeight = renderWidth / displayedAspect;
        }
        // Keep the editor/command proportional-size cap while preserving the
        // source aspect ratio and centered contain behavior.
        if (proportionalScale > 0) {
            float cap = Math.max(1.0F, Math.min(width, height) * proportionalScale - 2.0F);
            float capScale = Math.min(1.0F, cap / Math.max(renderWidth, renderHeight));
            renderWidth *= capScale;
            renderHeight *= capScale;
        }

        var pose = graphics.pose();
        pose.pushPose();
        if (rotated && rotateTexture) {
            pose.translate(x + width / 2.0f, y + height / 2.0f, ITEM_Z);
            pose.mulPose(Axis.ZP.rotationDegrees(90));
            pose.translate(-renderHeight / 2.0f, -renderWidth / 2.0f, 0);
            pose.scale(renderHeight / 16.0f, renderWidth / 16.0f, 1);
        } else {
            pose.translate(x + (width - renderWidth) / 2.0f, y + (height - renderHeight) / 2.0f, ITEM_Z);
            pose.scale(renderWidth / 16.0f, renderHeight / 16.0f, 1);
        }
        QualityItemBackground.pushSuppress();
        try {
            // GuiGraphics retains its tint between draws. Several surrounding
            // overlays use colored icons, so explicitly restore white before
            // rendering each item to keep its 64×64 sprite unmodified.
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.renderItem(stack, 0, 0);
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            QualityItemBackground.popSuppress();
        }
        pose.popPose();

        renderStackCount(graphics, font, stack, x + width, y + height);
        renderBoundIcon(graphics, stack, x, y, width, height);
    }

    public static ItemSize orientedSize(ItemStack stack, ItemSize baseSize, boolean rotated) {
        return rotated ? baseSize.rotated() : baseSize;
    }

    public static void renderStackCount(GuiGraphics graphics, Font font, ItemStack stack, int right, int bottom) {
        if (stack.getCount() <= 1) return;
        String text = String.valueOf(stack.getCount());
        int tx = right - font.width(text) - 1;
        int ty = bottom - 9;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, STACK_COUNT_Z);
        graphics.drawString(font, text, tx + 1, ty + 1, 0xFF000000, false);
        graphics.drawString(font, text, tx, ty, 0xFFFFFFFF, false);
        pose.popPose();
    }

    private static void renderBoundIcon(GuiGraphics graphics, ItemStack stack,
                                        int x, int y, int width, int height) {
        if (!BoundItemPolicy.isBound(stack)) return;
        int size = Math.max(6, Math.min(12, Math.min(width, height) / 3));
        int iconX = x + 2;
        int iconY = y + height - size - 2;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, BOUND_ICON_Z);
        graphics.blit(BOUND_ICON, iconX, iconY, 0, 0, size, size, 16, 16);
        pose.popPose();
    }
}
