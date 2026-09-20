package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.menu.CorpseMenu;
import com.google.gson.*;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ConfigPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;

import java.nio.file.*;
import java.util.Locale;
import java.util.*;

public class SafetyBoxOverlay {
    public static final int DEFAULT_OFFSET_X = -245;
    public static final int DEFAULT_OFFSET_Y = 130;
    private static final Gson GSON = new Gson();
    private static final Map<String, ScreenConfig> screenConfigs = new LinkedHashMap<>();
    private static final Map<String, int[]> DEFAULT_OFFSETS = new HashMap<>();
    static {
        DEFAULT_OFFSETS.put("net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer",
            new int[]{DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y});
    }
    private static volatile boolean configLoaded = false;

    // Hard-exclude by class
    private static final Set<Class<?>> HARD_EXCLUDE_MENUS = new HashSet<>();
    // Name-based exclusion safety net (for renamed/obfuscated mod classes)
    private static final Set<String> EXCLUDE_NAME_PATTERNS = Set.of("curios", "accessories");
    static {
        try { HARD_EXCLUDE_MENUS.add(Class.forName("top.theillusivec4.curios.common.inventory.container.CuriosContainer")); } catch (Exception ignored) {}
        try { HARD_EXCLUDE_MENUS.add(Class.forName("io.wispforest.accessories.menu.AccessoriesMenu")); } catch (Exception ignored) {}
    }

    private static final Set<Class<?>> DEFAULT_ENABLED_MENUS = Set.of(
        InventoryMenu.class, ChestMenu.class, ShulkerBoxMenu.class,
        HopperMenu.class, DispenserMenu.class, BrewingStandMenu.class,
        FurnaceMenu.class, BlastFurnaceMenu.class, SmokerMenu.class,
        BeaconMenu.class, AnvilMenu.class, SmithingMenu.class,
        EnchantmentMenu.class, MerchantMenu.class, LoomMenu.class,
        GrindstoneMenu.class, StonecutterMenu.class, CartographyTableMenu.class,
        CrafterMenu.class
    );

