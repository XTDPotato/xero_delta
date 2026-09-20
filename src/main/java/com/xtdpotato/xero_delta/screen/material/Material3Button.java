package com.xtdpotato.xero_delta.screen.material;

import net.minecraft.network.chat.Component;

/** Builder-friendly Material 3 button used by legacy configuration screens. */
public final class Material3Button extends Material2Button {
    @FunctionalInterface
    public interface OnPress {
        void onPress(Material3Button button);
    }

    private Material3Button(int x, int y, int width, int height, Component message,
                            Variant variant, OnPress action) {
        super(x, y, width, height, message, variant, () -> {
        });
        this.action = action;
    }

    private final OnPress action;

    @Override
    public void onPress() {
        action.onPress(this);
    }

    public static Builder builder(Component message, OnPress action) {
        return new Builder(message, action);
    }

    public static final class Builder {
        private final Component message;
        private final OnPress action;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private Variant variant = Variant.TONAL;

        private Builder(Component message, OnPress action) {
            this.message = message;
            this.action = action;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            return pos(x, y).size(width, height);
        }

        public Builder variant(Variant variant) {
            this.variant = variant;
            return this;
        }

        public Material3Button build() {
            return new Material3Button(x, y, width, height, message, variant, action);
        }
    }
}


