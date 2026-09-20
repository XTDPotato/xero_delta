package com.xtdpotato.xero_delta.trading;

import java.nio.file.Path;

/** Generates and loads config/delta_packs/trading.html without executing scripts or external resources. */
public final class TradingHtmlTemplate {
    private static final String RESOURCE = "/assets/xero_delta/trading/trading.html";
    private static final String VERSION_MARKER = "trading-market-v13";

    private TradingHtmlTemplate() {
    }

    public static Path path() {
        return SafeHtmlTemplate.path("trading.html");
    }

    public static TradingHtmlThemeParser.Theme loadTheme() {
        return loadDocument().theme();
    }

    public static TradingHtmlThemeParser.Document loadDocument() {
        return SafeHtmlTemplate.load("trading.html", RESOURCE, VERSION_MARKER);
    }

    public static void ensureExists() {
        SafeHtmlTemplate.load("trading.html", RESOURCE, VERSION_MARKER);
    }
}
