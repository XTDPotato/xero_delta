package com.xtdpotato.xero_delta.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xtdpotato.xero_delta.data.ConfigPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-only preferences for the Delta inventory layout. */
public final class DeltaInventoryUiState {
    private static final Gson GSON = new Gson();
    private static final Path FILE = ConfigPaths.file("delta_inventory_ui.json");
    private static boolean loaded;
    private static boolean safetyBoxPinned = true;

    private DeltaInventoryUiState() {}

    public static boolean safetyBoxPinned() {
        load();
        return safetyBoxPinned;
    }

    public static void setSafetyBoxPinned(boolean pinned) {
        load();
        safetyBoxPinned = pinned;
        save();
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            JsonObject root = JsonParser.parseString(
                Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            safetyBoxPinned = !root.has("safetyBoxPinned")
                || root.get("safetyBoxPinned").getAsBoolean();
        } catch (Exception ignored) {
            safetyBoxPinned = true;
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("safetyBoxPinned", safetyBoxPinned);
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
