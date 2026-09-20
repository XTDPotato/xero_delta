package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.DeltaContainerLayoutController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreen.class)
public abstract class CraftingScreenMixin {
    @Shadow private boolean widthTooNarrow;
    @Unique private ImageButton xero$recipeBookButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void xero$initializeDeltaRecipeBook(CallbackInfo ci) {
        CraftingScreen screen = (CraftingScreen) (Object) this;
        if (!DeltaContainerLayoutController.isPositioned(screen)) return;
        widthTooNarrow = false;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ImageButton button
                && button.getWidth() == 20 && button.getHeight() == 18) {
                xero$recipeBookButton = button;
                break;
            }
        }
        xero$positionRecipeBookButton(screen);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void xero$keepDeltaRecipeButtonPosition(GuiGraphics graphics,
                                                     int mouseX, int mouseY,
                                                     float partialTick,
                                                     CallbackInfo ci) {
        CraftingScreen screen = (CraftingScreen) (Object) this;
        if (DeltaContainerLayoutController.isPositioned(screen)) {
            widthTooNarrow = false;
            xero$positionRecipeBookButton(screen);
        }
    }

    @Unique
    private void xero$positionRecipeBookButton(CraftingScreen screen) {
        if (xero$recipeBookButton == null) return;
        xero$recipeBookButton.setPosition(
            DeltaContainerLayoutController.recipeBookButtonX(screen),
            DeltaContainerLayoutController.recipeBookButtonY(screen));
    }
}
