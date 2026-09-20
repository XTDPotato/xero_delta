package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.Config;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

public final class TooltipTitleComponent implements ClientTooltipComponent {
    private final ClientTooltipComponent delegate;
    private TooltipOverlapPadding.Padding autoPadding = TooltipOverlapPadding.NONE;

    public TooltipTitleComponent(TooltipTitleData data) {
        this.delegate = ClientTooltipComponent.create(data.title().getVisualOrderText());
    }

    @Override
    public int getHeight() {
        return autoPadding.top() + delegate.getHeight() + autoPadding.bottom()
            + Math.max(0, Config.INSTANCE.tooltipTitleOffsetY.get());
    }

    @Override
    public int getWidth(Font font) {
        return autoPadding.left() + delegate.getWidth(font)
            + Math.max(0, Config.INSTANCE.tooltipTitleOffsetX.get());
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f pose,
                           MultiBufferSource.BufferSource bufferSource) {
        delegate.renderText(font, x + autoPadding.left() + Config.INSTANCE.tooltipTitleOffsetX.get(),
            y + autoPadding.top() + Config.INSTANCE.tooltipTitleOffsetY.get(), pose, bufferSource);
    }

    int contentHeight() {
        return delegate.getHeight();
    }

    void setAutoPadding(TooltipOverlapPadding.Padding padding) {
        autoPadding = padding == null ? TooltipOverlapPadding.NONE : padding;
    }
}
