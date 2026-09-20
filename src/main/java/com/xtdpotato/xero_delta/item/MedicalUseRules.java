package com.xtdpotato.xero_delta.item;

import com.xtdpotato.xero_delta.ModEffects;
import com.xtdpotato.xero_delta.data.PlayerInjuryManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Set;

/** Shared item classification for injury-triggered medical use and the medical wheel. */
public final class MedicalUseRules {
    private static final Set<String> HEALTH_ITEMS = Set.of(
        "battlefield_medical_kit",
        "outdoor_medical_kit",
        "field_first_aid_kit",
        "strong_injector",
        "simple_injector",
        "vehicle_first_aid_kit");
    private static final Set<String> PAIN_RELIEF_HEALERS = Set.of(
        "battlefield_medical_kit", "outdoor_medical_kit");

    private MedicalUseRules() {
    }

    public static boolean isArmAutoItem(ItemStack stack) {
        String path = path(stack);
        return path.contains("surgery_kit")
            || path.contains("first_aid_kit")
            || path.equals("cat_tourniquet");
    }

    public static boolean requiresBleeding(ItemStack stack) {
        return false;
    }

    public static int priority(ItemStack stack) {
        String path = path(stack);
        if (path.contains("surgery_kit")) return 0;
        if (path.contains("first_aid_kit")) return 1;
        if (path.equals("cat_tourniquet")) return 2;
        return Integer.MAX_VALUE;
    }

    public static boolean isHealthItem(ItemStack stack) {
        return HEALTH_ITEMS.contains(path(stack));
    }

    public static boolean isSurgeryItem(ItemStack stack) {
        return path(stack).contains("surgery");
    }

    public static boolean isInjuryTreatment(MedicalTreatment treatment) {
        return switch (treatment) {
            case TRAUMA_MINOR, TRAUMA_MAJOR, BLEEDING_MINOR, BLEEDING_MAJOR,
                 TRAUMA_AND_BLEEDING_MINOR, TRAUMA_AND_BLEEDING_MAJOR -> true;
            case PAIN_RELIEF_SHORT, PAIN_RELIEF_LONG, WEIGHT_BOOST,
                 HEARING_BOOST, STAMINA_ATTRIBUTE_BOOST,
                 STAMINA_CAPACITY_BOOST -> false;
        };
    }

    public static boolean hasPainReliefHeal(ItemStack stack) {
        return PAIN_RELIEF_HEALERS.contains(path(stack));
    }

    public static boolean hasWoundTreatment(ItemStack stack) {
        return PAIN_RELIEF_HEALERS.contains(path(stack));
    }

    public static boolean isBattlefieldKit(ItemStack stack) {
        return "battlefield_medical_kit".equals(path(stack));
    }

    public static boolean isOutdoorKit(ItemStack stack) {
        return "outdoor_medical_kit".equals(path(stack));
    }

    public static boolean isDurabilityMedicalKit(ItemStack stack) {
        return isHealthItem(stack);
    }

