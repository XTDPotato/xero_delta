package com.xtdpotato.xero_delta.trading;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import com.xtdpotato.xero_delta.mail.MailData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** World-global escrow, wallet and history storage for the trading market. */
public final class TradingMarketData extends SavedData {
    private static final String DATA_NAME = "xero_delta_trading_market";
    public static final UUID WORLD_ACCOUNT_ID = UUID.nameUUIDFromBytes(
        "xero_delta:world_trading_account".getBytes(StandardCharsets.UTF_8));
    public static final String WORLD_ACCOUNT_NAME = "world";

    private final Map<UUID, TradingListing> listings = new LinkedHashMap<>();
    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, Set<String>> favorites = new HashMap<>();
    private final Map<String, UUID> unresolvedNamedAccounts = new HashMap<>();
    private final Deque<TradingRecord> history = new ArrayDeque<>();
    private final Map<UUID, Set<HistoryRecordKey>> hiddenHistory = new HashMap<>();
    private final Map<UUID, Integer> playerListingSlots = new HashMap<>();
    private long listingSequence;
    private int maxListingDays = TradingRules.DEFAULT_LISTING_DAYS;
    private int maxMarketListings = TradingRules.DEFAULT_MAX_MARKET_LISTINGS;
    private int maxPlayerListingSlots = TradingRules.DEFAULT_MAX_PLAYER_LISTING_SLOTS;
    private int listingSlotLevelCost = TradingRules.DEFAULT_LISTING_SLOT_LEVEL_COST;
    private long maxCurrency = TradingRules.MAX_CONFIG_CURRENCY;
    private transient MinecraftServer ownerServer;
    private transient boolean forbiddenListingsSanitized;

