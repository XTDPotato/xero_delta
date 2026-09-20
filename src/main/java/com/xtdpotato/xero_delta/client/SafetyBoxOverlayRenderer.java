package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.grid.GridGeometry;
import com.xtdpotato.xero_delta.screen.SafetyBoxLayoutPack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Shared overlay renderer 閳?renders ONLY header elements (icon, title, border).
 * Does NOT render slot backgrounds, cell fills, or grid backgrounds.
 * Those are handled exclusively by vanilla's Slot system (for overlay)
 * or by the caller for preview.
 */
public class SafetyBoxOverlayRenderer {

    public record HeaderResult(int headerX, int headerY, int headerW, int headerH,
                                int iconCX, int iconCY, int iconSize,
                                int textX, int textY, int textW, int textH) {}
    public record HeaderSize(int width, int height) {}

    /**
     * Render header only (icon + title + border). No grid/slot backgrounds.
     * Caller provides GridGeometry for grid positions; this method does NOT draw the grid.
     */
    public static HeaderResult renderHeader(GuiGraphics g, Font font,
                                             GridGeometry geom,
                                             SafetyBoxLayoutPack.LayoutData ld,
                                             float scale,
                                             ItemStack iconStack, String title) {

        Config.Layout layout = Config.Layout.valueOf(ld.layout);
        int bp = (int)(ld.borderPad * scale);
        float iconScale = (float) ld.iconScale;
        float textScale = (float) ld.textScale;
        int iconSize = (int)(16 * iconScale * scale);
        int textH = (int)(9 * textScale * scale);
        int tpad = (int)(ld.textPad * scale);
        int ioffX = (int)(ld.iconOffX * scale);
        int ioffY = (int)(ld.iconOffY * scale);
        int toffX = (int)(ld.textOffX * scale);
        int toffY = (int)(ld.textOffY * scale);
        int boffX = (int)(ld.borderOffX * scale);
        int boffY = (int)(ld.borderOffY * scale);
        boolean vertText = ld.verticalText;

        HeaderSize header = computeHeaderSize(font, geom, ld, scale, title);
        int headerW = header.width();
        int headerH = header.height();

        int hx, hy;
        if (layout == Config.Layout.TOP) {
            hx = geom.gridX() + (geom.pixelWidth() - headerW) / 2 + boffX;
            hy = geom.gridY() - headerH - (int)(4 * scale) + boffY;
        } else if (layout == Config.Layout.BOTTOM) {
            hx = geom.gridX() + (geom.pixelWidth() - headerW) / 2 + boffX;
            hy = geom.gridY() + geom.pixelHeight() + (int)(4 * scale) + boffY;
        } else if (layout == Config.Layout.LEFT) {
            hx = geom.gridX() - headerW - (int)(4 * scale) + boffX;
            hy = geom.gridY() + (geom.pixelHeight() - headerH) / 2 + boffY;
        } else { // RIGHT
            hx = geom.gridX() + geom.pixelWidth() + (int)(4 * scale) + boffX;
            hy = geom.gridY() + (geom.pixelHeight() - headerH) / 2 + boffY;
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 150);

        // Header background
        g.fill(hx, hy, hx + headerW, hy + headerH, 0xC0101010);
        // Header border
        g.fill(hx, hy, hx + headerW, hy + 1, 0xFF373737);
        g.fill(hx, hy + headerH - 1, hx + headerW, hy + headerH, 0xFF373737);
        g.fill(hx, hy, hx + 1, hy + headerH, 0xFF373737);
        g.fill(hx + headerW - 1, hy, hx + headerW, hy + headerH, 0xFF373737);

        // Icon
        float iconCX = hx + headerW / 2f + ioffX;
        float iconCY;
        if (vertText) {
            iconCY = hy + iconSize / 2f + bp + tpad + (int)(2 * scale) + ioffY;
        } else {
            iconCY = hy + headerH / 2f + ioffY;
        }
        renderHeaderIcon(g, ld, iconStack, iconCX, iconCY, iconSize, iconScale * scale);

        // Title text
        int textRX = 0, textRY = 0, textRW = 0, textRH = 0;
        if (vertText) {
            float cs = textScale * scale;
            float tx = hx + headerW / 2f + toffX;
            float ty = hy + iconSize + bp + tpad + (int)(6 * scale) + toffY;
            textRX = (int)(tx - font.width("\u5B89") * cs / 2f);
            textRY = (int)ty;
            textRW = (int)(font.width("\u5B89") * cs);
            textRH = title.length() * 11;
            pose.pushPose();
            pose.translate(tx, ty, 0);
            pose.scale(cs, cs, 1);
            for (int i = 0; i < title.length(); i++) {
                String ch = String.valueOf(title.charAt(i));
                g.drawString(font, ch, -font.width(ch) / 2, i * 11, 0xFFFFFF, false);
            }
            pose.popPose();
        } else {
            float ts = textScale * scale;
            float tx = hx + iconSize + bp + tpad + (int)(4 * scale) + toffX;
            float ty = hy + headerH / 2f - textH / 2f + toffY;
            textRX = (int)tx;
            textRY = (int)ty;
            textRW = (int)(font.width(title) * ts);
            textRH = textH;
            pose.pushPose();
            pose.translate(tx, ty, 0);
            pose.scale(ts, ts, 1);
            g.drawString(font, title, 0, 0, 0xFFFFFF, false);
            pose.popPose();
        }
        pose.popPose();

        return new HeaderResult(hx, hy, headerW, headerH,
                                (int)iconCX, (int)iconCY, iconSize,
                                textRX, textRY, textRW, textRH);
    }

