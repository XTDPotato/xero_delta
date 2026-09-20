package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.network.DownedStatePacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

/** Last server-authoritative downed snapshot for the local player. */
public final class DownedClientState {
    public static final DownedClientState INSTANCE = new DownedClientState();
    private byte stage;
    private int remainingTicks;
    private int durationTicks;
    private int rescueTicks;
    private int rescueDurationTicks;
    private boolean carried;
    private boolean carrying;
    private boolean rescuing;
    private boolean beingRescued;
    private String rescuerName = "";
    private byte carryPhase;
    private int carryTicks;
    private long carrySnapshotMillis;
    private long rescueSnapshotMillis;

    private DownedClientState() {}

    public void update(DownedStatePacket packet) {
        boolean wasRedDown = redDown();
        stage = packet.stage();
        remainingTicks = packet.remainingTicks();
        durationTicks = packet.durationTicks();
        rescueTicks = packet.rescueTicks();
        rescueDurationTicks = packet.rescueDurationTicks();
        carried = packet.carried();
        carrying = packet.carrying();
        rescuing = packet.rescuing();
        beingRescued = packet.beingRescued();
        rescuerName = packet.rescuerName();
        carryPhase = packet.carryPhase();
        carryTicks = Math.max(0, packet.carryTicks());
        carrySnapshotMillis = Util.getMillis();
        rescueSnapshotMillis = carrySnapshotMillis;
        if (wasRedDown != redDown()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) minecraft.player.refreshDimensions();
        }
    }

    public boolean redDown() { return stage == 1; }
    public boolean yellowDown() { return stage == 2; }
    public boolean downed() { return stage != 0; }
    public boolean carried() { return carried; }
    public boolean carrying() { return carrying; }
    public boolean interactionLocked() { return downed() || carrying || rescuing; }
    public int remainingTicks() { return remainingTicks; }
    public int durationTicks() { return durationTicks; }
    public int rescueTicks() { return rescueTicks; }
    public int rescueDurationTicks() { return rescueDurationTicks; }
    public boolean rescuing() { return rescuing; }
    public boolean beingRescued() { return beingRescued; }
    public String rescuerName() { return rescuerName; }
    public byte carryPhase() { return carryPhase; }
    public int carryTicks() { return carryTicks; }
    public float estimatedCarryTicks() {
        if (carryPhase != 1 && carryPhase != 3) return carryTicks;
        return CarryAnimationMath.estimatedRemaining(carryTicks,
            Util.getMillis() - carrySnapshotMillis);
    }
    public float estimatedRescueTicks() {
        if ((!beingRescued && !rescuing) || rescueDurationTicks <= 0) return rescueTicks;
        float elapsed = Math.max(0L, Util.getMillis() - rescueSnapshotMillis) / 50.0F;
        return Math.max(0.0F, Math.min(rescueDurationTicks, rescueTicks + elapsed));
    }
    public float rescueProgress() {
        return rescueDurationTicks <= 0 ? 0.0F
            : Math.max(0.0F, Math.min(1.0F,
                estimatedRescueTicks() / rescueDurationTicks));
    }
    public float rescueRemainingSeconds() {
        return Math.max(0.0F, rescueDurationTicks - estimatedRescueTicks()) / 20.0F;
    }
    public float progress() {
        return durationTicks <= 0 ? 0.0F
            : Math.max(0.0F, Math.min(1.0F, remainingTicks / (float) durationTicks));
    }
}
