package com.xtdpotato.xero_delta.trading;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Pure parser for the deliberately small, safe trading.html presentation subset. */
public final class TradingHtmlThemeParser {
    public record Theme(int background, int panel, int panelAlt, int border, int accent,
                        int text, int muted, int danger, int columns, int sidebarWidth,
                        int cardHeight, int cardGap) {
    }

    public record CategorySection(String id, List<String> categories, boolean expanded,
                                  String label, String iconItemId) {
        public CategorySection(String id, List<String> categories, boolean expanded) {
            this(id, categories, expanded, "", "");
        }

        public CategorySection {
            categories = List.copyOf(categories);
            label = label == null ? "" : label.trim();
            iconItemId = iconItemId == null ? "" : iconItemId.trim().toLowerCase(Locale.ROOT);
        }
    }

    public record Document(Theme theme, List<String> tabs, List<String> categories,
                           List<String> groups, List<String> actions,
                           List<CategorySection> categorySections,
                           boolean customCategorySections) {
        public Document {
            tabs = List.copyOf(tabs);
            categories = List.copyOf(categories);
            groups = List.copyOf(groups);
            actions = List.copyOf(actions);
            categorySections = List.copyOf(categorySections);
        }
    }

    public static final Theme DEFAULT = new Theme(
        Material3Theme.alpha(Material3Theme.BACKGROUND, 242),
        Material3Theme.alpha(Material3Theme.SURFACE_CONTAINER, 242),
        Material3Theme.alpha(Material3Theme.SURFACE_CONTAINER_HIGH, 242),
        Material3Theme.OUTLINE_VARIANT, Material3Theme.PRIMARY,
        Material3Theme.TEXT, Material3Theme.TEXT_MUTED, Material3Theme.ERROR,
        3, 210, 104, 6);

    private TradingHtmlThemeParser() {
    }

    public static Theme parse(String html) {
        if (html == null) return DEFAULT;
        return new Theme(
            color(html, "market-bg", DEFAULT.background),
            color(html, "market-panel", DEFAULT.panel),
            color(html, "market-panel-alt", DEFAULT.panelAlt),
            color(html, "market-border", DEFAULT.border),
            color(html, "market-accent", DEFAULT.accent),
            color(html, "market-text", DEFAULT.text),
            color(html, "market-muted", DEFAULT.muted),
            color(html, "market-danger", DEFAULT.danger),
            integer(html, "data-columns", DEFAULT.columns, 1, 10),
            integer(html, "data-sidebar-width", DEFAULT.sidebarWidth, 150, 320),
            integer(html, "data-card-height", DEFAULT.cardHeight, 72, 180),
            integer(html, "data-card-gap", DEFAULT.cardGap, 2, 24));
    }

    public static Document parseDocument(String html) {
        String source = html == null ? "" : html;
        return new Document(parse(source), ids(source, "data-tab"), ids(source, "data-category"),
            ids(source, "data-group"), ids(source, "data-action"), categorySections(source),
            booleanAttribute(source, "data-custom-category-sections"));
    }

    private static List<CategorySection> categorySections(String html) {
        Matcher matcher = Pattern.compile(
            "<section\\b([^>]*data-category-section[^>]*)>(.*?)</section\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        List<CategorySection> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        while (matcher.find() && result.size() < 16) {
            String id = identifier(matcher.group(1), "data-category-section");
            List<String> categories = ids(matcher.group(2), "data-category");
            if (id.isBlank() || categories.isEmpty() || !seen.add(id)) continue;
            result.add(new CategorySection(id, categories,
                booleanAttribute(matcher.group(1), "data-expanded"),
                textAttribute(matcher.group(1), "data-category-label", 64),
                textAttribute(matcher.group(1), "data-category-icon", 128)));
        }
        return result;
    }

    private static String identifier(String html, String attribute) {
        List<String> values = ids(html, attribute);
        return values.isEmpty() ? "" : values.getFirst();
    }

    private static boolean booleanAttribute(String html, String attribute) {
        String value = identifier(html, attribute);
        return value.equals("true") || value.equals("1");
    }

    private static String textAttribute(String html, String attribute, int maximumLength) {
        Matcher matcher = Pattern.compile(Pattern.quote(attribute) + "\\s*=",
            Pattern.CASE_INSENSITIVE).matcher(html);
        if (!matcher.find()) return "";
        int start = matcher.end();
        while (start < html.length() && Character.isWhitespace(html.charAt(start))) start++;
        if (start >= html.length()) return "";
        char quote = html.charAt(start);
        if (quote != 34 && quote != 39) return "";
        int end = html.indexOf(quote, start + 1);
        if (end < 0) return "";
        String value = html.substring(start + 1, end).trim();
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private static List<String> ids(String html, String attribute) {
        Matcher matcher = Pattern.compile(Pattern.quote(attribute)
            + "\\s*=\\s*['\"]([a-zA-Z0-9_-]{1,48})['\"]").matcher(html);
        LinkedHashSet<String> values = new LinkedHashSet<>();
        while (matcher.find() && values.size() < 64) values.add(matcher.group(1).toLowerCase(Locale.ROOT));
        return new ArrayList<>(values);
    }

    private static int color(String html, String name, int fallback) {
        Matcher matcher = Pattern.compile("--" + Pattern.quote(name) + "\\s*:\\s*#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")
            .matcher(html);
        if (!matcher.find()) return fallback;
        String value = matcher.group(1);
        try {
            long parsed = Long.parseUnsignedLong(value, 16);
            return value.length() == 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int integer(String html, String attribute, int fallback, int minimum, int maximum) {
        Matcher matcher = Pattern.compile(Pattern.quote(attribute) + "\\s*=\\s*['\"](\\d+)['\"]")
            .matcher(html);
        if (!matcher.find()) return fallback;
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(matcher.group(1))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

