package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** World-global lifetime and destruction rules for corpse entities. */
public final class CorpseRulesData extends SavedData {
    public static final int DEFAULT_LIFETIME_MINUTES = CorpseRules.DEFAULT_LIFETIME_MINUTES;
    public static final boolean DEFAULT_MOB_CORPSES_ATTACKABLE =
        CorpseRules.DEFAULT_MOB_CORPSES_ATTACKABLE;
    public static final int MIN_LIFETIME_MINUTES = CorpseRules.MIN_LIFETIME_MINUTES;
    public static final int MAX_LIFETIME_MINUTES = CorpseRules.MAX_LIFETIME_MINUTES;
    private static final String DATA_NAME = "xero_delta_corpse_rules";

    private int lifetimeMinutes = DEFAULT_LIFETIME_MINUTES;
    private boolean mobCorpsesAttackable = DEFAULT_MOB_CORPSES_ATTACKABLE;
    private final LinkedHashSet<String> mobEntityIds =
        new LinkedHashSet<>(CorpseRules.DEFAULT_MOB_ENTITY_IDS);
    private final LinkedHashMap<String, CorpseRules.EntityCarriers> entityCarrierRules =
        new LinkedHashMap<>();

    public CorpseRulesData() {
        applySettings(CorpseRulesConfig.load());
    }

