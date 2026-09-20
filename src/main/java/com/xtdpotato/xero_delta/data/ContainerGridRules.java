package com.xtdpotato.xero_delta.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.inventory.*;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ContainerGridRules {
    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "xero_delta_container_grid.json";

    private ContainerGridRules() {
    }

    public static boolean isScreenEnabled(String menuKey) {
        if (menuKey == null || menuKey.isEmpty()) return true;
        return load().getOrDefault(menuKey, true);
    }

    public static Map<String, Boolean> getAllRules() {
        LinkedHashMap<String, Boolean> result = defaultRules();
        result.putAll(load());
        return result;
    }

    public static void setScreenEnabled(String menuKey, boolean enabled) {
        if (menuKey == null || menuKey.isBlank()) return;
        LinkedHashMap<String, Boolean> rules = new LinkedHashMap<>(load());
        rules.put(menuKey, enabled);
        save(rules);
    }

    public static void reset() {
        save(new LinkedHashMap<>());
    }

    public static LinkedHashMap<String, Boolean> defaultRules() {
        LinkedHashMap<String, Boolean> rules = new LinkedHashMap<>();
        add(rules, InventoryMenu.class);
        add(rules, CraftingMenu.class);
        add(rules, ChestMenu.class);
        add(rules, ShulkerBoxMenu.class);
        add(rules, HopperMenu.class);
        add(rules, DispenserMenu.class);
        add(rules, BrewingStandMenu.class);
        add(rules, FurnaceMenu.class);
        add(rules, BlastFurnaceMenu.class);
        add(rules, SmokerMenu.class);
        add(rules, BeaconMenu.class);
        add(rules, AnvilMenu.class);
        add(rules, SmithingMenu.class);
        add(rules, EnchantmentMenu.class);
        add(rules, MerchantMenu.class);
        add(rules, LoomMenu.class);
        add(rules, GrindstoneMenu.class);
        add(rules, StonecutterMenu.class);
        add(rules, CartographyTableMenu.class);
        add(rules, CrafterMenu.class);
        return rules;
    }

    private static void add(Map<String, Boolean> rules, Class<?> clazz) {
        rules.put(clazz.getName(), true);
    }

    private static synchronized LinkedHashMap<String, Boolean> load() {
        ensureFile();
        LinkedHashMap<String, Boolean> rules = new LinkedHashMap<>();
        try {
            String json = Files.readString(path(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("screens") && root.get("screens").isJsonObject()) {
                for (var entry : root.getAsJsonObject("screens").entrySet()) {
                    rules.put(entry.getKey(), entry.getValue().getAsBoolean());
                }
            }
        } catch (Exception ignored) {
        }
        return rules;
    }

    private static synchronized void save(Map<String, Boolean> rules) {
        try {
            Files.createDirectories(path().getParent());
            JsonObject root = new JsonObject();
            JsonObject screens = new JsonObject();
            for (var entry : rules.entrySet()) {
                screens.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("screens", screens);
            Files.writeString(path(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static void ensureFile() {
        if (Files.exists(path())) return;
        save(new LinkedHashMap<>());
    }

    private static Path path() {
        return ConfigPaths.file(FILE_NAME);
    }
}
