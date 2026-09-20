package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.ContextInteractionClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents a carried target's ground shadow from being drawn at the carrier's camera. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "getShadowRadius", at = @At("HEAD"), cancellable = true)
    private void xeroDelta$hideCarriedShadow(Entity entity,
                                              CallbackInfoReturnable<Float> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && ContextInteractionClient.isCarriedTarget(minecraft, entity)) {
            callback.setReturnValue(0.0F);
        }
    }
}
