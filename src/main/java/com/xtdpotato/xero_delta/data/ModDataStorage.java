package com.xtdpotato.xero_delta.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import com.xtdpotato.xero_delta.ModDataComponents;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;

public class ModDataStorage extends SavedData {
    private static final String DATA_NAME = "safety_box_data";
    private final Map<String, Long> prices = new HashMap<>();
    private final Set<String> autoPriceKeys = new HashSet<>();
    private static final Map<String, Long> priceCache = new ConcurrentHashMap<>();

    public static ModDataStorage get(ServerLevel level) {
        var data = level.getDataStorage().computeIfAbsent(
            new Factory<>(ModDataStorage::new, ModDataStorage::load), DATA_NAME);
        data.syncCache();
        return data;
    }

    private void syncCache() {
        priceCache.clear();
        priceCache.putAll(prices);
    }

    // Key prefixes for different match modes
    public static final String PREFIX_WILDCARD = "~";  // wildcard: ~tacz:*
    public static final String PREFIX_TAG = "#";        // tag: #minecraft:swords

    public static String getKey(ItemStack stack) {
        return stableComponentKey(normalizedRuleStack(stack));
    }

    /** Legacy hashed key for rules persisted before the lossless component fingerprint. */
    public static String getLegacyComponentKey(ItemStack stack) {
        return legacyHashedComponentKey(normalizedRuleStack(stack));
    }

    /**
     * Compatibility key for the first v2 format.  That format encoded
     * DataComponentPatch#toString() directly, so equivalent TACZ stacks could
     * receive different keys when the component map was reloaded in another order.
     */
    public static String getLegacyUnorderedComponentKey(ItemStack stack) {
        return unorderedComponentKey(normalizedRuleStack(stack));
    }

    /** Supports rules written before GRID_ROTATED was excluded from item identity. */
    public static String getLegacyVisualStateKey(ItemStack stack) {
        return legacyHashedComponentKey(stack);
    }

    private static ItemStack normalizedRuleStack(ItemStack stack) {
        // Rotation and binding belong to one physical stack and must never create a
        // separate size, quality, value or weight rule identity for the same item.
        ItemStack normalized = stack.copy();
        normalized.remove(ModDataComponents.GRID_ROTATED.get());
        normalized.remove(ModDataComponents.ITEM_BOUND.get());
        normalized.remove(ModDataComponents.ITEM_BOUND_OWNER.get());
        normalized.remove(ModDataComponents.LOOT_SEARCHED.get());
        return normalized;
    }

    private static String stableComponentKey(ItemStack stack) {
        String id = stack.getItemHolder().getKey().location().toString();
        var tag = stack.getComponentsPatch();
        if (tag != null && !tag.isEmpty()) {
            String serialized = ComponentRuleKeys.canonicalizeComponentText(tag.toString());
            String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(serialized.getBytes(StandardCharsets.UTF_8));
            id += "{v2:" + encoded + "}";
        }
        return id;
    }

