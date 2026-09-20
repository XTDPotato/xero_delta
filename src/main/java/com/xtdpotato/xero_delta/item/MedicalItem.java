package com.xtdpotato.xero_delta.item;

import com.xtdpotato.xero_delta.data.PlayerInjuryManager;
import com.xtdpotato.xero_delta.data.MedicalUseManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Consumable medical item whose result is validated on the logical server. */
public final class MedicalItem extends Item implements TimedUseItem {
    private final MedicalTreatment treatment;

    public MedicalItem(MedicalTreatment treatment, Properties properties) {
        super(properties);
        this.treatment = treatment;
    }

    public MedicalTreatment treatment() {
        return treatment;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!MedicalUseManager.startHeld(serverPlayer, hand)) {
                return InteractionResultHolder.fail(stack);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        ConsumableProfile profile = ConsumableProfile.get(stack);
        if (profile != null) {
            if (profile.kind() == ConsumableProfile.Kind.HEALTH) {
                return profile.startupTicks() + MedicalHealthRules.healingTicks(
                    entity.getMaxHealth() - entity.getHealth(),
                    PlayerInjuryManager.baselineMaximumHealth(entity),
                    Math.max(0, stack.getMaxDamage() - stack.getDamageValue()),
                    profile.healPerSecond() / 20.0F);
            }
            return profile.startupTicks();
        }
        if (MedicalUseRules.isBattlefieldKit(stack)) {
            return BattlefieldMedicalKitRules.estimatedDurationTicks(
                entity.getHealth(), entity.getMaxHealth(), PlayerInjuryManager.baselineMaximumHealth(entity));
        }
        if (MedicalUseRules.isOutdoorKit(stack)) {
            return OutdoorMedicalKitRules.estimatedDurationTicks(
                entity.getHealth(), entity.getMaxHealth(), PlayerInjuryManager.baselineMaximumHealth(entity));
        }
        if (MedicalUseRules.isHealthItem(stack)) {
            return BasicHealthMedicalRules.durationTicks(entity.getMaxHealth());
        }
        return 24;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // Timed treatment is represented by the client self-treatment pose,
        // never by vanilla's held-item drinking animation.
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
        if (MedicalUseRules.isHealthItem(stack)) return stack;
        if (!level.isClientSide && entity instanceof ServerPlayer player
            && MedicalUseRules.applyItem(player, stack)) {
            if (!player.getAbilities().instabuild) consumeUse(stack);
        }
        return stack;
    }

    public static void consumeUse(ItemStack stack) {
        if (!stack.isDamageableItem()) { stack.shrink(1); return; }
        int next = stack.getDamageValue() + 1;
        if (next >= stack.getMaxDamage()) { stack.shrink(1); if (!stack.isEmpty()) stack.setDamageValue(0); }
        else stack.setDamageValue(next);
    }
}
