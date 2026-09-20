package com.xtdpotato.xero_delta.trading;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Persistent finite world supply and last observed ownership; never consumes the player's items. */
public final class RecipeWorldMarketData extends SavedData {
    private static final String NAME = "xero_delta_recipe_world_market";
    private final Map<String, Supply> supplies = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> ownership = new HashMap<>();
    private final Map<UUID, String> listingKeys = new HashMap<>();

    public static RecipeWorldMarketData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(RecipeWorldMarketData::new, RecipeWorldMarketData::load), NAME);
    }

    public static UUID listingId(String key) {
        return UUID.nameUUIDFromBytes(("xero_delta:recipe_supply:" + key).getBytes(StandardCharsets.UTF_8));
    }

    public static String publicListingId(String key) {
        // TradingSyncPacket reserves at most 32 characters for the public number.
        return "WR-" + listingId(key).toString().replace("-", "").substring(0, 28);
    }

    public static boolean isRecipeListing(TradingListing listing) {
        return listing != null && listing.virtualSupply()
            && TradingMarketData.WORLD_ACCOUNT_ID.equals(listing.sellerId())
            && listing.publicId() != null && listing.publicId().startsWith("WR-");
    }

    public Map<String, Long> observe(UUID player, Map<String, Long> current) {
        Map<String, Long> previous = ownership.getOrDefault(player, Map.of());
        Map<String, Long> gained = positiveDifference(previous, current);
        rebase(player, current);
        return gained;
    }

    public void rebase(UUID player, Map<String, Long> current) {
        if (!current.equals(ownership.get(player))) {
            ownership.put(player, Map.copyOf(current));
            setDirty();
        }
    }

    static Map<String, Long> positiveDifference(Map<String, Long> previous, Map<String, Long> current) {
        Map<String, Long> gains = new LinkedHashMap<>();
        Map<String, Long> previousTotals = itemTotals(previous);
        Map<String, Long> budgets = itemTotals(current);
        // Ammo/charge/NBT mutations do not create a new physical item. Only the
        // net increase of that item type can be allocated to component variants.
        budgets.replaceAll((item, count) -> Math.max(0, count - previousTotals.getOrDefault(item, 0L)));
        current.forEach((key, count) -> {
            long delta = count - previous.getOrDefault(key, 0L);
            String item = itemId(key);
            long granted = Math.min(Math.max(0, delta), budgets.getOrDefault(item, 0L));
            if (granted > 0) {
                gains.put(key, granted);
                budgets.put(item, budgets.get(item) - granted);
            }
        });
        return gains;
    }

    private static Map<String, Long> itemTotals(Map<String, Long> counts) {
        Map<String, Long> totals = new HashMap<>();
        counts.forEach((key, count) -> totals.merge(itemId(key), count, RecipeWorldMarketData::saturatedAdd));
        return totals;
    }

    private static String itemId(String key) {
        int components = key.indexOf('{');
        return components < 0 ? key : key.substring(0, components);
    }

    public void add(String key, ItemStack sample, long amount) {
        if (amount <= 0 || sample.isEmpty()) return;
        Supply old = supplies.get(key);
        supplies.put(key, new Supply(sample.copyWithCount(1),
            saturatedAdd(old == null ? 0 : old.remaining(), amount)));
        listingKeys.put(listingId(key), key);
        setDirty();
    }

    public String purchased(UUID listingId, int amount) {
        String key = listingKeys.get(listingId);
        if (key == null || amount <= 0) return null;
        Supply old = supplies.get(key);
        if (old != null) {
            supplies.put(key, new Supply(old.sample(), Math.max(0, old.remaining() - amount)));
            setDirty();
        }
        return key;
    }

    public void removeSupply(UUID listingId) {
        String key = listingKeys.remove(listingId);
        if (key != null && supplies.remove(key) != null) setDirty();
    }

    public void clearSupply() {
        supplies.clear();
        listingKeys.clear();
        setDirty();
    }

    public Set<String> keys() { return Set.copyOf(supplies.keySet()); }
    public Supply supply(String key) { return supplies.get(key); }

    static long saturatedAdd(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag stock = new ListTag();
        supplies.forEach((key, supply) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", key);
            entry.put("sample", supply.sample().save(registries));
            entry.putLong("remaining", supply.remaining());
            stock.add(entry);
        });
        tag.put("supply", stock);
        ListTag players = new ListTag();
        ownership.forEach((player, counts) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            CompoundTag items = new CompoundTag();
            counts.forEach(items::putLong);
            entry.put("items", items);
            players.add(entry);
        });
        tag.put("ownership", players);
        return tag;
    }

    public static RecipeWorldMarketData load(CompoundTag tag, HolderLookup.Provider registries) {
        RecipeWorldMarketData data = new RecipeWorldMarketData();
        for (var raw : tag.getList("supply", 10)) {
            CompoundTag entry = (CompoundTag) raw;
            ItemStack sample = ItemStack.parseOptional(registries, entry.getCompound("sample"));
            if (sample.isEmpty()) continue;
            String key = entry.getString("key");
            data.supplies.put(key, new Supply(sample.copyWithCount(1), Math.max(0, entry.getLong("remaining"))));
            data.listingKeys.put(listingId(key), key);
        }
        for (var raw : tag.getList("ownership", 10)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("player")) continue;
            CompoundTag items = entry.getCompound("items");
            Map<String, Long> counts = new HashMap<>();
            for (String key : items.getAllKeys()) {
                long count = items.getLong(key);
                if (count > 0) counts.put(key, count);
            }
            data.ownership.put(entry.getUUID("player"), Map.copyOf(counts));
        }
        return data;
    }

    public record Supply(ItemStack sample, long remaining) {}
}