    private static void renderHeaderIcon(GuiGraphics graphics, SafetyBoxLayoutPack.LayoutData layout,
                                         ItemStack fallback, float centerX, float centerY,
                                         int iconSize, float fallbackScale) {
        String path = legacyIconPath(layout);
        int x = (int)(centerX - iconSize / 2f);
        int y = (int)(centerY - iconSize / 2f);
        if (path != null && !"#delta_pack_icon".equals(path)) {
            if (path.startsWith("item:")) {
                try {
                    ItemStack item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(path.substring(5))).getDefaultInstance();
                    graphics.pose().pushPose();
                    graphics.pose().translate(x, y, 0);
                    float itemScale = Math.max(0.1f, iconSize / 16.0f);
                    graphics.pose().scale(itemScale, itemScale, 1);
                    graphics.renderItem(item, 0, 0);
                    graphics.pose().popPose();
                    return;
                } catch (Exception ignored) {
                }
            }
            if (WidgetImageCache.render(graphics, path, x, y, iconSize, iconSize)) return;
            try {
                ResourceLocation resource = ResourceLocation.parse(path);
                graphics.blit(resource, x, y, 0, 0, iconSize, iconSize, iconSize, iconSize);
                return;
            } catch (Exception ignored) {
            }
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(fallbackScale, fallbackScale, 1);
        if (!fallback.isEmpty()) graphics.renderItem(fallback, 0, 0);
        graphics.pose().popPose();
    }

    private static String legacyIconPath(SafetyBoxLayoutPack.LayoutData layout) {
        if (layout == null || layout.root == null) return null;
        for (SafetyBoxLayoutPack.WidgetNode node : SafetyBoxLayoutPack.flattenWidgets(layout)) {
            if (node.legacyBridge && "image".equals(node.type) && "Icon Widget".equals(node.name)) {
                return node.imagePath;
            }
        }
        return null;
    }

