package com.xtdpotato.xero_delta.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure corpse-rule calculations shared by the server data and tests. */
public final class CorpseRules {
    public static final int DEFAULT_LIFETIME_MINUTES = 5;
    public static final boolean DEFAULT_MOB_CORPSES_ATTACKABLE = true;
    public static final boolean DEFAULT_GENERATE_CHEST_RIG = true;
    public static final boolean DEFAULT_GENERATE_BACKPACK = false;
    public static final String DEFAULT_CHEST_RIG_ID = "xero_delta:dar_assault_chest_rig";
    public static final String DEFAULT_BACKPACK_ID = "xero_delta:gto_heavy_tactical_pack";
    public static final Set<String> DEFAULT_MOB_ENTITY_IDS = Set.of(
        "minecraft:zombie", "minecraft:zombie_villager", "minecraft:husk",
        "minecraft:drowned", "minecraft:skeleton", "minecraft:stray",
        "minecraft:wither_skeleton", "minecraft:bogged", "minecraft:creeper");
    public static final int MIN_LIFETIME_MINUTES = 1;
    public static final int MAX_LIFETIME_MINUTES = 1_440;
    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 1_000_000;

    private CorpseRules() {
    }

    public static int clampLifetimeMinutes(int value) {
        return Math.max(MIN_LIFETIME_MINUTES, Math.min(MAX_LIFETIME_MINUTES, value));
    }

    public static int lifetimeTicks(int minutes) {
        return clampLifetimeMinutes(minutes) * 60 * 20;
    }

    public static int clampWeight(int weight) {
        return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight));
    }

    public static Settings defaultSettings() {
        LinkedHashSet<String> entities = new LinkedHashSet<>(
            DEFAULT_MOB_ENTITY_IDS.stream().sorted().toList());
        LinkedHashMap<String, EntityCarriers> rules = new LinkedHashMap<>();
        for (String entityId : entities) {
            List<WeightedCarrier> chestRigs = DEFAULT_GENERATE_CHEST_RIG
                ? List.of(new WeightedCarrier(DEFAULT_CHEST_RIG_ID, 1)) : List.of();
            List<WeightedCarrier> backpacks = DEFAULT_GENERATE_BACKPACK
                ? List.of(new WeightedCarrier(DEFAULT_BACKPACK_ID, 1)) : List.of();
            rules.put(entityId, new EntityCarriers(chestRigs, backpacks));
        }
        return new Settings(entities, rules);
    }

    /** Converts the legacy global carrier settings into per-entity weighted lists. */
    public static Settings legacySettings(Set<String> entityIds,
                                          boolean chestRigEnabled, String chestRigItemId,
                                          boolean backpackEnabled, String backpackItemId) {
        LinkedHashMap<String, EntityCarriers> rules = new LinkedHashMap<>();
        for (String entityId : entityIds) {
            List<WeightedCarrier> chest = chestRigEnabled
                ? List.of(new WeightedCarrier(chestRigItemId, 1)) : List.of();
            List<WeightedCarrier> backpack = backpackEnabled
                ? List.of(new WeightedCarrier(backpackItemId, 1)) : List.of();
            rules.put(entityId, new EntityCarriers(chest, backpack));
        }
        return new Settings(entityIds, rules);
    }

    /** Selects one candidate using conventional proportional weights. */
    public static WeightedCarrier chooseWeighted(List<WeightedCarrier> candidates,
                                                  long randomValue) {
        if (candidates == null || candidates.isEmpty()) return null;
        long total = 0L;
        for (WeightedCarrier candidate : candidates) {
            total = Math.min(Long.MAX_VALUE,
                total + clampWeight(candidate.weight()));
        }
        if (total <= 0L) return null;
        long target = Math.floorMod(randomValue, total);
        for (WeightedCarrier candidate : candidates) {
            target -= clampWeight(candidate.weight());
            if (target < 0L) return candidate;
        }
        return candidates.getLast();
    }

    public record WeightedCarrier(String itemId, int weight) {
        public WeightedCarrier {
            itemId = itemId == null ? "" : itemId.trim();
            weight = clampWeight(weight);
        }
    }

    public record EntityCarriers(List<WeightedCarrier> chestRigs,
                                 List<WeightedCarrier> backpacks) {
        public static final EntityCarriers EMPTY = new EntityCarriers(List.of(), List.of());

        public EntityCarriers {
            chestRigs = List.copyOf(chestRigs == null ? List.of() : chestRigs);
            backpacks = List.copyOf(backpacks == null ? List.of() : backpacks);
        }

        public List<WeightedCarrier> candidates(boolean chestRig) {
            return chestRig ? chestRigs : backpacks;
        }
    }

    public record Settings(Set<String> entityIds,
                           Map<String, EntityCarriers> entityRules) {
        public Settings {
            LinkedHashSet<String> orderedIds = new LinkedHashSet<>(
                entityIds == null ? Set.of() : entityIds);
            LinkedHashMap<String, EntityCarriers> orderedRules = new LinkedHashMap<>();
            if (entityRules != null) {
                entityRules.forEach((id, rule) -> {
                    if (id != null && !id.isBlank()) {
                        orderedRules.put(id, rule == null ? EntityCarriers.EMPTY : rule);
                    }
                });
            }
            for (String id : orderedIds) orderedRules.putIfAbsent(id, EntityCarriers.EMPTY);
            entityIds = Collections.unmodifiableSet(orderedIds);
            entityRules = Collections.unmodifiableMap(orderedRules);
        }

        public EntityCarriers carriers(String entityId) {
            return entityRules.getOrDefault(entityId, EntityCarriers.EMPTY);
        }

        public List<WeightedCarrier> candidates(String entityId, boolean chestRig) {
            return carriers(entityId).candidates(chestRig);
        }

        public Settings withCandidates(String entityId, boolean chestRig,
                                       List<WeightedCarrier> candidates) {
            LinkedHashSet<String> ids = new LinkedHashSet<>(entityIds);
            ids.add(entityId);
            LinkedHashMap<String, EntityCarriers> rules = new LinkedHashMap<>(entityRules);
            EntityCarriers current = carriers(entityId);
            rules.put(entityId, chestRig
                ? new EntityCarriers(new ArrayList<>(candidates), current.backpacks())
                : new EntityCarriers(current.chestRigs(), new ArrayList<>(candidates)));
            return new Settings(ids, rules);
        }
    }
}
