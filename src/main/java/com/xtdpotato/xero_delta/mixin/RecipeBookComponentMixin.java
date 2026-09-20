package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.DeltaContainerLayoutController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow private int width;
    @Shadow private int height;
    @Shadow private boolean widthTooNarrow;

    @Inject(method = "init", at = @At("RETURN"))
    private void xero$disableNarrowOverlay(int width, int height,
                                           Minecraft minecraft,
                                           boolean widthTooNarrow,
                                           net.minecraft.world.inventory.RecipeBookMenu<?, ?> menu,
                                           CallbackInfo ci) {
        if (xero$recipeScreen() != null) this.widthTooNarrow = false;
    }

    @Inject(method = "initVisuals", at = @At("HEAD"))
    private void xero$placeMovableRecipeBook(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = xero$recipeScreen();
        if (screen == null) return;
        this.widthTooNarrow = false;
        int recipeX = DeltaContainerLayoutController.recipeBookX(screen);
        int recipeY = DeltaContainerLayoutController.recipeBookY(screen);
        this.width = 2 * (recipeX + 86) + RecipeBookComponent.IMAGE_WIDTH;
        this.height = 2 * recipeY + RecipeBookComponent.IMAGE_HEIGHT;
    }

    @Inject(method = "updateScreenPosition", at = @At("RETURN"), cancellable = true)
    private void xero$keepContainerAtEditedPosition(int screenWidth, int imageWidth,
                                                    CallbackInfoReturnable<Integer> cir) {
        AbstractContainerScreen<?> screen = xero$recipeScreen();
        if (screen != null) {
            cir.setReturnValue(DeltaContainerLayoutController.nativeLeft(
                screen, cir.getReturnValue()));
        }
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void xero$useMovedRecipeBookHitbox(double mouseX, double mouseY,
                                               int mainX, int mainY,
                                               int mainWidth, int mainHeight,
                                               int mouseButton,
                                               CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = xero$recipeScreen();
        if (screen == null) return;
        int recipeX = DeltaContainerLayoutController.recipeBookX(screen);
        int recipeY = DeltaContainerLayoutController.recipeBookY(screen);
        boolean inside = mouseX >= recipeX - 30
            && mouseX < recipeX + RecipeBookComponent.IMAGE_WIDTH
            && mouseY >= recipeY
            && mouseY < recipeY + RecipeBookComponent.IMAGE_HEIGHT;
        cir.setReturnValue(!inside);
    }

    private static AbstractContainerScreen<?> xero$recipeScreen() {
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen
            && DeltaContainerLayoutController.isPositioned(screen)
            && DeltaContainerLayoutController.hasMovableRecipeBook(screen)) {
            return screen;
        }
        return null;
    }
}