package com.xtdpotato.xero_delta.trading;

import java.nio.file.Path;

/** User-editable HTML theme/action manifest for the Xero Delta settings screen. */
public final class ConfigHtmlTemplate {
    private static final String RESOURCE = "/assets/xero_delta/config/config.html";
    private static final String VERSION_MARKER = "xero-delta-config-v2";

    private ConfigHtmlTemplate() {
    }

    public static Path path() {
        return SafeHtmlTemplate.path("config.html");
    }

    public static TradingHtmlThemeParser.Document loadDocument() {
        return SafeHtmlTemplate.load("config.html", RESOURCE, VERSION_MARKER);
    }

    public static void ensureExists() {
        loadDocument();
    }
}