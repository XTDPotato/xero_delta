package com.xtdpotato.xero_delta.trading;

import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.BoundItemPolicy;
import com.xtdpotato.xero_delta.mail.MailService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.UUID;
import java.util.List;

/** Atomic server-thread operations. Clients only submit intent. */
public final class TradingMarketService {
    public record Result(boolean success, String message, long value) {
        public static Result ok(String message) { return new Result(true, message, 0L); }
        public static Result ok(String message, long value) { return new Result(true, message, value); }
        public static Result fail(String message) { return new Result(false, message, 0L); }
    }

    private TradingMarketService() {
    }

    public static Result list(ServerPlayer player, String sourceId, int amount, long requestedPrice) {
        return list(player, sourceId, amount, requestedPrice, TradingRules.DEFAULT_LISTING_DAYS);
    }

    public static Result list(ServerPlayer player, String sourceId, int amount, long requestedPrice,
                              int durationDays) {
        TradingMarketData market = TradingMarketData.get(player.server);
        market.claimUnresolvedAccount(player.getUUID(), player.getGameProfile().getName());
        if (!market.hasListingCapacity()) {
            return Result.fail("market.xero_delta.error.listing_limit");
        }
        if (!market.hasPlayerListingCapacity(player.getUUID())) {
            return Result.fail("market.xero_delta.error.player_listing_slots");
        }
        if (durationDays <= 0 || durationDays > market.maxListingDays()) {
            return Result.fail("market.xero_delta.error.duration_limit");
        }
        TradingInventorySource source = TradingInventorySources.find(player, sourceId);
        if (source == null || amount <= 0) return Result.fail("market.xero_delta.error.source");
        if (!source.sellable()) return Result.fail(source.blockedReason());
        ItemStack sample = source.peek();
        String blockedReason = listingBlockedReason(player.server, sample);
        if (!blockedReason.isBlank()) return Result.fail(blockedReason);
        if (!sample.isEmpty() && amount > TradingRules.maxAmountPerListing(sample.getMaxStackSize())) {
            return Result.fail("market.xero_delta.error.stack_limit");
        }
        if (sample.isEmpty() || amount > TradingInventorySources.countMatching(player, sample)) {
            return Result.fail("market.xero_delta.error.source");
        }
        ItemStack simulated = TradingInventorySources.extractMatching(player, sample, amount, true);
        if (simulated.isEmpty() || simulated.getCount() != amount) {
            return Result.fail("market.xero_delta.error.source");
        }
        long unitValue = TradingRules.normalizeItemValue(
            ModDataStorage.get(player.server.overworld()).getPriceFor(simulated.copyWithCount(1)));
        long totalValue = safeMultiply(unitValue, amount);
        if (!TradingRules.isListingPriceAllowed(totalValue, requestedPrice)) {
            return Result.fail("market.xero_delta.error.price_limit");
        }
        ItemStack extracted = TradingInventorySources.extractMatching(player, sample, amount, false);
        if (extracted.isEmpty() || extracted.getCount() != amount) {
            if (!extracted.isEmpty()) giveOrDrop(player, extracted);
            return Result.fail("market.xero_delta.error.source_changed");
        }
        TradingListing listing = new TradingListing(UUID.randomUUID(), player.getUUID(),
            player.getGameProfile().getName(), extracted.copy(), requestedPrice, totalValue,
            player.server.overworld().getGameTime(), "", false, TradingRules.listingLifetimeTicks(durationDays));
        if (market.putListing(listing) == null) {
            giveOrDrop(player, extracted);
            return Result.fail("market.xero_delta.error.listing_limit");
        }
        player.getInventory().setChanged();
        return Result.ok("market.xero_delta.success.listed");
    }