    /** Server-authoritative condition used by the medical wheel. */
    public static boolean canUseWheelItem(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()
            || !(stack.getItem() instanceof MedicalItem medical)) return false;
        if ("cat_tourniquet".equals(path(stack))) return PlayerInjuryManager.hasArmInjury(player);
        if (isSurgeryItem(stack) || "elastic_bandage".equals(path(stack))) {
            return PlayerInjuryManager.canApplyTreatment(player, MedicalTreatment.TRAUMA_MINOR);
        }
        if (isHealthItem(stack)) {
            boolean healthMissing = MedicalHealthRules.hasMissingHealth(
                player.getHealth(), player.getMaxHealth());
            boolean injuryNeedsTreatment = isBattlefieldKit(stack)
                && PlayerInjuryManager.canApplyTreatment(player,
                    MedicalTreatment.TRAUMA_MINOR);
            return (healthMissing || injuryNeedsTreatment)
                && (!isDurabilityMedicalKit(stack)
                    || stack.getDamageValue() < stack.getMaxDamage());
        }
        return PlayerInjuryManager.canApplyTreatment(player, medical.treatment());
    }

    /** Client-side display approximation; the server performs the final check. */
    public static boolean canUseWheelItem(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()
            || !(stack.getItem() instanceof MedicalItem)) return false;
        if ("cat_tourniquet".equals(path(stack))) {
            return player.hasEffect(ModEffects.LEFT_ARM_INJURY)
                || player.hasEffect(ModEffects.RIGHT_ARM_INJURY);
        }
        if (isSurgeryItem(stack) || "elastic_bandage".equals(path(stack))) return hasVisibleInjury(player);
        if (isHealthItem(stack)) {
            boolean healthMissing = MedicalHealthRules.hasMissingHealth(
                player.getHealth(), player.getMaxHealth());
            boolean injuryNeedsTreatment = isBattlefieldKit(stack)
                && hasVisibleInjury(player);
            return (healthMissing || injuryNeedsTreatment)
                && (!isDurabilityMedicalKit(stack)
                    || stack.getDamageValue() < stack.getMaxDamage());
        }
        MedicalItem medical = (MedicalItem) stack.getItem();
        if (isInjuryTreatment(medical.treatment())) {
            return hasVisibleInjury(player) || player.hasEffect(ModEffects.BLEEDING);
        }
        return true;
    }

    private static boolean hasVisibleInjury(Player player) {
        return player.hasEffect(ModEffects.HEAD_INJURY)
            || player.hasEffect(ModEffects.CHEST_INJURY)
            || player.hasEffect(ModEffects.ABDOMEN_INJURY)
            || player.hasEffect(ModEffects.LEFT_ARM_INJURY)
            || player.hasEffect(ModEffects.RIGHT_ARM_INJURY)
            || player.hasEffect(ModEffects.LEFT_LEG_INJURY)
            || player.hasEffect(ModEffects.RIGHT_LEG_INJURY)
            || player.hasEffect(ModEffects.WHOLE_BODY_INJURY);
    }

    public static boolean canTreatInjury(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof MedicalItem medical)) return false;
        if (isHealthItem(stack)) return hasWoundTreatment(stack)
            && PlayerInjuryManager.canApplyTreatment(player, MedicalTreatment.TRAUMA_MINOR);
        return isInjuryTreatment(medical.treatment()) && canUseWheelItem(player, stack);
    }

    /**
     * Applies the item-specific result. Health items restore a quarter of the
     * current maximum health; battlefield/outdoor kits additionally relieve
     * pain for 30 seconds and attempt one wound treatment.
     */
    public static boolean applyItem(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()
            || !(stack.getItem() instanceof MedicalItem medical)) return false;
        // Durable kits are driven by MedicalUseManager's server tick state.
        // Keep this guard so a vanilla finishUsingItem path can never turn
        // them into a one-shot heal.
        if (isDurabilityMedicalKit(stack)) return false;
        if ("cat_tourniquet".equals(path(stack))) return PlayerInjuryManager.treatOneArmWound(player);
        if (isSurgeryItem(stack) || "elastic_bandage".equals(path(stack))) {
            return PlayerInjuryManager.applyTreatment(player, MedicalTreatment.TRAUMA_MINOR);
        }
        ConsumableProfile profile = ConsumableProfile.get(stack);
        if (profile != null && profile.kind() == ConsumableProfile.Kind.RECOVERY) {
            com.xtdpotato.xero_delta.data.PlayerStaminaManager.beginRapidRecovery(player, profile.effectSeconds() * 20);
            return true;
        }
        if (profile != null && profile.effectSeconds() > 0) {
            return PlayerInjuryManager.applyTimedTreatment(player, medical.treatment(), profile.effectSeconds() * 20);
        }
        if (!isHealthItem(stack)) {
            return PlayerInjuryManager.applyTreatment(player, medical.treatment());
        }
        if (!MedicalHealthRules.hasMissingHealth(
            player.getHealth(), player.getMaxHealth())) return false;

        float before = player.getHealth();
        float amount = Math.max(2.0F, player.getMaxHealth() * 0.25F);
        player.heal(amount);
        boolean changed = player.getHealth() > before;
        boolean woundTreated = false;
        if (hasPainReliefHeal(stack)) {
            player.addEffect(new MobEffectInstance(ModEffects.PAIN_RELIEF,
                20 * 30, 0, false, false, true));
            if (PlayerInjuryManager.canApplyTreatment(player,
                MedicalTreatment.TRAUMA_MINOR)) {
                woundTreated = PlayerInjuryManager.applyTreatment(player,
                    MedicalTreatment.TRAUMA_MINOR);
            }
        }
        if (changed || woundTreated) {
            if (!woundTreated) {
                player.displayClientMessage(Component.translatable(
                    "medical.xero_delta.used"), true);
            }
            return true;
        }
        return false;
    }

    public static String path(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.getPath().toLowerCase(Locale.ROOT);
    }
}
