package com.xtdpotato.xero_delta.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** File-backed knobs for the automatic quality, size and value formulas. */
public final class RuleFormulaConfig {
    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "item_formulas.json";
    private static final Map<String, String> DEFAULT_NAME_COLORS = new LinkedHashMap<>();
    private static volatile FormulaData loaded;

    static {
        DEFAULT_NAME_COLORS.put("black", "gray");
        DEFAULT_NAME_COLORS.put("dark_blue", "blue");
        DEFAULT_NAME_COLORS.put("dark_green", "green");
        DEFAULT_NAME_COLORS.put("dark_aqua", "blue");
        DEFAULT_NAME_COLORS.put("dark_red", "red");
        DEFAULT_NAME_COLORS.put("dark_purple", "purple");
        DEFAULT_NAME_COLORS.put("gold", "gold");
        DEFAULT_NAME_COLORS.put("gray", "gray");
        DEFAULT_NAME_COLORS.put("dark_gray", "gray");
        DEFAULT_NAME_COLORS.put("blue", "blue");
        DEFAULT_NAME_COLORS.put("green", "green");
        DEFAULT_NAME_COLORS.put("aqua", "blue");
        DEFAULT_NAME_COLORS.put("red", "red");
        DEFAULT_NAME_COLORS.put("light_purple", "purple");
        DEFAULT_NAME_COLORS.put("yellow", "gold");
        DEFAULT_NAME_COLORS.put("white", "gray");
    }

    private RuleFormulaConfig() {
    }

    public static final class Range {
        public long minimumPerSlot;
        public long maximumPerSlot;
        public long maximumTotal;

        public Range() {
        }

        public Range(long minimumPerSlot, long maximumPerSlot, long maximumTotal) {
            this.minimumPerSlot = minimumPerSlot;
            this.maximumPerSlot = maximumPerSlot;
            this.maximumTotal = maximumTotal;
        }

        public Range copy() {
            return new Range(minimumPerSlot, maximumPerSlot, maximumTotal);
        }
    }

    public static final class FormulaData {
        public int defaultWidth = 1;
        public int defaultHeight = 1;
        /** Whether automatic item sizing may derive dimensions from recipe ingredient counts. */
        public boolean recipeSizeEnabled = true;
        public String qualityFormula = "manual > explicit > legendary_tooltips > mod_name_color > mod > material > calculated";
        public String sizeFormula = "custom > compatibility > recipe > category > default";
        public String valueFormula = "clamp(calculated, min_per_slot * slots, max_per_slot * slots) + random_offset";
        public long randomOffsetMinimum = 1;
        public long randomOffsetMaximum = 999;
        public Map<String, Range> ranges = defaultRanges();
        public Map<String, String> nameColorQuality = new LinkedHashMap<>(DEFAULT_NAME_COLORS);

        public FormulaData copy() {
            FormulaData result = new FormulaData();
            result.defaultWidth = defaultWidth;
            result.defaultHeight = defaultHeight;
            result.recipeSizeEnabled = recipeSizeEnabled;
            result.qualityFormula = qualityFormula;
            result.sizeFormula = sizeFormula;
            result.valueFormula = valueFormula;
            result.randomOffsetMinimum = randomOffsetMinimum;
            result.randomOffsetMaximum = randomOffsetMaximum;
            result.ranges = new LinkedHashMap<>();
            if (ranges != null) for (var entry : ranges.entrySet()) {
                if (entry.getValue() != null) result.ranges.put(entry.getKey(), entry.getValue().copy());
            }
            result.nameColorQuality = nameColorQuality == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(nameColorQuality);
            return result;
        }
    }

    public static FormulaData get() {
        FormulaData current = loaded;
        if (current != null) return current.copy();
        synchronized (RuleFormulaConfig.class) {
            if (loaded == null) loaded = read();
            return loaded.copy();
        }
    }

    public static void save(FormulaData value) {
        FormulaData normalized = normalize(value == null ? new FormulaData() : value.copy());
        loaded = normalized;
        try {
            java.nio.file.Files.createDirectories(ConfigPaths.file(FILE_NAME).getParent());
            java.nio.file.Files.writeString(ConfigPaths.file(FILE_NAME), GSON.toJson(normalized));
        } catch (Exception ignored) {
        }
    }

    public static Range range(String quality) {
        FormulaData data = get();
        Range range = data.ranges.get(normalizeQuality(quality));
        return range == null ? defaultRanges().get("gray") : range;
    }

