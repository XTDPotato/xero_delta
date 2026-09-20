package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.network.TeamStatusPacket;
import net.minecraft.Util;

import java.util.List;

/** Latest online FTB team snapshot received from the server. */
public final class TeamStatusClientState {
    public static final TeamStatusClientState INSTANCE = new TeamStatusClientState();
    private volatile List<TeamStatusPacket.Entry> members = List.of();
    private volatile long snapshotMillis;

    private TeamStatusClientState() {
    }

    public void update(List<TeamStatusPacket.Entry> value) {
        members = value == null ? List.of() : List.copyOf(value);
        snapshotMillis = Util.getMillis();
    }

    public List<TeamStatusPacket.Entry> members() {
        return members;
    }

    public float estimatedCarryTicks(TeamStatusPacket.Entry entry) {
        if (entry.carryPhase() != 1 && entry.carryPhase() != 3) return entry.carryTicks();
        return CarryAnimationMath.estimatedRemaining(entry.carryTicks(),
            Util.getMillis() - snapshotMillis);
    }
}
