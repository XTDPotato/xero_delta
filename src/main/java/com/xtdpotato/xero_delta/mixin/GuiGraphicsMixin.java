package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.ContainerGridRenderBridge;
import com.xtdpotato.xero_delta.client.DeltaContainerLayoutController;
import com.xtdpotato.xero_delta.client.QualityItemBackground;
import com.xtdpotato.xero_delta.client.ScreenLayerResolver;
import com.xtdpotato.xero_delta.screen.PlayerStatusScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @ModifyConstant(
        method = "renderTooltipInternal",
        constant = @Constant(floatValue = 400.0F),
        require = 0
    )
    private float xero$raiseTooltipLayer(float original) {
        return ScreenLayerResolver.tooltipRenderOffset();
    }

    @ModifyConstant(
        method = "renderTooltipInternal",
        constant = @Constant(intValue = 400),
        require = 0
    )
    private int xero$raiseTooltipBackgroundLayer(int original) {
        return Math.round(ScreenLayerResolver.tooltipRenderOffset());
    }

    @Inject(
        method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void xero$renderQualityBackground(LivingEntity entity, Level level, ItemStack stack,
                                               int x, int y, int seed, int guiOffset, CallbackInfo ci) {
        if (ContainerGridRenderBridge.renderSizedSlotItem((GuiGraphics) (Object) this, stack, x, y)) {
            ci.cancel();
            return;
        }
        QualityItemBackground.render((GuiGraphics) (Object) this, stack, x, y, 16, 16);
    }

    @Inject(
        method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void xero$suppressSingleSlotDecorations(Font font, ItemStack stack, int x, int y, String text,
                                                     CallbackInfo ci) {
        var screen = Minecraft.getInstance().screen;
        boolean weightView = screen instanceof PlayerStatusScreen status
            && status.showsItemWeightBadges()
            || screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container
                && DeltaContainerLayoutController.showsItemWeightBadges(container);
        if (weightView || ContainerGridRenderBridge.suppressVanillaDecorations(stack, x, y)) {
            ci.cancel();
        }
    }
}
