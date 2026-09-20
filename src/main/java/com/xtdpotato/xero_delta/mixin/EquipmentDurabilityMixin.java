package com.xtdpotato.xero_delta.mixin;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keeps equippable items at one durability so they remain repairable instead of disappearing. */
@Mixin(ItemStack.class)
public abstract class EquipmentDurabilityMixin {
    @ModifyVariable(
        method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int xero$keepOneDurability(int amount) {
        ItemStack stack = (ItemStack) (Object) this;
        if (amount <= 0 || !(stack.getItem() instanceof ArmorItem)
            || !stack.isDamageableItem() || stack.getMaxDamage() <= 1) return amount;
        int allowed = stack.getMaxDamage() - 1 - stack.getDamageValue();
        return Math.max(0, Math.min(amount, allowed));
    }
}