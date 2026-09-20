package com.xtdpotato.xero_delta.client;

import com.mojang.math.Axis;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.DownedRules;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Presents the local downed player prone without changing gameplay collision. */
@EventBusSubscriber(modid = XeroDelta.MOD_ID, value = Dist.CLIENT)
public final class DownedPlayerRenderEvents {
    private static final java.util.Set<net.minecraft.world.entity.player.Player> TRANSFORMED =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private DownedPlayerRenderEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforePlayer(RenderPlayerEvent.Pre event) {
        CarryVisual carry = carryVisual(event.getEntity(), event.getPartialTick());
        if (!isDowned(event.getEntity()) && carry.progress() <= 0.0F) return;
        if (!TRANSFORMED.add(event.getEntity())) return;
        event.getPoseStack().pushPose();
        net.minecraft.world.entity.Entity carrier = event.getEntity().getVehicle();
        CarryWorldSmoother.apply(event.getEntity(), carrier, carry.phase(),
            carry.progress(), event.getPartialTick(), event.getPoseStack());
        if (carry.carrier()) {
            float bend = (float) Math.sin(carry.progress() * Math.PI);
            event.getPoseStack().translate(0.0D, -0.12D * bend, 0.10D * bend);
            event.getPoseStack().mulPose(Axis.XP.rotationDegrees(24.0F * bend));
            return;
        }
        if (carry.carried()) {
            float progress = carry.progress();
            event.getPoseStack().translate(0.0D, 1.45D * progress, 0.0D);
            event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(-12.0F * progress));
        }
        event.getPoseStack().translate(0.0D, 0.08D, 0.0D);
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(event.getEntity().getYRot()));
        event.getPoseStack().mulPose(Axis.XP.rotationDegrees(90.0F));
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(-event.getEntity().getYRot()));
        event.getPoseStack().translate(0.0D, -0.9D, 0.0D);
    }

    @SubscribeEvent
    public static void afterPlayer(RenderPlayerEvent.Post event) {
        if (TRANSFORMED.remove(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }

    private static boolean isDowned(net.minecraft.world.entity.player.Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == minecraft.player) return DownedClientState.INSTANCE.downed();
        return TeamStatusClientState.INSTANCE.members().stream().anyMatch(entry ->
            entry.playerId().equals(player.getUUID()) && entry.stage() != 0);
    }

    private static CarryVisual carryVisual(net.minecraft.world.entity.player.Player player,
                                           float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        byte phase;
        float ticks;
        boolean carrier;
        boolean carried;
        if (player == minecraft.player) {
            phase = DownedClientState.INSTANCE.carryPhase();
            ticks = DownedClientState.INSTANCE.estimatedCarryTicks();
            carrier = DownedClientState.INSTANCE.carrying();
            carried = DownedClientState.INSTANCE.carried();
        } else {
            var entry = TeamStatusClientState.INSTANCE.members().stream()
                .filter(value -> value.playerId().equals(player.getUUID()))
                .findFirst().orElse(null);
            if (entry == null) return CarryVisual.NONE;
            phase = entry.carryPhase();
            ticks = TeamStatusClientState.INSTANCE.estimatedCarryTicks(entry);
            carrier = entry.carrying();
            carried = entry.carried();
        }
        int actionDuration = phase == 3
            ? DownedRules.CARRY_DROP_TICKS : DownedRules.CARRY_WINDUP_TICKS;
        float progress = CarryAnimationMath.progress(phase, ticks,
            actionDuration, DownedRules.CARRY_ANIMATION_TICKS);
        return new CarryVisual(phase, progress, carrier, carried);
    }

    private record CarryVisual(byte phase, float progress, boolean carrier, boolean carried) {
        private static final CarryVisual NONE = new CarryVisual((byte) 0, 0.0F, false, false);
    }
}