    /**
     * Legacy entry point used by layout preview.
     * Renders header + placement preview highlights.
     * Does NOT render grid backgrounds or cell fills.
     */
    public static HeaderResult render(GuiGraphics g, Font font,
                                       int anchorX, int anchorY, float scale,
                                       SafetyBoxLayoutPack.LayoutData ld,
                                       ItemStack iconStack, String title,
                                       int gridCols, int gridRows) {
        Config.Layout layout = Config.Layout.valueOf(ld.layout);
        float gs = (float) ld.gridScale;
        int goffX = (int)(ld.gridOffX * scale);
        int goffY = (int)(ld.gridOffY * scale);
        int boffX = (int)(ld.borderOffX * scale);
        int boffY = (int)(ld.borderOffY * scale);

        // Compute header size for layout positioning
        int bp = (int)(ld.borderPad * scale);
        float iconScale = (float) ld.iconScale;
        float textScale = (float) ld.textScale;
        int iconSize = (int)(16 * iconScale * scale);
        int textH = (int)(9 * textScale * scale);
        int tpad = (int)(ld.textPad * scale);
        boolean vertText = ld.verticalText;
        int gridWpx = (int)(gridCols * 18 * scale * gs);
        int gridHpx = (int)(gridRows * 18 * scale * gs);
        GridGeometry baseGeom = new GridGeometry(gridCols, gridRows, 0, 0, scale, gs);
        HeaderSize header = computeHeaderSize(font, baseGeom, ld, scale, title);
        int headerW = header.width();
        int headerH = header.height();

        int gpx, gpy, hx, hy;
        switch (layout) {
            case LEFT -> {
                hx = anchorX + boffX; hy = anchorY + (gridHpx - headerH) / 2 + boffY;
                gpx = anchorX + headerW + 4 + goffX; gpy = anchorY + goffY;
            }
            case RIGHT -> {
                gpx = anchorX + goffX; gpy = anchorY + goffY;
                hx = anchorX + gridWpx + 4 + boffX; hy = anchorY + (gridHpx - headerH) / 2 + boffY;
            }
            case BOTTOM -> {
                gpx = anchorX + goffX; gpy = anchorY + goffY;
                hx = anchorX + (gridWpx - headerW) / 2 + boffX; hy = anchorY + gridHpx + 4 + boffY;
            }
            default -> {
                hx = anchorX + (gridWpx - headerW) / 2 + boffX; hy = anchorY + boffY;
                gpx = anchorX + goffX; gpy = anchorY + headerH + 4 + goffY;
            }
        }

        // Apply grid offsets for non-side layouts
        if (layout != Config.Layout.LEFT && layout != Config.Layout.RIGHT) {
            gpx += goffX; gpy += goffY;
        }

        GridGeometry geom = new GridGeometry(gridCols, gridRows, gpx, gpy, scale, gs);

        // Render header
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 150);

        // Header background
        g.fill(hx, hy, hx + headerW, hy + headerH, 0xC0101010);
        // Header border
        g.fill(hx, hy, hx + headerW, hy + 1, 0xFF373737);
        g.fill(hx, hy + headerH - 1, hx + headerW, hy + headerH, 0xFF373737);
        g.fill(hx, hy, hx + 1, hy + headerH, 0xFF373737);
        g.fill(hx + headerW - 1, hy, hx + headerW, hy + headerH, 0xFF373737);

        // Icon
        float iconCX = hx + headerW / 2f + (int)(ld.iconOffX * scale);
        float iconCY;
        if (vertText) {
            iconCY = hy + iconSize / 2f + bp + tpad + (int)(2 * scale) + (int)(ld.iconOffY * scale);
        } else {
            iconCY = hy + headerH / 2f + (int)(ld.iconOffY * scale);
        }
        pose.pushPose();
        pose.translate(iconCX - iconSize / 2f, iconCY - iconSize / 2f, 0);
        pose.scale(iconScale * scale, iconScale * scale, 1);
        if (!iconStack.isEmpty()) g.renderItem(iconStack, 0, 0);
        pose.popPose();

