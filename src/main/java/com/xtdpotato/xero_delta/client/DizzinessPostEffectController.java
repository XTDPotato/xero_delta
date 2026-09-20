package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.ModEffects;
import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** Coordinates Xero's mutually exclusive world post chains. */
@EventBusSubscriber(modid = XeroDelta.MOD_ID, value = Dist.CLIENT)
public final class DizzinessPostEffectController {
    private static final ResourceLocation DIZZINESS = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "shaders/post/dizziness.json");
    private static final ResourceLocation DOWNED = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "shaders/post/downed_desaturate.json");
    private static final ResourceLocation DIZZINESS_DOWNED = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "shaders/post/dizziness_downed.json");
    private static final int RETRY_TICKS = 20;
    private static ResourceLocation requestedEffect;
    private static int retryCooldown;
    private static boolean wasDizzy;

    private DizzinessPostEffectController() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean dizzy = minecraft.player != null
            && minecraft.player.hasEffect(ModEffects.HEAD_DIZZINESS);
        boolean downed = minecraft.player != null && DownedClientState.INSTANCE.downed();

        if (dizzy && !wasDizzy) {
            // Remove sounds that were already playing when hearing loss started.
            minecraft.getSoundManager().stop();
        }
        wasDizzy = dizzy;

        ResourceLocation desired = dizzy
            ? (downed ? DIZZINESS_DOWNED : DIZZINESS)
            : (downed ? DOWNED : null);
        PostChain current = minecraft.gameRenderer.currentEffect();
        ResourceLocation currentManaged = managedEffect(current);

        if (desired != null) {
            if (desired.equals(currentManaged)) {
                requestedEffect = desired;
                retryCooldown = RETRY_TICKS;
                return;
            }
            if (retryCooldown > 0 && desired.equals(requestedEffect)) retryCooldown--;
            if (!desired.equals(requestedEffect) || retryCooldown == 0) {
                minecraft.gameRenderer.loadEffect(desired);
                requestedEffect = desired;
                retryCooldown = RETRY_TICKS;
            }
            return;
        }

        retryCooldown = 0;
        requestedEffect = null;
        if (currentManaged == null) return;
        minecraft.gameRenderer.shutdownEffect();
        if (minecraft.getCameraEntity() != null) {
            minecraft.gameRenderer.checkEntityPostEffect(minecraft.getCameraEntity());
        }
    }

    /**
     * Dizziness is represented as temporary hearing loss. The event is client-only,
     * so this affects only the local player and does not alter world sound state.
     */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
            && minecraft.player.hasEffect(ModEffects.HEAD_DIZZINESS)) {
            event.setSound(null);
        }
    }

    private static ResourceLocation managedEffect(PostChain chain) {
        if (chain == null) return null;
        String name = chain.getName();
        if (DIZZINESS.toString().equals(name)) return DIZZINESS;
        if (DOWNED.toString().equals(name)) return DOWNED;
        if (DIZZINESS_DOWNED.toString().equals(name)) return DIZZINESS_DOWNED;
        return null;
    }
}
