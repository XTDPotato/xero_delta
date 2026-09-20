package com.xtdpotato.xero_delta;

import com.xtdpotato.xero_delta.data.SafetyBoxItemPolicy;
import com.xtdpotato.xero_delta.data.MedicalShortcutRules;
import com.xtdpotato.xero_delta.data.CorpseRules;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class Config {
    public static final Config INSTANCE;
    public static final ModConfigSpec SPEC;

    public final ModConfigSpec.BooleanValue quickMoveEnabled;
    public final ModConfigSpec.IntValue quickMoveValueThreshold;
    public final ModConfigSpec.BooleanValue itemGridEnabled;
    public final ModConfigSpec.DoubleValue gridBorderThickness;
    public final ModConfigSpec.ConfigValue<String> tooltipSizeMode;
    public final ModConfigSpec.DoubleValue tooltipSizeScale;
    public final ModConfigSpec.IntValue tooltipSizePaddingTop;
    public final ModConfigSpec.IntValue tooltipSizePaddingBottom;
    public final ModConfigSpec.IntValue tooltipSizePaddingLeft;
    public final ModConfigSpec.IntValue tooltipSizePaddingRight;
    public final ModConfigSpec.IntValue tooltipTitleOffsetX;
    public final ModConfigSpec.IntValue tooltipTitleOffsetY;
    public final ModConfigSpec.BooleanValue tooltipTitleAutoOffset;
    public final ModConfigSpec.BooleanValue inventoryShowEquipment;
    public final ModConfigSpec.ConfigValue<String> inventoryModelControl;
    public final ModConfigSpec.DoubleValue medicalWheelHoldSeconds;
    public final ModConfigSpec.IntValue markerWheelSlots;
    public final ModConfigSpec.DoubleValue markerWheelHoldSeconds;
    public final ModConfigSpec.DoubleValue markerDoubleClickSeconds;
    public final ModConfigSpec.DoubleValue markerLifetimeSeconds;
    public final ModConfigSpec.DoubleValue enemyMarkerLifetimeSeconds;
    public final ModConfigSpec.ConfigValue<String> rescueRequestSound;
    public final ModConfigSpec.ConfigValue<String> configTheme;
    public final ModConfigSpec.ConfigValue<String> configThemeHighlight;
    public final ModConfigSpec.ConfigValue<String> configThemePrimary;
    public final ModConfigSpec.ConfigValue<String> configThemeSecondary;
    public final ModConfigSpec.ConfigValue<String> qualityRedColor;
    public final ModConfigSpec.ConfigValue<String> qualityGoldColor;
    public final ModConfigSpec.ConfigValue<String> qualityPurpleColor;
    public final ModConfigSpec.ConfigValue<String> qualityBlueColor;
    public final ModConfigSpec.ConfigValue<String> qualityGreenColor;
    public final ModConfigSpec.ConfigValue<String> qualityGrayColor;
    public final ModConfigSpec.ConfigValue<List<? extends String>> safetyBoxAllowlist;
    public final ModConfigSpec.ConfigValue<List<? extends String>> safetyBoxBlacklist;
    public final ModConfigSpec.ConfigValue<List<? extends String>> corpseEntityIds;
    public final ModConfigSpec.ConfigValue<List<? extends String>> corpseChestRigCandidates;
    public final ModConfigSpec.ConfigValue<List<? extends String>> corpseBackpackCandidates;
    public final ModConfigSpec.IntValue overlayFontSize;
        public final ModConfigSpec.ConfigValue<String> overlayLayout;
    public final ModConfigSpec.DoubleValue overlayIconScale;
    public final ModConfigSpec.IntValue overlayIconOffsetX;
    public final ModConfigSpec.IntValue overlayIconOffsetY;
    public final ModConfigSpec.DoubleValue overlayTextScale;
    public final ModConfigSpec.IntValue overlayTextOffsetX;
    public final ModConfigSpec.IntValue overlayTextOffsetY;
    public final ModConfigSpec.IntValue overlayTextPadding;
    public final ModConfigSpec.IntValue overlayBgWidth;
    public final ModConfigSpec.IntValue overlayBgHeight;
    public final ModConfigSpec.IntValue overlayBorderPadding;
    public final ModConfigSpec.IntValue overlayGlobalOffsetX;
    public final ModConfigSpec.IntValue overlayGlobalOffsetY;
    public final ModConfigSpec.IntValue overlayCenterX;
    public final ModConfigSpec.IntValue overlayCenterY;
    public final ModConfigSpec.IntValue overlayGridOffsetX;
    public final ModConfigSpec.IntValue overlayGridOffsetY;
    public final ModConfigSpec.DoubleValue overlayGridScale;
    public final ModConfigSpec.BooleanValue overlayVerticalText;
    public final ModConfigSpec.IntValue overlayBorderOffsetX;
    public final ModConfigSpec.IntValue overlayBorderOffsetY;

    static {
        var pair = new ModConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    @SuppressWarnings("deprecation")
    private Config(ModConfigSpec.Builder builder) {
        builder.push("quick_move");
        quickMoveEnabled = builder.comment("Enable quick-move auto-placement into safety box")
            .define("quickMoveEnabled", true);
        quickMoveValueThreshold = builder.comment("Minimum item value for quick-move to safety box")
            .defineInRange("quickMoveValueThreshold", 10000, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("item_grid");
        itemGridEnabled = builder.comment("Enable item size occupancy display and protection in container screens")
            .define("itemGridEnabled", true);
        gridBorderThickness = builder.comment("Custom item grid border thickness in pixels")
            .defineInRange("gridBorderThickness", 0.5, 0.1, 2.0);
        tooltipSizeMode = builder.comment("Item size tooltip mode: TEXT, GRAPHIC, VISUAL, or OFF")
            .define("tooltipSizeMode", TooltipSizeMode.VISUAL.name());
        tooltipSizeScale = builder.comment("Scale for graphical item size tooltip display")
            .defineInRange("tooltipSizeScale", 2.0, 0.5, 3.0);
        tooltipSizePaddingTop = builder.comment("Top padding around the item size tooltip component in pixels")
            .defineInRange("tooltipSizePaddingTop", 4, 0, 20);
        tooltipSizePaddingBottom = builder.comment("Bottom padding around the item size tooltip component in pixels")
            .defineInRange("tooltipSizePaddingBottom", 4, 0, 20);
        tooltipSizePaddingLeft = builder.comment("Left padding around the item size tooltip component in pixels")
            .defineInRange("tooltipSizePaddingLeft", 4, 0, 20);
        tooltipSizePaddingRight = builder.comment("Right padding around the item size tooltip component in pixels")
            .defineInRange("tooltipSizePaddingRight", 4, 0, 20);
        tooltipTitleOffsetX = builder.comment("Horizontal offset of the item tooltip title, including its quality icon")
            .defineInRange("tooltipTitleOffsetX", 0, -100, 100);
        tooltipTitleOffsetY = builder.comment("Vertical offset of the item tooltip title, including its quality icon")
            .defineInRange("tooltipTitleOffsetY", 0, -100, 100);
        tooltipTitleAutoOffset = builder.comment("Automatically avoid inline tooltip models from other mods")
            .define("tooltipTitleAutoOffset", true);
        inventoryShowEquipment = builder.comment("Show the player's worn equipment in the inventory model")
            .define("inventoryShowEquipment", true);
        inventoryModelControl = builder.comment("Inventory model control: MOUSE or DRAG")
            .define("inventoryModelControl", "MOUSE");
        builder.pop();

        builder.push("wheel_menu");
        medicalWheelHoldSeconds = builder.comment(
                "Seconds the medical shortcut must be held before opening its wheel")
            .defineInRange("medicalWheelHoldSeconds",
                MedicalShortcutRules.DEFAULT_HOLD_SECONDS, 0.10D, 3.0D);
        markerWheelSlots = builder.comment("Number of slots in the tactical marker wheel (4 or 8)")
            .defineInRange("markerWheelSlots", 8, 4, 8);
        markerWheelHoldSeconds = builder.comment(
                "Seconds the middle mouse button must be held before opening the marker wheel")
            .defineInRange("markerWheelHoldSeconds", 0.75D, 0.10D, 3.0D);
        markerDoubleClickSeconds = builder.comment(
                "Maximum interval between middle-clicks for an enemy marker")
            .defineInRange("markerDoubleClickSeconds", 1.0D, 0.10D, 3.0D);
        markerLifetimeSeconds = builder.comment(
                "How long server-authoritative tactical markers remain visible")
            .defineInRange("markerLifetimeSeconds", 20.0D, 1.0D, 60.0D);
        enemyMarkerLifetimeSeconds = builder.comment(
                "How long enemy markers remain visible")
            .defineInRange("enemyMarkerLifetimeSeconds", 10.0D, 1.0D, 60.0D);
        builder.pop();

        builder.push("downed");
        rescueRequestSound = builder.comment(
                "Sound played for every teammate when a downed player requests rescue")
            .define("rescueRequestSound", "minecraft:block.note_block.bell", value ->
                value instanceof String text && text.length() <= 128);
        builder.pop();

        builder.push("config_screen");
        configTheme = builder.comment("Color theme for the Xero Delta configuration screen")
            .define("theme", "mint");
        configThemeHighlight = builder.comment("Custom theme highlight/accent ARGB color")
            .define("customHighlight", "0xFF55D6B0");
        configThemePrimary = builder.comment("Custom theme primary/background ARGB color")
            .define("customPrimary", "0xF20B1418");
        configThemeSecondary = builder.comment("Custom theme secondary/panel ARGB color")
            .define("customSecondary", "0xF2223238");
        builder.pop();

        builder.push("quality_colors");
        qualityRedColor = builder.comment("ARGB color for red quality background")
            .define("red", "0x66CC3333");
        qualityGoldColor = builder.comment("ARGB color for gold quality background")
            .define("gold", "0x66D6A629");
        qualityPurpleColor = builder.comment("ARGB color for purple quality background")
            .define("purple", "0x669554D9");
        qualityBlueColor = builder.comment("ARGB color for blue quality background")
            .define("blue", "0x664D8FD9");
        qualityGreenColor = builder.comment("ARGB color for green quality background")
            .define("green", "0x6658A65C");
        qualityGrayColor = builder.comment("ARGB color for gray/default quality background")
            .define("gray", "0x66666666");
        builder.pop();

        builder.push("display");
        overlayFontSize = builder.comment("Font size for the safety box overlay text (legacy)")
            .defineInRange("overlayFontSize", 4, 4, 24);
        builder.pop();

        builder.push("layout");
        overlayLayout = builder.comment("Layout of safety box UI: TOP, BOTTOM, LEFT, or RIGHT")
            .define("overlayLayout", "TOP");
        overlayIconScale = builder.comment("Icon scale multiplier (0.2-2.0)")
            .defineInRange("overlayIconScale", 0.5, 0.2, 2.0);
        overlayIconOffsetX = builder.comment("Icon horizontal offset")
            .defineInRange("overlayIconOffsetX", 0, -30, 30);
        overlayIconOffsetY = builder.comment("Icon vertical offset")
            .defineInRange("overlayIconOffsetY", 0, -30, 30);
        overlayTextScale = builder.comment("Text scale multiplier (0.2-2.0)")
            .defineInRange("overlayTextScale", 0.5, 0.2, 2.0);
        overlayTextOffsetX = builder.comment("Text horizontal offset")
            .defineInRange("overlayTextOffsetX", 0, -30, 30);
        overlayTextOffsetY = builder.comment("Text vertical offset")
            .defineInRange("overlayTextOffsetY", 0, -30, 30);
        overlayTextPadding = builder.comment("Text padding / margin within header")
            .defineInRange("overlayTextPadding", 4, 0, 12);
        overlayBgWidth = builder.comment("Header background width (0 = auto)")
            .defineInRange("overlayBgWidth", 0, 0, 200);
        overlayBgHeight = builder.comment("Header background height (0 = auto)")
            .defineInRange("overlayBgHeight", 0, 0, 200);
        overlayBorderPadding = builder.comment("Border padding around header")
            .defineInRange("overlayBorderPadding", 2, 0, 10);
        overlayGlobalOffsetX = builder.comment("Global horizontal offset of entire overlay")
            .defineInRange("overlayGlobalOffsetX", 0, -100, 100);
        overlayGlobalOffsetY = builder.comment("Global vertical offset of entire overlay")
            .defineInRange("overlayGlobalOffsetY", 0, -100, 100);
        overlayCenterX = builder.comment("Center X position of overlay (-1 = auto, 0-100 = percent from left)")
            .defineInRange("overlayCenterX", -1, -1, 100);
        overlayCenterY = builder.comment("Center Y position of overlay (-1 = auto, 0-100 = percent from top)")
            .defineInRange("overlayCenterY", -1, -1, 100);
        overlayGridOffsetX = builder.comment("Grid horizontal offset")
            .defineInRange("overlayGridOffsetX", 0, -30, 30);
        overlayGridOffsetY = builder.comment("Grid vertical offset")
            .defineInRange("overlayGridOffsetY", 0, -30, 30);
        overlayGridScale = builder.comment("Grid cell scale multiplier (0.5-2.0)")
            .defineInRange("overlayGridScale", 1.0, 0.5, 2.0);
        overlayVerticalText = builder.comment("Display text vertically (one char per line)")
            .define("overlayVerticalText", false);
        overlayBorderOffsetX = builder.comment("Header border horizontal offset")
            .defineInRange("overlayBorderOffsetX", 0, -30, 30);
        overlayBorderOffsetY = builder.comment("Header border vertical offset")
            .defineInRange("overlayBorderOffsetY", 0, -30, 30);
        builder.pop();

        builder.push("blacklist");
        safetyBoxAllowlist = builder.comment("Item patterns allowed into safety box even if blacklisted (supports wildcards, overrides blacklist)")
            .defineList("safetyBoxAllowlist", List.of(
                "tacz:ammo*"
            ), s -> s instanceof String);
        safetyBoxBlacklist = builder.comment("Item patterns blocked from safety box (supports wildcards)")
            .defineList("safetyBoxBlacklist", List.of(
                "sophisticatedbackpacks:*",
                "travelersbackpack:*",
                "xero_delta:*",
                "tacz:*"
            ), s -> s instanceof String);
        builder.pop();

        builder.push("corpse_rules");
        List<String> defaultCorpseEntities = CorpseRules.DEFAULT_MOB_ENTITY_IDS.stream()
            .sorted().toList();
        corpseEntityIds = builder.comment(
                "Entity IDs that generate lootable corpses")
            .defineList("entityIds", defaultCorpseEntities, value -> value instanceof String);
        corpseChestRigCandidates = builder.comment(
                "Weighted chest-rig entries: entity_id|item_id|weight")
            .defineList("chestRigCandidates", defaultCorpseEntities.stream()
                .map(entity -> entity + "|" + CorpseRules.DEFAULT_CHEST_RIG_ID + "|1")
                .toList(), value -> value instanceof String);
        corpseBackpackCandidates = builder.comment(
                "Weighted backpack entries: entity_id|item_id|weight")
            .defineList("backpackCandidates", List.of(), value -> value instanceof String);
        builder.pop();
    }

    public enum Layout { TOP, BOTTOM, LEFT, RIGHT }

    public enum TooltipSizeMode {
        TEXT,
        GRAPHIC,
        VISUAL,
        OFF
    }

    public TooltipSizeMode getTooltipSizeMode() {
        try {
            return TooltipSizeMode.valueOf(tooltipSizeMode.get().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {
            return TooltipSizeMode.VISUAL;
        }
    }

    public Layout getLayout() {
        try { return Layout.valueOf(overlayLayout.get().toUpperCase()); }
        catch (IllegalArgumentException e) { return Layout.TOP; }
    }

    public float getEffectiveIconScale() { return overlayIconScale.get().floatValue(); }
    public float getEffectiveTextScale() { return overlayTextScale.get().floatValue(); }

    public int getQualityColor(String quality) {
        String value = switch (quality == null ? "gray" : quality.toLowerCase(java.util.Locale.ROOT)) {
            case "red" -> qualityRedColor.get();
            case "gold" -> qualityGoldColor.get();
            case "purple" -> qualityPurpleColor.get();
            case "blue" -> qualityBlueColor.get();
            case "green" -> qualityGreenColor.get();
            default -> qualityGrayColor.get();
        };
        try {
            String normalized = value == null ? "" : value.trim();
            if (normalized.startsWith("#")) normalized = normalized.substring(1);
            else if (normalized.startsWith("0x") || normalized.startsWith("0X")) normalized = normalized.substring(2);
            long color = Long.parseUnsignedLong(normalized, 16);
            if (normalized.length() <= 6) color |= 0xFF000000L;
            return (int) color;
        } catch (Exception ignored) {
            return 0x66666666;
        }
    }

    public void setQualityColor(String quality, String color) {
        switch (quality == null ? "gray" : quality.toLowerCase(java.util.Locale.ROOT)) {
            case "red" -> qualityRedColor.set(color);
            case "gold" -> qualityGoldColor.set(color);
            case "purple" -> qualityPurpleColor.set(color);
            case "blue" -> qualityBlueColor.set(color);
            case "green" -> qualityGreenColor.set(color);
            default -> qualityGrayColor.set(color);
        }
    }

    public boolean isBlacklisted(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = stack.getItemHolder().getKey().location().toString();
        for (String pattern : safetyBoxAllowlist.get()) {
            if (matchesPattern(pattern, id)) return false;
        }
        if (SafetyBoxItemPolicy.isExplicitlyAllowed(stack)) return false;
        if (stack.is(ItemTags.DURABILITY_ENCHANTABLE)) {
            return true;
        }
        for (String pattern : safetyBoxBlacklist.get()) {
            if (matchesPattern(pattern, id)) return true;
        }
        return false;
    }

    private static boolean matchesPattern(String pattern, String id) {
        if (pattern.equals(id)) return true;
        if (pattern.endsWith("*")) {
            return id.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        int ci = pattern.indexOf(':');
        if (ci >= 0) {
            String ns = pattern.substring(0, ci);
            String path = pattern.substring(ci + 1);
            if ("*".equals(path)) return id.startsWith(ns + ":");
            int idCi = id.indexOf(':');
            if (idCi < 0 || !id.substring(0, idCi).equals(ns)) return false;
            String idPath = id.substring(idCi + 1);
            if (path.startsWith("*") && path.endsWith("*") && path.length() > 2) {
                String keyword = path.substring(1, path.length() - 1);
                return idPath.contains(keyword);
            }
            if (path.startsWith("*")) {
                String suffix = path.substring(1);
                return idPath.endsWith(suffix);
            }
        }
        return false;
    }
}
