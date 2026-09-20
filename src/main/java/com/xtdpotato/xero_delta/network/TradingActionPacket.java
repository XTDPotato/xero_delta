package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.trading.TradingMarketData;
import com.xtdpotato.xero_delta.trading.TradingMarketService;
import com.xtdpotato.xero_delta.trading.TradingRules;
import com.xtdpotato.xero_delta.trading.TradingScreenOpener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record TradingActionPacket(Action action, String sourceId, int amount, long price,
                                  UUID listingId, String itemId, int durationDays) implements CustomPacketPayload {
    public static final String SOURCE_LIST_SEPARATOR = "\u001F";
    public enum Action { REFRESH, LIST, CREATIVE_LIST, CREATIVE_EDIT, BUY, CANCEL, RELIST, FAVORITE, RECYCLE, UNLOCK_LISTING_SLOT,
        DELETE_HISTORY, DELETE_ALL_HISTORY,
        OPEN_OPERATOR, OPEN_RECYCLING, OPEN_MARKET, OPEN_MARKET_DETAIL }

    private static final UUID EMPTY_UUID = new UUID(0L, 0L);
    public static final Type<TradingActionPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "trading_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradingActionPacket> STREAM_CODEC =
        StreamCodec.of((buf, packet) -> {
            buf.writeEnum(packet.action);
            buf.writeUtf(packet.sourceId == null ? "" : packet.sourceId, 8192);
            buf.writeVarInt(Math.max(0, packet.amount));
            buf.writeLong(packet.price);
            buf.writeUUID(packet.listingId == null ? EMPTY_UUID : packet.listingId);
            buf.writeUtf(packet.itemId == null ? "" : packet.itemId, 8192);
            buf.writeVarInt(Math.max(0, packet.durationDays));
        }, buf -> new TradingActionPacket(buf.readEnum(Action.class), buf.readUtf(8192), buf.readVarInt(),
            buf.readLong(), buf.readUUID(), buf.readUtf(8192), buf.readVarInt()));

    public static TradingActionPacket refresh() {
        return new TradingActionPacket(Action.REFRESH, "", 0, 0, EMPTY_UUID, "", 0);
    }

    public static TradingActionPacket list(String sourceId, int amount, long price) {
        return list(sourceId, amount, price, TradingRules.DEFAULT_LISTING_DAYS);
    }

    public static TradingActionPacket list(String sourceId, int amount, long price, int durationDays) {
        return new TradingActionPacket(Action.LIST, sourceId, amount, price, EMPTY_UUID, "", durationDays);
    }

    public static TradingActionPacket creativeList(String itemId, int amount, long price, int durationDays) {
        return new TradingActionPacket(Action.CREATIVE_LIST, "", Math.max(1, amount), price,
            EMPTY_UUID, itemId == null ? "" : itemId, Math.max(1, durationDays));
    }

    public static TradingActionPacket creativeEdit(UUID listingId, int amount, long price, int durationDays) {
        return new TradingActionPacket(Action.CREATIVE_EDIT, "", Math.max(1, amount), price,
            listingId, "", Math.max(1, durationDays));
    }

    public static TradingActionPacket listing(Action action, UUID listingId) {
        return new TradingActionPacket(action, "", action == Action.BUY ? 1 : 0, 0, listingId, "", 0);
    }

    public static TradingActionPacket relist(UUID listingId, int amount, long price, int durationDays) {
        return new TradingActionPacket(Action.RELIST, "", Math.max(1, amount), price,
            listingId, "", Math.max(1, durationDays));
    }

    public static TradingActionPacket buy(UUID listingId, int amount) {
        return new TradingActionPacket(Action.BUY, "", Math.max(1, amount), 0, listingId, "", 0);
    }

    public static TradingActionPacket deleteHistory(UUID listingId, long completedEpochMillis) {
        return new TradingActionPacket(Action.DELETE_HISTORY, "", 0, completedEpochMillis,
            listingId, "", 0);
    }

    public static TradingActionPacket deleteAllHistory() {
        return new TradingActionPacket(Action.DELETE_ALL_HISTORY, "", 0, 0, EMPTY_UUID, "", 0);
    }

    public static TradingActionPacket favorite(String itemId) {
        return new TradingActionPacket(Action.FAVORITE, "", 0, 0, EMPTY_UUID, itemId, 0);
    }

    public static TradingActionPacket recycle(String sourceId, int amount) {
        return new TradingActionPacket(Action.RECYCLE, sourceId, amount, 0, EMPTY_UUID, "", 0);
    }

    public static TradingActionPacket unlockListingSlot() {
        return new TradingActionPacket(Action.UNLOCK_LISTING_SLOT, "", 0, 0, EMPTY_UUID, "", 0);
    }

    public static TradingActionPacket openOperator() { return openOperator(""); }

    public static TradingActionPacket openOperator(String sourceId) {
        return new TradingActionPacket(Action.OPEN_OPERATOR, sourceId, 0, 0, EMPTY_UUID, "", 0);
    }

    public static TradingActionPacket openRecycling(String sourceId) {
        return new TradingActionPacket(Action.OPEN_RECYCLING, sourceId, 0, 0, EMPTY_UUID, "", 0);
    }

    public static TradingActionPacket openMarketDetail(UUID listingId) {
        return new TradingActionPacket(Action.OPEN_MARKET_DETAIL, "", 0, 0, listingId, "", 0);
    }

    public static TradingActionPacket openMarket(String tab) {
        return new TradingActionPacket(Action.OPEN_MARKET, tab == null ? "buy" : tab,
            0, 0, EMPTY_UUID, "", 0);
    }

    public static void handle(TradingActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (packet.action == Action.OPEN_OPERATOR) {
                TradingScreenOpener.openOperator(player, packet.sourceId);
                return;
            }
            if (packet.action == Action.OPEN_RECYCLING) {
                TradingScreenOpener.openRecycling(player, packet.sourceId);
                return;
            }
            if (packet.action == Action.OPEN_MARKET_DETAIL) {
                TradingScreenOpener.openMarketDetail(player, packet.listingId);
                return;
            }
            if (packet.action == Action.OPEN_MARKET) {
                TradingScreenOpener.openMarket(player, "tab:" + packet.sourceId);
                return;
            }
            TradingMarketService.Result result = switch (packet.action) {
                case REFRESH -> TradingMarketService.Result.ok("");
                case LIST -> TradingMarketService.list(player, packet.sourceId, packet.amount, packet.price,
                    packet.durationDays);
                case CREATIVE_LIST -> TradingMarketService.creativeList(player, packet.itemId, packet.amount,
                    packet.price, packet.durationDays);
                case CREATIVE_EDIT -> TradingMarketService.creativeEdit(player, packet.listingId,
                    packet.amount, packet.price, packet.durationDays);
                case BUY -> TradingMarketService.buy(player, packet.listingId, packet.amount);
                case CANCEL -> TradingMarketService.cancel(player, packet.listingId);
                case RELIST -> packet.amount > 0 && packet.durationDays > 0
                    ? TradingMarketService.relist(player, packet.listingId, packet.amount,
                        packet.price, packet.durationDays)
                    : TradingMarketService.relist(player, packet.listingId);
                case FAVORITE -> {
                    TradingMarketData market = TradingMarketData.get(player.server);
                    market.claimUnresolvedAccount(player.getUUID(), player.getGameProfile().getName());
                    market.toggleFavorite(player.getUUID(), packet.itemId);
                    yield TradingMarketService.Result.ok("");
                }
                case RECYCLE -> TradingMarketService.recycle(player, packet.sourceId, packet.amount);
                case UNLOCK_LISTING_SLOT -> TradingMarketService.unlockListingSlot(player);
                case DELETE_HISTORY -> TradingMarketService.deleteHistory(player, packet.listingId, packet.price);
                case DELETE_ALL_HISTORY -> TradingMarketService.deleteAllHistory(player);
                case OPEN_OPERATOR, OPEN_RECYCLING, OPEN_MARKET, OPEN_MARKET_DETAIL ->
                    throw new IllegalStateException("Handled before switch");
            };
            PacketDistributor.sendToPlayer(player,
                TradingSyncPacket.snapshot(player, result.message(), result.success(), result.value()));
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