    private static String unorderedComponentKey(ItemStack stack) {
        String id = stack.getItemHolder().getKey().location().toString();
        var tag = stack.getComponentsPatch();
        if (tag != null && !tag.isEmpty()) {
            String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tag.toString().getBytes(StandardCharsets.UTF_8));
            id += "{v2:" + encoded + "}";
        }
        return id;
    }

    private static String legacyHashedComponentKey(ItemStack stack) {
        String id = stack.getItemHolder().getKey().location().toString();
        var tag = stack.getComponentsPatch();
        if (tag != null && !tag.isEmpty()) {
            id += "{" + Integer.toUnsignedString(tag.toString().hashCode(), 16) + "}";
        }
        return id;
    }

    public static String getIdOnlyKey(ItemStack stack) {
        return stack.getItemHolder().getKey().location().toString();
    }

    /** Preserves item-specific components while deliberately ignoring vanilla wear. */
    public static String getTypeKey(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.remove(DataComponents.DAMAGE);
        return getKey(copy);
    }

    public static String getLegacyTypeKey(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.remove(DataComponents.DAMAGE);
        return getLegacyComponentKey(copy);
    }

    public static String getLegacyUnorderedTypeKey(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.remove(DataComponents.DAMAGE);
        return getLegacyUnorderedComponentKey(copy);
    }

    /** Canonicalizes a persisted raw stack key while leaving ID, wildcard and tag keys untouched. */
    public static String canonicalizeRuleKey(String key) {
        return ComponentRuleKeys.canonicalizeRuleKey(key);
    }

    /** Build a wildcard key for storage */
    public static String wildcardKey(String pattern) {
        return PREFIX_WILDCARD + pattern;
    }

    /** Build a tag key for storage */
    public static String tagKey(String tagId) {
        return PREFIX_TAG + tagId;
    }

    /** Check if a pattern matches an item ID (wildcard matching) */
    public static boolean matchesWildcard(String pattern, String id) {
        if (pattern.equals(id)) return true;
        if (pattern.equals("*")) return true;
        if (pattern.endsWith("*")) {
            return id.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        int ci = pattern.indexOf(':');
        if (ci >= 0) {
            String ns = pattern.substring(0, ci);
            String path = pattern.substring(ci + 1);
            int idCi = id.indexOf(':');
            if (idCi < 0) return false;
            String idNs = id.substring(0, idCi);
            String idPath = id.substring(idCi + 1);
            if (!ns.equals("*") && !ns.equals(idNs)) return false;
            if (path.equals("*")) return true;
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

    /** Get price for an item stack, checking exact > component > wildcard > tag */
    public long getPriceFor(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        // 1. Exact with components
        String exactKey = getKey(stack);
        Long val = priceCache.get(exactKey);
        if (val != null) return val;
        val = prices.get(exactKey);
        if (val != null) return val;
        String legacyUnorderedKey = getLegacyUnorderedComponentKey(stack);
        val = priceCache.get(legacyUnorderedKey);
        if (val != null) return val;
        val = prices.get(legacyUnorderedKey);
        if (val != null) return val;
        String legacyExactKey = getLegacyComponentKey(stack);
        val = priceCache.get(legacyExactKey);
        if (val != null) return val;
        val = prices.get(legacyExactKey);
        if (val != null) return val;
        String legacyVisualKey = getLegacyVisualStateKey(stack);
        val = priceCache.get(legacyVisualKey);
        if (val != null) return val;
        val = prices.get(legacyVisualKey);
        if (val != null) return val;
        String typeKey = getTypeKey(stack);
        val = priceCache.get(typeKey);
        if (val != null) return val;
        val = prices.get(typeKey);
        if (val != null) return val;
        String legacyUnorderedTypeKey = getLegacyUnorderedTypeKey(stack);
        val = priceCache.get(legacyUnorderedTypeKey);
        if (val != null) return val;
        val = prices.get(legacyUnorderedTypeKey);
        if (val != null) return val;
        String legacyTypeKey = getLegacyTypeKey(stack);
        val = priceCache.get(legacyTypeKey);
        if (val != null) return val;
        val = prices.get(legacyTypeKey);
        if (val != null) return val;
        for (var entry : prices.entrySet()) {
            if (DurabilityRange.matches(entry.getKey(), stack)) return entry.getValue();
        }
        // 2. A manual ID rule still takes priority over component-derived auto values.
        String idKey = getIdOnlyKey(stack);
        val = priceCache.get(idKey);
        if (val != null && !autoPriceKeys.contains(idKey)) return val;
        val = prices.get(idKey);
        if (val != null && !autoPriceKeys.contains(idKey)) return val;
        // 3. Wildcard matches
        for (var e : prices.entrySet()) {
            if (e.getKey().startsWith(PREFIX_WILDCARD)) {
                String pattern = e.getKey().substring(PREFIX_WILDCARD.length());
                if (matchesWildcard(pattern, idKey)) {
                    return e.getValue();
                }
            }
        }
        // 4. Tag matches
        var itemTags = stack.getTags().toList();
        for (var e : prices.entrySet()) {
            if (e.getKey().startsWith(PREFIX_TAG)) {
                String tagId = e.getKey().substring(PREFIX_TAG.length());
                for (var tag : itemTags) {
                    if (tag.location().toString().equals(tagId)) {
                        return e.getValue();
                    }
                }
            }
        }
        if (TaczCompatibilityRules.isLrTacticalMelee(stack)) return 0L;
        BuiltinItemRuleCatalog.Rule builtin = BuiltinItemRuleCatalog.explicit(stack);
        if (builtin != null) return builtin.price();
        DynamicItemValuation.Rule dynamic = DynamicItemValuation.resolve(stack);
        if (dynamic != null) return dynamic.price();
        return val == null ? 0 : val;
    }

    public long getPrice(String key) {
        Long cached = priceCache.get(key);
        if (cached != null) return cached;
        return prices.getOrDefault(key, 0L);
    }

    public void setPrice(String key, long price) {
        key = canonicalizeRuleKey(key);
        prices.put(key, price);
        autoPriceKeys.remove(key);
        priceCache.put(key, price);
        setDirty();
    }

    public void removePrice(String key) {
        key = canonicalizeRuleKey(key);
        prices.remove(key);
        autoPriceKeys.remove(key);
        priceCache.remove(key);
        setDirty();
    }

    public void setAutoPrices(Map<String, Long> calculatedPrices) {
        for (String key : autoPriceKeys) prices.remove(key);
        autoPriceKeys.clear();
        for (var entry : calculatedPrices.entrySet()) {
            if (prices.containsKey(entry.getKey())) continue;
            prices.put(entry.getKey(), entry.getValue());
            autoPriceKeys.add(entry.getKey());
        }
        syncCache();
        setDirty();
    }

    public void clearPrices() {
        prices.clear();
        autoPriceKeys.clear();
        syncCache();
        setDirty();
    }

    public Map<String, Long> getManualPrices() {
        Map<String, Long> manualPrices = new HashMap<>(prices);
        for (String key : autoPriceKeys) manualPrices.remove(key);
        return Map.copyOf(manualPrices);
    }

    public Set<String> getAutoPriceKeys() {
        return Set.copyOf(autoPriceKeys);
    }

    public ItemSize getSizeFor(ItemStack stack) {
        return ServerItemRules.getSizeFor(stack);
    }

    public static ItemSize getCachedSizeFor(ItemStack stack) {
        return ServerItemRules.getSizeFor(stack);
    }

    public void setSize(String key, ItemSize size) {
        ServerItemRules.setSize(key, size);
    }

    public void setSize(String key, ItemSize size, boolean rotateTexture) {
        ServerItemRules.setSize(key, size, rotateTexture);
    }

    public void setSize(String key, ItemSize size, boolean rotateTexture, boolean stretchTexture) {
        ServerItemRules.setSize(key, size, rotateTexture, stretchTexture);
    }

    public void setSize(String key, ItemSize size, boolean rotateTexture, boolean stretchTexture, int proportionalScale) {
        ServerItemRules.setSize(key, size, rotateTexture, stretchTexture, proportionalScale);
    }

    public static boolean shouldRotateTexture(ItemStack stack) {
        return ServerItemRules.shouldRotateTexture(stack);
    }

    public static boolean shouldStretchTexture(ItemStack stack) {
        return ServerItemRules.shouldStretchTexture(stack);
    }

    public void removeSize(String key) {
        ServerItemRules.removeSize(key);
    }

    public String getQualityFor(ItemStack stack) {
        return ServerItemRules.getQualityFor(stack);
    }

    public static String getCachedQualityFor(ItemStack stack) {
        return ServerItemRules.getQualityFor(stack);
    }

    public void setQuality(String key, String quality) {
        ServerItemRules.setQuality(key, quality);
    }

    public void removeQuality(String key) {
        ServerItemRules.removeQuality(key);
    }

    public Map<String, Long> getAllPrices() {
        syncCache();
        return Map.copyOf(prices);
    }

    public Map<String, Long> getAllSizes() {
        return ServerItemRules.getAllSizes();
    }

    public Map<String, String> getAllQualities() {
        return ServerItemRules.getAllQualities();
    }

    public static String normalizeQuality(String quality) {
        return switch (quality == null ? "gray" : quality.toLowerCase(java.util.Locale.ROOT)) {
            case "red", "gold", "purple", "blue", "green", "gray" -> quality.toLowerCase(java.util.Locale.ROOT);
            default -> "gray";
        };
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        var priceList = new ListTag();
        for (var e : prices.entrySet()) {
            var t = new CompoundTag();
            t.putString("key", e.getKey());
            t.putLong("p", e.getValue());
            if (autoPriceKeys.contains(e.getKey())) t.putBoolean("auto", true);
            priceList.add(t);
        }
        tag.put("prices", priceList);
        return tag;
    }

    public static ModDataStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ModDataStorage();
        if (tag.contains("prices")) {
            for (var t : tag.getList("prices", 10)) {
                var ct = (CompoundTag) t;
                String key = canonicalizeRuleKey(ct.getString("key"));
                data.prices.put(key, ct.getLong("p"));
                if (ct.contains("auto") && ct.getBoolean("auto")) data.autoPriceKeys.add(key);
            }
        }
        if (tag.contains("auto_price_keys")) {
            for (var value : tag.getList("auto_price_keys", 8)) {
                data.autoPriceKeys.add(canonicalizeRuleKey(value.getAsString()));
            }
        }
        data.syncCache();
        return data;
    }
}
