package com.xtdpotato.xero_delta.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ConfigPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-screen position and scale for native controls and their background texture. */
public final class ContainerUiPositionState {
    public record Offset(int interfaceX, int interfaceY, int textureX, int textureY,
                         double interfaceScaleX, double interfaceScaleY,
                         double textureScaleX, double textureScaleY) {
        public static final Offset ZERO = new Offset(0, 0, 0, 0,
            1.0D, 1.0D, 1.0D, 1.0D);
    }

    private static final Gson GSON = new Gson();
    private static final Map<String, Offset> OFFSETS = new LinkedHashMap<>();
    private static boolean loaded;
    private static int revision;

    private ContainerUiPositionState() {}

    public static synchronized Offset get(String key) {
        ensureLoaded();
        return key == null ? Offset.ZERO : OFFSETS.getOrDefault(key, Offset.ZERO);
    }

    public static synchronized int revision() {
        ensureLoaded();
        return revision;
    }

    public static synchronized void setInterface(String key, int x, int y) {
        if (key == null) return;
        ensureLoaded();
        Offset old = get(key);
        OFFSETS.put(key, new Offset(x, y, old.textureX(), old.textureY(),
            old.interfaceScaleX(), old.interfaceScaleY(),
            old.textureScaleX(), old.textureScaleY()));
        revision++;
    }

    public static synchronized void setTexture(String key, int x, int y) {
        if (key == null) return;
        ensureLoaded();
        Offset old = get(key);
        OFFSETS.put(key, new Offset(old.interfaceX(), old.interfaceY(), x, y,
            old.interfaceScaleX(), old.interfaceScaleY(),
            old.textureScaleX(), old.textureScaleY()));
        revision++;
    }

    public static synchronized void setInterfaceScale(String key, double x, double y) {
        if (key == null) return;
        ensureLoaded();
        Offset old = get(key);
        OFFSETS.put(key, new Offset(old.interfaceX(), old.interfaceY(),
            old.textureX(), old.textureY(), validScale(x), validScale(y),
            old.textureScaleX(), old.textureScaleY()));
        revision++;
    }

    public static synchronized void setTextureScale(String key, double x, double y) {
        if (key == null) return;
        ensureLoaded();
        Offset old = get(key);
        OFFSETS.put(key, new Offset(old.interfaceX(), old.interfaceY(),
            old.textureX(), old.textureY(), old.interfaceScaleX(),
            old.interfaceScaleY(), validScale(x), validScale(y)));
        revision++;
    }

    public static synchronized void set(String key, Offset value) {
        if (key == null) return;
        ensureLoaded();
        if (value == null || Offset.ZERO.equals(value)) OFFSETS.remove(key);
        else OFFSETS.put(key, value);
        revision++;
    }

    public static synchronized void reset(String key) {
        if (key == null) return;
        ensureLoaded();
        OFFSETS.remove(key);
        revision++;
        save();
    }

    public static synchronized void save() {
        ensureLoaded();
        JsonObject root = new JsonObject();
        for (var entry : OFFSETS.entrySet()) {
            Offset value = entry.getValue();
            JsonObject object = new JsonObject();
            object.addProperty("interfaceX", value.interfaceX());
            object.addProperty("interfaceY", value.interfaceY());
            object.addProperty("textureX", value.textureX());
            object.addProperty("textureY", value.textureY());
            object.addProperty("interfaceScaleX", value.interfaceScaleX());
            object.addProperty("interfaceScaleY", value.interfaceScaleY());
            object.addProperty("textureScaleX", value.textureScaleX());
            object.addProperty("textureScaleY", value.textureScaleY());
            root.add(entry.getKey(), object);
        }
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root));
        } catch (Exception exception) {
            XeroDelta.LOGGER.error("Failed to save container UI positions", exception);
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path path = path();
            if (!Files.exists(path)) return;
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (var entry : root.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject object = entry.getValue().getAsJsonObject();
                OFFSETS.put(entry.getKey(), new Offset(
                    integer(object, "interfaceX"), integer(object, "interfaceY"),
                    integer(object, "textureX"), integer(object, "textureY"),
                    decimal(object, "interfaceScaleX", 1.0D),
                    decimal(object, "interfaceScaleY", 1.0D),
                    decimal(object, "textureScaleX", 1.0D),
                    decimal(object, "textureScaleY", 1.0D)));
            }
        } catch (Exception exception) {
            XeroDelta.LOGGER.error("Failed to load container UI positions", exception);
        }
    }

    private static int integer(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }

    private static double decimal(JsonObject object, String key, double fallback) {
        return object.has(key) ? validScale(object.get(key).getAsDouble()) : fallback;
    }

    private static double validScale(double value) {
        if (!Double.isFinite(value)) return 1.0D;
        return Math.max(0.01D, value);
    }

    private static Path path() {
        return ConfigPaths.file("container_ui_positions.json");
    }
}
