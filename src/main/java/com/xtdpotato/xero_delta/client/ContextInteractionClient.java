package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.network.CorpseOpenPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Resolves the target under the crosshair for the dedicated F/H interaction prompts. */
public final class ContextInteractionClient {
    private ContextInteractionClient() {
    }

    public static boolean activate(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.gameMode == null || minecraft.screen != null) return false;
        if (DownedClientState.INSTANCE.carrying()) return true;
        if (minecraft.hitResult instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (isCarriedTarget(minecraft, target)) return true;
            if (target instanceof CorpseEntity corpse) {
                ModNetwork.sendToServer(new CorpseOpenPacket(corpse.getId()));
                return true;
            }
            minecraft.gameMode.interact(minecraft.player, target, InteractionHand.MAIN_HAND);
            return true;
        }
        if (minecraft.hitResult instanceof BlockHitResult blockHit) {
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, blockHit);
            return true;
        }
        return false;
    }

    public static boolean isRescueTarget(Minecraft minecraft, Entity target) {
        if (minecraft.player == null || target == minecraft.player) return false;
        if (target instanceof Player player) return teammateStage(player.getUUID()) == 1;
        if (target instanceof CorpseEntity corpse) {
            return !corpse.isLootBox() && corpse.ownerId() != null
                && teammateStage(corpse.ownerId()) == 2;
        }
        return false;
    }

    public static boolean isCarriedTarget(Minecraft minecraft, Entity target) {
        if (target instanceof CorpseEntity corpse) return corpse.carryingPlayer() != null;
        if (!(target instanceof Player player)) return false;
        if (player.getVehicle() instanceof Player) return true;
        if (player == minecraft.player) return DownedClientState.INSTANCE.carried();
        return TeamStatusClientState.INSTANCE.members().stream().anyMatch(entry ->
            entry.playerId().equals(player.getUUID()) && entry.carried());
    }

    public static int carryTargetId(Minecraft minecraft) {
        if (minecraft.hitResult instanceof EntityHitResult hit) {
            Entity target = hit.getEntity();
            if (!isCarriedTarget(minecraft, target) && (isRescueTarget(minecraft, target)
                || target instanceof CorpseEntity)) return target.getId();
        }
        return -1;
    }

    public static byte teammateStage(java.util.UUID playerId) {
        for (var member : TeamStatusClientState.INSTANCE.members()) {
            if (member.playerId().equals(playerId)) return member.stage();
        }
        return 0;
    }
}
