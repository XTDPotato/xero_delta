package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.network.StaminaStatePacket;

/** Latest server-authoritative stamina snapshot used only for presentation. */
public final class StaminaClientState {
    public static final StaminaClientState INSTANCE = new StaminaClientState();
    private volatile float current = 180.0F;
    private volatile float maximum = 180.0F;
    private volatile boolean exhausted;

    private StaminaClientState() {}

    public void update(StaminaStatePacket packet) {
        maximum = Math.max(1.0F, packet.maximum());
        current = Math.max(0.0F, Math.min(maximum, packet.current()));
        exhausted = packet.exhausted();
    }

    public float current() { return current; }
    public float maximum() { return maximum; }
    public boolean exhausted() { return exhausted; }
    public float fraction() { return Math.max(0.0F, Math.min(1.0F, current / maximum)); }
}
