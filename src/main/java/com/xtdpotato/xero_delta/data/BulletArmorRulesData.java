package com.xtdpotato.xero_delta.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** World-global per-projectile armor durability multipliers. */
public final class BulletArmorRulesData extends SavedData {
    private static final String DATA_NAME = "xero_delta_bullet_armor_rules";
    private final Map<String, List<Double>> rules = new LinkedHashMap<>();

    public static BulletArmorRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(BulletArmorRulesData::new, BulletArmorRulesData::load), DATA_NAME);
    }

    public synchronized List<Double> resolve(ItemStack stack) {
        return BallisticArmorRules.resolve(rules, stack);
    }

    public synchronized void set(String key, List<Double> values) {
        if (key == null || key.isBlank() || !BallisticArmorRules.valid(values)) return;
        List<Double> copy = List.copyOf(values);
        if (copy.equals(rules.put(key, copy))) return;
        setDirty();
    }

    public synchronized Map<String, List<Double>> rules() {
        return BallisticArmorRules.immutableRules(rules);
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                   HolderLookup.@NotNull Provider registries) {
        ListTag list = new ListTag();
        rules.forEach((key, values) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", key);
            for (int index = 0; index < BallisticArmorRules.TIER_COUNT; index++) {
                entry.putDouble("tier" + index, values.get(index));
            }
            list.add(entry);
        });
        tag.put("rules", list);
        return tag;
    }

    public static BulletArmorRulesData load(CompoundTag tag, HolderLookup.Provider registries) {
        BulletArmorRulesData data = new BulletArmorRulesData();
        for (var raw : tag.getList("rules", 10)) {
            CompoundTag entry = (CompoundTag) raw;
            String key = entry.getString("key");
            var values = new java.util.ArrayList<Double>(BallisticArmorRules.TIER_COUNT);
            for (int index = 0; index < BallisticArmorRules.TIER_COUNT; index++) {
                values.add(entry.getDouble("tier" + index));
            }
            if (!key.isBlank() && BallisticArmorRules.valid(values)) data.rules.put(key, List.copyOf(values));
        }
        return data;
    }
}