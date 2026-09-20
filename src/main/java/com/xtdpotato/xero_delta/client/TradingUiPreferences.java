package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.data.ConfigPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

/** Small client-only layout preferences saved under config/delta_packs. */
public final class TradingUiPreferences {
    private static final int SEARCH_HISTORY_LIMIT = 5;
    private static final Path PATH = ConfigPaths.file("trading_ui.properties");
    private static final Properties VALUES = new Properties();
    private static boolean loaded;

    private TradingUiPreferences() {
    }

    public static synchronized int marketColumns() { return get("market_columns", 3, 1, 10); }
    public static synchronized int operatorSourceColumns() { return get("operator_source_columns", 8, 1, 10); }
    public static synchronized int operatorOwnedColumns() { return get("operator_owned_columns", 1, 1, 3); }
    public static synchronized int recyclingColumns() { return get("recycling_columns", 1, 1, 10); }
    public static synchronized int tradingUiScaleLevel() {
        return get("trading_ui_scale_level", TradingUiScale.DEFAULT_LEVEL,
            TradingUiScale.MIN_LEVEL, TradingUiScale.MAX_LEVEL);
    }
    public static synchronized int safetyBoxEditorScaleLevel() {
        return get("safety_box_editor_scale_level", -1,
            TradingUiScale.MIN_LEVEL, TradingUiScale.MAX_LEVEL);
    }
    public static synchronized List<String> marketSearchHistory() {
        load();
        List<String> result = new ArrayList<>(SEARCH_HISTORY_LIMIT);
        for (int index = 0; index < SEARCH_HISTORY_LIMIT; index++) {
            String encoded = VALUES.getProperty("market_search_" + index, "");
            if (encoded.isBlank()) continue;
            try {
                String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).trim();
                if (!value.isBlank()) result.add(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return List.copyOf(result);
    }

    public static synchronized void setMarketColumns(int value) { set("market_columns", value, 1, 10); }
    public static synchronized void setOperatorSourceColumns(int value) { set("operator_source_columns", value, 1, 10); }
    public static synchronized void setOperatorOwnedColumns(int value) { set("operator_owned_columns", value, 1, 3); }
    public static synchronized void setRecyclingColumns(int value) { set("recycling_columns", value, 1, 10); }
    public static synchronized void setTradingUiScaleLevel(int value) {
        set("trading_ui_scale_level", value, TradingUiScale.MIN_LEVEL, TradingUiScale.MAX_LEVEL);
    }
    public static synchronized void setSafetyBoxEditorScaleLevel(int value) {
        set("safety_box_editor_scale_level", value,
            TradingUiScale.MIN_LEVEL, TradingUiScale.MAX_LEVEL);
    }
    public static synchronized void rememberMarketSearch(String query) {
        String value = query == null ? "" : query.trim();
        if (value.isBlank()) return;
        if (value.length() > 128) value = value.substring(0, 128);
        List<String> values = new ArrayList<>(marketSearchHistory());
        String retained = value;
        values.removeIf(existing -> existing.equalsIgnoreCase(retained));
        values.addFirst(value);
        writeSearchHistory(values);
    }

    public static synchronized void clearMarketSearchHistory() {
        load();
        for (int index = 0; index < SEARCH_HISTORY_LIMIT; index++) {
            VALUES.remove("market_search_" + index);
        }
        save();
    }

    private static void writeSearchHistory(List<String> values) {
        load();
        for (int index = 0; index < SEARCH_HISTORY_LIMIT; index++) {
            if (index < values.size()) {
                String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    values.get(index).getBytes(StandardCharsets.UTF_8));
                VALUES.setProperty("market_search_" + index, encoded);
            } else {
                VALUES.remove("market_search_" + index);
            }
        }
        save();
    }

    private static int get(String key, int fallback, int minimum, int maximum) {
        load();
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(VALUES.getProperty(key, String.valueOf(fallback)))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void set(String key, int value, int minimum, int maximum) {
        load();
        VALUES.setProperty(key, String.valueOf(Math.max(minimum, Math.min(maximum, value))));
        save();
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(PATH)) return;
        try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            VALUES.load(reader);
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                VALUES.store(writer, "Xero Delta trading UI layout");
            }
        } catch (Exception ignored) {
        }
    }
}