    public static TradingMarketData get(MinecraftServer server) {
        TradingMarketData data = server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(TradingMarketData::new, TradingMarketData::load), DATA_NAME);
        data.ownerServer = server;
        data.removeForbiddenListings();
        return data;
    }

    private MinecraftServer currentServer() {
        if (ownerServer == null) throw new IllegalStateException("Trading market is not attached to a server");
        return ownerServer;
    }

    public synchronized List<TradingListing> listings() {
        return listings.values().stream().limit(TradingRules.MAX_SYNC_LISTINGS)
            .map(TradingListing::copy).toList();
    }

    public synchronized TradingListing getListing(UUID id) {
        TradingListing listing = listings.get(id);
        return listing == null ? null : listing.copy();
    }

    public synchronized int listingCount(UUID sellerId) {
        int count = 0;
        for (TradingListing listing : listings.values()) if (listing.sellerId().equals(sellerId)) count++;
        return count;
    }

    public synchronized TradingListing putListing(TradingListing listing) {
        if (listing == null || !TradingItemEligibility.canList(listing.stack())) return null;
        if (!listings.containsKey(listing.id()) && (listings.size() >= TradingRules.MAX_SYNC_LISTINGS
            || (!RecipeWorldMarketData.isRecipeListing(listing) && normalListingCount() >= maxMarketListings))) return null;
        if (!listings.containsKey(listing.id()) && RecipeWorldMarketData.isRecipeListing(listing)
            && listings.size() - normalListingCount() >= TradingRules.MAX_SYNC_LISTINGS - maxMarketListings) return null;
        if (listing.publicId() == null || listing.publicId().isBlank()) {
            listing = new TradingListing(listing.id(), listing.sellerId(), listing.sellerName(),
                listing.stack().copy(), listing.price(), listing.itemValue(), listing.createdAt(),
                nextPublicId(), listing.virtualSupply(), listing.durationTicks());
        }
        listings.put(listing.id(), listing.copy());
        setDirty();
        return listing.copy();
    }

    private synchronized void removeForbiddenListings() {
        if (forbiddenListingsSanitized) return;
        if (listings.values().removeIf(listing -> !TradingItemEligibility.canList(listing.stack()))) {
            setDirty();
        }
        forbiddenListingsSanitized = true;
    }

    public synchronized TradingListing getListing(String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        for (TradingListing listing : listings.values()) {
            if (publicId.equalsIgnoreCase(listing.publicId())) return listing.copy();
        }
        return null;
    }

    public synchronized TradingListing removeListing(UUID id) {
        TradingListing removed = listings.remove(id);
        if (removed != null) setDirty();
        return removed == null ? null : removed.copy();
    }

    public synchronized List<TradingListing> removeAllListings() {
        List<TradingListing> removed = listings.values().stream().map(TradingListing::copy).toList();
        if (!removed.isEmpty()) {
            listings.clear();
            setDirty();
        }
        return removed;
    }

    public synchronized boolean hasListingCapacity() {
        return normalListingCount() < maxMarketListings && listings.size() < TradingRules.MAX_SYNC_LISTINGS;
    }

    private int normalListingCount() {
        return (int) listings.values().stream().filter(value -> !RecipeWorldMarketData.isRecipeListing(value)).count();
    }

    public synchronized List<ItemStack> escrowFor(UUID player) {
        return listings.values().stream().filter(value -> !value.virtualSupply() && value.sellerId().equals(player))
            .map(value -> value.stack().copy()).toList();
    }

    public synchronized boolean hasPlayerListingCapacity(UUID playerId) {
        return listingCount(playerId) < playerListingSlots(playerId);
    }

    public synchronized int playerListingSlots(UUID playerId) {
        return Math.min(maxPlayerListingSlots, Math.max(1,
            playerListingSlots.getOrDefault(playerId, TradingRules.DEFAULT_PLAYER_LISTING_SLOTS)));
    }

    public synchronized void setPlayerListingSlots(UUID playerId, int count) {
        playerListingSlots.put(playerId, Math.max(1, Math.min(maxPlayerListingSlots, count)));
        setDirty();
    }

    public synchronized int unlockNextPlayerListingSlot(UUID playerId) {
        int current = playerListingSlots(playerId);
        if (current >= maxPlayerListingSlots) return current;
        setPlayerListingSlots(playerId, current + 1);
        return current + 1;
    }

    public synchronized int maxPlayerListingSlots() {
        return maxPlayerListingSlots;
    }

    public synchronized void setMaxPlayerListingSlots(int count) {
        maxPlayerListingSlots = Math.max(1,
            Math.min(TradingRules.MAX_CONFIG_PLAYER_LISTING_SLOTS, count));
        playerListingSlots.replaceAll((id, slots) -> Math.min(maxPlayerListingSlots, Math.max(1, slots)));
        setDirty();
    }

    public synchronized int listingSlotLevelCost() {
        return listingSlotLevelCost;
    }

    public synchronized void setListingSlotLevelCost(int levels) {
        listingSlotLevelCost = Math.max(0,
            Math.min(TradingRules.MAX_CONFIG_LISTING_SLOT_LEVEL_COST, levels));
        setDirty();
    }

    public synchronized int maxListingDays() {
        return maxListingDays;
    }

    public synchronized void setMaxListingDays(int days) {
        maxListingDays = Math.max(1, Math.min(TradingRules.MAX_CONFIG_LISTING_DAYS, days));
        setDirty();
    }

    public synchronized int maxMarketListings() {
        return maxMarketListings;
    }

    public synchronized void setMaxMarketListings(int count) {
        maxMarketListings = Math.max(1, Math.min(TradingRules.MAX_CONFIG_MARKET_LISTINGS, count));
        setDirty();
    }

    public synchronized long maxCurrency() {
        return maxCurrency;
    }

    public synchronized void setMaxCurrency(long amount) {
        maxCurrency = Math.max(1L, Math.min(TradingRules.MAX_CONFIG_CURRENCY, amount));
        TradingRules.setMaxCurrency(maxCurrency);
        balances.replaceAll((id, balance) -> Math.min(maxCurrency, Math.max(0L, balance)));
        setDirty();
    }

    public synchronized long balance(UUID playerId) {
        Long current = balances.get(playerId);
        if (current != null) return current;
        if (WORLD_ACCOUNT_ID.equals(playerId)) return 0L;
        balances.put(playerId, TradingRules.DEFAULT_STARTING_BALANCE);
        setDirty();
        return TradingRules.DEFAULT_STARTING_BALANCE;
    }

    public synchronized boolean debit(UUID playerId, long amount) {
        if (amount <= 0) return false;
        long current = balance(playerId);
        if (current < amount) return false;
        balances.put(playerId, current - amount);
        setDirty();
        return true;
    }

    public synchronized long credit(UUID playerId, long amount) {
        long before = balance(playerId);
        long after = TradingRules.addBalance(before, amount);
        balances.put(playerId, after);
        setDirty();
        return after - before;
    }

    public synchronized void setBalance(UUID playerId, long amount) {
        balances.put(playerId, TradingRules.clampBalance(amount));
        setDirty();
    }

    public synchronized long worldBalance() {
        return balances.getOrDefault(WORLD_ACCOUNT_ID, 0L);
    }

    public synchronized long creditWorld(long amount) {
        return credit(WORLD_ACCOUNT_ID, amount);
    }

    public synchronized UUID unresolvedAccount(String playerName) {
        String normalized = normalizeAccountName(playerName);
        UUID existing = unresolvedNamedAccounts.get(normalized);
        if (existing != null) return existing;
        UUID created = UUID.nameUUIDFromBytes(
            ("xero_delta:unresolved_seller:" + normalized).getBytes(StandardCharsets.UTF_8));
        unresolvedNamedAccounts.put(normalized, created);
        setDirty();
        return created;
    }

    /** Moves command-created offline proceeds to the real player the first time that name logs in. */
    public synchronized void claimUnresolvedAccount(UUID playerId, String playerName) {
        UUID unresolved = unresolvedNamedAccounts.remove(normalizeAccountName(playerName));
        if (unresolved == null || unresolved.equals(playerId)) return;

        MailData.get(currentServer()).migrateMailbox(unresolved, playerId, playerName);

        Long pendingBalance = balances.remove(unresolved);
        if (pendingBalance != null) {
            Long current = balances.get(playerId);
            if (current == null) balances.put(playerId, pendingBalance);
            else balances.put(playerId, TradingRules.addBalance(current,
                Math.max(0L, pendingBalance - TradingRules.DEFAULT_STARTING_BALANCE)));
        }
        listings.replaceAll((id, listing) -> unresolved.equals(listing.sellerId())
            ? new TradingListing(listing.id(), playerId, playerName, listing.stack().copy(), listing.price(),
                listing.itemValue(), listing.createdAt(), listing.publicId(), listing.virtualSupply(),
                listing.durationTicks())
            : listing);
        Set<String> pendingFavorites = favorites.remove(unresolved);
        if (pendingFavorites != null && !pendingFavorites.isEmpty()) {
            Set<String> mergedFavorites = favorites.computeIfAbsent(playerId, ignored -> new HashSet<>());
            mergedFavorites.addAll(pendingFavorites);
            TradingFavoriteKeys.normalize(mergedFavorites);
        }
        if (!history.isEmpty()) {
            Deque<TradingRecord> migrated = new ArrayDeque<>();
            for (TradingRecord record : history) {
                UUID seller = unresolved.equals(record.sellerId()) ? playerId : record.sellerId();
                UUID buyer = unresolved.equals(record.buyerId()) ? playerId : record.buyerId();
                String sellerName = unresolved.equals(record.sellerId()) ? playerName : record.sellerName();
                String buyerName = unresolved.equals(record.buyerId()) ? playerName : record.buyerName();
                migrated.addLast(new TradingRecord(record.listingId(), seller, sellerName, buyer, buyerName,
                    record.stack().copy(), record.price(), record.completedAt(), record.publicId(),
                    record.completedEpochMillis()));
            }
            history.clear();
            history.addAll(migrated);
        }
        setDirty();
    }

    public static long publicIdCreatedAtMillis(String publicId) {
        return TradingListingId.createdAtMillis(publicId);
    }

    private String nextPublicId() {
        listingSequence++;
        return formatPublicId(System.currentTimeMillis(), listingSequence);
    }

    static String formatPublicId(long epochMillis, long sequence) {
        return TradingListingId.format(epochMillis, sequence);
    }

    private static String normalizeAccountName(String playerName) {
        return playerName == null ? "" : playerName.trim().toLowerCase(Locale.ROOT);
    }

    public synchronized Set<String> favorites(UUID playerId) {
        Set<String> values = favorites.get(playerId);
        if (values == null || values.isEmpty()) return Set.of();
        if (TradingFavoriteKeys.normalize(values)) setDirty();
        return Set.copyOf(values);
    }

    public synchronized boolean toggleFavorite(UUID playerId, String itemId) {
        if (itemId == null || itemId.isBlank() || itemId.length() > 8192) return false;
        Set<String> values = favorites.computeIfAbsent(playerId, ignored -> new HashSet<>());
        boolean changed = TradingFavoriteKeys.normalize(values);
        boolean selected;
        if (values.remove(itemId)) {
            selected = false;
            changed = true;
        }
        else {
            changed |= TradingFavoriteKeys.removeLegacyBaseForVariant(values, itemId);
            if (values.size() >= 256) {
                if (changed) setDirty();
                return false;
            }
            values.add(itemId);
            selected = true;
            changed = true;
        }
        if (changed) setDirty();
        return selected;
    }

    public synchronized void addRecord(TradingRecord record) {
        history.addFirst(record.copy());
        while (history.size() > TradingRules.MAX_HISTORY) history.removeLast();
        setDirty();
    }

    public synchronized List<TradingRecord> historyFor(UUID playerId) {
        List<TradingRecord> result = new ArrayList<>();
        Set<HistoryRecordKey> hidden = hiddenHistory.getOrDefault(playerId, Set.of());
        for (TradingRecord record : history) {
            if ((record.sellerId().equals(playerId) || record.buyerId().equals(playerId))
                && !hidden.contains(HistoryRecordKey.of(record))) result.add(record.copy());
            if (result.size() >= 100) break;
        }
        return List.copyOf(result);
    }

    public synchronized boolean hideHistoryRecord(UUID playerId, UUID listingId, long completedEpochMillis) {
        for (TradingRecord record : history) {
            if (record.listingId().equals(listingId)
                && record.completedEpochMillis() == completedEpochMillis
                && (record.sellerId().equals(playerId) || record.buyerId().equals(playerId))) {
                boolean changed = hiddenHistory.computeIfAbsent(playerId, ignored -> new HashSet<>())
                    .add(HistoryRecordKey.of(record));
                if (changed) setDirty();
                return changed;
            }
        }
        return false;
    }

    public synchronized int hideAllHistory(UUID playerId) {
        Set<HistoryRecordKey> hidden = hiddenHistory.computeIfAbsent(playerId, ignored -> new HashSet<>());
        int changed = 0;
        for (TradingRecord record : history) {
            if ((record.sellerId().equals(playerId) || record.buyerId().equals(playerId))
                && hidden.add(HistoryRecordKey.of(record))) changed++;
        }
        if (changed > 0) setDirty();
        return changed;
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                   HolderLookup.@NotNull Provider registries) {
        ListTag listingTags = new ListTag();
        for (TradingListing listing : listings.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", listing.id());
            value.putUUID("seller", listing.sellerId());
            value.putString("seller_name", listing.sellerName());
            value.putInt("stack_count", Math.max(1, listing.stack().getCount()));
            value.put("stack", listing.stack().copyWithCount(1).save(registries));
            value.putLong("price", listing.price());
            value.putLong("item_value", listing.itemValue());
            value.putLong("created", listing.createdAt());
            value.putString("public_id", listing.publicId());
            value.putBoolean("virtual_supply", listing.virtualSupply());
            value.putLong("duration_ticks", listing.durationTicks());
            listingTags.add(value);
        }
        tag.put("listings", listingTags);
        tag.putLong("listing_sequence", listingSequence);
        tag.putInt("max_listing_days", maxListingDays);
        tag.putInt("max_market_listings", maxMarketListings);
        tag.putInt("max_player_listing_slots", maxPlayerListingSlots);
        tag.putInt("listing_slot_level_cost", listingSlotLevelCost);
        tag.putLong("max_currency", maxCurrency);

        ListTag playerListingSlotTags = new ListTag();
        playerListingSlots.forEach((id, slots) -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("player", id);
            value.putInt("slots", slots);
            playerListingSlotTags.add(value);
        });
        tag.put("player_listing_slots", playerListingSlotTags);

        ListTag balanceTags = new ListTag();
        balances.forEach((id, balance) -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", id);
            value.putLong("balance", balance);
            balanceTags.add(value);
        });
        tag.put("balances", balanceTags);

        ListTag favoriteTags = new ListTag();
        favorites.forEach((id, values) -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", id);
            ListTag items = new ListTag();
            values.forEach(item -> items.add(StringTag.valueOf(item)));
            value.put("items", items);
            favoriteTags.add(value);
        });
        tag.put("favorites", favoriteTags);

        ListTag namedAccountTags = new ListTag();
        unresolvedNamedAccounts.forEach((name, id) -> {
            CompoundTag value = new CompoundTag();
            value.putString("name", name);
            value.putUUID("id", id);
            namedAccountTags.add(value);
        });
        tag.put("unresolved_named_accounts", namedAccountTags);

        ListTag historyTags = new ListTag();
        for (TradingRecord record : history) {
            CompoundTag value = new CompoundTag();
            value.putUUID("listing", record.listingId());
            value.putUUID("seller", record.sellerId());
            value.putString("seller_name", record.sellerName());
            value.putUUID("buyer", record.buyerId());
            value.putString("buyer_name", record.buyerName());
            value.putInt("stack_count", Math.max(1, record.stack().getCount()));
            value.put("stack", record.stack().copyWithCount(1).save(registries));
            value.putLong("price", record.price());
            value.putLong("completed", record.completedAt());
            value.putString("public_id", record.publicId());
            value.putLong("completed_epoch", record.completedEpochMillis());
            historyTags.add(value);
        }
        tag.put("history", historyTags);

        ListTag hiddenHistoryTags = new ListTag();
        hiddenHistory.forEach((playerId, records) -> {
            for (HistoryRecordKey record : records) {
                CompoundTag value = new CompoundTag();
                value.putUUID("player", playerId);
                value.putUUID("listing", record.listingId());
                value.putLong("completed_epoch", record.completedEpochMillis());
                hiddenHistoryTags.add(value);
            }
        });
        tag.put("hidden_history", hiddenHistoryTags);
        return tag;
    }

    public static TradingMarketData load(CompoundTag tag, HolderLookup.Provider registries) {
        TradingMarketData data = new TradingMarketData();
        data.listingSequence = Math.max(0L, tag.getLong("listing_sequence"));
        if (tag.contains("max_listing_days")) data.setMaxListingDays(tag.getInt("max_listing_days"));
        if (tag.contains("max_market_listings")) data.setMaxMarketListings(tag.getInt("max_market_listings"));
        if (tag.contains("max_player_listing_slots")) {
            data.setMaxPlayerListingSlots(tag.getInt("max_player_listing_slots"));
        }
        if (tag.contains("listing_slot_level_cost")) {
            data.setListingSlotLevelCost(tag.getInt("listing_slot_level_cost"));
        }
        if (tag.contains("max_currency")) data.setMaxCurrency(tag.getLong("max_currency"));
        for (var raw : tag.getList("player_listing_slots", 10)) {
            CompoundTag value = (CompoundTag) raw;
            if (value.hasUUID("player")) {
                data.playerListingSlots.put(value.getUUID("player"), Math.max(1,
                    Math.min(data.maxPlayerListingSlots, value.getInt("slots"))));
            }
        }
        for (var raw : tag.getList("listings", 10)) {
            CompoundTag value = (CompoundTag) raw;
            ItemStack stack = ItemStack.parseOptional(registries, value.getCompound("stack"));
            if (!TradingItemEligibility.canList(stack) || !value.hasUUID("id") || !value.hasUUID("seller")) continue;
            if (value.contains("stack_count")) stack.setCount(Math.max(1, value.getInt("stack_count")));
            TradingListing listing = new TradingListing(value.getUUID("id"), value.getUUID("seller"),
                value.getString("seller_name"), stack, value.getLong("price"), value.getLong("item_value"),
                value.getLong("created"), value.getString("public_id"), value.getBoolean("virtual_supply"),
                value.contains("duration_ticks") ? Math.max(1L, value.getLong("duration_ticks"))
                    : TradingRules.LISTING_LIFETIME_TICKS);
            if (listing.publicId().isBlank()) {
                listing = new TradingListing(listing.id(), listing.sellerId(), listing.sellerName(), listing.stack(),
                    listing.price(), listing.itemValue(), listing.createdAt(), data.nextPublicId(),
                    listing.virtualSupply(), listing.durationTicks());
            }
            data.listings.put(listing.id(), listing);
        }
        for (var raw : tag.getList("balances", 10)) {
            CompoundTag value = (CompoundTag) raw;
            if (value.hasUUID("id")) data.balances.put(value.getUUID("id"),
                TradingRules.clampBalance(value.getLong("balance")));
        }
        for (var raw : tag.getList("favorites", 10)) {
            CompoundTag value = (CompoundTag) raw;
            if (!value.hasUUID("id")) continue;
            Set<String> items = new HashSet<>();
            for (var item : value.getList("items", 8)) if (items.size() < 256) items.add(item.getAsString());
            if (TradingFavoriteKeys.normalize(items)) data.setDirty();
            data.favorites.put(value.getUUID("id"), items);
        }
        for (var raw : tag.getList("unresolved_named_accounts", 10)) {
            CompoundTag value = (CompoundTag) raw;
            if (value.hasUUID("id") && !value.getString("name").isBlank()) {
                data.unresolvedNamedAccounts.put(normalizeAccountName(value.getString("name")), value.getUUID("id"));
            }
        }
        for (var raw : tag.getList("history", 10)) {
            CompoundTag value = (CompoundTag) raw;
            ItemStack stack = ItemStack.parseOptional(registries, value.getCompound("stack"));
            if (stack.isEmpty() || !value.hasUUID("listing") || !value.hasUUID("seller")
                || !value.hasUUID("buyer")) continue;
            if (value.contains("stack_count")) stack.setCount(Math.max(1, value.getInt("stack_count")));
            data.history.addLast(new TradingRecord(value.getUUID("listing"), value.getUUID("seller"),
                value.getString("seller_name"), value.getUUID("buyer"), value.getString("buyer_name"),
                stack, value.getLong("price"), value.getLong("completed"), value.getString("public_id"),
                value.contains("completed_epoch") ? value.getLong("completed_epoch") : 0L));
            if (data.history.size() >= TradingRules.MAX_HISTORY) break;
        }
        for (var raw : tag.getList("hidden_history", 10)) {
            CompoundTag value = (CompoundTag) raw;
            if (!value.hasUUID("player") || !value.hasUUID("listing")) continue;
            data.hiddenHistory.computeIfAbsent(value.getUUID("player"), ignored -> new HashSet<>())
                .add(new HistoryRecordKey(value.getUUID("listing"), value.getLong("completed_epoch")));
        }
        return data;
    }

    private record HistoryRecordKey(UUID listingId, long completedEpochMillis) {
        private static HistoryRecordKey of(TradingRecord record) {
            return new HistoryRecordKey(record.listingId(), record.completedEpochMillis());
        }
    }
}
