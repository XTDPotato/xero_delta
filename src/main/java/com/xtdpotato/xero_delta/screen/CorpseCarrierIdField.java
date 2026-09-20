package com.xtdpotato.xero_delta.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Inline candidate ID editor with right-click clipboard support. */
final class CorpseCarrierIdField extends MaterialEditBox {
    private final Consumer<String> copyAction;

    CorpseCarrierIdField(Font font, int x, int y, int width, String value,
                         Consumer<String> copyAction) {
        super(font, x, y, width, 20, Component.empty());
        this.copyAction = copyAction;
        setValue(value);
        setFilter(text -> text.isEmpty() || text.matches("[a-zA-Z0-9_.:-]+"));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && active && visible && isMouseOver(mouseX, mouseY)) {
            copyAction.accept(getValue());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
