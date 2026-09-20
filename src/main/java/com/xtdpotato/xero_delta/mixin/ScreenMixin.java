package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.DeltaContainerLayoutController;
import com.xtdpotato.xero_delta.client.CreativeItemRuleEditor;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes inherited pointer gestures to the Delta panel on container screens. */
@Mixin(ContainerEventHandler.class)
public interface ScreenMixin {
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void xero$scrollDeltaPlayerPanel(double mouseX, double mouseY,
                                             double scrollX, double scrollY,
                                             CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof AbstractContainerScreen<?> screen
            && DeltaContainerLayoutController.mouseScrolled(
                screen, mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void xero$dragDeltaPlayerPanel(double mouseX, double mouseY, int button,
                                           double dragX, double dragY,
                                           CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof AbstractContainerScreen<?> screen
            && DeltaContainerLayoutController.mouseDragged(
                screen, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void xero$releaseDeltaPlayerPanel(double mouseX, double mouseY, int button,
                                              CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof AbstractContainerScreen<?> screen
            && DeltaContainerLayoutController.mouseReleased(
                screen, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void xero$typeContainerPositionValue(char character, int modifiers,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof net.minecraft.client.gui.screens.Screen screen
            && CreativeItemRuleEditor.charTyped(screen, character)) {
            cir.setReturnValue(true);
            return;
        }
        if ((Object) this instanceof AbstractContainerScreen<?> screen
            && DeltaContainerLayoutController.editorCharTyped(screen, character)) {
            cir.setReturnValue(true);
        }
    }
}
