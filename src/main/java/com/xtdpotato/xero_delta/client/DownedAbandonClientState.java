package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.data.DownedRules;
import net.minecraft.Util;

/** Local presentation state for the server-authoritative hold-to-abandon action. */
public final class DownedAbandonClientState {
    public static final DownedAbandonClientState INSTANCE = new DownedAbandonClientState();
    private long startedAtMs = -1L;

    private DownedAbandonClientState() {
    }

    public boolean start() {
        if (startedAtMs >= 0L) return false;
        startedAtMs = Util.getMillis();
        return true;
    }

    public boolean cancel() {
        if (startedAtMs < 0L) return false;
        startedAtMs = -1L;
        return true;
    }

    public boolean holding() {
        return startedAtMs >= 0L;
    }

    public float progress() {
        if (startedAtMs < 0L) return 0.0F;
        long elapsedMs = Math.max(0L, Util.getMillis() - startedAtMs);
        int heldTicks = (int) Math.min(Integer.MAX_VALUE, elapsedMs / 50L);
        return DownedRules.abandonProgress(heldTicks);
    }
}