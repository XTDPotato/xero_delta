package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.TradingClientState;
import com.xtdpotato.xero_delta.trading.TradingInventorySource;
import com.xtdpotato.xero_delta.trading.TradingInventorySources;
import com.xtdpotato.xero_delta.trading.TradingItemEligibility;
import com.xtdpotato.xero_delta.trading.TradingListing;
import com.xtdpotato.xero_delta.trading.TradingMarketData;
import com.xtdpotato.xero_delta.trading.TradingRecord;
import com.xtdpotato.xero_delta.trading.TradingRules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record TradingSyncPacket(long balance, int maxListingDays, int maxMarketListings,
                                int playerListingSlots, int maxPlayerListingSlots,
                                int listingSlotLevelCost,
                                List<ListingView> listings, List<SourceView> sources,
                                List<SourceGroupView> sourceGroups,
                                List<RecordView> history, Set<String> favorites,
                                String message, boolean success, long messageValue,
                                String contextSourceId) implements CustomPacketPayload {
    public static final Type<TradingSyncPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "trading_sync"));

    public record ListingView(UUID id, UUID sellerId, String sellerName, ItemStack stack,
                              long price, long itemValue, long createdAt, long expiresAt, boolean expired,
                              String publicId, boolean virtualSupply) {
    }

    public record SourceView(String id, String label, String groupId, ItemStack stack,
                             int availableCount, boolean sellable, String blockedReason) {
    }

    public record SourceGroupView(String id, ItemStack icon, int ordinal) {
    }

    public record RecordView(UUID listingId, UUID sellerId, String sellerName, UUID buyerId,
                             String buyerName, ItemStack stack, long price, long completedAt,
                             String publicId, long completedEpochMillis) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TradingSyncPacket> STREAM_CODEC =
        StreamCodec.of(TradingSyncPacket::encode, TradingSyncPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, TradingSyncPacket packet) {
        buf.writeLong(packet.balance);
        buf.writeVarInt(packet.maxListingDays);
        buf.writeVarInt(packet.maxMarketListings);
        buf.writeVarInt(packet.playerListingSlots);
        buf.writeVarInt(packet.maxPlayerListingSlots);
        buf.writeVarInt(packet.listingSlotLevelCost);
        buf.writeVarInt(Math.min(packet.listings.size(), TradingRules.MAX_SYNC_LISTINGS));
        for (ListingView value : packet.listings.stream().limit(TradingRules.MAX_SYNC_LISTINGS).toList()) {
            buf.writeUUID(value.id); buf.writeUUID(value.sellerId); buf.writeUtf(value.sellerName, 64);
            buf.writeUtf(value.publicId, 32); buf.writeBoolean(value.virtualSupply);
            encodeStackWithCount(buf, value.stack);
            buf.writeLong(value.price); buf.writeLong(value.itemValue); buf.writeLong(value.createdAt);
            buf.writeLong(value.expiresAt); buf.writeBoolean(value.expired);
        }
        buf.writeVarInt(Math.min(packet.sources.size(), 512));
        for (SourceView value : packet.sources.stream().limit(512).toList()) {
            buf.writeUtf(value.id, 256); buf.writeUtf(value.label, 64); buf.writeUtf(value.groupId, 256);
            encodeStackWithCount(buf, value.stack);
            buf.writeVarInt(value.availableCount); buf.writeBoolean(value.sellable);
            buf.writeUtf(value.blockedReason, 128);
        }
        buf.writeVarInt(Math.min(packet.sourceGroups.size(), 128));
        for (SourceGroupView value : packet.sourceGroups.stream().limit(128).toList()) {
            buf.writeUtf(value.id, 256);
            encodeStackWithCount(buf, value.icon);
            buf.writeVarInt(value.ordinal);
        }
        buf.writeVarInt(Math.min(packet.history.size(), 100));
        for (RecordView value : packet.history.stream().limit(100).toList()) {
            buf.writeUUID(value.listingId); buf.writeUUID(value.sellerId); buf.writeUtf(value.sellerName, 64);
            buf.writeUUID(value.buyerId); buf.writeUtf(value.buyerName, 64);
            encodeStackWithCount(buf, value.stack);
            buf.writeLong(value.price); buf.writeLong(value.completedAt);
            buf.writeUtf(value.publicId, 32); buf.writeLong(value.completedEpochMillis);
        }
        buf.writeVarInt(Math.min(packet.favorites.size(), 256));
        packet.favorites.stream().limit(256).forEach(value -> buf.writeUtf(value, 8192));
        buf.writeUtf(packet.message == null ? "" : packet.message, 256);
        buf.writeBoolean(packet.success);
        buf.writeLong(packet.messageValue);
        buf.writeUtf(packet.contextSourceId == null ? "" : packet.contextSourceId, 256);
    }

    private static TradingSyncPacket decode(RegistryFriendlyByteBuf buf) {
        long balance = buf.readLong();
        int maxListingDays = buf.readVarInt();
        int maxMarketListings = buf.readVarInt();
        int playerListingSlots = buf.readVarInt();
        int maxPlayerListingSlots = buf.readVarInt();
        int listingSlotLevelCost = buf.readVarInt();
        int listingCount = Math.min(buf.readVarInt(), TradingRules.MAX_SYNC_LISTINGS);
        List<ListingView> listings = new ArrayList<>(listingCount);
        for (int i = 0; i < listingCount; i++) {
            UUID id = buf.readUUID();
            UUID sellerId = buf.readUUID();
            String sellerName = buf.readUtf(64);
            String publicId = buf.readUtf(32);
            boolean virtualSupply = buf.readBoolean();
            listings.add(new ListingView(id, sellerId, sellerName,
                decodeStackWithCount(buf), buf.readLong(), buf.readLong(), buf.readLong(),
                buf.readLong(), buf.readBoolean(), publicId, virtualSupply));
        }
        int sourceCount = Math.min(buf.readVarInt(), 512);
        List<SourceView> sources = new ArrayList<>(sourceCount);
        for (int i = 0; i < sourceCount; i++) {
            sources.add(new SourceView(buf.readUtf(256), buf.readUtf(64), buf.readUtf(256),
                decodeStackWithCount(buf), buf.readVarInt(), buf.readBoolean(), buf.readUtf(128)));
        }
        int sourceGroupCount = Math.min(buf.readVarInt(), 128);
        List<SourceGroupView> sourceGroups = new ArrayList<>(sourceGroupCount);
        for (int i = 0; i < sourceGroupCount; i++) {
            sourceGroups.add(new SourceGroupView(buf.readUtf(256),
                decodeStackWithCount(buf), buf.readVarInt()));
        }
        int historyCount = Math.min(buf.readVarInt(), 100);
        List<RecordView> history = new ArrayList<>(historyCount);
        for (int i = 0; i < historyCount; i++) {
            history.add(new RecordView(buf.readUUID(), buf.readUUID(), buf.readUtf(64), buf.readUUID(),
                buf.readUtf(64), decodeStackWithCount(buf), buf.readLong(), buf.readLong(),
                buf.readUtf(32), buf.readLong()));
        }
        int favoriteCount = Math.min(buf.readVarInt(), 256);
        Set<String> favorites = new HashSet<>();
        for (int i = 0; i < favoriteCount; i++) favorites.add(buf.readUtf(8192));
        return new TradingSyncPacket(balance, maxListingDays, maxMarketListings,
            playerListingSlots, maxPlayerListingSlots, listingSlotLevelCost,
            List.copyOf(listings), List.copyOf(sources), List.copyOf(sourceGroups), List.copyOf(history),
            Set.copyOf(favorites), buf.readUtf(256), buf.readBoolean(), buf.readLong(), buf.readUtf(256));
    }

    private static void encodeStackWithCount(RegistryFriendlyByteBuf buf, ItemStack stack) {
        int count = stack == null || stack.isEmpty() ? 0 : Math.max(1, stack.getCount());
        buf.writeVarInt(count);
        ItemStack sample = count == 0 ? ItemStack.EMPTY : stack.copyWithCount(1);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, sample);
    }

    private static ItemStack decodeStackWithCount(RegistryFriendlyByteBuf buf) {
        int count = Math.max(0, buf.readVarInt());
        ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        if (!stack.isEmpty() && count > 0) stack.setCount(count);
        return stack;
    }

    public static TradingSyncPacket snapshot(ServerPlayer player, String message, boolean success) {
        return snapshot(player, message, success, 0L, "");
    }

    public static TradingSyncPacket snapshot(ServerPlayer player, String message, boolean success, long messageValue) {
        return snapshot(player, message, success, messageValue, "");
    }

    public static TradingSyncPacket snapshot(ServerPlayer player, String message, boolean success, long messageValue,
                                             String contextSourceId) {
        TradingMarketData market = TradingMarketData.get(player.server);
        market.claimUnresolvedAccount(player.getUUID(), player.getGameProfile().getName());
        long gameTime = player.server.overworld().getGameTime();
        List<ListingView> listings = market.listings().stream()
            .map(value -> listingView(value, gameTime)).toList();
        TradingInventorySource.Catalog sourceCatalog = TradingInventorySources.catalog(player);
        List<SourceView> sources = sourceCatalog.sources().stream()
            .filter(value -> TradingItemEligibility.canRecycle(player.server, value.stack()))
            .map(TradingSyncPacket::sourceView).toList();
        List<SourceGroupView> sourceGroups = sourceCatalog.groups().stream()
            .map(value -> new SourceGroupView(value.id(), value.icon(), value.ordinal())).toList();
        List<RecordView> history = market.historyFor(player.getUUID()).stream()
            .map(TradingSyncPacket::recordView).toList();
        return new TradingSyncPacket(market.balance(player.getUUID()), market.maxListingDays(),
            market.maxMarketListings(), market.playerListingSlots(player.getUUID()),
            market.maxPlayerListingSlots(), market.listingSlotLevelCost(), listings, sources, sourceGroups, history,
            market.favorites(player.getUUID()), message == null ? "" : message, success, messageValue,
            contextSourceId == null ? "" : contextSourceId);
    }

    private static ListingView listingView(TradingListing value, long gameTime) {
        return new ListingView(value.id(), value.sellerId(), value.sellerName(), value.stack().copy(),
            value.price(), value.itemValue(), value.createdAt(),
            TradingRules.expiresAt(value.createdAt(), value.durationTicks()),
            TradingRules.isExpired(value.createdAt(), gameTime, value.durationTicks()),
            value.publicId(), value.virtualSupply());
    }

    private static SourceView sourceView(TradingInventorySource.View value) {
        return new SourceView(value.id(), value.label(), value.groupId(), value.stack().copy(),
            value.availableCount(), value.sellable(), value.blockedReason());
    }

    private static RecordView recordView(TradingRecord value) {
        return new RecordView(value.listingId(), value.sellerId(), value.sellerName(), value.buyerId(),
            value.buyerName(), value.stack().copy(), value.price(), value.completedAt(), value.publicId(),
            value.completedEpochMillis());
    }

    public static void handle(TradingSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> TradingClientState.INSTANCE.update(packet));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
