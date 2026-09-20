package com.xtdpotato.xero_delta.data;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class ServerGridRotationState {
    private static final Map<ServerPlayer, Boolean> MANUAL_PRIORITY =
        Collections.synchronizedMap(new WeakHashMap<>());

    private ServerGridRotationState() {
    }

    public static void setManualPriority(ServerPlayer player, boolean enabled) {
        if (enabled) MANUAL_PRIORITY.put(player, true);
        else MANUAL_PRIORITY.remove(player);
    }

    public static boolean hasManualPriority(ServerPlayer player) {
        return MANUAL_PRIORITY.containsKey(player);
    }
}