    public static boolean hasPlayerSlots(AbstractContainerMenu menu) {
        if (menu == null) return false;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) return true;
        }
        return false;
    }

    /** Count non-player container slots */
    public static int countContainerSlots(AbstractContainerMenu menu) {
        if (menu == null) return 0;
        int c = 0;
        for (Slot s : menu.slots) if (!(s.container instanceof Inventory)) c++;
        return c;
    }

    /** Menu must have BOTH player inventory AND container slots */
    public static boolean hasBothInventoryAndContainer(AbstractContainerMenu menu) {
        return menu != null && menu.slots.size() > 1 && hasPlayerSlots(menu) && countContainerSlots(menu) > 0;
    }

    public static boolean isBackpackMenu(AbstractContainerMenu menu) {
        if (menu == null) return false;
        String name = menu.getClass().getName();
        return name.contains("sophisticatedbackpacks") && name.contains("Backpack");
    }

    public static boolean shouldRender(AbstractContainerMenu menu) {
        if (menu == null) return false;

        // The corpse screen owns both columns itself and deliberately never exposes
        // the local player's protected safety box on top of the loot layout.
        if (menu instanceof CorpseMenu) return false;

        // Hard exclude: Creative mode inventory
        if (menu.getClass().getName().contains("CreativeMode")) return false;

        // Hard exclude: Curios/Accessories by class instance
        for (Class<?> excluded : HARD_EXCLUDE_MENUS) {
            if (excluded.isInstance(menu)) return false;
        }

        // Safety net: exclude by class name pattern
        String lowerName = menu.getClass().getName().toLowerCase(Locale.ROOT);
        for (String pattern : EXCLUDE_NAME_PATTERNS) {
            if (lowerName.contains(pattern)) return false;
        }

        // Known good vanilla menus: always render
        if (DEFAULT_ENABLED_MENUS.stream().anyMatch(c -> c.isInstance(menu))) return true;
        if (isBackpackMenu(menu)) return true;

        // Safe fallback: has both player inventory AND container slots
        if (hasBothInventoryAndContainer(menu)) return true;

        return false;
    }

    public static class ScreenConfig {
        public boolean enabled = true;
        public boolean manualPosition;
        public int offsetX, offsetY;
        public ScreenConfig() { this.offsetX = DEFAULT_OFFSET_X; this.offsetY = DEFAULT_OFFSET_Y; }
        public ScreenConfig(int x, int y) { this.offsetX = x; this.offsetY = y; }
    }

    private static void ensureLoaded() {
        if (!configLoaded) {
            synchronized (SafetyBoxOverlay.class) {
                if (!configLoaded) { loadConfig(); configLoaded = true; }
            }
        }
    }

    public static String resolveMenuKey(AbstractContainerMenu menu) {
        if (menu == null) return null;
        return menu.getClass().getName();
    }

    public static boolean isMenuEnabled(String key) {
        ensureLoaded();
        if (key == null) return false;
        ScreenConfig cfg = screenConfigs.get(key);
        return cfg == null || cfg.enabled;
    }

    public static int[] getScreenOffset(String key) {
        ensureLoaded();
        ScreenConfig cfg = screenConfigs.get(key);
        if (cfg == null) {
            int[] def = DEFAULT_OFFSETS.get(key);
            cfg = def != null ? new ScreenConfig(def[0], def[1]) : new ScreenConfig();
            screenConfigs.put(key, cfg);
        }
        return new int[]{cfg.offsetX, cfg.offsetY};
    }

    public static void setScreenOffset(String key, int x, int y) {
        setScreenOffset(key, x, y, true);
    }

    public static void setScreenOffset(String key, int x, int y, boolean manualPosition) {
        ensureLoaded();
        ScreenConfig cfg = screenConfigs.computeIfAbsent(key, k -> new ScreenConfig());
        cfg.offsetX = x;
        cfg.offsetY = y;
        cfg.manualPosition = manualPosition;
    }

    public static boolean hasManualPosition(String key) {
        ensureLoaded();
        ScreenConfig cfg = screenConfigs.get(key);
        return cfg != null && cfg.manualPosition;
    }

    public static void setScreenEnabled(String key, boolean enabled) {
        ensureLoaded();
        screenConfigs.computeIfAbsent(key, k -> new ScreenConfig()).enabled = enabled;
    }

    public static boolean addScreenIfAbsent(String key) {
        ensureLoaded();
        if (key == null) return false;
        ScreenConfig existing = screenConfigs.get(key);
        if (existing != null) { if (!existing.enabled) { existing.enabled = true; saveConfig(); return true; } return false; }
        screenConfigs.put(key, new ScreenConfig());
        saveConfig();
        return true;
    }

    public static boolean toggleMenu(String key) {
        ensureLoaded();
        if (key == null) return false;
        ScreenConfig cfg = screenConfigs.get(key);
        if (cfg == null) { cfg = new ScreenConfig(); cfg.enabled = false; screenConfigs.put(key, cfg); saveConfig(); return false; }
        cfg.enabled = !cfg.enabled;
        saveConfig();
        return cfg.enabled;
    }

    public static Map<String, ScreenConfig> getAllConfigs() {
        ensureLoaded();
        return new LinkedHashMap<>(screenConfigs);
    }

    public static void resetScreen(String key) { screenConfigs.put(key, new ScreenConfig()); }
    public static void resetAll() { screenConfigs.clear(); saveConfig(); }

    private static Path getConfigPath() {
        return ConfigPaths.file("safety_box_screens.json");
    }

    private static void loadConfig() {
        try {
            Path path = getConfigPath();
            if (Files.exists(path)) {
                String json = Files.readString(path);
                JsonElement root = JsonParser.parseString(json);
                if (root.isJsonObject()) {
                    for (var entry : root.getAsJsonObject().entrySet()) {
                        JsonElement val = entry.getValue();
                        ScreenConfig cfg = new ScreenConfig();
                        if (val.isJsonObject()) {
                            JsonObject obj = val.getAsJsonObject();
                            if (obj.has("enabled")) cfg.enabled = obj.get("enabled").getAsBoolean();
                            if (obj.has("x")) cfg.offsetX = obj.get("x").getAsInt();
                            if (obj.has("y")) cfg.offsetY = obj.get("y").getAsInt();
                            if (obj.has("manualPosition")) cfg.manualPosition = obj.get("manualPosition").getAsBoolean();
                        } else if (val.isJsonArray()) {
                            var arr = val.getAsJsonArray();
                            if (arr.size() >= 2) { cfg.offsetX = arr.get(0).getAsInt(); cfg.offsetY = arr.get(1).getAsInt(); }
                        }
                        if (!cfg.manualPosition) {
                            cfg.offsetX = DEFAULT_OFFSET_X;
                            cfg.offsetY = DEFAULT_OFFSET_Y;
                        }
                        screenConfigs.put(entry.getKey(), cfg);
                    }
                }
            }
        } catch (Exception e) {
            XeroDelta.LOGGER.error("Failed to load screen config: {}", e.getMessage());
        }
    }

    public static void saveConfig() {
        try {
            JsonObject root = new JsonObject();
            for (var e : screenConfigs.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("enabled", e.getValue().enabled);
                obj.addProperty("manualPosition", e.getValue().manualPosition);
                obj.addProperty("x", e.getValue().offsetX);
                obj.addProperty("y", e.getValue().offsetY);
                root.add(e.getKey(), obj);
            }
            Files.createDirectories(getConfigPath().getParent());
            Files.writeString(getConfigPath(), GSON.toJson(root));
        } catch (Exception ex) {
            XeroDelta.LOGGER.error("Failed to save screen config: {}", ex.getMessage());
        }
    }
}
