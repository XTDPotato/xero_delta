package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.DeltaContainerLayoutController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectRenderingInventoryScreenMixin {
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void xeroDelta$replaceEffects(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (DeltaContainerLayoutController.isActive((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }
}
