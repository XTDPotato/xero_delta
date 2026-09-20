package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.data.KnifeSkinRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class KnifeSlotMixin {
    @Shadow public abstract ItemStack getItem();

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void xero$lockKnifePickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (KnifeSkinRules.blocksPlayerInventoryAction(player, getItem())) cir.setReturnValue(false);
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void xero$lockKnifePlacement(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (KnifeSkinRules.locked(getItem()) || KnifeSkinRules.locked(stack)) cir.setReturnValue(false);
    }
}
