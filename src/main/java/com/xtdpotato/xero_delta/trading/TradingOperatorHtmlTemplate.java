package com.xtdpotato.xero_delta.trading;

public final class TradingOperatorHtmlTemplate {
    private static final String RESOURCE = "/assets/xero_delta/trading/trading_op.html";
    private static final String VERSION_MARKER = "trading-operator-v8";

    private TradingOperatorHtmlTemplate() {
    }

    public static TradingHtmlThemeParser.Document loadDocument() {
        return SafeHtmlTemplate.load("trading_op.html", RESOURCE, VERSION_MARKER);
    }
}
