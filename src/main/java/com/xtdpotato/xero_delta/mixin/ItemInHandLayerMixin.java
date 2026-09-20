package com.xtdpotato.xero_delta.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xtdpotato.xero_delta.client.SafetyBoxInspectAnimation;
import com.xtdpotato.xero_delta.client.MedicalUseClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps vanilla held stacks from overlapping the inspection model. */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void xero$hideHeldItemsDuringInspection(PoseStack pose, MultiBufferSource buffers, int light,
                                                     LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                     float partialTick, float ageInTicks, float netHeadYaw,
                                                     float headPitch, CallbackInfo callbackInfo) {
        if (entity instanceof AbstractClientPlayer player
            && (SafetyBoxInspectAnimation.isPlaying(player.getId())
                || (player == Minecraft.getInstance().player
                    && MedicalUseClientState.INSTANCE.medicalActive()))) {
            callbackInfo.cancel();
        }
    }
}
