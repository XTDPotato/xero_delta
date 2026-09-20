package com.xtdpotato.xero_delta.item;

import com.xtdpotato.xero_delta.data.MedicalUseManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Timed armor repair consumable with a tiered durability restoration. */
public final class RepairKitItem extends Item implements TimedUseItem {
    public enum Target {
        HELMET(EquipmentSlot.HEAD),
        ARMOR(EquipmentSlot.CHEST);

        private final EquipmentSlot slot;

        Target(EquipmentSlot slot) {
            this.slot = slot;
        }
    }

    private final Target target;
    private final float repairFraction;

    public RepairKitItem(Target target, float repairFraction, Properties properties) {
        super(properties);
        this.target = target;
        this.repairFraction = Math.max(0.0F, Math.min(1.0F, repairFraction));
    }

    public boolean canUse(Player player) {
        if (player == null) return false;
        ItemStack equipment = player.getItemBySlot(target.slot);
        ConsumableProfile profile = ConsumableProfile.get(getDefaultInstance());
        if (profile != null && !player.level().isClientSide) {
            String quality = com.xtdpotato.xero_delta.data.ServerItemRules.getQualityFor(equipment);
            if (com.xtdpotato.xero_delta.data.BallisticArmorRules.tierIndex(quality) + 1 > profile.repairLevel()) return false;
        }
        return !equipment.isEmpty() && equipment.isDamageableItem()
            && equipment.getDamageValue() > 0;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                   InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUse(player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MedicalUseManager.startHeld(serverPlayer, hand);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 90;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack,
                          int remainingUseDuration) {
        if (!level.isClientSide) {
            entity.setSprinting(false);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && canUse(player)) {
            ItemStack equipment = player.getItemBySlot(target.slot);
            if (!equipment.isEmpty() && equipment.isDamageableItem()
                && equipment.getDamageValue() > 0) {
                int restored = Math.max(1,
                    Math.round(equipment.getMaxDamage() * repairFraction));
                ConsumableProfile profile = ConsumableProfile.get(stack);
                int available = stack.getMaxDamage() - stack.getDamageValue();
                int cost = profile == null ? 1 : Math.min(available, profile.repairCost());
                if (cost <= 0) return stack;
                if (profile != null) restored = Math.max(1,
                    Math.round(restored * cost / (float) profile.repairCost()));
                equipment.setDamageValue(Math.max(0,
                    equipment.getDamageValue() - restored));
                if (!player.getAbilities().instabuild) {
                    int next = stack.getDamageValue() + cost;
                    if (next >= stack.getMaxDamage()) stack.shrink(1);
                    else stack.setDamageValue(next);
                }
                player.inventoryMenu.broadcastChanges();
            }
        }
        return stack;
    }
}
