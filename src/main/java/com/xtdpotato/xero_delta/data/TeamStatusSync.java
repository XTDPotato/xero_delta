package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.network.TeamStatusPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import com.xtdpotato.xero_delta.client.TeamColorPalette;

/** Builds one compact status packet for the viewer's currently-online FTB teammates. */
public final class TeamStatusSync {
    private TeamStatusSync() {
    }

    public static void send(ServerPlayer viewer) {
        List<TeamStatusPacket.Entry> entries = new ArrayList<>();
        int teamNumber = 2;
        for (ServerPlayer member : FtbTeamIntegration.onlineTeammates(viewer)) {
            DownedManager.TeamSnapshot downed = DownedManager.teamSnapshot(member);
            entries.add(new TeamStatusPacket.Entry(member.getUUID(),
                member.getGameProfile().getName(),
                member.level().dimension().location().toString(), member.getX(), member.getY(), member.getZ(),
                (byte) teamNumber, TeamColorPalette.colorForNumber(teamNumber),
                member.getHealth(), member.getMaxHealth(),
                member.hasEffect(MobEffects.WEAKNESS), downed.stage(), downed.remainingTicks(),
                downed.durationTicks(), downed.rescueTicks(), downed.rescueDurationTicks(),
                downed.carried(), downed.carrying(), downed.rescuing(), downed.beingRescued(),
                downed.carryPhase(), downed.carryTicks()));
            teamNumber++;
        }
        PacketDistributor.sendToPlayer(viewer, new TeamStatusPacket(entries));
    }
}
