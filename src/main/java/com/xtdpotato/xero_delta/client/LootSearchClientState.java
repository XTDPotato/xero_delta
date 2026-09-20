package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.network.LootSearchStatePacket;
import net.minecraft.Util;

import java.util.HashSet;
import java.util.Set;

/** Client-only rendering snapshot; slot access remains authoritative on the server. */
public final class LootSearchClientState {
    public static final LootSearchClientState INSTANCE = new LootSearchClientState();

    private int containerId = -1;
    private Set<Integer> hiddenSlots = Set.of();
    private int currentSlot = -1;
    private int elapsedTicks;
    private int durationTicks;
    private Set<Integer> teammateSearchingSlots = Set.of();
    private long receivedAtMillis;

    private LootSearchClientState() {
    }

    public void apply(LootSearchStatePacket packet) {
        containerId = packet.containerId();
        hiddenSlots = Set.copyOf(new HashSet<>(packet.hiddenSlots()));
        currentSlot = packet.currentSlot();
        elapsedTicks = Math.max(0, packet.elapsedTicks());
        durationTicks = Math.max(0, packet.durationTicks());
        teammateSearchingSlots = Set.copyOf(packet.teammateSearchingSlots());
        receivedAtMillis = Util.getMillis();
    }

    public boolean isActiveFor(int menuContainerId) {
        return containerId == menuContainerId && !hiddenSlots.isEmpty();
    }

    public boolean isHidden(int menuContainerId, int slotId) {
        return containerId == menuContainerId && hiddenSlots.contains(slotId);
    }

    public boolean isCurrent(int menuContainerId, int slotId) {
        return containerId == menuContainerId && currentSlot == slotId;
    }

    public boolean isTeammateSearching(int menuContainerId, int slotId) {
        return containerId == menuContainerId && teammateSearchingSlots.contains(slotId);
    }

    public double progress() {
        if (durationTicks <= 0) return 1.0D;
        double clientTicks = Math.max(0L, Util.getMillis() - receivedAtMillis) / 50.0D;
        return Math.max(0.0D, Math.min(1.0D,
            (elapsedTicks + clientTicks) / durationTicks));
    }

    public int currentSlot() {
        return currentSlot;
    }
}
