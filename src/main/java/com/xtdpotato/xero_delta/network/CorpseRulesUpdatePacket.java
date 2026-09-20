package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.CorpseRules;
import com.xtdpotato.xero_delta.data.CorpseRulesData;
import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CorpseRulesUpdatePacket(CorpseRules.Settings settings)
    implements CustomPacketPayload {
    public static final Type<CorpseRulesUpdatePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "corpse_rules_update"));
    public static final StreamCodec<FriendlyByteBuf, CorpseRulesUpdatePacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> CorpseRulesPacketCodec.write(buffer,
                packet.settings),
            buffer -> new CorpseRulesUpdatePacket(CorpseRulesPacketCodec.read(buffer)));

    /** Legacy UI bridge: applies one candidate to every configured entity. */
    public CorpseRulesUpdatePacket(Set<String> entityIds,
                                   boolean chestRigEnabled, String chestRigItemId,
                                   boolean backpackEnabled, String backpackItemId) {
        this(CorpseRules.legacySettings(entityIds, chestRigEnabled, chestRigItemId,
            backpackEnabled, backpackItemId));
    }

    public static void handle(CorpseRulesUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !player.hasPermissions(2)) return;
            CorpseRulesData rules = CorpseRulesData.get(player.server);
            rules.replaceSettings(validatedSettings(packet.settings));
            for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(online, CorpseRulesSyncPacket.from(online));
            }
        });
    }

    private static CorpseRules.Settings validatedSettings(
        CorpseRules.Settings requested) {
        LinkedHashSet<String> entities = new LinkedHashSet<>();
        LinkedHashMap<String, CorpseRules.EntityCarriers> rules = new LinkedHashMap<>();
        for (String value : requested.entityIds()) {
            ResourceLocation entityId = ResourceLocation.tryParse(value);
            if (entityId == null
                || !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .containsKey(entityId)) continue;
            String normalized = entityId.toString();
            entities.add(normalized);
            rules.put(normalized, new CorpseRules.EntityCarriers(
                validatedCandidates(requested.candidates(value, true),
                    CorpseMenu.CHEST_RIG_SLOT),
                validatedCandidates(requested.candidates(value, false),
                    CorpseMenu.BACKPACK_SLOT)));
        }
        return new CorpseRules.Settings(entities, rules);
    }

    private static List<CorpseRules.WeightedCarrier> validatedCandidates(
        List<CorpseRules.WeightedCarrier> requested, int slot) {
        List<CorpseRules.WeightedCarrier> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (CorpseRules.WeightedCarrier candidate : requested) {
            ResourceLocation id = ResourceLocation.tryParse(candidate.itemId());
            if (id == null || !seen.add(id.toString())
                || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) continue;
            ItemStack stack = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(id).getDefaultInstance();
            if (CorpseEntity.isCarrierStackForSlot(slot, stack)) {
                result.add(new CorpseRules.WeightedCarrier(id.toString(),
                    candidate.weight()));
            }
        }
        return result;
    }

    @Override public Type<CorpseRulesUpdatePacket> type() { return TYPE; }
}
