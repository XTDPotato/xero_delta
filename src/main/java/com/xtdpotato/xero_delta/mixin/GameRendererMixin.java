package com.xtdpotato.xero_delta.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Avoids rendering one stale world frame while Minecraft is between camera
 * entities (for example during a respawn or world disconnect).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean xeroDelta$requirePlayerForWorldFrame(boolean renderLevel) {
        // Keep rendering loading/disconnect screens, but skip the world and HUD
        // together until their local-player dependency is available again.
        Minecraft minecraft = Minecraft.getInstance();
        return renderLevel && minecraft.level != null && minecraft.player != null;
    }

    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void xeroDelta$skipFrameWithoutCamera(CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            callback.cancel();
        }
    }
}
