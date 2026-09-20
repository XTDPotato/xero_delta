package com.xtdpotato.xero_delta.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiScreenTargetTest {
    @Test
    void resolvesCanonicalEnglishAndChineseAliases() {
        assertEquals(GuiScreenTarget.TRADING_MARKET,
            GuiScreenTarget.parse("trading_market").orElseThrow());
        assertEquals(GuiScreenTarget.RECYCLING,
            GuiScreenTarget.parse("recycler").orElseThrow());
        assertEquals(GuiScreenTarget.SAFETY_BOX,
            GuiScreenTarget.parse("\u5b89\u5168\u7bb1\u9009\u62e9").orElseThrow());
        assertEquals(GuiScreenTarget.KNIFE,
            GuiScreenTarget.parse("\u5200\u76ae").orElseThrow());
    }

    @Test
    void normalizesHyphensAndRejectsUnknownNames() {
        assertEquals(GuiScreenTarget.CARD_HOLDER,
            GuiScreenTarget.parse("card-holder").orElseThrow());
        assertTrue(GuiScreenTarget.parse("missing_gui").isEmpty());
    }
}
