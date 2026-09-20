package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.ItemSizeRule;
import com.xtdpotato.xero_delta.data.ServerItemRules;
import com.xtdpotato.xero_delta.data.TaczCompatibilityRules;
import com.xtdpotato.xero_delta.data.DurabilityRange;
import com.xtdpotato.xero_delta.data.DynamicItemValuation;
import com.xtdpotato.xero_delta.data.RuleFormulaConfig;
import com.xtdpotato.xero_delta.data.BuiltinItemRuleCatalog;
import com.xtdpotato.xero_delta.data.AutomaticItemWeight;
import com.xtdpotato.xero_delta.data.AutomaticItemValuation;
import com.xtdpotato.xero_delta.data.size.ItemSizeProviders;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ClientDataCache {
    public static final ClientDataCache INSTANCE = new ClientDataCache();
    private final Map<String, Long> prices = new HashMap<>();
    private final Map<String, Long> sizes = new HashMap<>();
    private final Map<String, String> qualities = new HashMap<>();
    private final Map<String, Double> weights = new HashMap<>();
    private final Set<String> autoPriceKeys = new java.util.HashSet<>();
    private final Set<String> autoSizeKeys = new java.util.HashSet<>();
    private final Set<String> autoQualityKeys = new java.util.HashSet<>();
    private volatile Map<String, Long> manualPrices = Map.of();
    private volatile Map<String, Long> manualSizes = Map.of();
    private volatile Map<String, String> manualQualities = Map.of();
    private final Map<String, Long> resolvedPrices = new ConcurrentHashMap<>();
    private final Map<String, ItemSizeRule> resolvedSizes = new ConcurrentHashMap<>();
    private final Map<String, String> resolvedQualities = new ConcurrentHashMap<>();
    private final Map<String, Double> resolvedWeights = new ConcurrentHashMap<>();
    private volatile Map<String, List<PriceRule>> durabilityPrices = Map.of();
    private volatile List<PriceRule> wildcardPrices = List.of();
    private volatile Map<String, Long> tagPrices = Map.of();
    private final Map<ItemStack, CachedStackKey> stackKeys =
        Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<String, Boolean> itemGridScreens = new HashMap<>();
    private boolean itemGridEnabled = true;
    private volatile long revision;

    private ClientDataCache() {}

    public void update(Map<String, Long> prices) {
        this.prices.clear();
        this.prices.putAll(prices);
        rebuildPriceIndexes();
        resolvedPrices.clear();
        revision++;
    }

    public void update(Map<String, Long> prices, Map<String, Long> sizes) {
        update(prices, sizes, Map.of());
    }

    public void update(Map<String, Long> prices, Map<String, Long> sizes, Map<String, String> qualities) {
        update(prices, sizes, qualities, Map.of(), Set.of(), Set.of(), Set.of(), true, Map.of());
    }

    public void update(Map<String, Long> prices, Map<String, Long> sizes, Map<String, String> qualities,
                       Map<String, Double> weights,
                       Set<String> autoPriceKeys, Set<String> autoSizeKeys, Set<String> autoQualityKeys,
                       boolean itemGridEnabled, Map<String, Boolean> itemGridScreens) {
        update(prices);
        this.sizes.clear();
        this.sizes.putAll(sizes);
        this.qualities.clear();
        this.qualities.putAll(qualities);
        this.weights.clear();
        this.weights.putAll(weights);
        this.autoPriceKeys.clear();
        this.autoPriceKeys.addAll(autoPriceKeys);
        this.autoSizeKeys.clear();
        this.autoSizeKeys.addAll(autoSizeKeys);
        this.autoQualityKeys.clear();
        this.autoQualityKeys.addAll(autoQualityKeys);
        this.manualPrices = manualEntries(this.prices, this.autoPriceKeys);
        this.manualSizes = manualEntries(this.sizes, this.autoSizeKeys);
        this.manualQualities = manualEntries(this.qualities, this.autoQualityKeys);
        this.resolvedSizes.clear();
        this.resolvedQualities.clear();
        this.resolvedWeights.clear();
        this.itemGridEnabled = itemGridEnabled;
        this.itemGridScreens.clear();
        this.itemGridScreens.putAll(itemGridScreens);
    }

    public long getPrice(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        String cacheKey = canonicalStackKey(stack);
        return resolvedPrices.computeIfAbsent(cacheKey, ignored -> resolvePrice(stack));
    }

    private long resolvePrice(ItemStack stack) {
        // 1. Exact with components
        String exact = ModDataStorage.getKey(stack);
        Long val = prices.get(exact);
        if (val != null) return val;
        String legacyUnordered = ModDataStorage.getLegacyUnorderedComponentKey(stack);
        val = prices.get(legacyUnordered);
        if (val != null) return val;
        String legacyExact = ModDataStorage.getLegacyComponentKey(stack);
        val = prices.get(legacyExact);
        if (val != null) return val;
        String legacyVisual = ModDataStorage.getLegacyVisualStateKey(stack);
        val = prices.get(legacyVisual);
        if (val != null) return val;
        String typeKey = ModDataStorage.getTypeKey(stack);
        val = prices.get(typeKey);
        if (val != null) return val;
        String legacyUnorderedType = ModDataStorage.getLegacyUnorderedTypeKey(stack);
        val = prices.get(legacyUnorderedType);
        if (val != null) return val;
        String legacyTypeKey = ModDataStorage.getLegacyTypeKey(stack);
        val = prices.get(legacyTypeKey);
        if (val != null) return val;
        for (PriceRule rule : durabilityPrices.getOrDefault(ModDataStorage.getIdOnlyKey(stack), List.of())) {
            if (DurabilityRange.matches(rule.key(), stack)) return rule.value();
        }
        // 2. A manual ID rule is stronger than a dynamic component rule.
        String idKey = ModDataStorage.getIdOnlyKey(stack);
        val = prices.get(idKey);
        if (val != null && !autoPriceKeys.contains(idKey)) return val;
        // 3. Wildcard matches
        for (PriceRule rule : wildcardPrices) {
            String pattern = rule.key().substring(ModDataStorage.PREFIX_WILDCARD.length());
            if (ModDataStorage.matchesWildcard(pattern, idKey)) {
                return rule.value();
            }
        }
        // 4. Tag matches
        var itemTags = stack.getTags().toList();
        for (var tag : itemTags) {
            Long tagValue = tagPrices.get(tag.location().toString());
            if (tagValue != null) {
                return tagValue;
            }
        }
        if (TaczCompatibilityRules.isLrTacticalMelee(stack)) return 0L;
        BuiltinItemRuleCatalog.Rule builtin = BuiltinItemRuleCatalog.explicit(stack);
        if (builtin != null) return builtin.price();
        DynamicItemValuation.Rule dynamic = DynamicItemValuation.resolve(stack);
        if (dynamic != null) return dynamic.price();
        return val == null ? 0 : val;
    }

    public Map<String, Long> getAllPrices() { return Map.copyOf(prices); }

    /** Best non-manual value available to prefill the creative price editor. */
    public long getReferencePrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        for (String key : List.of(ModDataStorage.getKey(stack),
            ModDataStorage.getTypeKey(stack), ModDataStorage.getIdOnlyKey(stack))) {
            Long value = prices.get(key);
            if (value != null && autoPriceKeys.contains(key)) return Math.max(0L, value);
        }
        DynamicItemValuation.Rule dynamic = DynamicItemValuation.resolve(stack);
        if (dynamic != null) return dynamic.price();
        BuiltinItemRuleCatalog.Rule builtin = BuiltinItemRuleCatalog.explicit(stack);
        if (builtin != null) return builtin.price();
        return BuiltinItemRuleCatalog.defaultRule(stack).price();
    }

    /** True only when an administrator explicitly configured the price source. */
    public boolean isManualPrice(ItemStack stack) {
        return stack != null && !stack.isEmpty() && findPriceRuleKey(manualPrices, stack) != null;
    }

    public ItemSize getSize(ItemStack stack) {
        return getSizeRule(stack).size();
    }

    public boolean shouldRotateTexture(ItemStack stack) {
        return getSizeRule(stack).rotateTexture();
    }

    public boolean shouldStretchTexture(ItemStack stack) {
        return getSizeRule(stack).stretchTexture();
    }

    public int proportionalTextureScale(ItemStack stack) {
        return getSizeRule(stack).proportionalScale();
    }

    private ItemSizeRule getSizeRule(ItemStack stack) {
        if (stack.isEmpty()) return ItemSizeRule.DEFAULT;
        String cacheKey = canonicalStackKey(stack);
        return resolvedSizes.computeIfAbsent(cacheKey, ignored -> resolveSizeRule(stack));
    }

    private ItemSizeRule resolveSizeRule(ItemStack stack) {
        boolean xeroItem = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(stack.getItem()).getNamespace().equals("xero_delta");
        String manualKey = findRuleKey(manualSizes, stack);
        if (manualKey != null) return ItemSizeRule.unpack(sizes.get(manualKey));
        ItemSize fixedSize = BuiltinItemRuleCatalog.fixedSize(stack);
        if (fixedSize != null) return new ItemSizeRule(fixedSize, true,
            xeroItem && !BuiltinItemRuleCatalog.shouldFitTextureAspect(stack));
        String configuredKey = findRuleKey(sizes, stack);
        Long packed = configuredKey == null ? null : sizes.get(configuredKey);
        ItemSizeRule automatic = packed == null ? null : ItemSizeRule.unpack(packed);
        ItemSizeRule compatibility = ItemSizeProviders.resolveCompatibility(stack);
        if (compatibility != null) {
            return automatic == null
                ? compatibility
                : new ItemSizeRule(compatibility.size(), automatic.rotateTexture(),
                    automatic.stretchTexture(), automatic.proportionalScale());
        }
        if (automatic != null) return automatic;
        BuiltinItemRuleCatalog.Rule builtin = BuiltinItemRuleCatalog.explicit(stack);
        return builtin == null ? ItemSizeRule.DEFAULT
            : new ItemSizeRule(builtin.size(), true,
                !BuiltinItemRuleCatalog.shouldFitTextureAspect(stack)
                    && (xeroItem || builtin.stretchTexture()));
    }

    public Map<String, Long> getAllSizes() { return Map.copyOf(sizes); }

    /** True only when an administrator explicitly configured this item's footprint. */
    public boolean isManualSize(ItemStack stack) {
        return stack != null && !stack.isEmpty() && findRuleKey(manualSizes, stack) != null;
    }

    public String getQuality(ItemStack stack) {
        if (stack.isEmpty()) return "gray";
        String cacheKey = canonicalStackKey(stack);
        return resolvedQualities.computeIfAbsent(cacheKey, ignored -> resolveQuality(stack));
    }

    private String resolveQuality(ItemStack stack) {
        String safetyBoxQuality = AutomaticItemValuation.safetyBoxQualities()
            .get(ModDataStorage.getIdOnlyKey(stack));
        if (safetyBoxQuality != null) return safetyBoxQuality;
        String manualKey = findRuleKey(manualQualities, stack);
        if (manualKey != null) return qualities.get(manualKey);
        String key = findRuleKey(qualities, stack);
        String quality = key == null ? null : qualities.get(key);
        boolean automatic = quality != null && autoQualityKeys.contains(key);
        if (quality != null && !automatic) return quality;
        if (TaczCompatibilityRules.isLrTacticalMelee(stack)) return "red";
        if (automatic) {
            DynamicItemValuation.Rule dynamic = DynamicItemValuation.resolve(stack);
            if (dynamic != null) return dynamic.quality();
        }
        BuiltinItemRuleCatalog.Rule explicit = BuiltinItemRuleCatalog.explicit(stack);
        if (explicit != null) return explicit.quality();
        String legendaryTooltipsQuality = LegendaryTooltipsCompat.resolveQuality(stack);
        if (legendaryTooltipsQuality != null) return legendaryTooltipsQuality;
        String modNameQuality = RuleFormulaConfig.modQualityFromName(stack);
        if (modNameQuality != null) return ModDataStorage.normalizeQuality(modNameQuality);
        return quality == null ? "gray" : quality;
    }

    public Map<String, String> getAllQualities() { return Map.copyOf(qualities); }

    /** True only when an administrator explicitly configured this item's quality tier. */
    public boolean isManualQuality(ItemStack stack) {
        return stack != null && !stack.isEmpty() && findRuleKey(manualQualities, stack) != null;
    }

    public double getWeight(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0D;
        String cacheKey = canonicalStackKey(stack);
        return resolvedWeights.computeIfAbsent(cacheKey, ignored -> {
            String key = findRuleKey(weights, stack);
            Double configured = key == null ? null : weights.get(key);
            return configured == null ? AutomaticItemWeight.estimate(stack) : configured;
        });
    }

    public Map<String, Double> getAllWeights() {
        return Map.copyOf(weights);
    }

    public boolean isItemGridEnabled(String menuKey) {
        if (!itemGridEnabled) return false;
        if (menuKey == null || menuKey.isEmpty()) return true;
        return itemGridScreens.getOrDefault(menuKey, true);
    }

    public boolean isItemGridGloballyEnabled() {
        return itemGridEnabled;
    }

    public Map<String, Boolean> getItemGridScreens() {
        return Map.copyOf(itemGridScreens);
    }

    /** Changes whenever a synced rule set invalidates resolved item metadata. */
    public long revision() {
        return revision;
    }

    public void setLocalItemGridEnabled(boolean enabled) {
        this.itemGridEnabled = enabled;
    }

    public void setLocalItemGridScreen(String menuKey, boolean enabled) {
        this.itemGridScreens.put(menuKey, enabled);
    }

    public void resetLocalItemGridScreens(Map<String, Boolean> defaults) {
        this.itemGridScreens.clear();
        this.itemGridScreens.putAll(defaults);
    }

    private static <T> T findRule(Map<String, T> rules, String exactStackKey, String itemId) {
        T exactStack = rules.get(ServerItemRules.exactKey(exactStackKey));
        if (exactStack != null) return exactStack;
        T exact = rules.get(ServerItemRules.exactKey(itemId));
        if (exact != null) return exact;
        T legacyStack = rules.get(exactStackKey);
        if (legacyStack != null) return legacyStack;
        T legacyExact = rules.get(itemId);
        if (legacyExact != null) return legacyExact;
        String bestPrefix = null;
        T best = null;
        for (var entry : rules.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("type:")) continue;
            String prefix = key.substring("type:".length());
            if (itemId.startsWith(prefix) && (bestPrefix == null || prefix.length() > bestPrefix.length())) {
                bestPrefix = prefix;
                best = entry.getValue();
            }
        }
        return best != null ? best : rules.get("*");
    }

    private static <T> T findRule(Map<String, T> rules, ItemStack stack) {
        String exactStackKey = ModDataStorage.getKey(stack);
        T exactStack = rules.get(ServerItemRules.exactKey(exactStackKey));
        if (exactStack != null) return exactStack;
        String legacyUnorderedKey = ModDataStorage.getLegacyUnorderedComponentKey(stack);
        T legacyUnordered = rules.get(ServerItemRules.exactKey(legacyUnorderedKey));
        if (legacyUnordered != null) return legacyUnordered;
        String legacyExactKey = ModDataStorage.getLegacyComponentKey(stack);
        T legacyExact = rules.get(ServerItemRules.exactKey(legacyExactKey));
        if (legacyExact != null) return legacyExact;
        String legacyVisualKey = ModDataStorage.getLegacyVisualStateKey(stack);
        T legacyVisual = rules.get(ServerItemRules.exactKey(legacyVisualKey));
        if (legacyVisual != null) return legacyVisual;
        T typeRule = rules.get(ServerItemRules.exactKey(ModDataStorage.getTypeKey(stack)));
        if (typeRule != null) return typeRule;
        T legacyUnorderedTypeRule = rules.get(ServerItemRules.exactKey(ModDataStorage.getLegacyUnorderedTypeKey(stack)));
        if (legacyUnorderedTypeRule != null) return legacyUnorderedTypeRule;
        T legacyTypeRule = rules.get(ServerItemRules.exactKey(ModDataStorage.getLegacyTypeKey(stack)));
        if (legacyTypeRule != null) return legacyTypeRule;
        for (var entry : rules.entrySet()) {
            if (DurabilityRange.matches(entry.getKey(), stack)) return entry.getValue();
        }
        return findRule(rules, exactStackKey, ModDataStorage.getIdOnlyKey(stack));
    }

    private static String findRuleKey(Map<String, ?> rules, ItemStack stack) {
        return findRuleKey(rules, stack, Set.of());
    }

    private static String findRuleKey(Map<String, ?> rules, ItemStack stack, Set<String> excluded) {
        String exact = ServerItemRules.exactKey(ModDataStorage.getKey(stack));
        if (isAvailable(rules, exact, excluded)) return exact;
        String legacyUnordered = ServerItemRules.exactKey(ModDataStorage.getLegacyUnorderedComponentKey(stack));
        if (isAvailable(rules, legacyUnordered, excluded)) return legacyUnordered;
        String legacy = ServerItemRules.exactKey(ModDataStorage.getLegacyComponentKey(stack));
        if (isAvailable(rules, legacy, excluded)) return legacy;
        String legacyVisual = ServerItemRules.exactKey(ModDataStorage.getLegacyVisualStateKey(stack));
        if (isAvailable(rules, legacyVisual, excluded)) return legacyVisual;
        String type = ServerItemRules.exactKey(ModDataStorage.getTypeKey(stack));
        if (isAvailable(rules, type, excluded)) return type;
        String legacyUnorderedType = ServerItemRules.exactKey(ModDataStorage.getLegacyUnorderedTypeKey(stack));
        if (isAvailable(rules, legacyUnorderedType, excluded)) return legacyUnorderedType;
        String legacyType = ServerItemRules.exactKey(ModDataStorage.getLegacyTypeKey(stack));
        if (isAvailable(rules, legacyType, excluded)) return legacyType;
        for (String candidate : rules.keySet()) {
            if (!excluded.contains(candidate) && DurabilityRange.matches(candidate, stack)) return candidate;
        }
        String id = ModDataStorage.getIdOnlyKey(stack);
        String idKey = ServerItemRules.exactKey(id);
        if (isAvailable(rules, idKey, excluded)) return idKey;
        String bestPrefix = null;
        String bestKey = null;
        for (String candidate : rules.keySet()) {
            if (excluded.contains(candidate) || !candidate.startsWith("type:")) continue;
            String prefix = candidate.substring("type:".length());
            if (id.startsWith(prefix) && (bestPrefix == null || prefix.length() > bestPrefix.length())) {
                bestPrefix = prefix;
                bestKey = candidate;
            }
        }
        if (bestKey != null) return bestKey;
        return isAvailable(rules, "*", excluded) ? "*" : null;
    }

    private static boolean isAvailable(Map<String, ?> rules, String key, Set<String> excluded) {
        return rules.containsKey(key) && !excluded.contains(key);
    }

    /**
     * Price data predates the server item-rule key namespace.  Unlike size and
     * quality entries, its keys are raw {@link ModDataStorage} keys, including
     * wildcard and tag prefixes, so it must not use {@link #findRuleKey}.
     */
    private static String findPriceRuleKey(Map<String, ?> rules, ItemStack stack) {
        for (String key : List.of(
            ModDataStorage.getKey(stack),
            ModDataStorage.getLegacyUnorderedComponentKey(stack),
            ModDataStorage.getLegacyComponentKey(stack),
            ModDataStorage.getLegacyVisualStateKey(stack),
            ModDataStorage.getTypeKey(stack),
            ModDataStorage.getLegacyUnorderedTypeKey(stack),
            ModDataStorage.getLegacyTypeKey(stack))) {
            if (rules.containsKey(key)) return key;
        }

        String itemId = ModDataStorage.getIdOnlyKey(stack);
        for (String key : rules.keySet().stream().sorted().toList()) {
            if (DurabilityRange.matches(key, stack)) return key;
        }
        if (rules.containsKey(itemId)) return itemId;

        for (String key : rules.keySet().stream().sorted().toList()) {
            if (key.startsWith(ModDataStorage.PREFIX_WILDCARD)
                && ModDataStorage.matchesWildcard(
                    key.substring(ModDataStorage.PREFIX_WILDCARD.length()), itemId)) {
                return key;
            }
        }
        for (String key : rules.keySet().stream().sorted().toList()) {
            if (!key.startsWith(ModDataStorage.PREFIX_TAG)) continue;
            String tagId = key.substring(ModDataStorage.PREFIX_TAG.length());
            if (stack.getTags().anyMatch(tag -> tag.location().toString().equals(tagId))) return key;
        }
        return null;
    }

    private static <T> Map<String, T> manualEntries(Map<String, T> source, Set<String> automaticKeys) {
        if (source.isEmpty()) return Map.of();
        Map<String, T> manual = new HashMap<>();
        for (var entry : source.entrySet()) {
            if (!automaticKeys.contains(entry.getKey())) manual.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(manual);
    }

    private void rebuildPriceIndexes() {
        Map<String, List<PriceRule>> durability = new HashMap<>();
        List<PriceRule> wildcards = new ArrayList<>();
        Map<String, Long> tags = new HashMap<>();
        prices.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key = entry.getKey();
            if (key.startsWith(DurabilityRange.PREFIX)) {
                String[] parts = key.split("\\|", 3);
                if (parts.length == 3) {
                    durability.computeIfAbsent(parts[1], ignored -> new ArrayList<>())
                        .add(new PriceRule(key, entry.getValue()));
                }
            } else if (key.startsWith(ModDataStorage.PREFIX_WILDCARD)) {
                wildcards.add(new PriceRule(key, entry.getValue()));
            } else if (key.startsWith(ModDataStorage.PREFIX_TAG)) {
                tags.putIfAbsent(key.substring(ModDataStorage.PREFIX_TAG.length()), entry.getValue());
            }
        });
        Map<String, List<PriceRule>> immutableDurability = new HashMap<>();
        durability.forEach((key, value) -> immutableDurability.put(key, List.copyOf(value)));
        durabilityPrices = Map.copyOf(immutableDurability);
        wildcardPrices = List.copyOf(wildcards);
        tagPrices = Map.copyOf(tags);
    }

    private record PriceRule(String key, long value) {
    }

    private String canonicalStackKey(ItemStack stack) {
        Object componentPatch = stack.getComponentsPatch();
        int componentHash = componentPatch.hashCode();
        CachedStackKey cached = stackKeys.get(stack);
        if (cached != null && cached.item() == stack.getItem()
            && cached.componentHash() == componentHash
            && cached.componentPatch().equals(componentPatch)) {
            return cached.key();
        }
        String key = ModDataStorage.getKey(stack);
        stackKeys.put(stack, new CachedStackKey(stack.getItem(), componentPatch, componentHash, key));
        return key;
    }

    private record CachedStackKey(Object item, Object componentPatch, int componentHash, String key) {
    }
}
