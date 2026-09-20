package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.DownedClientState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void xeroDelta$blockDownedUseItem(CallbackInfo callback) {
        if (DownedClientState.INSTANCE.interactionLocked()) callback.cancel();
    }
}
