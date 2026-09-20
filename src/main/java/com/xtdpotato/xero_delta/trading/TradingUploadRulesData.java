package com.xtdpotato.xero_delta.trading;

import com.xtdpotato.xero_delta.data.DurabilityRange;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.ServerItemRules;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/** World-global item trading policy: up, recycle-only, or none. */
public final class TradingUploadRulesData extends SavedData {
    public static final String UP = "up";
    public static final String RECYCLE = "recycle";
    public static final String NONE = "none";
    private static final String DATA_NAME = "xero_delta_trading_upload_rules";
    private final Map<String, String> rules = new LinkedHashMap<>();

    public static TradingUploadRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(TradingUploadRulesData::new, TradingUploadRulesData::load), DATA_NAME);
    }

    public synchronized boolean isAllowed(ItemStack stack) {
        return canList(stack);
    }

    public synchronized String mode(ItemStack stack) {
        return resolve(rules, stack);
    }

    public synchronized boolean canList(ItemStack stack) {
        return UP.equals(mode(stack));
    }

    public synchronized boolean canRecycle(ItemStack stack) {
        String mode = mode(stack);
        return UP.equals(mode) || RECYCLE.equals(mode);
    }

    /** Legacy boolean setter: true maps to up, false maps to none. */
    public synchronized void set(String key, boolean allowed) {
        set(key, allowed ? UP : NONE);
    }

    public synchronized void set(String key, String mode) {
        if (key == null || key.isBlank()) return;
        String normalized = normalizeMode(mode);
        String previous = rules.put(key, normalized);
        if (!normalized.equals(previous)) setDirty();
    }

    public synchronized Map<String, String> rules() {
        return Map.copyOf(rules);
    }

    public static String resolve(Map<String, String> source, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return UP;
        source = source == null ? Map.of() : source;
        String[] exactKeys = {
            ServerItemRules.exactKey(ModDataStorage.getKey(stack)),
            ServerItemRules.exactKey(ModDataStorage.getTypeKey(stack)),
            ServerItemRules.exactKey(ModDataStorage.getIdOnlyKey(stack))
        };
        for (String key : exactKeys) {
            String value = source.get(key);
            if (value != null) return normalizeMode(value);
        }
        String durabilityKey = null;
        for (String key : source.keySet()) {
            if (DurabilityRange.matches(key, stack)
                && (durabilityKey == null || key.compareTo(durabilityKey) < 0)) {
                durabilityKey = key;
            }
        }
        if (durabilityKey != null) return normalizeMode(source.get(durabilityKey));
        String bestPrefix = null;
        String best = null;
        String id = ModDataStorage.getIdOnlyKey(stack);
        for (var entry : source.entrySet()) {
            if (!entry.getKey().startsWith("type:")) continue;
            String prefix = entry.getKey().substring("type:".length());
            if (id.startsWith(prefix) && (bestPrefix == null || prefix.length() > bestPrefix.length())) {
                bestPrefix = prefix;
                best = normalizeMode(entry.getValue());
            }
        }
        if (best != null) return best;
        return normalizeMode(source.getOrDefault("*", UP));
    }

    private static String normalizeMode(String mode) {
        if (mode == null) return UP;
        if ("true".equalsIgnoreCase(mode) || UP.equalsIgnoreCase(mode)) return UP;
        if (RECYCLE.equalsIgnoreCase(mode)) return RECYCLE;
        return NONE;
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                   HolderLookup.@NotNull Provider registries) {
        ListTag values = new ListTag();
        rules.forEach((key, mode) -> {
            CompoundTag value = new CompoundTag();
            value.putString("key", key);
            value.putString("mode", normalizeMode(mode));
            values.add(value);
        });
        tag.put("rules", values);
        return tag;
    }

    public static TradingUploadRulesData load(CompoundTag tag, HolderLookup.Provider registries) {
        TradingUploadRulesData data = new TradingUploadRulesData();
        for (var raw : tag.getList("rules", 10)) {
            CompoundTag value = (CompoundTag) raw;
            String key = value.getString("key");
            if (!key.isBlank()) {
                String mode = value.contains("mode", 8)
                    ? value.getString("mode")
                    : value.getBoolean("allowed") ? UP : NONE;
                data.rules.put(key, normalizeMode(mode));
            }
        }
        return data;
    }
}