    public static Result unlockListingSlot(ServerPlayer player) {
        TradingMarketData market = TradingMarketData.get(player.server);
        int current = market.playerListingSlots(player.getUUID());
        if (current >= market.maxPlayerListingSlots()) {
            return Result.fail("market.xero_delta.error.listing_slots_max");
        }
        int cost = market.listingSlotLevelCost();
        if (!player.isCreative() && player.experienceLevel < cost) {
            return Result.fail("market.xero_delta.error.listing_slot_levels");
        }
        if (!player.isCreative() && cost > 0) player.giveExperienceLevels(-cost);
        int unlocked = market.unlockNextPlayerListingSlot(player.getUUID());
        return Result.ok("market.xero_delta.success.listing_slot_unlocked", unlocked);
    }

    /** Creative-only virtual supply. No inventory or backpack stack is read or consumed. */
    public static Result creativeList(ServerPlayer player, String itemId, int amount,
                                      long requestedPrice, int durationDays) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || !TradingItemEligibility.canList(player.server, id)
            || BuiltInRegistries.ITEM.get(id) == Items.AIR) {
            return Result.fail("market.xero_delta.error.unsupported_item");
        }
        return creativeList(player, BuiltInRegistries.ITEM.get(id).getDefaultInstance(), amount,
            requestedPrice, durationDays);
    }

    /** Creative virtual supply preserving modded data components such as TACZ gun ids. */
    public static Result creativeList(ServerPlayer player, ItemStack requestedStack, int amount,
                                      long requestedPrice, int durationDays) {
        if (!player.isCreative()) return Result.fail("market.xero_delta.error.creative_only");
        if (requestedStack == null || requestedStack.isEmpty()) {
            return Result.fail("market.xero_delta.error.unsupported_item");
        }
        ItemStack sample = requestedStack.copyWithCount(1);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(sample.getItem());
        if (id == null || sample.getItem() == Items.AIR || !TradingItemEligibility.canList(player.server, sample)) {
            return Result.fail("market.xero_delta.error.unsupported_item");
        }
        TradingMarketData market = TradingMarketData.get(player.server);
        if (!market.hasListingCapacity()) return Result.fail("market.xero_delta.error.listing_limit");
        if (durationDays <= 0 || durationDays > market.maxListingDays()) {
            return Result.fail("market.xero_delta.error.duration_limit");
        }
        if (amount <= 0 || amount > TradingRules.MAX_CREATIVE_LISTING_AMOUNT) {
            return Result.fail("market.xero_delta.error.amount");
        }
        long unitValue = TradingRules.normalizeItemValue(
            ModDataStorage.get(player.server.overworld()).getPriceFor(sample.copyWithCount(1)));
        long totalValue = safeMultiply(unitValue, amount);
        if (!TradingRules.isListingPriceAllowed(totalValue, requestedPrice)) {
            return Result.fail("market.xero_delta.error.price_limit");
        }
        long unitPrice = Math.max(1L, requestedPrice / Math.max(1, amount));
        // Creative listings are world-managed virtual supply. They neither consume
        // player inventory/backpack items nor count against that player's listing slots.
        TradingListing listing = adminList(player.server, TradingMarketData.WORLD_ACCOUNT_ID,
            TradingMarketData.WORLD_ACCOUNT_NAME, sample, amount, unitPrice, durationDays);
        return listing == null ? Result.fail("market.xero_delta.error.listing_limit")
            : Result.ok("market.xero_delta.success.listed");
    }

    /** Creative administrator edit of an existing world-managed virtual listing. */
    public static Result creativeEdit(ServerPlayer player, UUID listingId, int amount,
                                      long requestedPrice, int durationDays) {
        if (!player.isCreative()) return Result.fail("market.xero_delta.error.creative_only");
        TradingMarketData market = TradingMarketData.get(player.server);
        TradingListing listing = market.getListing(listingId);
        if (listing == null) return Result.fail("market.xero_delta.error.stale");
        if (!listing.virtualSupply() || !TradingMarketData.WORLD_ACCOUNT_ID.equals(listing.sellerId())) {
            return Result.fail("market.xero_delta.error.creative_edit_world_only");
        }
        if (amount <= 0 || amount > TradingRules.MAX_CREATIVE_LISTING_AMOUNT) {
            return Result.fail("market.xero_delta.error.amount");
        }
        if (durationDays <= 0 || durationDays > market.maxListingDays()) {
            return Result.fail("market.xero_delta.error.duration_limit");
        }
        ItemStack sample = listing.stack().copyWithCount(1);
        String blockedReason = listingBlockedReason(player.server, sample);
        if (!blockedReason.isBlank()) return Result.fail(blockedReason);
        long unitValue = TradingRules.normalizeItemValue(
            ModDataStorage.get(player.server.overworld()).getPriceFor(sample));
        long totalValue = safeMultiply(unitValue, amount);
        if (!TradingRules.isListingPriceAllowed(totalValue, requestedPrice)) {
            return Result.fail("market.xero_delta.error.price_limit");
        }
        TradingListing edited = new TradingListing(listing.id(), TradingMarketData.WORLD_ACCOUNT_ID,
            TradingMarketData.WORLD_ACCOUNT_NAME, sample.copyWithCount(amount), requestedPrice,
            totalValue, player.server.overworld().getGameTime(), listing.publicId(), true,
            TradingRules.listingLifetimeTicks(durationDays));
        return market.putListing(edited) == null
            ? Result.fail("market.xero_delta.error.listing_limit")
            : Result.ok("market.xero_delta.success.creative_edited");
    }

    /** Creates command-managed supply without reading or consuming an inventory. */
    public static TradingListing adminList(MinecraftServer server, UUID sellerId, String sellerName,
                                           ItemStack sample, int amount, long requestedUnitPrice) {
        return adminList(server, sellerId, sellerName, sample, amount, requestedUnitPrice,
            TradingRules.DEFAULT_LISTING_DAYS);
    }

    public static TradingListing adminList(MinecraftServer server, UUID sellerId, String sellerName,
                                           ItemStack sample, int amount, long requestedUnitPrice,
                                           int durationDays) {
        TradingMarketData market = server == null ? null : TradingMarketData.get(server);
        if (server == null || !TradingItemEligibility.canList(server, sample) || amount <= 0 || amount > 9_999
            || requestedUnitPrice <= 0 || requestedUnitPrice > TradingRules.MAX_CURRENCY
            || durationDays <= 0 || durationDays > market.maxListingDays() || !market.hasListingCapacity()) return null;
        ItemStack listed = sample.copy();
        listed.setCount(amount);
        long unitValue = TradingRules.normalizeItemValue(
            ModDataStorage.get(server.overworld()).getPriceFor(sample.copyWithCount(1)));
        TradingListing listing = new TradingListing(UUID.randomUUID(), sellerId, sellerName, listed,
            safeMultiply(requestedUnitPrice, amount), safeMultiply(unitValue, amount),
            server.overworld().getGameTime(), "", true, TradingRules.listingLifetimeTicks(durationDays));
        return market.putListing(listing);
    }

    public static Result buy(ServerPlayer buyer, UUID listingId) {
        return buy(buyer, listingId, 1);
    }

    public static Result buy(ServerPlayer buyer, UUID listingId, int amount) {
        TradingMarketData market = TradingMarketData.get(buyer.server);
        market.claimUnresolvedAccount(buyer.getUUID(), buyer.getGameProfile().getName());
        TradingListing listing = market.getListing(listingId);
        if (listing == null) return Result.fail("market.xero_delta.error.stale");
        if (amount <= 0 || amount > 640) {
            return Result.fail("market.xero_delta.error.amount");
        }
        long gameTime = buyer.server.overworld().getGameTime();
        if (TradingRules.isExpired(listing.createdAt(), gameTime, listing.durationTicks())) {
            return Result.fail("market.xero_delta.error.expired");
        }
        if (listing.sellerId().equals(buyer.getUUID())) return Result.fail("market.xero_delta.error.own_listing");
        synchronized (market) {
            TradingListing current = market.getListing(listingId);
            if (current == null) return Result.fail("market.xero_delta.error.stale");
            TradingListing lowest = lowestActiveListing(market, current.stack(),
                buyer.server.overworld().getGameTime(), buyer.getUUID());
            if (lowest == null || !lowest.id().equals(current.id())) {
                return Result.fail("market.xero_delta.error.stale");
            }
            long lowestUnitPrice = unitPrice(lowest);
            long now = buyer.server.overworld().getGameTime();
            List<TradingListing> candidates = market.listings().stream()
                .filter(value -> !value.sellerId().equals(buyer.getUUID()))
                .filter(value -> !TradingRules.isExpired(value.createdAt(), now, value.durationTicks()))
                .filter(value -> ItemStack.isSameItemSameComponents(current.stack(), value.stack()))
                .filter(value -> unitPrice(value) == lowestUnitPrice)
                .sorted((first, second) -> {
                    int price = Long.compare(unitPrice(first), unitPrice(second));
                    return price != 0 ? price : Long.compare(first.createdAt(), second.createdAt());
                }).toList();
            List<PurchasePart> parts = new java.util.ArrayList<>();
            int remainingAmount = amount;
            long totalPrice = 0L;
            for (TradingListing candidate : candidates) {
                if (remainingAmount <= 0) break;
                int take = Math.min(remainingAmount, candidate.stack().getCount());
                long partPrice = proportionalCeil(candidate.price(), take, candidate.stack().getCount());
                long partValue = proportionalCeil(candidate.itemValue(), take, candidate.stack().getCount());
                parts.add(new PurchasePart(candidate, take, partPrice, partValue));
                totalPrice = Math.min(TradingRules.MAX_CURRENCY, totalPrice + partPrice);
                remainingAmount -= take;
            }
            if (remainingAmount > 0) return Result.fail("market.xero_delta.error.amount");
            ItemStack purchased = current.stack().copyWithCount(amount);
            if (!canFit(buyer, purchased)) return Result.fail("market.xero_delta.error.inventory_full");
            if (!market.debit(buyer.getUUID(), totalPrice)) {
                return Result.fail("market.xero_delta.error.balance");
            }
            for (PurchasePart part : parts) {
                TradingListing sold = part.listing();
                RecipeWorldMarket.purchased(buyer.server, sold, part.amount());
                if (part.amount() >= sold.stack().getCount()) market.removeListing(sold.id());
                else {
                    ItemStack remaining = sold.stack().copyWithCount(sold.stack().getCount() - part.amount());
                    market.putListing(new TradingListing(sold.id(), sold.sellerId(), sold.sellerName(), remaining,
                        Math.max(1L, sold.price() - part.price()), Math.max(1L, sold.itemValue() - part.itemValue()),
                        sold.createdAt(), sold.publicId(), sold.virtualSupply(), sold.durationTicks()));
                }
                long sellerProceeds = TradingRules.sellerProceeds(part.price());
                if (TradingMarketData.WORLD_ACCOUNT_ID.equals(sold.sellerId())) {
                    market.creditWorld(sellerProceeds);
                } else {
                    MailService.sendTradeSale(buyer.server, sold.sellerId(), sold.sellerName(),
                        buyer.getGameProfile().getName(), sold.stack().copyWithCount(part.amount()),
                        sellerProceeds, sold.publicId());
                }
                market.creditWorld(TradingRules.marketDeductions(part.price()));
                market.addRecord(new TradingRecord(sold.id(), sold.sellerId(), sold.sellerName(),
                    buyer.getUUID(), buyer.getGameProfile().getName(),
                    sold.stack().copyWithCount(part.amount()), part.price(), now, sold.publicId(),
                    System.currentTimeMillis()));
            }
            RecipeWorldMarket.observe(buyer);
            giveOrDrop(buyer, purchased, true);
            RecipeWorldMarket.rebaseAfterPurchase(buyer);
            return Result.ok("market.xero_delta.success.bought", totalPrice);
        }
    }

    private record PurchasePart(TradingListing listing, int amount, long price, long itemValue) {
    }

    public static Result cancel(ServerPlayer seller, UUID listingId) {
        TradingMarketData market = TradingMarketData.get(seller.server);
        market.claimUnresolvedAccount(seller.getUUID(), seller.getGameProfile().getName());
        TradingListing listing = market.getListing(listingId);
        if (listing == null) return Result.fail("market.xero_delta.error.stale");
        if (!listing.sellerId().equals(seller.getUUID())) return Result.fail("market.xero_delta.error.not_owner");
        TradingListing removed = market.removeListing(listingId);
        if (removed == null) return Result.fail("market.xero_delta.error.stale");
        if (!removed.virtualSupply()) giveOrDrop(seller, removed.stack().copy());
        return Result.ok("market.xero_delta.success.cancelled");
    }

    public static Result deleteHistory(ServerPlayer player, UUID listingId, long completedEpochMillis) {
        TradingMarketData market = TradingMarketData.get(player.server);
        return market.hideHistoryRecord(player.getUUID(), listingId, completedEpochMillis)
            ? Result.ok("market.xero_delta.success.history_deleted")
            : Result.fail("market.xero_delta.error.history_missing");
    }

    public static Result deleteAllHistory(ServerPlayer player) {
        TradingMarketData market = TradingMarketData.get(player.server);
        int deleted = market.hideAllHistory(player.getUUID());
        return Result.ok("market.xero_delta.success.history_all_deleted", deleted);
    }

    public static Result cancelByPublicId(MinecraftServer server, String publicId) {
        TradingMarketData market = TradingMarketData.get(server);
        TradingListing listing = market.getListing(publicId);
        if (listing == null) return Result.fail("command.xero_trading.error.unknown_listing");
        ServerPlayer seller = server.getPlayerList().getPlayer(listing.sellerId());
        if (!listing.virtualSupply() && seller == null) {
            return Result.fail("command.xero_trading.error.offline_real_listing");
        }
        TradingListing removed = market.removeListing(listing.id());
        if (removed == null) return Result.fail("command.xero_trading.error.unknown_listing");
        if (RecipeWorldMarketData.isRecipeListing(removed)) RecipeWorldMarketData.get(server).removeSupply(removed.id());
        if (!removed.virtualSupply()) giveOrDrop(seller, removed.stack().copy());
        return Result.ok("command.xero_trading.down.success");
    }

    public static int clearAll(MinecraftServer server) {
        TradingMarketData market = TradingMarketData.get(server);
        RecipeWorldMarketData.get(server).clearSupply();
        int count = 0;
        for (TradingListing listing : market.removeAllListings()) {
            count++;
            if (!listing.virtualSupply()) {
                ServerPlayer seller = server.getPlayerList().getPlayer(listing.sellerId());
                if (seller != null) giveOrDrop(seller, listing.stack().copy());
            }
        }
        return count;
    }

    public static Result relist(ServerPlayer seller, UUID listingId) {
        TradingMarketData market = TradingMarketData.get(seller.server);
        market.claimUnresolvedAccount(seller.getUUID(), seller.getGameProfile().getName());
        TradingListing listing = market.getListing(listingId);
        if (listing == null) return Result.fail("market.xero_delta.error.stale");
        if (!listing.sellerId().equals(seller.getUUID())) {
            return Result.fail("market.xero_delta.error.not_owner");
        }
        String blockedReason = listingBlockedReason(seller.server, listing.stack());
        if (!blockedReason.isBlank()) return Result.fail(blockedReason);
        long now = seller.server.overworld().getGameTime();
        if (!TradingRules.isExpired(listing.createdAt(), now, listing.durationTicks())) {
            return Result.fail("market.xero_delta.error.not_expired");
        }
        market.putListing(new TradingListing(listing.id(), listing.sellerId(), listing.sellerName(),
            listing.stack().copy(), listing.price(), listing.itemValue(), now, "",
            listing.virtualSupply(), listing.durationTicks()));
        return Result.ok("market.xero_delta.success.relisted");
    }

    public static Result relist(ServerPlayer seller, UUID listingId, int amount,
                                long requestedPrice, int durationDays) {
        TradingMarketData market = TradingMarketData.get(seller.server);
        market.claimUnresolvedAccount(seller.getUUID(), seller.getGameProfile().getName());
        TradingListing listing = market.getListing(listingId);
        if (listing == null) return Result.fail("market.xero_delta.error.stale");
        if (!listing.sellerId().equals(seller.getUUID())) {
            return Result.fail("market.xero_delta.error.not_owner");
        }
        String blockedReason = listingBlockedReason(seller.server, listing.stack());
        if (!blockedReason.isBlank()) return Result.fail(blockedReason);
        long now = seller.server.overworld().getGameTime();
        if (!TradingRules.isExpired(listing.createdAt(), now, listing.durationTicks())) {
            return Result.fail("market.xero_delta.error.not_expired");
        }
        int currentAmount = listing.stack().getCount();
        if (amount <= 0 || amount > currentAmount
            || amount > TradingRules.maxAmountPerListing(listing.stack().getMaxStackSize())) {
            return Result.fail("market.xero_delta.error.amount");
        }
        if (durationDays <= 0 || durationDays > market.maxListingDays()) {
            return Result.fail("market.xero_delta.error.duration_limit");
        }
        long selectedItemValue = proportionalCeil(listing.itemValue(), amount, currentAmount);
        if (!TradingRules.isListingPriceAllowed(selectedItemValue, requestedPrice)) {
            return Result.fail("market.xero_delta.error.price_limit");
        }
        ItemStack relistedStack = listing.stack().copyWithCount(amount);
        TradingListing relisted = new TradingListing(listing.id(), listing.sellerId(), listing.sellerName(),
            relistedStack, requestedPrice, selectedItemValue, now, "", listing.virtualSupply(),
            TradingRules.listingLifetimeTicks(durationDays));
        if (market.putListing(relisted) == null) {
            return Result.fail("market.xero_delta.error.listing_limit");
        }
        if (!listing.virtualSupply() && amount < currentAmount) {
            giveOrDrop(seller, listing.stack().copyWithCount(currentAmount - amount));
        }
        return Result.ok("market.xero_delta.success.relisted");
    }

    public static Result relistByPublicId(MinecraftServer server, String publicId) {
        TradingMarketData market = TradingMarketData.get(server);
        TradingListing listing = market.getListing(publicId);
        if (listing == null) return Result.fail("command.xero_trading.error.unknown_listing");
        String blockedReason = listingBlockedReason(server, listing.stack());
        if (!blockedReason.isBlank()) return Result.fail(blockedReason);
        market.putListing(new TradingListing(listing.id(), listing.sellerId(), listing.sellerName(),
            listing.stack().copy(), listing.price(), listing.itemValue(), server.overworld().getGameTime(),
            "", listing.virtualSupply(), listing.durationTicks()));
        return Result.ok("command.xero_trading.relist.success");
    }

    public static Result recycle(ServerPlayer player, String sourceId, int amount) {
        if (amount <= 0 || amount > 99_999) return Result.fail("recycle.xero_delta.error.amount");
        TradingInventorySource source = TradingInventorySources.find(player, sourceId);
        if (source == null) return Result.fail("market.xero_delta.error.source");
        if (!source.sellable()) return Result.fail(source.blockedReason());
        ItemStack simulated = source.extract(amount, true);
        if (simulated.isEmpty() || simulated.getCount() != amount) {
            return Result.fail("market.xero_delta.error.source");
        }
        ItemStack unitStack = simulated.copyWithCount(1);
        if (!TradingItemEligibility.canRecycle(player.server, unitStack)) {
            return Result.fail("recycle.xero_delta.error.unsupported_item");
        }
        long configuredValue = TradingRules.normalizeItemValue(
            ModDataStorage.get(player.server.overworld()).getPriceFor(unitStack));
        long unitValue = BuiltinRecycleValueCatalog.resolve(
            BuiltInRegistries.ITEM.getKey(unitStack.getItem()).toString(), configuredValue);
        long totalValue = safeMultiply(unitValue, amount);
        TradingMarketData market = TradingMarketData.get(player.server);
        market.claimUnresolvedAccount(player.getUUID(), player.getGameProfile().getName());
        if (market.balance(player.getUUID()) > TradingRules.MAX_CURRENCY - totalValue) {
            return Result.fail("recycle.xero_delta.error.wallet_full");
        }
        ItemStack extracted = source.extract(amount, false);
        if (extracted.isEmpty() || extracted.getCount() != amount
            || !ItemStack.isSameItemSameComponents(simulated, extracted)) {
            if (!extracted.isEmpty()) giveOrDrop(player, extracted);
            return Result.fail("market.xero_delta.error.source_changed");
        }
        long credited = market.credit(player.getUUID(), totalValue);
        market.creditWorld(totalValue);
        player.getInventory().setChanged();
        return Result.ok("recycle.xero_delta.success", credited);
    }

    private static String listingBlockedReason(MinecraftServer server, ItemStack stack) {
        if (BoundItemPolicy.isBound(stack)) return "market.xero_delta.error.bound_item";
        if (TradingItemEligibility.isKnifeSkin(stack)) return "market.xero_delta.error.knife_skin";
        return TradingItemEligibility.canList(server, stack)
            ? "" : "market.xero_delta.error.unsupported_item";
    }

    static long safeMultiply(long value, int count) {
        if (value <= 0 || count <= 0) return 0;
        if (value > TradingRules.MAX_CURRENCY / count) return TradingRules.MAX_CURRENCY;
        return value * count;
    }

    static long proportionalCeil(long total, int amount, int totalAmount) {
        if (total <= 0 || amount <= 0 || totalAmount <= 0) return 0L;
        if (amount >= totalAmount) return total;
        long quotient = total / totalAmount;
        long remainder = total % totalAmount;
        long base = safeMultiply(quotient, amount);
        long remainderPart = (remainder * amount + totalAmount - 1L) / totalAmount;
        return Math.min(total, base + remainderPart);
    }

    private static TradingListing lowestActiveListing(TradingMarketData market, ItemStack stack, long gameTime,
                                                       UUID buyerId) {
        TradingListing lowest = null;
        for (TradingListing listing : market.listings()) {
            if (TradingRules.isExpired(listing.createdAt(), gameTime, listing.durationTicks())
                || listing.sellerId().equals(buyerId)
                || !ItemStack.isSameItemSameComponents(stack, listing.stack())) continue;
            if (lowest == null || unitPrice(listing) < unitPrice(lowest)
                || unitPrice(listing) == unitPrice(lowest) && listing.createdAt() < lowest.createdAt()) {
                lowest = listing;
            }
        }
        return lowest;
    }

    private static long unitPrice(TradingListing listing) {
        int count = Math.max(1, listing.stack().getCount());
        return Math.max(1L, (listing.price() + count - 1L) / count);
    }

    private static boolean canFit(ServerPlayer player, ItemStack incoming) {
        int remaining = incoming.getCount();
        int slotCapacity = Math.max(1, Math.min(incoming.getMaxStackSize(), player.getInventory().getMaxStackSize()));
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                remaining -= slotCapacity;
                if (remaining <= 0) return true;
                continue;
            }
            if (ItemStack.isSameItemSameComponents(stack, incoming)) {
                remaining -= Math.max(0, Math.min(stack.getMaxStackSize(), player.getInventory().getMaxStackSize())
                    - stack.getCount());
                if (remaining <= 0) return true;
            }
        }
        return false;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        giveOrDrop(player, stack, false);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack, boolean marketPurchase) {
        if (marketPurchase) {
            stack = com.xtdpotato.xero_delta.grid.WarehouseTransferService.storePurchase(player, stack);
        }
        int remainingCount = stack.getCount();
        int chunkSize = Math.max(1, stack.getMaxStackSize());
        while (remainingCount > 0) {
            ItemStack remaining = stack.copyWithCount(Math.min(chunkSize, remainingCount));
            player.getInventory().add(remaining);
            if (!remaining.isEmpty()) {
                var dropped = player.drop(remaining, false);
                if (marketPurchase && dropped != null)
                    dropped.getPersistentData().putBoolean(RecipeWorldMarket.PURCHASE_DROP, true);
            }
            remainingCount -= Math.min(chunkSize, remainingCount);
        }
        player.getInventory().setChanged();
    }
}
