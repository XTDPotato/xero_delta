package com.xtdpotato.xero_delta.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Material 3 text field that preserves compact legacy screen geometry. */
public final class Material3CompactEditBox extends MaterialEditBox {
    public Material3CompactEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message, 18);
    }

    @Override
    protected boolean supportsFloatingLabel() {
        return false;
    }
}
