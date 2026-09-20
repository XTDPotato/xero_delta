package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.network.KnifeAccessPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World-persistent LR Tactical Workshop knife-skin unlocks. */
public final class KnifeAccessData extends SavedData {
    private static final String DATA_NAME = "xero_delta_knife_access";
    private final Map<UUID, LinkedHashSet<String>> unlocked = new HashMap<>();
    private final Map<UUID, String> selected = new HashMap<>();
    private final Map<UUID, Map<String, CompoundTag>> stackData = new HashMap<>();

    public static KnifeAccessData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(KnifeAccessData::new, KnifeAccessData::load), DATA_NAME);
    }

    public synchronized boolean unlock(UUID playerId, String itemId) {
        return unlock(playerId, itemId, null, null);
    }

    public synchronized boolean unlock(UUID playerId, String itemId, ItemStack stack,
                                       HolderLookup.Provider registries) {
        boolean changed = unlocked.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>()).add(itemId);
        if (stack != null && !stack.isEmpty() && registries != null) {
            CompoundTag encoded = (CompoundTag) stack.save(registries);
            CompoundTag previous = stackData.computeIfAbsent(playerId,
                ignored -> new HashMap<>()).put(itemId, encoded.copy());
            changed |= previous == null || !previous.equals(encoded);
        }
        if (changed) setDirty();
        return changed;
    }

    public synchronized ItemStack stack(UUID playerId, String itemId,
                                        HolderLookup.Provider registries) {
        CompoundTag encoded = stackData.getOrDefault(playerId, Map.of()).get(itemId);
        if (encoded == null || registries == null) return ItemStack.EMPTY;
        return ItemStack.parseOptional(registries, encoded.copy());
    }

    public synchronized boolean lock(UUID playerId, String itemId) {
        LinkedHashSet<String> ids = unlocked.get(playerId);
        boolean changed = ids != null && ids.remove(itemId);
        if (ids != null && ids.isEmpty()) unlocked.remove(playerId);
        Map<String, CompoundTag> savedStacks = stackData.get(playerId);
        if (savedStacks != null) {
            savedStacks.remove(itemId);
            if (savedStacks.isEmpty()) stackData.remove(playerId);
        }
        if (itemId.equals(selected.get(playerId))) {
            selected.remove(playerId);
            changed = true;
        }
        if (changed) setDirty();
        return changed;
    }

    public synchronized boolean isUnlocked(UUID playerId, String itemId) {
        return unlocked.getOrDefault(playerId, new LinkedHashSet<>()).contains(itemId);
    }

    public synchronized Set<String> snapshot(UUID playerId) {
        return Set.copyOf(unlocked.getOrDefault(playerId, new LinkedHashSet<>()));
    }

    public synchronized String selected(UUID playerId) {
        return selected.getOrDefault(playerId, "");
    }

    public synchronized boolean select(UUID playerId, String itemId) {
        if (!isUnlocked(playerId, itemId)) return false;
        selected.put(playerId, itemId);
        setDirty();
        return true;
    }

    public void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
            new KnifeAccessPacket(snapshot(player.getUUID()), selected(player.getUUID()),
                Map.copyOf(stackData.getOrDefault(player.getUUID(), Map.of()))));
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                   HolderLookup.@NotNull Provider registries) {
        ListTag players = new ListTag();
        unlocked.forEach((playerId, ids) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", playerId);
            ListTag items = new ListTag();
            for (String itemId : ids) {
                CompoundTag item = new CompoundTag();
                item.putString("id", itemId);
                items.add(item);
            }
            entry.put("items", items);
            String selectedId = selected.get(playerId);
            if (selectedId != null) entry.putString("selected", selectedId);
            Map<String, CompoundTag> savedStacks = stackData.get(playerId);
            if (savedStacks != null) {
                CompoundTag stacks = new CompoundTag();
                savedStacks.forEach(stacks::put);
                entry.put("stacks", stacks);
            }
            players.add(entry);
        });
        tag.put("players", players);
        return tag;
    }

    public static KnifeAccessData load(CompoundTag tag, HolderLookup.Provider registries) {
        KnifeAccessData data = new KnifeAccessData();
        ListTag players = tag.getList("players", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag entry = players.getCompound(index);
            if (!entry.hasUUID("player")) continue;
            UUID playerId = entry.getUUID("player");
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            ListTag items = entry.getList("items", CompoundTag.TAG_COMPOUND);
            for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                String id = items.getCompound(itemIndex).getString("id");
                if (!id.isBlank()) ids.add(id);
            }
            if (!ids.isEmpty()) data.unlocked.put(playerId, ids);
            CompoundTag stacks = entry.getCompound("stacks");
            if (!stacks.isEmpty()) {
                Map<String, CompoundTag> decoded = new HashMap<>();
                for (String key : stacks.getAllKeys()) {
                    CompoundTag value = stacks.getCompound(key);
                    if (!value.isEmpty()) decoded.put(key, value.copy());
                }
                if (!decoded.isEmpty()) data.stackData.put(playerId, decoded);
            }
            String selectedId = entry.getString("selected");
            if (!selectedId.isBlank() && ids.contains(selectedId)) {
                data.selected.put(playerId, selectedId);
            }
        }
        return data;
    }
}
