package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradingNavigationHistoryTest {
    @Test
    void rootEntryClearsPreviousNavigationSoEscCloses() {
        TradingNavigationHistory history = new TradingNavigationHistory();
        history.remember("market:sell");
        history.remember("recycling:inventory");

        history.acceptContext("root:market");

        assertEquals(0, history.size());
        assertEquals("", history.pop());
    }

    @Test
    void topTabsDoNotCreateNavigationEntries() {
        TradingNavigationHistory history = new TradingNavigationHistory();

        history.acceptContext("root:market");
        history.acceptContext("tab:sell");
        history.acceptContext("tab:history");
        history.acceptContext("tab:buy");

        assertEquals(0, history.size());
        assertEquals("", history.pop());
    }

    @Test
    void ordinaryPagesReturnInLastOpenedFirstReturnedOrder() {
        TradingNavigationHistory history = new TradingNavigationHistory();
        history.remember("market:sell");
        history.remember("market:sell");
        history.remember("recycling:inventory");
        history.acceptContext("tab:buy");

        assertEquals("recycling:inventory", history.pop());
        assertEquals("market:sell", history.pop());
        assertEquals("", history.pop());
    }
}
