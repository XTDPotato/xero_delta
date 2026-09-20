package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.grid.GridGeometry;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.screen.SafetyBoxLayoutPack;
import java.text.NumberFormat;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class GridPreviewClientComponent implements ClientTooltipComponent {
    private static final int PADDING = 4;
    private static final int GAP = 4;
    private static final int VALUE_LINE_H = 12;
    private static final int PACK_INFO_H = 30;
    private static final ResourceLocation COIN_TEX =
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "textures/quality/coin.png");
    private static final NumberFormat PRICE_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final GridPreviewData data;

    public GridPreviewClientComponent(GridPreviewData data) {
        this.data = data;
    }

    @Override
    public int getHeight() {
        return measure(Minecraft.getInstance().font).height();
    }

    @Override
    public int getWidth(Font font) {
        return measure(font).width();
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        Metrics m = measure(font);
        SafetyBoxLayoutPack.LayoutData ld = layoutData();
        GridGeometry geom = new GridGeometry(data.gridWidth(), data.gridHeight(),
            x + m.gridX(), y + m.gridY(), 1.0f, (float) ld.gridScale);

        ItemStack boxStack = data.boxStack();
        String title = title(ld, boxStack);
        SafetyBoxOverlayRenderer.renderHeader(g, font, geom, ld, 1.0f, boxStack, title);
        renderGrid(g, Minecraft.getInstance(), geom);

        long total = totalValue();
        if (total > 0) {
            int lineY = y + m.valueY();
            String valueText = valueText(total);
            g.blit(COIN_TEX, x + PADDING, lineY + 1, 0, 0, 9, 9, 9, 9);
            g.drawString(font, valueText, x + PADDING + 12, lineY + 2, 0xFFDAA520, false);
        }
        if (boxStack.getItem() instanceof DeltaPackItem pack) {
            int infoY = y + m.packY();
            g.drawString(font, Component.translatable("tooltip.xero_delta.pack.capacity",
                pack.capacity()), x + PADDING, infoY + 1, 0xFFE6E6E6, false);
            ResourceLocation speedIcon = movementSpeedIcon(pack.speedPenalty());
            g.blit(speedIcon, x + PADDING, infoY + 12, 0, 0, 16, 16, 16, 16);
            String speed = BigDecimal.valueOf(-pack.speedPenalty() * 100.0D)
                .stripTrailingZeros().toPlainString();
            g.drawString(font, Component.translatable("tooltip.xero_delta.pack.movement_speed",
                speed), x + PADDING + 20, infoY + 16, 0xFFFF7777, false);
        }
    }

    private void renderGrid(GuiGraphics g, Minecraft mc, GridGeometry geom) {
        DeltaGridCellRenderer.renderGrid(g, geom);
        int cell = geom.cellSize();
        if (geom.isSplit()) {
            int sepX = geom.separatorX();
            g.fill(sepX, geom.gridY(), sepX + 1, geom.gridY() + geom.pixelHeight(), 0xFF555555);
        }

        List<ItemStack> items = data.items();
        for (int i = 0; i < items.size() && i < geom.cols() * geom.rows(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            int cx = i % geom.cols();
            int cy = i / geom.cols();
            boolean rotated = GridBackingStore.isRotated(stack);
            ItemSize size = ClientDataCache.INSTANCE.getSize(stack);
            if (rotated) size = size.rotated();
            int endCx = Math.min(geom.cols() - 1, cx + size.width() - 1);
            int endCy = Math.min(geom.rows() - 1, cy + size.height() - 1);
            int x1 = geom.cellX(cx);
            int y1 = geom.cellY(cy);
            int x2 = geom.cellX(endCx) + cell;
            int y2 = geom.cellY(endCy) + cell;
            GridItemRenderer.renderSizedItem(g, mc.font, stack, x1, y1, x2 - x1, y2 - y1,
                rotated, ClientDataCache.INSTANCE.shouldRotateTexture(stack),
                ClientDataCache.INSTANCE.shouldStretchTexture(stack),
                ClientDataCache.INSTANCE.proportionalTextureScale(stack));
        }
    }

    private Metrics measure(Font font) {
        SafetyBoxLayoutPack.LayoutData ld = layoutData();
        ItemStack boxStack = data.boxStack();
        String title = title(ld, boxStack);
        SafetyBoxOverlayRenderer.HeaderSize header = headerSize(font, ld, title);
        GridGeometry base = new GridGeometry(data.gridWidth(), data.gridHeight(), 0, 0, 1.0f, (float) ld.gridScale);
        Config.Layout layout = safeLayout(ld);

        int gridX = PADDING;
        int gridY = PADDING;
        int contentW;
        int contentH;
        switch (layout) {
            case LEFT -> {
                gridX += header.width() + GAP;
                contentW = header.width() + GAP + base.pixelWidth();
                contentH = Math.max(header.height(), base.pixelHeight());
                gridY += Math.max(0, (contentH - base.pixelHeight()) / 2);
            }
            case RIGHT -> {
                contentW = base.pixelWidth() + GAP + header.width();
                contentH = Math.max(header.height(), base.pixelHeight());
                gridY += Math.max(0, (contentH - base.pixelHeight()) / 2);
            }
            case BOTTOM -> {
                contentW = Math.max(base.pixelWidth(), header.width());
                contentH = base.pixelHeight() + GAP + header.height();
                gridX += Math.max(0, (contentW - base.pixelWidth()) / 2);
            }
            default -> {
                contentW = Math.max(base.pixelWidth(), header.width());
                contentH = header.height() + GAP + base.pixelHeight();
                gridX += Math.max(0, (contentW - base.pixelWidth()) / 2);
                gridY += header.height() + GAP;
            }
        }
        int width = contentW + PADDING * 2;
        int valueY = PADDING + contentH + GAP;
        long total = totalValue();
        if (total > 0) {
            width = Math.max(width, PADDING * 2 + 12 + font.width(valueText(total)));
        }
        int packY = valueY + (total > 0 ? VALUE_LINE_H : 0);
        if (boxStack.getItem() instanceof DeltaPackItem pack) {
            String capacity = Component.translatable("tooltip.xero_delta.pack.capacity",
                pack.capacity()).getString();
            String speed = Component.translatable("tooltip.xero_delta.pack.movement_speed",
                BigDecimal.valueOf(-pack.speedPenalty() * 100.0D)
                    .stripTrailingZeros().toPlainString()).getString();
            width = Math.max(width, PADDING * 2 + Math.max(font.width(capacity), 20 + font.width(speed)));
        }
        int height = packY
            + (boxStack.getItem() instanceof DeltaPackItem ? PACK_INFO_H : 0) + PADDING;
        return new Metrics(width, height, gridX, gridY, valueY, packY);
    }

    private SafetyBoxOverlayRenderer.HeaderSize headerSize(Font font, SafetyBoxLayoutPack.LayoutData ld, String title) {
        GridGeometry geom = new GridGeometry(data.gridWidth(), data.gridHeight(), 0, 0, 1.0f, (float) ld.gridScale);
        return SafetyBoxOverlayRenderer.computeHeaderSize(font, geom, ld, 1.0f, title);
    }

    private long totalValue() {
        long total = 0;
        for (ItemStack stack : data.items()) {
            if (!stack.isEmpty()) total += ClientDataCache.INSTANCE.getPrice(stack) * stack.getCount();
        }
        return total;
    }

    private static String valueText(long total) {
        return Component.translatable("tooltip.xero_delta.box_total", PRICE_FORMAT.format(total)).getString();
    }

    private SafetyBoxLayoutPack.LayoutData layoutData() {
        ItemStack boxStack = data.boxStack();
        String id = boxStack.isEmpty() ? "xero_delta:safety_box_3x3" : boxStack.getItemHolder().getKey().location().toString();
        return SafetyBoxLayoutPack.getLayoutForBox(id);
    }

    private static String title(SafetyBoxLayoutPack.LayoutData ld, ItemStack stack) {
        if (ld.customText != null && !ld.customText.isEmpty()) return ld.customText;
        return stack.isEmpty() ? "" : stack.getHoverName().getString();
    }

    private static Config.Layout safeLayout(SafetyBoxLayoutPack.LayoutData ld) {
        try {
            return Config.Layout.valueOf(ld.layout);
        } catch (Exception ignored) {
            return Config.Layout.TOP;
        }
    }

    private static ResourceLocation movementSpeedIcon(double penalty) {
        int level = Math.max(1, Math.min(4, (int)Math.ceil(Math.abs(penalty) * 4.0D)));
        String direction = penalty > 0.0D ? "down" : "up";
        return ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID,
            "textures/gui/effect/movement_speed_" + direction + "_" + level + ".png");
    }

    private record Metrics(int width, int height, int gridX, int gridY, int valueY, int packY) {}
}
