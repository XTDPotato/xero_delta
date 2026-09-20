package com.xtdpotato.xero_delta.screen.material;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/** Material 3 slider with stable stepped values and a compact floating indicator. */
public final class Material2Slider extends AbstractSliderButton {
    private final double minimum;
    private final double maximum;
    private final double step;
    private final DoubleConsumer consumer;
    private final int primary;
    private final int inactive;
    private final int indicator;
    private boolean dragging;
    private Component indicatorLabel = Component.empty();
    private Material2Icon icon;

    public Material2Slider(int x, int y, int width, double minimum, double maximum,
                           double step, double initial, DoubleConsumer consumer,
                           int primary, int inactive, int indicator) {
        super(x, y, width, 16, Component.empty(), fraction(initial, minimum, maximum));
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.consumer = consumer == null ? ignored -> { } : consumer;
        this.primary = primary;
        this.inactive = inactive;
        this.indicator = indicator;
        updateMessage();
    }

    public Material2Slider icon(Material2Icon value) {
        icon = value;
        return this;
    }

    public Material2Slider indicatorLabel(Component value) {
        indicatorLabel = value == null ? Component.empty() : value;
        return this;
    }

    public void setNumberValue(double number) {
        value = fraction(number, minimum, maximum);
        updateMessage();
    }

    public boolean isDraggingValue() {
        return dragging;
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(MaterialSliderValue.format(step, currentNumber())));
    }

    @Override
    protected void applyValue() {
        consumer.accept(currentNumber());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        dragging = handled && button == 0;
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerY = getY() + getHeight() / 2;
        int left = getX() + (icon == null ? 6 : 28);
        int right = getX() + getWidth() - 6;
        int handleX = left + (int) Math.round(value * Math.max(1, right - left));
        Material2Drawing.roundedRect(graphics, left, centerY - 2,
            Math.max(1, handleX - left), 4, Material3Theme.RADIUS_FULL, primary);
        Material2Drawing.roundedRect(graphics, handleX, centerY - 2,
            Math.max(1, right - handleX), 4, Material3Theme.RADIUS_FULL, inactive);
        Material2Drawing.circle(graphics, handleX, centerY, dragging ? 5 : 4, primary);
        if (icon != null) icon.render(graphics, getX() + 12, centerY, primary);
    }

    public void renderFloating(GuiGraphics graphics) {
        if (!visible || !active || !dragging) return;
        int left = getX() + (icon == null ? 6 : 28);
        int right = getX() + getWidth() - 6;
        int handleX = left + (int) Math.round(value * Math.max(1, right - left));
        Component shown = indicatorLabel.getString().isEmpty() ? getMessage()
            : Component.literal(indicatorLabel.getString() + ": " + getMessage().getString());
        int bubbleWidth = Math.max(30, Minecraft.getInstance().font.width(shown) + 14);
        int bubbleX = Math.max(getX(), Math.min(handleX - bubbleWidth / 2,
            getX() + getWidth() - bubbleWidth));
        int bubbleY = getY() - 23;
        Material2Drawing.roundedRect(graphics, bubbleX, bubbleY, bubbleWidth, 19,
            Material3Theme.RADIUS_FULL, indicator);
        graphics.drawCenteredString(Minecraft.getInstance().font, shown,
            bubbleX + bubbleWidth / 2, bubbleY + 5, Material3Theme.TEXT);
    }

    private double currentNumber() {
        return MaterialSliderValue.quantize(minimum, maximum, step,
            minimum + value * (maximum - minimum));
    }

    private static double fraction(double number, double minimum, double maximum) {
        if (maximum <= minimum) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, (number - minimum) / (maximum - minimum)));
    }
}

