package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.block.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player permissions for changing virtual Delta equipment. */
public final class PlayerFeatureAccessData extends SavedData {
    private static final String DATA_NAME = "xero_delta_player_feature_access";
    private final Map<UUID, Boolean> values = new HashMap<>();
    private final Map<UUID, Boolean> layoutClickDisabled = new HashMap<>();

    public static PlayerFeatureAccessData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(PlayerFeatureAccessData::new, PlayerFeatureAccessData::load), DATA_NAME);
    }

    public synchronized boolean allowChangeBc(UUID playerId) {
        return values.getOrDefault(playerId, false);
    }

    public boolean allowChangeBc(ServerPlayer player) {
        if (player == null) return false;
        if (allowChangeBc(player.getUUID())) return true;
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-5, -5, -5), center.offset(5, 5, 5))) {
            if (player.level().getBlockState(pos).is(ModBlocks.PERSONAL_WAREHOUSE.get())) {
                return true;
            }
        }
        return false;
    }

    public synchronized void setAllowChangeBc(UUID playerId, boolean enabled) {
        if (enabled) values.put(playerId, true);
        else values.remove(playerId);
        setDirty();
    }

    /** Enhanced Delta drag, details and double-click handling defaults to enabled. */
    public synchronized boolean layoutClick(UUID playerId) {
        return !layoutClickDisabled.getOrDefault(playerId, false);
    }

    public synchronized void setLayoutClick(UUID playerId, boolean enabled) {
        if (enabled) layoutClickDisabled.remove(playerId);
        else layoutClickDisabled.put(playerId, true);
        setDirty();
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                   HolderLookup.@NotNull Provider registries) {
        ListTag players = new ListTag();
        java.util.Set<UUID> playerIds = new java.util.HashSet<>(values.keySet());
        playerIds.addAll(layoutClickDisabled.keySet());
        playerIds.forEach(playerId -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", playerId);
            if (values.getOrDefault(playerId, false)) {
                entry.putBoolean("allowChangeBc", true);
            }
            if (layoutClickDisabled.getOrDefault(playerId, false)) {
                entry.putBoolean("layoutClick", false);
            }
            players.add(entry);
        });
        tag.put("players", players);
        return tag;
    }

    public static PlayerFeatureAccessData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerFeatureAccessData data = new PlayerFeatureAccessData();
        ListTag players = tag.getList("players", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag entry = players.getCompound(index);
            if (!entry.hasUUID("player")) continue;
            boolean enabled = FeatureAccessMigration.resolve(
                entry.contains("allowChangeBc"), entry.getBoolean("allowChangeBc"),
                entry.getBoolean("canSetKnife"), entry.getBoolean("canSetSafetyBox"));
            UUID playerId = entry.getUUID("player");
            if (enabled) data.values.put(playerId, true);
            if (entry.contains("layoutClick") && !entry.getBoolean("layoutClick")) {
                data.layoutClickDisabled.put(playerId, true);
            }
        }
        return data;
    }
}
