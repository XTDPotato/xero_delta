package com.xtdpotato.xero_delta.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xtdpotato.xero_delta.data.ConfigPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent absolute position for the independent player status panel. */
public final class PlayerStatusPanelState {
    private static final Gson GSON = new Gson();
    private static final Path FILE = ConfigPaths.file("player_status_panel.json");
    private static boolean loaded;
    private static boolean manual;
    private static boolean collapsed;
    private static int x;
    private static int y;

    private PlayerStatusPanelState() {
    }

    public static int[] position(int defaultX, int defaultY, int screenWidth, int screenHeight,
                                 int panelWidth, int panelHeight) {
        load();
        int resolvedX = manual ? x : defaultX;
        int resolvedY = manual ? y : defaultY;
        return new int[]{
            clamp(resolvedX, 4, Math.max(4, screenWidth - panelWidth - 4)),
            clamp(resolvedY, 4, Math.max(4, screenHeight - panelHeight - 4))
        };
    }

    public static void set(int nextX, int nextY) {
        loaded = true;
        manual = true;
        x = nextX;
        y = nextY;
    }

    public static boolean isCollapsed() {
        load();
        return collapsed;
    }

    public static void setCollapsed(boolean value) {
        load();
        collapsed = value;
        save();
    }

    public static void save() {
        if (!loaded) return;
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("manual", manual);
            if (manual) {
                root.addProperty("x", x);
                root.addProperty("y", y);
            }
            root.addProperty("collapsed", collapsed);
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            JsonObject root = JsonParser.parseString(
                Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            collapsed = root.has("collapsed") && root.get("collapsed").getAsBoolean();
            manual = !root.has("manual") || root.get("manual").getAsBoolean();
            if (manual) {
                x = root.get("x").getAsInt();
                y = root.get("y").getAsInt();
            }
        } catch (Exception ignored) {
            manual = false;
            collapsed = false;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
