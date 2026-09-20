package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.Config;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Human-readable common-config persistence for per-entity corpse carrier rules. */
public final class CorpseRulesConfig {
    private CorpseRulesConfig() {
    }

    public static CorpseRules.Settings load() {
        LinkedHashSet<String> entities = new LinkedHashSet<>();
        for (String value : Config.INSTANCE.corpseEntityIds.get()) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) entities.add(id.toString());
        }
        LinkedHashMap<String, MutableCarriers> mutable = new LinkedHashMap<>();
        readEntries(Config.INSTANCE.corpseChestRigCandidates.get(), true, mutable);
        readEntries(Config.INSTANCE.corpseBackpackCandidates.get(), false, mutable);
        LinkedHashMap<String, CorpseRules.EntityCarriers> rules = new LinkedHashMap<>();
        mutable.forEach((entity, carriers) -> rules.put(entity,
            new CorpseRules.EntityCarriers(carriers.chestRigs, carriers.backpacks)));
        return new CorpseRules.Settings(entities, rules);
    }

    public static void save(CorpseRules.Settings settings) {
        Config.INSTANCE.corpseEntityIds.set(new ArrayList<>(settings.entityIds()));
        Config.INSTANCE.corpseChestRigCandidates.set(
            encodeEntries(settings, true));
        Config.INSTANCE.corpseBackpackCandidates.set(
            encodeEntries(settings, false));
        Config.SPEC.save();
    }

    private static void readEntries(List<? extends String> values, boolean chestRig,
                                    Map<String, MutableCarriers> result) {
        for (String value : values) {
            String[] parts = value.split("\\|", 3);
            if (parts.length != 3) continue;
            ResourceLocation entityId = ResourceLocation.tryParse(parts[0]);
            ResourceLocation itemId = ResourceLocation.tryParse(parts[1]);
            if (entityId == null || itemId == null) continue;
            int weight;
            try {
                weight = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            MutableCarriers carriers = result.computeIfAbsent(entityId.toString(),
                ignored -> new MutableCarriers());
            (chestRig ? carriers.chestRigs : carriers.backpacks).add(
                new CorpseRules.WeightedCarrier(itemId.toString(), weight));
        }
    }

    private static List<String> encodeEntries(CorpseRules.Settings settings,
                                              boolean chestRig) {
        List<String> result = new ArrayList<>();
        for (String entityId : settings.entityIds()) {
            for (CorpseRules.WeightedCarrier candidate
                : settings.candidates(entityId, chestRig)) {
                result.add(entityId + "|" + candidate.itemId() + "|"
                    + candidate.weight());
            }
        }
        return result;
    }

    private static final class MutableCarriers {
        private final List<CorpseRules.WeightedCarrier> chestRigs = new ArrayList<>();
        private final List<CorpseRules.WeightedCarrier> backpacks = new ArrayList<>();
    }
}
