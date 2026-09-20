package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.Config;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;

public final class ItemSizeTooltipComponent implements ClientTooltipComponent {
    private static final int GRAPHIC_CELL = 5;
    private static final int VISUAL_CELL = 10;
    private static final int LABEL_HEIGHT = 11;
    private final ItemSizeTooltipData data;

    public ItemSizeTooltipComponent(ItemSizeTooltipData data) {
        this.data = data;
    }

    @Override
    public int getHeight() {
        int contentHeight = switch (data.mode()) {
            case TEXT -> 10;
            case GRAPHIC, VISUAL -> LABEL_HEIGHT + data.size().height() * cellSize();
            case OFF -> 0;
        };
        return data.mode() == Config.TooltipSizeMode.OFF
            ? 0
            : Config.INSTANCE.tooltipSizePaddingTop.get() + contentHeight
                + Config.INSTANCE.tooltipSizePaddingBottom.get();
    }

    @Override
    public int getWidth(Font font) {
        int label = font.width(label());
        int contentWidth = switch (data.mode()) {
            case TEXT, OFF -> label;
            case GRAPHIC, VISUAL -> Math.max(label, data.size().width() * cellSize());
        };
        return data.mode() == Config.TooltipSizeMode.OFF ? 0
            : Config.INSTANCE.tooltipSizePaddingLeft.get() + contentWidth
                + Config.INSTANCE.tooltipSizePaddingRight.get();
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        if (data.mode() == Config.TooltipSizeMode.OFF) return;
        int contentX = x + Config.INSTANCE.tooltipSizePaddingLeft.get();
        int contentY = y + Config.INSTANCE.tooltipSizePaddingTop.get();
        graphics.drawString(font, label(), contentX, contentY + 1, 0xFFD0D0D0, false);
        if (data.mode() == Config.TooltipSizeMode.TEXT) return;
        int cell = cellSize();
        int gridY = contentY + LABEL_HEIGHT;
        for (int row = 0; row < data.size().height(); row++) {
            for (int column = 0; column < data.size().width(); column++) {
                DeltaGridCellRenderer.render(graphics,
                    contentX + column * cell, gridY + row * cell, cell);
            }
        }
        if (data.mode() == Config.TooltipSizeMode.VISUAL) {
            GridItemRenderer.renderSizedItem(graphics, font, data.stack(), contentX, gridY,
                data.size().width() * cell, data.size().height() * cell,
                false, ClientDataCache.INSTANCE.shouldRotateTexture(data.stack()),
                ClientDataCache.INSTANCE.shouldStretchTexture(data.stack()),
                ClientDataCache.INSTANCE.proportionalTextureScale(data.stack()));
        }
    }

    private String label() {
        String weight = BigDecimal.valueOf(data.weight()).stripTrailingZeros().toPlainString();
        return weight + "KG  " + Component.translatable("tooltip.xero_delta.item_size",
            data.size().width(), data.size().height()).getString();
    }

    private int cellSize() {
        int base = data.mode() == Config.TooltipSizeMode.GRAPHIC ? GRAPHIC_CELL : VISUAL_CELL;
        return Math.max(1, (int)Math.round(base * Config.INSTANCE.tooltipSizeScale.get()));
    }
}
