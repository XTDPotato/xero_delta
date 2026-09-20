package com.xtdpotato.xero_delta.client;

import com.mojang.math.Axis;
import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.screen.SafetyBoxLayoutPack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SafetyBoxWidgetRenderer {
    @FunctionalInterface
    public interface GridContentRenderer {
        void render(GuiGraphics graphics, SafetyBoxLayoutPack.WidgetNode node, int x, int y, int width, int height);
    }
    public record Bounds(int minX, int minY, int maxX, int maxY) {
        public Bounds include(Bounds other) {
            return new Bounds(Math.min(minX, other.minX), Math.min(minY, other.minY),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY));
        }
    }

    private SafetyBoxWidgetRenderer() {
    }

    public static Bounds render(GuiGraphics graphics, Font font, SafetyBoxLayoutPack.LayoutData layout,
                                int originX, int originY, int parentWidth, int parentHeight,
                                ItemStack iconStack, String title) {
        return render(graphics, font, layout, originX, originY, parentWidth, parentHeight, iconStack, title, null);
    }

    public static Bounds render(GuiGraphics graphics, Font font, SafetyBoxLayoutPack.LayoutData layout,
                                int originX, int originY, int parentWidth, int parentHeight,
                                ItemStack iconStack, String title, GridContentRenderer gridContentRenderer) {
        if (layout == null || layout.root == null) return new Bounds(originX, originY, originX, originY);
        return renderChildren(graphics, font, layout.root, originX, originY, parentWidth, parentHeight,
            iconStack, title, new Bounds(originX, originY, originX + parentWidth, originY + parentHeight), gridContentRenderer);
    }

    private static Bounds renderChildren(GuiGraphics graphics, Font font, SafetyBoxLayoutPack.WidgetNode parent,
                                         int originX, int originY, int parentWidth, int parentHeight,
                                         ItemStack iconStack, String title, Bounds bounds) {
        return renderChildren(graphics, font, parent, originX, originY, parentWidth, parentHeight,
            iconStack, title, bounds, null);
    }

    private static Bounds renderChildren(GuiGraphics graphics, Font font, SafetyBoxLayoutPack.WidgetNode parent,
                                         int originX, int originY, int parentWidth, int parentHeight,
                                         ItemStack iconStack, String title, Bounds bounds, GridContentRenderer gridContentRenderer) {
        List<SafetyBoxLayoutPack.WidgetNode> children = new ArrayList<>(parent.children);
        children.sort(Comparator.comparingInt(node -> node.z));
        Bounds result = bounds;
        for (SafetyBoxLayoutPack.WidgetNode node : children) {
            int width = width(font, node, parentWidth, title);
            int height = height(node, parentHeight);
            int x = originX + node.x;
            int y = originY + node.y;
            Bounds nodeBounds = new Bounds(x, y, x + width, y + height);
            result = result.include(nodeBounds);
            if (!node.legacyBridge) renderNode(graphics, font, node, x, y, width, height, iconStack, title, gridContentRenderer);
            result = renderChildren(graphics, font, node, x, y, width, height, iconStack, title, result, gridContentRenderer);
        }
        return result;
    }

    private static void renderNode(GuiGraphics graphics, Font font, SafetyBoxLayoutPack.WidgetNode node,
                                   int x, int y, int width, int height, ItemStack iconStack, String title) {
        renderNode(graphics, font, node, x, y, width, height, iconStack, title, null);
    }

    private static void renderNode(GuiGraphics graphics, Font font, SafetyBoxLayoutPack.WidgetNode node,
                                   int x, int y, int width, int height, ItemStack iconStack, String title,
                                   GridContentRenderer gridContentRenderer) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + width / 2.0, y + height / 2.0, node.z);
        pose.mulPose(Axis.ZP.rotationDegrees(node.rotation));
        pose.translate(-width / 2.0, -height / 2.0, 0);
        if ((node.backgroundColor >>> 24) != 0) fillRounded(graphics, 0, 0, width, height, node.cornerRadius, node.backgroundColor);

        if ("text".equals(node.type)) {
            String text = "#delta_pack_name".equals(node.text) ? title : node.text == null ? "" : node.text;
            int color = "#quality_color".equals(node.textColor)
                ? Config.INSTANCE.getQualityColor(ClientDataCache.INSTANCE.getQuality(iconStack)) | 0xFF000000
                : color(node.textColor, 0xFFFFFFFF);
            pose.pushPose();
            pose.scale((float)Math.max(0.2, node.fontSize), (float)Math.max(0.2, node.fontSize), 1);
            if (node.verticalText) {
                int line = 0;
                for (int codePoint : text.codePoints().toArray()) {
                    graphics.drawString(font, new String(Character.toChars(codePoint)), 0,
                        line++ * font.lineHeight, color, false);
                }
            } else {
                graphics.drawString(font, text, 0, 0, color, false);
            }
            pose.popPose();
        } else if ("image".equals(node.type)) {
            renderImage(graphics, font, node, width, height, iconStack);
        } else if ("grid".equals(node.type)) {
            int cellWidth = Math.max(1, width / Math.max(1, node.gridColumns));
            int cellHeight = Math.max(1, height / Math.max(1, node.gridRows));
            for (int row = 0; row < node.gridRows; row++) {
                for (int column = 0; column < node.gridColumns; column++) {
                    DeltaGridCellRenderer.render(graphics,
                        column * cellWidth, row * cellHeight, cellWidth, cellHeight);
                }
            }
            if (gridContentRenderer != null) gridContentRenderer.render(graphics, node, x, y, width, height);
        }
        renderBorders(graphics, node, width, height);
        pose.popPose();
    }

    private static void renderImage(GuiGraphics graphics, Font font, SafetyBoxLayoutPack.WidgetNode node,
                                    int width, int height, ItemStack iconStack) {
        if ("#delta_pack_icon".equals(node.imagePath)) {
            graphics.pose().pushPose();
            float scale = Math.max(0.1f, Math.min(width, height) / 16.0f);
            graphics.pose().scale(scale, scale, 1);
            graphics.renderItem(iconStack, 0, 0);
            graphics.pose().popPose();
            return;
        }
        if (node.imagePath != null && node.imagePath.startsWith("item:")) {
            try {
                ItemStack stack = BuiltInRegistries.ITEM.get(ResourceLocation.parse(node.imagePath.substring(5))).getDefaultInstance();
                graphics.pose().pushPose();
                float scale = Math.max(0.1f, Math.min(width, height) / 16.0f);
                graphics.pose().scale(scale, scale, 1);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
                return;
            } catch (Exception ignored) {
            }
        }
        if (WidgetImageCache.render(graphics, node.imagePath, 0, 0, width, height)) return;
        try {
            ResourceLocation texture = ResourceLocation.parse(node.imagePath);
            graphics.blit(texture, 0, 0, 0, 0, width, height, width, height);
        } catch (Exception ignored) {
            graphics.fill(0, 0, width, height, 0x403F7FBF);
            graphics.drawString(font, font.plainSubstrByWidth(node.imagePath == null ? "Image" : node.imagePath,
                Math.max(1, width - 4)), 2, 2, 0xFFFFFFFF, false);
        }
    }

    private static void renderBorders(GuiGraphics graphics, SafetyBoxLayoutPack.WidgetNode node,
                                      int width, int height) {
        if (node.outerBorder.enabled) {
            int borderColor = color(node.outerBorder.color, 0xFFFFFFFF);
            for (int offset = 0; offset < node.outerBorder.size; offset++) {
                graphics.renderOutline(-offset, -offset, width + offset * 2, height + offset * 2, borderColor);
            }
        }
        if (node.innerBorder.enabled) {
            int borderColor = color(node.innerBorder.color, 0xFFFFFFFF);
            for (int offset = 0; offset < node.innerBorder.size; offset++) {
                int inset = offset + 1;
                if (width > inset * 2 && height > inset * 2) {
                    graphics.renderOutline(inset, inset, width - inset * 2, height - inset * 2, borderColor);
                }
            }
        }
    }

    private static int width(Font font, SafetyBoxLayoutPack.WidgetNode node, int parentWidth, String title) {
        if (node.width == -1) return Math.max(1, parentWidth);
        if (node.width > 0) return node.width;
        if ("layout".equals(node.type)) {
            int extent = 1;
            for (SafetyBoxLayoutPack.WidgetNode child : node.children) {
                extent = Math.max(extent, child.x + width(font, child, parentWidth, title));
            }
            return extent;
        }
        if ("text".equals(node.type)) {
            String text = "#delta_pack_name".equals(node.text) ? title : node.text;
            if (node.verticalText) {
                int widest = (text == null ? "" : text).codePoints()
                    .map(codePoint -> font.width(new String(Character.toChars(codePoint))))
                    .max().orElse(1);
                return Math.max(1, (int)(widest * node.fontSize));
            }
            return Math.max(1, (int)(font.width(text == null ? "" : text) * node.fontSize));
        }
        if ("grid".equals(node.type)) return Math.max(1, node.gridColumns * 18);
        return 16;
    }

    private static int height(SafetyBoxLayoutPack.WidgetNode node, int parentHeight) {
        if (node.height == -1) return Math.max(1, parentHeight);
        if (node.height > 0) return node.height;
        if ("layout".equals(node.type)) {
            int extent = 1;
            for (SafetyBoxLayoutPack.WidgetNode child : node.children) {
                extent = Math.max(extent, child.y + height(child, parentHeight));
            }
            return extent;
        }
        if ("text".equals(node.type)) {
            int lines = node.verticalText ? Math.max(1, (int)(node.text == null ? 0 : node.text.codePoints().count())) : 1;
            return Math.max(1, (int)(9 * lines * node.fontSize));
        }
        if ("grid".equals(node.type)) return Math.max(1, node.gridRows * 18);
        return 16;
    }

    private static int color(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            String normalized = value.startsWith("#") ? "0x" + value.substring(1) : value;
            return (int)Long.decode(normalized).longValue();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void fillRounded(GuiGraphics graphics, int x, int y, int width, int height,
                                    int radius, int color) {
        int actualRadius = Math.max(0, Math.min(Math.min(width, height) / 2, radius));
        if (actualRadius == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + actualRadius, y, x + width - actualRadius, y + height, color);
        graphics.fill(x, y + actualRadius, x + width, y + height - actualRadius, color);
        for (int offset = 0; offset < actualRadius; offset++) {
            int inset = actualRadius - (int)Math.sqrt(actualRadius * actualRadius - offset * offset);
            graphics.fill(x + inset, y + offset, x + width - inset, y + offset + 1, color);
            graphics.fill(x + inset, y + height - offset - 1, x + width - inset, y + height - offset, color);
        }
    }
}
