package com.xtdpotato.xero_delta.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.item.ItemStack;
import com.xtdpotato.xero_delta.data.size.ItemSizeProviders;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerItemRules {
    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "xero_delta_item_rules.json";
    private static final long FILE_CHECK_INTERVAL_NANOS = 1_000_000_000L;
    private static volatile Rules cachedRules;
    private static volatile long cachedModified = Long.MIN_VALUE;
    private static volatile long cachedFileSize = Long.MIN_VALUE;
    private static volatile long nextFileCheckNanos;
    private static final Map<String, ItemSizeRule> RESOLVED_SIZE_RULES = new ConcurrentHashMap<>();
    private static final Map<String, String> RESOLVED_QUALITY_RULES = new ConcurrentHashMap<>();

    private ServerItemRules() {
    }

    public static ItemSize getSizeFor(ItemStack stack) {
        return getSizeRuleFor(stack).size();
    }

    public static ItemSizeRule getSizeRuleFor(ItemStack stack) {
        if (stack.isEmpty()) return ItemSizeRule.DEFAULT;
        Rules rules = load();
        String exactKey = ModDataStorage.getKey(stack);
        boolean xeroItem = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(stack.getItem()).getNamespace().equals("xero_delta");
        return RESOLVED_SIZE_RULES.computeIfAbsent(exactKey, ignored -> {
            String manualKey = findRuleKey(rules.manualSizes(), stack);
            if (manualKey != null) return ItemSizeRule.unpack(rules.sizes().get(manualKey));
            ItemSize fixedSize = BuiltinItemRuleCatalog.fixedSize(stack);
            if (fixedSize != null) {
                return new ItemSizeRule(fixedSize, true,
                    xeroItem && !BuiltinItemRuleCatalog.shouldFitTextureAspect(stack));
            }
            BuiltinItemRuleCatalog.Rule builtin = BuiltinItemRuleCatalog.explicit(stack);
            if (builtin != null && builtin.stretchTexture()) {
                return new ItemSizeRule(builtin.size(), true, true);
            }
            String configuredKey = findRuleKey(rules.sizes(), stack);
            ItemSizeRule automatic = configuredKey == null
                ? null
                : ItemSizeRule.unpack(rules.sizes().get(configuredKey));
            ItemSizeRule compatibility = ItemSizeProviders.resolveCompatibility(stack);
            if (compatibility != null) {
                // Keep component/NBT-sensitive compatibility dimensions (for
                // example individual TACZ guns), while applying the visual
                // policy written by /xero_size auto for this item type.
                return automatic == null
                    ? compatibility
                    : new ItemSizeRule(compatibility.size(), automatic.rotateTexture(),
                        automatic.stretchTexture(), automatic.proportionalScale());
            }
            if (automatic != null) return automatic;
            return builtin == null ? ItemSizeRule.DEFAULT
                : new ItemSizeRule(builtin.size(), true,
                    !BuiltinItemRuleCatalog.shouldFitTextureAspect(stack)
                        && (xeroItem || builtin.stretchTexture()));
        });
    }

    public static ItemSizeRule getConfiguredSizeRuleFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Rules rules = load();
        String key = findRuleKey(rules.manualSizes(), stack);
        if (key == null) key = findRuleKey(rules.sizes(), stack);
        Long packed = key == null ? null : rules.sizes().get(key);
        return packed == null ? null : ItemSizeRule.unpack(packed);
    }

    public static ItemSizeRule getConfiguredManualSizeRuleFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Rules rules = load();
        String key = findRuleKey(rules.manualSizes(), stack);
        return key == null ? null : ItemSizeRule.unpack(rules.sizes().get(key));
    }

    public static boolean shouldRotateTexture(ItemStack stack) {
        return getSizeRuleFor(stack).rotateTexture();
    }

    public static boolean shouldStretchTexture(ItemStack stack) {
        return getSizeRuleFor(stack).stretchTexture();
    }

    public static int proportionalTextureScale(ItemStack stack) {
        return getSizeRuleFor(stack).proportionalScale();
    }

    public static String getQualityFor(ItemStack stack) {
        if (stack.isEmpty()) return "gray";
        Rules rules = load();
        String exactKey = ModDataStorage.getKey(stack);
        String itemId = ModDataStorage.getIdOnlyKey(stack);
        String safetyBoxQuality = AutomaticItemValuation.safetyBoxQualities().get(itemId);
        if (safetyBoxQuality != null) return safetyBoxQuality;
        return RESOLVED_QUALITY_RULES.computeIfAbsent(exactKey, ignored -> {
            String manualKey = findRuleKey(rules.manualQualities(), stack);
            if (manualKey != null) return rules.qualities().get(manualKey);
            String qualityKey = findRuleKey(rules.qualities(), stack);
            String quality = qualityKey == null ? null : rules.qualities().get(qualityKey);
            boolean automatic = qualityKey != null && rules.autoQualityKeys().contains(qualityKey);
            if (quality != null && !automatic) return quality;
            if (TaczCompatibilityRules.isLrTacticalMelee(stack)) return "red";
            if (automatic) {
                DynamicItemValuation.Rule dynamic = DynamicItemValuation.resolve(stack);
                if (dynamic != null) return dynamic.quality();
            }
            BuiltinItemRuleCatalog.Rule builtin = BuiltinItemRuleCatalog.explicit(stack);
            if (builtin != null) return builtin.quality();
            String modNameQuality = RuleFormulaConfig.modQualityFromName(stack);
            if (modNameQuality != null) return ModDataStorage.normalizeQuality(modNameQuality);
            return quality == null
                ? AutomaticItemValuation.safetyBoxQualities().getOrDefault(itemId, "gray")
                : quality;
        });
    }

    public static String getConfiguredQualityFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Rules rules = load();
        String key = findRuleKey(rules.manualQualities(), stack);
        if (key == null) key = findRuleKey(rules.qualities(), stack);
        return key == null ? null : rules.qualities().get(key);
    }

    public static String getConfiguredManualQualityFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Rules rules = load();
        String key = findRuleKey(rules.manualQualities(), stack);
        return key == null ? null : rules.qualities().get(key);
    }

    public static boolean hasConfiguredRules() {
        Rules rules = load();
        return !rules.sizes().isEmpty() || !rules.qualities().isEmpty();
    }

    public static Map<String, Long> getAllSizes() {
        return Map.copyOf(load().sizes());
    }

    public static Map<String, String> getAllQualities() {
        Map<String, String> qualities = new HashMap<>(load().qualities());
        for (var entry : AutomaticItemValuation.safetyBoxQualities().entrySet()) {
            qualities.put(exactKey(entry.getKey()), entry.getValue());
        }
        return Map.copyOf(qualities);
    }

    public static Set<String> getAutoQualityKeys() {
        return load().autoQualityKeys();
    }

    public static Set<String> getAutoSizeKeys() {
        return load().autoSizeKeys();
    }

    public static void setSize(String key, ItemSize size) {
        setSize(key, size, true, false);
    }

    public static void setSize(String key, ItemSize size, boolean rotateTexture) {
        setSize(key, size, rotateTexture, false);
    }

    public static void setSize(String key, ItemSize size, boolean rotateTexture, boolean stretchTexture) {
        setSize(key, size, rotateTexture, stretchTexture, 0);
    }

    public static synchronized void setSize(String key, ItemSize size, boolean rotateTexture, boolean stretchTexture, int proportionalScale) {
        key = canonicalRuleKey(key);
        Rules rules = load();
        Map<String, Long> sizes = new HashMap<>(rules.sizes());
        sizes.put(key, new ItemSizeRule(size, rotateTexture, stretchTexture, proportionalScale).pack());
        Set<String> autoSizeKeys = new HashSet<>(rules.autoSizeKeys());
        autoSizeKeys.remove(key);
        save(new Rules(sizes, rules.qualities(), autoSizeKeys, rules.autoQualityKeys()));
    }

    public static synchronized void removeSize(String key) {
        key = canonicalRuleKey(key);
        Rules rules = load();
        Map<String, Long> sizes = new HashMap<>(rules.sizes());
        sizes.remove(key);
        Set<String> autoSizeKeys = new HashSet<>(rules.autoSizeKeys());
        autoSizeKeys.remove(key);
        save(new Rules(sizes, rules.qualities(), autoSizeKeys, rules.autoQualityKeys()));
    }

    public static synchronized void setAutoSizes(Map<String, ItemSizeRule> calculatedSizes) {
        Rules rules = load();
        Map<String, Long> sizes = new HashMap<>(rules.sizes());
        for (String key : rules.autoSizeKeys()) sizes.remove(key);
        Set<String> autoSizeKeys = new HashSet<>();
        for (var entry : calculatedSizes.entrySet()) {
            String key = automaticRuleKey(entry.getKey());
            if (sizes.containsKey(key)) continue;
            sizes.put(key, entry.getValue().pack());
            autoSizeKeys.add(key);
        }
        save(new Rules(sizes, rules.qualities(), autoSizeKeys, rules.autoQualityKeys()));
    }

    static String automaticRuleKey(String key) {
        if (key != null && (key.startsWith("item:") || key.startsWith(DurabilityRange.PREFIX)
            || key.startsWith("type:") || "*".equals(key))) {
            return canonicalRuleKey(key);
        }
        return exactKey(key == null ? "" : key);
    }

    public static synchronized void clearSizes() {
        Rules rules = load();
        save(new Rules(Map.of(), rules.qualities(), Set.of(), rules.autoQualityKeys()));
    }

    public static synchronized void setQuality(String key, String quality) {
        key = canonicalRuleKey(key);
        Rules rules = load();
        Map<String, String> qualities = new HashMap<>(rules.qualities());
        qualities.put(key, ModDataStorage.normalizeQuality(quality));
        Set<String> autoQualityKeys = new HashSet<>(rules.autoQualityKeys());
        autoQualityKeys.remove(key);
        save(new Rules(rules.sizes(), qualities, rules.autoSizeKeys(), autoQualityKeys));
    }

    public static synchronized void removeQuality(String key) {
        key = canonicalRuleKey(key);
        Rules rules = load();
        Map<String, String> qualities = new HashMap<>(rules.qualities());
        qualities.remove(key);
        Set<String> autoQualityKeys = new HashSet<>(rules.autoQualityKeys());
        autoQualityKeys.remove(key);
        save(new Rules(rules.sizes(), qualities, rules.autoSizeKeys(), autoQualityKeys));
    }

    public static synchronized void setAutoQualities(Map<String, String> itemQualities) {
        Rules rules = load();
        Map<String, String> qualities = new HashMap<>(rules.qualities());
        for (String key : rules.autoQualityKeys()) qualities.remove(key);
        Set<String> autoQualityKeys = new HashSet<>();
        for (var entry : itemQualities.entrySet()) {
            String key = exactKey(entry.getKey());
            if (qualities.containsKey(key)) continue;
            qualities.put(key, ModDataStorage.normalizeQuality(entry.getValue()));
            autoQualityKeys.add(key);
        }
        save(new Rules(rules.sizes(), qualities, rules.autoSizeKeys(), autoQualityKeys));
    }

    public static synchronized void setAutoQualityAndSize(Map<String, String> itemQualities,
                                                          Map<String, ItemSizeRule> calculatedSizes) {
        Rules rules = load();

        Map<String, Long> sizes = new HashMap<>(rules.sizes());
        for (String key : rules.autoSizeKeys()) sizes.remove(key);
        Set<String> autoSizeKeys = new HashSet<>();
        for (var entry : calculatedSizes.entrySet()) {
            String key = automaticRuleKey(entry.getKey());
            if (sizes.containsKey(key)) continue;
            sizes.put(key, entry.getValue().pack());
            autoSizeKeys.add(key);
        }

        Map<String, String> qualities = new HashMap<>(rules.qualities());
        for (String key : rules.autoQualityKeys()) qualities.remove(key);
        Set<String> autoQualityKeys = new HashSet<>();
        for (var entry : itemQualities.entrySet()) {
            String key = exactKey(entry.getKey());
            if (qualities.containsKey(key)) continue;
            qualities.put(key, ModDataStorage.normalizeQuality(entry.getValue()));
            autoQualityKeys.add(key);
        }

        save(new Rules(sizes, qualities, autoSizeKeys, autoQualityKeys));
    }

    public static synchronized void clearQualities() {
        Rules rules = load();
        save(new Rules(rules.sizes(), Map.of(), rules.autoSizeKeys(), Set.of()));
    }

    private static Rules load() {
        long now = System.nanoTime();
        Rules current = cachedRules;
        if (current != null && now < nextFileCheckNanos) return current;
        synchronized (ServerItemRules.class) {
            now = System.nanoTime();
            current = cachedRules;
            if (current != null && now < nextFileCheckNanos) return current;
            Path path = path();
            ensureFile(path);
            nextFileCheckNanos = now + FILE_CHECK_INTERVAL_NANOS;
            try {
                long modified = Files.getLastModifiedTime(path).toMillis();
                long fileSize = Files.size(path);
                if (current != null && modified == cachedModified && fileSize == cachedFileSize) return current;
                String json = Files.readString(path, StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                Map<String, Long> sizes = readSizes(root);
                Map<String, String> qualities = readQualities(root);
                Set<String> autoSizeKeys = readAutoSizeKeys(root);
                Set<String> autoQualityKeys = readAutoQualityKeys(root, qualities);
                Rules loaded = new Rules(Map.copyOf(sizes), Map.copyOf(qualities), Set.copyOf(autoSizeKeys),
                    Set.copyOf(autoQualityKeys));
                updateCache(loaded, modified, fileSize);
                return loaded;
            } catch (Exception ignored) {
                return current != null ? current : new Rules(Map.of(), Map.of(), Set.of(), Set.of());
            }
        }
    }

    private static synchronized void save(Rules rules) {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            rules = canonicalRules(rules);
            JsonObject root = new JsonObject();
            JsonObject sizes = new JsonObject();
            for (var entry : rules.sizes().entrySet()) {
                ItemSizeRule rule = ItemSizeRule.unpack(entry.getValue());
                JsonObject value = new JsonObject();
                value.addProperty("size", rule.size().width() + "x" + rule.size().height());
                value.addProperty("rotateTexture", rule.rotateTexture());
                value.addProperty("stretchTexture", rule.stretchTexture());
                if (rule.proportionalScale() > 0) value.addProperty("proportionalScale", rule.proportionalScale());
                if (rules.autoSizeKeys().contains(entry.getKey())) value.addProperty("auto", true);
                sizes.add(entry.getKey(), value);
            }
            root.add("sizes", sizes);

            JsonObject qualities = new JsonObject();
            for (var entry : rules.qualities().entrySet()) {
                String quality = ModDataStorage.normalizeQuality(entry.getValue());
                if (rules.autoQualityKeys().contains(entry.getKey())) {
                    JsonObject value = new JsonObject();
                    value.addProperty("quality", quality);
                    value.addProperty("auto", true);
                    qualities.add(entry.getKey(), value);
                } else {
                    qualities.addProperty(entry.getKey(), quality);
                }
            }
            root.add("qualities", qualities);
            // A direct overwrite can leave the rules file empty if the integrated
            // server is stopped while a command is being saved.  Replace it only
            // after the complete JSON has been written and closed.
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            updateCache(new Rules(Map.copyOf(rules.sizes()), Map.copyOf(rules.qualities()), Set.copyOf(rules.autoSizeKeys()),
                    Set.copyOf(rules.autoQualityKeys())),
                Files.getLastModifiedTime(path).toMillis(), Files.size(path));
            nextFileCheckNanos = System.nanoTime() + FILE_CHECK_INTERVAL_NANOS;
        } catch (IOException ignored) {
        }
    }

    private static void updateCache(Rules rules, long modified, long fileSize) {
        cachedRules = rules;
        cachedModified = modified;
        cachedFileSize = fileSize;
        RESOLVED_SIZE_RULES.clear();
        RESOLVED_QUALITY_RULES.clear();
    }

    private static Map<String, Long> readSizes(JsonObject root) {
        if (!root.has("sizes") || !root.get("sizes").isJsonObject()) return Map.of();
        Map<String, Long> sizes = new HashMap<>();
        for (var entry : root.getAsJsonObject("sizes").entrySet()) {
            ItemSizeRule rule = parseSizeRule(entry.getValue());
            sizes.put(canonicalRuleKey(entry.getKey()), rule.pack());
        }
        return sizes;
    }

    private static Map<String, String> readQualities(JsonObject root) {
        if (!root.has("qualities") || !root.get("qualities").isJsonObject()) return Map.of();
        Map<String, String> qualities = new HashMap<>();
        for (var entry : root.getAsJsonObject("qualities").entrySet()) {
            JsonElement value = entry.getValue();
            String quality = value.isJsonObject() && value.getAsJsonObject().has("quality")
                ? value.getAsJsonObject().get("quality").getAsString()
                : value.getAsString();
            qualities.put(canonicalRuleKey(entry.getKey()), ModDataStorage.normalizeQuality(quality));
        }
        return qualities;
    }

    private static Set<String> readAutoSizeKeys(JsonObject root) {
        Set<String> keys = new HashSet<>();
        if (root.has("sizes") && root.get("sizes").isJsonObject()) {
            for (var entry : root.getAsJsonObject("sizes").entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonObject() && value.getAsJsonObject().has("auto")
                    && value.getAsJsonObject().get("auto").getAsBoolean()) {
                    keys.add(canonicalRuleKey(entry.getKey()));
                }
            }
        }
        if (root.has("autoSizeKeys") && root.get("autoSizeKeys").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("autoSizeKeys")) {
                keys.add(canonicalRuleKey(element.getAsString()));
            }
        }
        return keys;
    }

    private static Set<String> readAutoQualityKeys(JsonObject root, Map<String, String> qualities) {
        Set<String> keys = new HashSet<>();
        if (root.has("qualities") && root.get("qualities").isJsonObject()) {
            for (var entry : root.getAsJsonObject("qualities").entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonObject() && value.getAsJsonObject().has("auto")
                    && value.getAsJsonObject().get("auto").getAsBoolean()) {
                    keys.add(canonicalRuleKey(entry.getKey()));
                }
            }
        }
        if (root.has("autoQualityKeys") && root.get("autoQualityKeys").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("autoQualityKeys")) {
                keys.add(canonicalRuleKey(element.getAsString()));
            }
        } else if (keys.isEmpty() && root.has("qualities")) {
            boolean hasEmbeddedFormat = root.getAsJsonObject("qualities").entrySet().stream()
                .anyMatch(entry -> entry.getValue().isJsonObject());
            if (!hasEmbeddedFormat) keys.addAll(qualities.keySet());
        }
        return keys;
    }

    private static ItemSize parseSize(String value) {
        String[] parts = value.toLowerCase(java.util.Locale.ROOT).split("x", 2);
        if (parts.length != 2) return ItemSize.ONE;
        try {
            return new ItemSize(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException ignored) {
            return ItemSize.ONE;
        }
    }

    private static ItemSizeRule parseSizeRule(JsonElement value) {
        if (value.isJsonPrimitive()) {
            return new ItemSizeRule(parseSize(value.getAsString()), true, false);
        }
        if (!value.isJsonObject()) return ItemSizeRule.DEFAULT;
        JsonObject object = value.getAsJsonObject();
        ItemSize size = object.has("size") ? parseSize(object.get("size").getAsString()) : ItemSize.ONE;
        boolean rotateTexture = !object.has("rotateTexture") || object.get("rotateTexture").getAsBoolean();
        boolean stretchTexture = object.has("stretchTexture") && object.get("stretchTexture").getAsBoolean();
        int proportionalScale = object.has("proportionalScale") ? object.get("proportionalScale").getAsInt() : 0;
        return new ItemSizeRule(size, rotateTexture, stretchTexture, proportionalScale);
    }

    private static <T> T findRule(Map<String, T> rules, String exactStackKey, String itemId) {
        String key = findRuleKey(rules, exactStackKey, itemId);
        return key == null ? null : rules.get(key);
    }

    private static <T> String findRuleKey(Map<String, T> rules, String exactStackKey, String itemId) {
        return findRuleKey(rules, exactStackKey, itemId, Set.of());
    }

    private static <T> String findRuleKey(Map<String, T> rules, String exactStackKey, String itemId,
                                          Set<String> excluded) {
        if (isAvailable(rules, exactKey(exactStackKey), excluded)) return exactKey(exactStackKey);
        if (isAvailable(rules, exactKey(itemId), excluded)) return exactKey(itemId);
        if (isAvailable(rules, exactStackKey, excluded)) return exactStackKey;
        if (isAvailable(rules, itemId, excluded)) return itemId;
        String bestPrefix = null;
        String bestKey = null;
        for (var entry : rules.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("type:") || excluded.contains(key)) continue;
            String prefix = key.substring("type:".length());
            if (matchesType(prefix, itemId) && (bestPrefix == null || prefix.length() > bestPrefix.length())) {
                bestPrefix = prefix;
                bestKey = key;
            }
        }
        if (bestKey != null) return bestKey;
        return isAvailable(rules, "*", excluded) ? "*" : null;
    }

    private static <T> String findRuleKey(Map<String, T> rules, ItemStack stack) {
        return findRuleKey(rules, stack, Set.of());
    }

    private static <T> String findRuleKey(Map<String, T> rules, ItemStack stack, Set<String> excluded) {
        if (rules.isEmpty()) return null;
        String exactStackKey = ModDataStorage.getKey(stack);
        if (isAvailable(rules, exactKey(exactStackKey), excluded)) return exactKey(exactStackKey);
        String legacyUnorderedKey = ModDataStorage.getLegacyUnorderedComponentKey(stack);
        if (!legacyUnorderedKey.equals(exactStackKey)
            && isAvailable(rules, exactKey(legacyUnorderedKey), excluded)) {
            return exactKey(legacyUnorderedKey);
        }
        String legacyExactKey = ModDataStorage.getLegacyComponentKey(stack);
        if (!legacyExactKey.equals(exactStackKey) && !legacyExactKey.equals(legacyUnorderedKey)
            && isAvailable(rules, exactKey(legacyExactKey), excluded)) {
            return exactKey(legacyExactKey);
        }
        String legacyVisualKey = ModDataStorage.getLegacyVisualStateKey(stack);
        if (!legacyVisualKey.equals(exactStackKey) && !legacyVisualKey.equals(legacyExactKey)
            && isAvailable(rules, exactKey(legacyVisualKey), excluded)) {
            return exactKey(legacyVisualKey);
        }
        String typeKey = ModDataStorage.getTypeKey(stack);
        if (isAvailable(rules, exactKey(typeKey), excluded)) return exactKey(typeKey);
        String legacyUnorderedTypeKey = ModDataStorage.getLegacyUnorderedTypeKey(stack);
        if (!legacyUnorderedTypeKey.equals(typeKey)
            && isAvailable(rules, exactKey(legacyUnorderedTypeKey), excluded)) {
            return exactKey(legacyUnorderedTypeKey);
        }
        String legacyTypeKey = ModDataStorage.getLegacyTypeKey(stack);
        if (!legacyTypeKey.equals(typeKey) && isAvailable(rules, exactKey(legacyTypeKey), excluded)) {
            return exactKey(legacyTypeKey);
        }
        String bestDurabilityKey = null;
        for (String key : rules.keySet()) {
            if (excluded.contains(key) || !DurabilityRange.matches(key, stack)) continue;
            if (bestDurabilityKey == null || key.compareTo(bestDurabilityKey) < 0) bestDurabilityKey = key;
        }
        return bestDurabilityKey != null ? bestDurabilityKey
            : findRuleKey(rules, exactStackKey, ModDataStorage.getIdOnlyKey(stack), excluded);
    }

    private static boolean isAvailable(Map<String, ?> rules, String key, Set<String> excluded) {
        return rules.containsKey(key) && !excluded.contains(key);
    }

    static <T> Map<String, T> manualEntries(Map<String, T> source, Set<String> automaticKeys) {
        if (source.isEmpty() || automaticKeys.isEmpty()) return source;
        if (source.size() == automaticKeys.size() && automaticKeys.containsAll(source.keySet())) return Map.of();
        Map<String, T> result = new HashMap<>(source);
        automaticKeys.forEach(result::remove);
        return Map.copyOf(result);
    }

    public static String ruleKey(String itemId, String typeSetting) {
        String setting = typeSetting == null ? "true" : typeSetting.toLowerCase(java.util.Locale.ROOT);
        return switch (setting) {
            case "*" -> "*";
            case "false" -> exactKey(itemId);
            default -> exactKey(itemId);
        };
    }

    public static String exactKey(String itemId) {
        return "item:" + itemId;
    }

    private static String canonicalRuleKey(String key) {
        if (key == null || !key.startsWith("item:")) return key == null ? "" : key;
        return exactKey(ModDataStorage.canonicalizeRuleKey(key.substring("item:".length())));
    }

    private static Rules canonicalRules(Rules rules) {
        Map<String, Long> sizes = new HashMap<>();
        for (var entry : rules.sizes().entrySet()) sizes.put(canonicalRuleKey(entry.getKey()), entry.getValue());
        Map<String, String> qualities = new HashMap<>();
        for (var entry : rules.qualities().entrySet()) qualities.put(canonicalRuleKey(entry.getKey()), entry.getValue());
        Set<String> autoSizes = new HashSet<>();
        for (String key : rules.autoSizeKeys()) autoSizes.add(canonicalRuleKey(key));
        Set<String> autoQualities = new HashSet<>();
        for (String key : rules.autoQualityKeys()) autoQualities.add(canonicalRuleKey(key));
        return new Rules(Map.copyOf(sizes), Map.copyOf(qualities), Set.copyOf(autoSizes), Set.copyOf(autoQualities));
    }

    public static String namespaceKey(String namespace) {
        return "type:" + namespace + ":";
    }

    private static String normalizeTypePattern(String itemId) {
        return itemId.endsWith("*") ? itemId.substring(0, itemId.length() - 1) : itemId;
    }

    private static boolean matchesType(String prefix, String itemId) {
        return "*".equals(prefix) || itemId.startsWith(prefix);
    }

    private static void ensureFile(Path path) {
        if (Files.exists(path)) return;
        save(new Rules(Map.of(), Map.of(), Set.of(), Set.of()));
    }

    private static Path path() {
        return ConfigPaths.file(FILE_NAME);
    }

    private static final class Rules {
        private final Map<String, Long> sizes;
        private final Map<String, String> qualities;
        private final Set<String> autoSizeKeys;
        private final Set<String> autoQualityKeys;
        private final Map<String, Long> manualSizes;
        private final Map<String, String> manualQualities;

        private Rules(Map<String, Long> sizes, Map<String, String> qualities,
                      Set<String> autoSizeKeys, Set<String> autoQualityKeys) {
            this.sizes = sizes;
            this.qualities = qualities;
            this.autoSizeKeys = autoSizeKeys;
            this.autoQualityKeys = autoQualityKeys;
            this.manualSizes = manualEntries(sizes, autoSizeKeys);
            this.manualQualities = manualEntries(qualities, autoQualityKeys);
        }

        private Map<String, Long> sizes() { return sizes; }
        private Map<String, String> qualities() { return qualities; }
        private Set<String> autoSizeKeys() { return autoSizeKeys; }
        private Set<String> autoQualityKeys() { return autoQualityKeys; }
        private Map<String, Long> manualSizes() { return manualSizes; }
        private Map<String, String> manualQualities() { return manualQualities; }

    }
}