    public static CorpseRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(CorpseRulesData::new, CorpseRulesData::load), DATA_NAME);
    }

    public synchronized int lifetimeMinutes() {
        return lifetimeMinutes;
    }

    public synchronized int lifetimeTicks() {
        return CorpseRules.lifetimeTicks(lifetimeMinutes);
    }

    public synchronized boolean mobCorpsesAttackable() {
        return mobCorpsesAttackable;
    }

    public synchronized Set<String> mobEntityIds() { return Set.copyOf(mobEntityIds); }
    public synchronized CorpseRules.Settings settings() {
        return new CorpseRules.Settings(mobEntityIds, entityCarrierRules);
    }

    // Compatibility accessors for commands and older screens.
    public synchronized boolean generateChestRig() { return hasAnyCandidate(true); }
    public synchronized boolean generateBackpack() { return hasAnyCandidate(false); }
    public synchronized String chestRigItemId() {
        return firstCandidateId(true, CorpseRules.DEFAULT_CHEST_RIG_ID);
    }
    public synchronized String backpackItemId() {
        return firstCandidateId(false, CorpseRules.DEFAULT_BACKPACK_ID);
    }

    public synchronized boolean shouldGenerateFor(LivingEntity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && mobEntityIds.contains(id.toString());
    }

    public synchronized ItemStack generatedCarrier(LivingEntity entity, boolean chestRig) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityId == null) return ItemStack.EMPTY;
        int slot = chestRig ? CorpseMenu.CHEST_RIG_SLOT : CorpseMenu.BACKPACK_SLOT;
        List<CorpseRules.WeightedCarrier> valid = new ArrayList<>();
        for (CorpseRules.WeightedCarrier candidate
            : settings().candidates(entityId.toString(), chestRig)) {
            ResourceLocation itemId = ResourceLocation.tryParse(candidate.itemId());
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) continue;
            ItemStack stack = BuiltInRegistries.ITEM.get(itemId).getDefaultInstance();
            if (CorpseEntity.isCarrierStackForSlot(slot, stack)) valid.add(candidate);
        }
        CorpseRules.WeightedCarrier selected = CorpseRules.chooseWeighted(
            valid, entity.getRandom().nextLong());
        if (selected == null) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(selected.itemId());
        return id == null ? ItemStack.EMPTY
            : BuiltInRegistries.ITEM.get(id).getDefaultInstance();
    }

    public synchronized void replaceMobEntityIds(Set<String> values) {
        mobEntityIds.clear();
        for (String value : values) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                mobEntityIds.add(id.toString());
            }
        }
        setDirty();
    }

    public synchronized void setGeneratedCarriers(boolean chestRigEnabled,
                                                   String chestRigId,
                                                   boolean backpackEnabled,
                                                   String backpackId) {
        applySettings(CorpseRules.legacySettings(mobEntityIds,
            chestRigEnabled, normalizeId(chestRigId, CorpseRules.DEFAULT_CHEST_RIG_ID),
            backpackEnabled, normalizeId(backpackId, CorpseRules.DEFAULT_BACKPACK_ID)));
        CorpseRulesConfig.save(settings());
        setDirty();
    }

    public synchronized void replaceSettings(CorpseRules.Settings settings) {
        applySettings(settings);
        CorpseRulesConfig.save(settings());
        setDirty();
    }

    public synchronized void setLifetimeMinutes(int value) {
        int clamped = clampLifetimeMinutes(value);
        if (lifetimeMinutes == clamped) return;
        lifetimeMinutes = clamped;
        setDirty();
    }

    public synchronized void setMobCorpsesAttackable(boolean value) {
        if (mobCorpsesAttackable == value) return;
        mobCorpsesAttackable = value;
        setDirty();
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                   HolderLookup.@NotNull Provider registries) {
        tag.putInt("lifetimeMinutes", lifetimeMinutes);
        tag.putBoolean("mobCorpsesAttackable", mobCorpsesAttackable);
        net.minecraft.nbt.ListTag ids = new net.minecraft.nbt.ListTag();
        mobEntityIds.forEach(id -> ids.add(net.minecraft.nbt.StringTag.valueOf(id)));
        tag.put("mobEntityIds", ids);
        ListTag carrierRules = new ListTag();
        for (String entityId : mobEntityIds) {
            CorpseRules.EntityCarriers carriers = entityCarrierRules.getOrDefault(
                entityId, CorpseRules.EntityCarriers.EMPTY);
            writeCandidates(carrierRules, entityId, true, carriers.chestRigs());
            writeCandidates(carrierRules, entityId, false, carriers.backpacks());
        }
        tag.put("carrierRules", carrierRules);
        return tag;
    }

    public static CorpseRulesData load(CompoundTag tag, HolderLookup.Provider registries) {
        CorpseRulesData data = new CorpseRulesData();
        if (tag.contains("lifetimeMinutes", Tag.TAG_ANY_NUMERIC)) {
            data.lifetimeMinutes = clampLifetimeMinutes(tag.getInt("lifetimeMinutes"));
        }
        if (tag.contains("mobCorpsesAttackable", Tag.TAG_BYTE)) {
            data.mobCorpsesAttackable = tag.getBoolean("mobCorpsesAttackable");
        }
        if (tag.contains("mobEntityIds", Tag.TAG_LIST)) {
            data.mobEntityIds.clear();
            net.minecraft.nbt.ListTag ids = tag.getList("mobEntityIds", Tag.TAG_STRING);
            for (int index = 0; index < ids.size(); index++) {
                ResourceLocation id = ResourceLocation.tryParse(ids.getString(index));
                if (id != null) data.mobEntityIds.add(id.toString());
            }
        }
        if (tag.contains("carrierRules", Tag.TAG_LIST)) {
            LinkedHashMap<String, MutableCarriers> mutable = new LinkedHashMap<>();
            ListTag entries = tag.getList("carrierRules", Tag.TAG_COMPOUND);
            for (int index = 0; index < entries.size(); index++) {
                CompoundTag entry = entries.getCompound(index);
                ResourceLocation entityId = ResourceLocation.tryParse(entry.getString("entity"));
                ResourceLocation itemId = ResourceLocation.tryParse(entry.getString("item"));
                if (entityId == null || itemId == null) continue;
                MutableCarriers carriers = mutable.computeIfAbsent(entityId.toString(),
                    ignored -> new MutableCarriers());
                List<CorpseRules.WeightedCarrier> target = entry.getBoolean("chestRig")
                    ? carriers.chestRigs : carriers.backpacks;
                target.add(new CorpseRules.WeightedCarrier(itemId.toString(),
                    entry.contains("weight", Tag.TAG_ANY_NUMERIC) ? entry.getInt("weight") : 1));
            }
            LinkedHashMap<String, CorpseRules.EntityCarriers> rules = new LinkedHashMap<>();
            mutable.forEach((entity, carriers) -> rules.put(entity,
                new CorpseRules.EntityCarriers(carriers.chestRigs, carriers.backpacks)));
            data.applySettings(new CorpseRules.Settings(data.mobEntityIds, rules));
            // The common config is deliberately authoritative once the new
            // format has been written, so edits made outside the game apply.
            CorpseRules.Settings configured = CorpseRulesConfig.load();
            data.applySettings(configured);
        } else {
            boolean chestEnabled = !tag.contains("generateChestRig", Tag.TAG_BYTE)
                || tag.getBoolean("generateChestRig");
            boolean backpackEnabled = tag.contains("generateBackpack", Tag.TAG_BYTE)
                && tag.getBoolean("generateBackpack");
            String chestId = normalizeId(tag.getString("chestRigItemId"),
                CorpseRules.DEFAULT_CHEST_RIG_ID);
            String backpackId = normalizeId(tag.getString("backpackItemId"),
                CorpseRules.DEFAULT_BACKPACK_ID);
            data.applySettings(CorpseRules.legacySettings(data.mobEntityIds,
                chestEnabled, chestId, backpackEnabled, backpackId));
            CorpseRulesConfig.save(data.settings());
        }
        return data;
    }

    static int clampLifetimeMinutes(int value) {
        return CorpseRules.clampLifetimeMinutes(value);
    }

    private static String normalizeId(String value, String fallback) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? fallback : id.toString();
    }

    private synchronized void applySettings(CorpseRules.Settings settings) {
        mobEntityIds.clear();
        mobEntityIds.addAll(settings.entityIds());
        entityCarrierRules.clear();
        entityCarrierRules.putAll(settings.entityRules());
    }

    private boolean hasAnyCandidate(boolean chestRig) {
        return mobEntityIds.stream().anyMatch(id ->
            !settings().candidates(id, chestRig).isEmpty());
    }

    private String firstCandidateId(boolean chestRig, String fallback) {
        for (String entityId : mobEntityIds) {
            List<CorpseRules.WeightedCarrier> candidates =
                settings().candidates(entityId, chestRig);
            if (!candidates.isEmpty()) return candidates.getFirst().itemId();
        }
        return fallback;
    }

    private static void writeCandidates(ListTag result, String entityId,
                                        boolean chestRig,
                                        List<CorpseRules.WeightedCarrier> candidates) {
        for (CorpseRules.WeightedCarrier candidate : candidates) {
            CompoundTag entry = new CompoundTag();
            entry.putString("entity", entityId);
            entry.putBoolean("chestRig", chestRig);
            entry.putString("item", candidate.itemId());
            entry.putInt("weight", candidate.weight());
            result.add(entry);
        }
    }

    private static final class MutableCarriers {
        private final List<CorpseRules.WeightedCarrier> chestRigs = new ArrayList<>();
        private final List<CorpseRules.WeightedCarrier> backpacks = new ArrayList<>();
    }
}
