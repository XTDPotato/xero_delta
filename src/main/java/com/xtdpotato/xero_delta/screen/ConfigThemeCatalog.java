package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.trading.TradingHtmlThemeParser;
import com.xtdpotato.xero_delta.trading.ConfigHtmlTemplate;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import java.util.List;
import java.util.Locale;

/** Built-in palettes used by the Xero Delta configuration surface. */
public final class ConfigThemeCatalog {
    public static final String DEFAULT_ID = "mint";
    public static final String CUSTOM_ID = "custom";

    private static final List<Entry> THEMES = List.of(
        new Entry("mint", "screen.xero_delta.config.theme.mint",
            ConfigHtmlTemplate.loadDocument().theme()),
        new Entry("graphite", "screen.xero_delta.config.theme.graphite",
            new TradingHtmlThemeParser.Theme(
                0xF2111312, 0xF2202221, 0xF22A2D2B, 0xFF444846,
                0xFFC4C7C5, 0xFFE2E3E1, 0xFFC3C7C4, 0xFFFFB4AB,
                3, 210, 104, 6)),
        new Entry("amber", "screen.xero_delta.config.theme.amber",
            new TradingHtmlThemeParser.Theme(
                0xF2181511, 0xF229221A, 0xF23B2E20, 0xFF6B5942,
                0xFFF2B85B, 0xFFFFF5E7, 0xFFBBA994, 0xFFE25B58,
                3, 210, 104, 6)),
        new Entry("ocean", "screen.xero_delta.config.theme.ocean",
            new TradingHtmlThemeParser.Theme(
                0xF208121D, 0xF2142533, 0xF21E3447, 0xFF42627A,
                0xFF65C7E8, 0xFFEAF6FC, 0xFF9CB8C9, 0xFFE25B58,
                3, 210, 104, 6)),
        new Entry("violet", "screen.xero_delta.config.theme.violet",
            new TradingHtmlThemeParser.Theme(
                0xF215101B, 0xF21E1726, 0xF2281E33, 0xFF51445C,
                0xFFD0BCFF, 0xFFF6EDFF, 0xFFD0BDDC, 0xFFFFB4AB,
                3, 210, 104, 6))
    );

    private ConfigThemeCatalog() {
    }

    public static List<Entry> entries() {
        java.util.ArrayList<Entry> values = new java.util.ArrayList<>(THEMES);
        values.add(new Entry(CUSTOM_ID, "screen.xero_delta.config.theme_custom", customTheme()));
        return List.copyOf(values);
    }

    public static TradingHtmlThemeParser.Theme theme(String id) {
        String normalized = normalize(id);
        if (CUSTOM_ID.equals(normalized)) return customTheme();
        return THEMES.stream().filter(value -> value.id().equals(normalized))
            .findFirst().orElse(THEMES.getFirst()).theme();
    }

    public static TradingHtmlThemeParser.Theme customTheme() {
        return customTheme(com.xtdpotato.xero_delta.Config.INSTANCE.configThemeHighlight.get(),
            com.xtdpotato.xero_delta.Config.INSTANCE.configThemePrimary.get(),
            com.xtdpotato.xero_delta.Config.INSTANCE.configThemeSecondary.get());
    }

    public static TradingHtmlThemeParser.Theme customTheme(String highlightValue,
                                                            String primaryValue,
                                                            String secondaryValue) {
        int highlight = color(highlightValue,
            TradingHtmlThemeParser.DEFAULT.accent());
        int primary = color(primaryValue,
            TradingHtmlThemeParser.DEFAULT.background());
        int secondary = color(secondaryValue,
            TradingHtmlThemeParser.DEFAULT.panelAlt());
        return new TradingHtmlThemeParser.Theme(primary,
            blend(primary, secondary, 0.55F), secondary,
            blend(secondary, highlight, 0.35F), highlight,
            Material3Theme.TEXT, Material3Theme.TEXT_MUTED, Material3Theme.ERROR,
            3, 210, 104, 6);
    }

    public static boolean isCustom(String id) {
        return CUSTOM_ID.equals(normalize(id));
    }

    public static String normalize(String id) {
        if (id == null) return DEFAULT_ID;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (CUSTOM_ID.equals(normalized)) return CUSTOM_ID;
        return THEMES.stream().anyMatch(theme -> theme.id().equals(normalized))
            ? normalized : DEFAULT_ID;
    }

    public static String next(String id) {
        String normalized = normalize(id);
        if (CUSTOM_ID.equals(normalized)) return THEMES.getFirst().id();
        int index = 0;
        for (int i = 0; i < THEMES.size(); i++) {
            if (THEMES.get(i).id().equals(normalized)) {
                index = i;
                break;
            }
        }
        return THEMES.get((index + 1) % THEMES.size()).id();
    }

    public record Entry(String id, String labelKey, TradingHtmlThemeParser.Theme theme) {
    }

    private static int color(String value, int fallback) {
        if (value == null) return fallback;
        String text = value.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("0x")) text = text.substring(2);
        if (text.startsWith("#")) text = text.substring(1);
        try {
            long parsed = Long.parseLong(text, 16);
            if (text.length() <= 6) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int blend(int first, int second, float amount) {
        amount = Math.max(0.0F, Math.min(1.0F, amount));
        int a = Math.round(((first >>> 24) & 255) * (1.0F - amount)
            + ((second >>> 24) & 255) * amount);
        int r = Math.round(((first >>> 16) & 255) * (1.0F - amount)
            + ((second >>> 16) & 255) * amount);
        int g = Math.round(((first >>> 8) & 255) * (1.0F - amount)
            + ((second >>> 8) & 255) * amount);
        int b = Math.round((first & 255) * (1.0F - amount) + (second & 255) * amount);
        return a << 24 | r << 16 | g << 8 | b;
    }
}