        // Title text
        int textRX = 0, textRY = 0, textRW = 0, textRH = 0;
        if (vertText) {
            float cs = textScale * scale;
            float tx = hx + headerW / 2f + (int)(ld.textOffX * scale);
            float ty = hy + iconSize + bp + tpad + (int)(6 * scale) + (int)(ld.textOffY * scale);
            textRX = (int)(tx - font.width("\u5B89") * cs / 2f);
            textRY = (int)ty;
            textRW = (int)(font.width("\u5B89") * cs);
            textRH = title.length() * 11;
            pose.pushPose();
            pose.translate(tx, ty, 0);
            pose.scale(cs, cs, 1);
            for (int i = 0; i < title.length(); i++) {
                String ch = String.valueOf(title.charAt(i));
                g.drawString(font, ch, -font.width(ch) / 2, i * 11, 0xFFFFFF, false);
            }
            pose.popPose();
        } else {
            float ts = textScale * scale;
            float tx = hx + iconSize + bp + tpad + (int)(4 * scale) + (int)(ld.textOffX * scale);
            float ty = hy + headerH / 2f - textH / 2f + (int)(ld.textOffY * scale);
            textRX = (int)tx;
            textRY = (int)ty;
            textRW = (int)(font.width(title) * ts);
            textRH = textH;
            pose.pushPose();
            pose.translate(tx, ty, 0);
            pose.scale(ts, ts, 1);
            g.drawString(font, title, 0, 0, 0xFFFFFF, false);
            pose.popPose();
        }
        pose.popPose();

        return new HeaderResult(hx, hy, headerW, headerH,
                                (int)iconCX, (int)iconCY, iconSize,
                                textRX, textRY, textRW, textRH);
    }

    /** Render grid cells with the shared Delta inventory chrome. */
    public static void renderGridCells(GuiGraphics g, GridGeometry geom) {
        DeltaGridCellRenderer.renderGrid(g, geom);
    }

    public static HeaderSize computeHeaderSize(Font font, GridGeometry geom,
                                               SafetyBoxLayoutPack.LayoutData ld,
                                               float scale, String title) {
        Config.Layout layout = Config.Layout.valueOf(ld.layout);
        int bp = (int)(ld.borderPad * scale);
        int iconSize = (int)(16 * ld.iconScale * scale);
        int textH = (int)(9 * ld.textScale * scale);
        int tpad = (int)(ld.textPad * scale);
        boolean vertText = ld.verticalText;

        int headerH = vertText
            ? iconSize + bp * 2 + title.length() * (int)(11 * ld.textScale * scale) + tpad * 2 + (int)(8 * scale)
            : Math.max(iconSize, textH) + bp * 2 + tpad * 2 + (int)(4 * scale);

        int headerW;
        if (vertText && (layout == Config.Layout.TOP || layout == Config.Layout.BOTTOM)) {
            headerW = Math.max(iconSize, font.width("\u5B89")) + bp * 2 + tpad * 2 + (int)(10 * scale);
        } else if (layout == Config.Layout.TOP || layout == Config.Layout.BOTTOM) {
            headerW = geom.pixelWidth();
        } else {
            headerW = Math.max(iconSize, (int)(font.width(title) * ld.textScale * scale)) + bp * 2 + tpad * 2 + (int)(10 * scale);
        }

        if (isExtendedSafetyBox(ld)) {
            if (ld.bgW <= 0) headerW = (int)(50 * scale);
            if (ld.bgH <= 0) headerH = (int)(9 * scale);
        }
        if (ld.bgW > 0) headerW = (int)(ld.bgW * scale);
        if (ld.bgH > 0) headerH = (int)(ld.bgH * scale);
        return new HeaderSize(Math.max(1, headerW), Math.max(1, headerH));
    }

    private static boolean isExtendedSafetyBox(SafetyBoxLayoutPack.LayoutData ld) {
        return ld.boxId != null && ld.boxId.contains("safety_box_4x2");
    }
}
