package com.xtdpotato.xero_delta.client;

import net.minecraft.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Short-lived rescue-request pulses keyed by the requesting player's UUID. */
public final class RescueRequestClientState {
    public static final RescueRequestClientState INSTANCE = new RescueRequestClientState();
    public static final long DURATION_MS = 1_200L;
    public static final long LOCAL_COOLDOWN_MS = 3_000L;
    private final Map<UUID, Pulse> pulses = new HashMap<>();
    private long nextLocalPulseAtMs;

    private RescueRequestClientState() {
    }

    public void trigger(UUID playerId, byte stage) {
        if (stage != 1 && stage != 2) return;
        pulses.put(playerId, new Pulse(stage, Util.getMillis()));
    }

    public boolean tryTriggerLocal(UUID playerId, byte stage) {
        long now = Util.getMillis();
        if (now < nextLocalPulseAtMs) return false;
        nextLocalPulseAtMs = now + LOCAL_COOLDOWN_MS;
        pulses.put(playerId, new Pulse(stage, now));
        return true;
    }

    public Sample sample(UUID playerId) {
        Pulse pulse = pulses.get(playerId);
        if (pulse == null) return Sample.NONE;
        float progress = progress(Util.getMillis() - pulse.startedAtMs);
        if (progress >= 1.0F) {
            pulses.remove(playerId);
            return Sample.NONE;
        }
        return new Sample(true, pulse.stage, progress);
    }

    static float progress(long elapsedMs) {
        return Math.max(0.0F, Math.min(1.0F, elapsedMs / (float) DURATION_MS));
    }

    private record Pulse(byte stage, long startedAtMs) {
    }

    public record Sample(boolean active, byte stage, float progress) {
        private static final Sample NONE = new Sample(false, (byte) 0, 1.0F);
    }
}
