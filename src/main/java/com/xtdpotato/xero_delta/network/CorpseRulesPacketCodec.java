package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.data.CorpseRules;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

final class CorpseRulesPacketCodec {
    private static final int MAX_ENTITIES = 4096;
    private static final int MAX_CANDIDATES_PER_TYPE = 256;
    private static final int MAX_ID_LENGTH = 256;

    private CorpseRulesPacketCodec() {
    }

    static void write(FriendlyByteBuf buffer, CorpseRules.Settings settings) {
        buffer.writeVarInt(settings.entityIds().size());
        for (String entityId : settings.entityIds()) {
            buffer.writeUtf(entityId, MAX_ID_LENGTH);
            writeCandidates(buffer, settings.candidates(entityId, true));
            writeCandidates(buffer, settings.candidates(entityId, false));
        }
    }

    static CorpseRules.Settings read(FriendlyByteBuf buffer) {
        int entityCount = checkedCount(buffer.readVarInt(), MAX_ENTITIES,
            "corpse entity");
        LinkedHashSet<String> entityIds = new LinkedHashSet<>();
        LinkedHashMap<String, CorpseRules.EntityCarriers> rules = new LinkedHashMap<>();
        for (int index = 0; index < entityCount; index++) {
            String entityId = buffer.readUtf(MAX_ID_LENGTH);
            List<CorpseRules.WeightedCarrier> chestRigs = readCandidates(buffer);
            List<CorpseRules.WeightedCarrier> backpacks = readCandidates(buffer);
            entityIds.add(entityId);
            rules.put(entityId, new CorpseRules.EntityCarriers(chestRigs, backpacks));
        }
        return new CorpseRules.Settings(entityIds, rules);
    }

    private static void writeCandidates(FriendlyByteBuf buffer,
                                        List<CorpseRules.WeightedCarrier> candidates) {
        buffer.writeVarInt(candidates.size());
        for (CorpseRules.WeightedCarrier candidate : candidates) {
            buffer.writeUtf(candidate.itemId(), MAX_ID_LENGTH);
            buffer.writeVarInt(candidate.weight());
        }
    }

    private static List<CorpseRules.WeightedCarrier> readCandidates(
        FriendlyByteBuf buffer) {
        int count = checkedCount(buffer.readVarInt(), MAX_CANDIDATES_PER_TYPE,
            "corpse carrier candidate");
        List<CorpseRules.WeightedCarrier> candidates = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            candidates.add(new CorpseRules.WeightedCarrier(
                buffer.readUtf(MAX_ID_LENGTH), buffer.readVarInt()));
        }
        return candidates;
    }

    private static int checkedCount(int count, int maximum, String kind) {
        if (count < 0 || count > maximum) {
            throw new DecoderException("Invalid " + kind + " count: " + count);
        }
        return count;
    }
}
