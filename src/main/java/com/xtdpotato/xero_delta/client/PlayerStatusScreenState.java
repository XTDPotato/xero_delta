package com.xtdpotato.xero_delta.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xtdpotato.xero_delta.data.ConfigPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent client preference for replacing the normal inventory with the status screen. */
public final class PlayerStatusScreenState {
    private static final Gson GSON = new Gson();
    private static final Path FILE = ConfigPaths.file("player_status_screen.json");
    private static boolean loaded;
    // The Delta screen remains an InventoryScreen subclass, so integrations
    // that identify the player's inventory by type keep working.
    private static boolean enabled = true;
    private static boolean inventoryBypass;

    private PlayerStatusScreenState() {
    }

    public static boolean isEnabled() {
        load();
        return enabled;
    }

    public static void enable() {
        load();
        if (enabled) return;
        enabled = true;
        save();
    }

    public static void disable() {
        load();
        if (!enabled) return;
        enabled = false;
        inventoryBypass = false;
        save();
    }

    /** Allows the status screen's close action to show InventoryScreen exactly once. */
    public static void bypassNextInventoryReplacement() {
        inventoryBypass = true;
    }

    public static boolean isInventoryBypassed() {
        return inventoryBypass;
    }

    public static boolean shouldReplaceInventory() {
        load();
        if (!enabled) return false;
        if (inventoryBypass) return false;
        return true;
    }

    /** Ends the bypass only after the vanilla inventory is no longer open. */
    public static void endInventoryBypass() {
        inventoryBypass = false;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            JsonObject root = JsonParser.parseString(
                Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            enabled = !root.has("inventoryReplacementEnabled")
                || root.get("inventoryReplacementEnabled").getAsBoolean();
        } catch (Exception ignored) {
            enabled = true;
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("inventoryReplacementEnabled", enabled);
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
