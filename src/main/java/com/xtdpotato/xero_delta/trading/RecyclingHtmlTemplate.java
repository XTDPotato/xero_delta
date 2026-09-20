package com.xtdpotato.xero_delta.trading;

public final class RecyclingHtmlTemplate {
    private static final String RESOURCE = "/assets/xero_delta/trading/recycle.html";
    private static final String VERSION_MARKER = "recycling-station-v3";

    private RecyclingHtmlTemplate() {
    }

    public static TradingHtmlThemeParser.Document loadDocument() {
        return SafeHtmlTemplate.load("recycle.html", RESOURCE, VERSION_MARKER);
    }
}
