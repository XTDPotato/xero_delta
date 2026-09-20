package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.DeltaContainerLayoutController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
    @Shadow private EditBox name;

    @Inject(method = "renderFg(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At("HEAD"), require = 0)
    private void xero$keepNameFieldWithMovedAnvil(GuiGraphics graphics, int mouseX,
                                                   int mouseY, float partialTick,
                                                   CallbackInfo ci) {
        AnvilScreen screen = (AnvilScreen) (Object) this;
        if (name == null || !DeltaContainerLayoutController.isPositioned(screen)) return;
        name.setX(DeltaContainerLayoutController.nativeLeft(screen, name.getX() - 62) + 62);
        name.setY(DeltaContainerLayoutController.nativeTop(screen, name.getY() - 24) + 24);
    }
}