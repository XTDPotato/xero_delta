package com.xtdpotato.xero_delta.trading;

import com.xtdpotato.xero_delta.network.TradingSyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TradingScreenOpener {
    private TradingScreenOpener() {
    }

    public static void openMarket(ServerPlayer player) {
        openMarket(player, "root:market");
    }

    public static void openMarket(ServerPlayer player, String context) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> new TradingMenu(containerId, inventory),
            Component.translatable("screen.xero_delta.trading_market")));
        sync(player, context);
    }

    public static void openMarketDetail(ServerPlayer player, ItemStack target, boolean exactComponents) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> new TradingMenu(containerId, inventory),
            Component.translatable("screen.xero_delta.trading_market")));
        TradingListing lowest = null;
        long gameTime = player.server.overworld().getGameTime();
        for (TradingListing listing : TradingMarketData.get(player.server).listings()) {
            boolean matches = exactComponents
                ? ItemStack.isSameItemSameComponents(target, listing.stack())
                : target.is(listing.stack().getItem());
            if (!matches || listing.sellerId().equals(player.getUUID())
                || TradingRules.isExpired(listing.createdAt(), gameTime)) continue;
            if (lowest == null || unitPrice(listing) < unitPrice(lowest)) lowest = listing;
        }
        String context = lowest == null ? "" : "detail-listing:" + lowest.id();
        String message = lowest == null ? "market.xero_delta.no_item_listings" : "";
        PacketDistributor.sendToPlayer(player,
            TradingSyncPacket.snapshot(player, message, lowest != null, 0L, context));
    }

    public static void openMarketDetail(ServerPlayer player, java.util.UUID listingId) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> new TradingMenu(containerId, inventory),
            Component.translatable("screen.xero_delta.trading_market")));
        TradingListing listing = TradingMarketData.get(player.server).getListing(listingId);
        String context = listing == null ? "" : "detail-listing:" + listing.id();
        String message = listing == null ? "market.xero_delta.error.stale" : "";
        PacketDistributor.sendToPlayer(player,
            TradingSyncPacket.snapshot(player, message, listing != null, 0L, context));
    }

    public static void openRecycling(ServerPlayer player) {
        openRecycling(player, "root:recycling");
    }

    public static void openRecycling(ServerPlayer player, String sourceId) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> new RecyclingMenu(containerId, inventory),
            Component.translatable("screen.xero_delta.recycling_station")));
        sync(player, sourceId);
    }

    public static void openOperator(ServerPlayer player) {
        openOperator(player, "root:operator");
    }

    public static void openOperator(ServerPlayer player, String sourceId) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> new TradingOperatorMenu(containerId, inventory),
            Component.translatable("screen.xero_delta.trading_operator")));
        sync(player, sourceId);
    }

    private static void sync(ServerPlayer player) {
        sync(player, "");
    }

    private static void sync(ServerPlayer player, String sourceId) {
        PacketDistributor.sendToPlayer(player, TradingSyncPacket.snapshot(player, "", true, 0L, sourceId));
    }

    private static long unitPrice(TradingListing listing) {
        int count = Math.max(1, listing.stack().getCount());
        return Math.max(1L, (listing.price() + count - 1L) / count);
    }
}
