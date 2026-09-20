package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.RescueHoldPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/** Tracks the local physical F hold and keeps the server session alive. */
public final class RescueHoldClientState {
    public static final RescueHoldClientState INSTANCE = new RescueHoldClientState();
    private static final int HEARTBEAT_INTERVAL_TICKS = 4;

    private boolean active;
    private int targetEntityId = -1;
    private int heartbeatTicks;

    private RescueHoldClientState() {
    }

    public boolean begin(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.screen != null
            || DownedClientState.INSTANCE.downed()
            || !(minecraft.hitResult instanceof EntityHitResult hit)) return false;
        var target = hit.getEntity();
        if (!ContextInteractionClient.isRescueTarget(minecraft, target)) return false;
        if (active && targetEntityId == target.getId()) return true;
        if (active) sendCancel(minecraft);
        active = true;
        targetEntityId = target.getId();
        heartbeatTicks = 0;
        if (minecraft.getConnection() != null) {
            ModNetwork.sendToServer(RescueHoldPacket.start(targetEntityId));
        }
        return true;
    }

    public boolean release(Minecraft minecraft) {
        if (!active) return false;
        sendCancel(minecraft);
        clear();
        return true;
    }

    public void tick(Minecraft minecraft, boolean keyHeld) {
        if (!active) return;
        if (!keyHeld || minecraft.player == null || minecraft.screen != null
            || minecraft.player.isDeadOrDying() || DownedClientState.INSTANCE.downed()) {
            release(minecraft);
            return;
        }
        if (++heartbeatTicks >= HEARTBEAT_INTERVAL_TICKS) {
            heartbeatTicks = 0;
            if (minecraft.getConnection() != null) {
                ModNetwork.sendToServer(RescueHoldPacket.heartbeat(targetEntityId));
            }
        }
    }

    public boolean active() {
        return active;
    }

    private void sendCancel(Minecraft minecraft) {
        if (minecraft.getConnection() != null) {
            ModNetwork.sendToServer(RescueHoldPacket.cancel());
        }
    }

    private void clear() {
        active = false;
        targetEntityId = -1;
        heartbeatTicks = 0;
    }
}
