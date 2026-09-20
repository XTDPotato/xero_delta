package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.network.TradingSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TradingClientState {
    public static final TradingClientState INSTANCE = new TradingClientState();

    private volatile long balance;
    private volatile int maxListingDays = com.xtdpotato.xero_delta.trading.TradingRules.DEFAULT_LISTING_DAYS;
    private volatile int maxMarketListings = com.xtdpotato.xero_delta.trading.TradingRules.DEFAULT_MAX_MARKET_LISTINGS;
    private volatile int playerListingSlots = com.xtdpotato.xero_delta.trading.TradingRules.DEFAULT_PLAYER_LISTING_SLOTS;
    private volatile int maxPlayerListingSlots = com.xtdpotato.xero_delta.trading.TradingRules.DEFAULT_MAX_PLAYER_LISTING_SLOTS;
    private volatile int listingSlotLevelCost = com.xtdpotato.xero_delta.trading.TradingRules.DEFAULT_LISTING_SLOT_LEVEL_COST;
    private volatile List<TradingSyncPacket.ListingView> listings = List.of();
    private volatile List<TradingSyncPacket.SourceView> sources = List.of();
    private volatile List<TradingSyncPacket.SourceGroupView> sourceGroups = List.of();
    private volatile List<TradingSyncPacket.RecordView> history = List.of();
    private volatile Set<String> favorites = Set.of();
    private volatile String message = "";
    private volatile boolean success = true;
    private volatile long messageValue;
    private volatile long revision;
    private String pendingSourceId = "";
    private List<ItemStack> pendingCreativeDrafts = List.of();
    private double pendingCursorX;
    private double pendingCursorY;
    private boolean restoreCursor;
    private final TradingNavigationHistory screenHistory = new TradingNavigationHistory();

    private TradingClientState() {
    }

    public synchronized void update(TradingSyncPacket packet) {
        balance = packet.balance();
        maxListingDays = packet.maxListingDays();
        maxMarketListings = packet.maxMarketListings();
        playerListingSlots = packet.playerListingSlots();
        maxPlayerListingSlots = packet.maxPlayerListingSlots();
        listingSlotLevelCost = packet.listingSlotLevelCost();
        listings = List.copyOf(packet.listings());
        sources = List.copyOf(packet.sources());
        sourceGroups = List.copyOf(packet.sourceGroups());
        history = List.copyOf(packet.history());
        favorites = Set.copyOf(packet.favorites());
        message = packet.message();
        success = packet.success();
        messageValue = packet.messageValue();
        String context = packet.contextSourceId();
        screenHistory.acceptContext(context);
        if (context.startsWith("root:")) {
            pendingSourceId = "";
        } else if (!context.isBlank()) {
            pendingSourceId = context;
        }
        revision++;
    }

    public long balance() { return balance; }
    public int maxListingDays() { return maxListingDays; }
    public int maxMarketListings() { return maxMarketListings; }
    public int playerListingSlots() { return playerListingSlots; }
    public int maxPlayerListingSlots() { return maxPlayerListingSlots; }
    public int listingSlotLevelCost() { return listingSlotLevelCost; }
    public List<TradingSyncPacket.ListingView> listings() { return listings; }
    public List<TradingSyncPacket.SourceView> sources() { return sources; }
    public List<TradingSyncPacket.SourceGroupView> sourceGroups() { return sourceGroups; }
    public List<TradingSyncPacket.RecordView> history() { return history; }
    public Set<String> favorites() { return favorites; }

    /** Immediate client feedback while the authoritative favorite packet is in flight. */
    public synchronized void toggleFavoriteOptimistic(String key) {
        if (key == null || key.isBlank()) return;
        Set<String> updated = new HashSet<>(favorites);
        if (!updated.remove(key)) updated.add(key);
        favorites = Set.copyOf(updated);
        revision++;
    }
    public String message() { return message; }
    public boolean success() { return success; }
    public long messageValue() { return messageValue; }
    public long revision() { return revision; }

    public synchronized String consumePendingSourceId() {
        String value = pendingSourceId;
        pendingSourceId = "";
        return value;
    }

    public synchronized void queueCreativeDrafts(List<ItemStack> stacks) {
        pendingCreativeDrafts = stacks == null ? List.of() : stacks.stream()
            .filter(stack -> stack != null && !stack.isEmpty())
            .map(stack -> stack.copyWithCount(1)).toList();
    }

    public synchronized List<ItemStack> consumeCreativeDrafts() {
        List<ItemStack> values = pendingCreativeDrafts;
        pendingCreativeDrafts = List.of();
        return values;
    }

    public synchronized void rememberCursor() {
        Minecraft minecraft = Minecraft.getInstance();
        pendingCursorX = minecraft.mouseHandler.xpos();
        pendingCursorY = minecraft.mouseHandler.ypos();
        restoreCursor = true;
    }

    public synchronized void rememberTransition(String currentScreen) {
        rememberCursor();
        screenHistory.remember(currentScreen);
    }

    public synchronized String popPreviousScreen() {
        return screenHistory.pop();
    }

    public synchronized void restoreCursor() {
        if (!restoreCursor) return;
        double x = pendingCursorX;
        double y = pendingCursorY;
        restoreCursor = false;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), x, y));
    }
}