    /** Maps the rendered item-name color to a configured quality. */
    public static String qualityFromName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        TextColor color = findNameColor(stack.getHoverName());
        if (color == null) return null;
        String formatting = nearestFormatting(color.getValue());
        if (formatting == null) return null;
        return get().nameColorQuality.get(formatting);
    }

    /**
     * Mod item names are an automatic quality source evaluated per concrete
     * stack so component/NBT variants sharing one registry id can differ.
     */
    public static String modQualityFromName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        if (namespace.equals("minecraft") || namespace.equals("xero_delta")) return null;
        return qualityFromName(stack);
    }

    private static TextColor findNameColor(Component component) {
        Style style = component.getStyle();
        if (style != null && style.getColor() != null) return style.getColor();
        for (Component sibling : component.getSiblings()) {
            TextColor color = findNameColor(sibling);
            if (color != null) return color;
        }
        return null;
    }

    private static String nearestFormatting(int value) {
        String exact = null;
        int bestDistance = Integer.MAX_VALUE;
        for (ChatFormatting formatting : ChatFormatting.values()) {
            if (!formatting.isColor() || formatting.getColor() == null) continue;
            int candidate = formatting.getColor();
            int distance = colorDistance(value, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                exact = formatting.getName();
            }
        }
        return exact;
    }

    private static int colorDistance(int first, int second) {
        int fr = first >> 16 & 255, fg = first >> 8 & 255, fb = first & 255;
        int sr = second >> 16 & 255, sg = second >> 8 & 255, sb = second & 255;
        int dr = fr - sr, dg = fg - sg, db = fb - sb;
        return dr * dr + dg * dg + db * db;
    }

    private static FormulaData read() {
        try {
            var path = ConfigPaths.file(FILE_NAME);
            if (java.nio.file.Files.exists(path)) {
                FormulaData parsed = GSON.fromJson(java.nio.file.Files.readString(path), FormulaData.class);
                if (parsed != null) return normalize(parsed);
            }
        } catch (Exception ignored) {
        }
        FormulaData defaults = normalize(new FormulaData());
        save(defaults);
        return defaults;
    }

    private static FormulaData normalize(FormulaData value) {
        if (value.ranges == null) value.ranges = new LinkedHashMap<>();
        Map<String, Range> defaults = defaultRanges();
        for (var entry : defaults.entrySet()) {
            Range range = value.ranges.get(entry.getKey());
            if (range == null) value.ranges.put(entry.getKey(), entry.getValue().copy());
            else {
                range.minimumPerSlot = Math.max(1, range.minimumPerSlot);
                range.maximumPerSlot = Math.max(range.minimumPerSlot, range.maximumPerSlot);
                range.maximumTotal = Math.max(range.maximumPerSlot, range.maximumTotal);
            }
        }
        if (value.nameColorQuality == null) value.nameColorQuality = new LinkedHashMap<>();
        for (var entry : DEFAULT_NAME_COLORS.entrySet()) value.nameColorQuality.putIfAbsent(entry.getKey(), entry.getValue());
        value.defaultWidth = Math.max(1, Math.min(8, value.defaultWidth));
        value.defaultHeight = Math.max(1, Math.min(8, value.defaultHeight));
        value.randomOffsetMinimum = Math.max(0L, Math.min(9_999_999L, value.randomOffsetMinimum));
        value.randomOffsetMaximum = Math.max(value.randomOffsetMinimum,
            Math.min(9_999_999L, value.randomOffsetMaximum));
        return value;
    }

    private static Map<String, Range> defaultRanges() {
        Map<String, Range> ranges = new LinkedHashMap<>();
        ranges.put("gray", new Range(100, 2_000, 2_000));
        ranges.put("green", new Range(2_001, 7_000, 7_000));
        ranges.put("blue", new Range(7_001, 25_000, 25_000));
        ranges.put("purple", new Range(9_000, 21_000, 150_000));
        ranges.put("gold", new Range(50_000, 1_200_000, 1_200_000));
        ranges.put("red", new Range(300_000, 5_000_000, 5_000_000));
        return ranges;
    }

    private static String normalizeQuality(String quality) {
        String normalized = quality == null ? "gray" : quality.toLowerCase(Locale.ROOT);
        return DEFAULT_NAME_COLORS.containsValue(normalized) || normalized.equals("gray") ? normalized : "gray";
    }
}
