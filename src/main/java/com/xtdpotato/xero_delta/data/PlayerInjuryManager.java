package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.ModEffects;
import com.xtdpotato.xero_delta.item.MedicalTreatment;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent localized injuries and their server-authoritative penalties. */
public final class PlayerInjuryManager {
    private static final String ROOT = "xero_delta_injuries";
    private static final int EFFECT_DURATION = 20 * 60 * 30;
    private static final int DERIVED_EFFECT_DURATION = 45;
    private static final int MAX_BLEEDING_STACKS = 5;
    private static final ResourceLocation HEAD_PAIN_HEALTH = id("head_pain_health");
    private static final ResourceLocation CHEST_PAIN_HEALTH = id("chest_pain_health");
    private static final ResourceLocation ABDOMINAL_PAIN_HEALTH = id("abdominal_pain_health");
    private static final ResourceLocation WEAKNESS_HEALTH = id("weakness_health");
    private static final ResourceLocation ARM_FRACTURE_BREAK_SPEED = id("arm_fracture_break_speed");
    private static final ResourceLocation LEG_FRACTURE_JUMP = id("leg_fracture_jump");
    private static final Map<UUID, Integer> OBSERVED_SLOT = new HashMap<>();
    private static final Map<UUID, PendingSwitch> PENDING_SWITCH = new HashMap<>();
    private static final Map<UUID, Integer> JUMP_SLOW_UNTIL = new HashMap<>();

    private PlayerInjuryManager() {
    }

    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float damage = event.getNewDamage();
        if (damage <= 0.0F) return;
        Entity direct = event.getSource().getDirectEntity();
        Entity attacker = event.getSource().getEntity();
        boolean explosion = event.getSource().is(DamageTypeTags.IS_EXPLOSION);
        if (!explosion && (!(direct instanceof Projectile) && !(attacker instanceof LivingEntity)
            || attacker == player)) return;

        HealthSystemRulesData rules = HealthSystemRulesData.get(player.server);
        if (explosion) {
            Vec3 explosionOrigin = event.getSource().getSourcePosition();
            if (explosionOrigin == null && event.getSource().getEntity() != null) {
                explosionOrigin = event.getSource().getEntity().position();
            }
            double distanceSquared = explosionOrigin == null
                ? 0.0D : explosionOrigin.distanceToSqr(player.position());
            double distanceScale = Math.max(0.25D,
                1.0D - Math.sqrt(Math.max(0.0D, distanceSquared)) / 10.0D);
            player.addEffect(new MobEffectInstance(ModEffects.HEAD_DIZZINESS,
                HealthEffectScalingRule.explosionDizzinessTicks(
                    rules.effectMultiplier() * distanceScale,
                    player.level().getDifficulty().getId()),
                0, false, true, true));

            // Blast pressure is distributed across the body. This supplements
            // the normal hit-part injury so chest, abdomen and arms are often
            // injured together by a close explosion.
        }

        BodyPart part = resolveHitPart(player, direct, attacker);
        int impact = HealthEffectScalingRule.scaleInjuryPoints(
            InjuryImpactRule.points(damage, player.getRandom().nextDouble()),
            rules.effectMultiplier(), player.level().getDifficulty().getId());
        if (impact <= 0) return;
        if (explosion) {
            Vec3 explosionOrigin = event.getSource().getSourcePosition();
            if (explosionOrigin == null && event.getSource().getEntity() != null) {
                explosionOrigin = event.getSource().getEntity().position();
            }
            double distanceSquared = explosionOrigin == null
                ? 0.0D : explosionOrigin.distanceToSqr(player.position());
            int blastPoints = HealthEffectScalingRule.explosionRegionPoints(
                impactForExplosion(damage), distanceSquared);
            double chance = HealthEffectScalingRule.explosionInjuryChance(distanceSquared);
            for (BodyPart blastPart : BodyPart.LOCAL_PARTS) {
                if (player.getRandom().nextDouble() < chance) addPoints(player, blastPart, blastPoints);
            }
        }
        addPoints(player, part, impact);
        addPoints(player, BodyPart.WHOLE_BODY, Math.max(1.0F, impact * 0.20F));
        if (part == BodyPart.HEAD && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            player.addEffect(new MobEffectInstance(ModEffects.HEAD_DIZZINESS, 60, 0,
                false, true, true));
        }
        if (isBulletLike(direct) && player.getRandom().nextFloat() < 0.35F) {
            setBleedingStacks(player, bleedingStacks(player) + 1);
        }
        refreshEffect(player, part);
        refreshEffect(player, BodyPart.WHOLE_BODY);
        refreshStatusEffects(player);
        refreshVanillaConsequences(player);
    }

    private static int impactForExplosion(float damage) {
        if (!Float.isFinite(damage) || damage <= 0.0F) return 0;
        return Math.max(1, Math.min(20, Math.round(damage * 1.5F)));
    }

    /** Undo only our active injury modifier, preserving other mods' maximum health. */
    public static float baselineMaximumHealth(net.minecraft.world.entity.LivingEntity entity) {
        var attribute = entity.getAttribute(Attributes.MAX_HEALTH);
        var modifier = attribute == null ? null : attribute.getModifier(WEAKNESS_HEALTH);
        double multiplier = modifier == null ? 1.0D : 1.0D + modifier.amount();
        return (float) (Math.max(1.0F, entity.getMaxHealth()) / Math.max(0.1D, multiplier));
    }

    public static Snapshot snapshot(ServerPlayer player) {
        return new Snapshot(points(player, BodyPart.HEAD), points(player, BodyPart.CHEST),
            points(player, BodyPart.ABDOMEN),
            points(player, BodyPart.LEFT_ARM), points(player, BodyPart.RIGHT_ARM),
            points(player, BodyPart.LEFT_LEG), points(player, BodyPart.RIGHT_LEG),
            points(player, BodyPart.WHOLE_BODY));
    }

    public static double healthPenalty(ServerPlayer player, Snapshot snapshot) {
        HealthPenaltyRulesData rules = HealthPenaltyRulesData.get(player.server);
        return HealthPenaltyRule.fraction(player.hasEffect(ModEffects.PAIN_RELIEF),
            player.hasEffect(MobEffects.WEAKNESS),
            DownedManager.hasYellowRescueHealthPenalty(player),
            snapshot.head(), snapshot.chest(), snapshot.abdomen(),
            rules.chestFraction(), rules.yellowRescueFraction());
    }

    public static double movementPenalty(Snapshot snapshot) {
        return movementPenalty(snapshot, false);
    }

    public static double movementPenalty(Snapshot snapshot, boolean jumpSlowActive) {
        return movementPenalty(snapshot, jumpSlowActive, false);
    }

    public static double movementPenalty(Snapshot snapshot, boolean jumpSlowActive,
                                         boolean painReliefActive) {
        double penalty = LegInjuryRule.movementPenalty(
            snapshot.leftLeg() >= 100.0F, snapshot.rightLeg() >= 100.0F,
            jumpSlowActive, painReliefActive);
        if (snapshot.wholeBody() >= 100.0F) penalty += 0.10D;
        return Math.min(0.75D, penalty);
    }

    public static boolean beginBrokenLegJumpPenalty(ServerPlayer player) {
        Snapshot injury = snapshot(player);
        if (injury.leftLeg() < 100.0F && injury.rightLeg() < 100.0F) return false;
        JUMP_SLOW_UNTIL.put(player.getUUID(), player.tickCount + LegInjuryRule.JUMP_SLOW_TICKS);
        maybeCreateLegFissure(player, injury, 0.30F);
        return true;
    }

    public static boolean jumpSlowActive(ServerPlayer player) {
        Integer until = JUMP_SLOW_UNTIL.get(player.getUUID());
        return until != null && player.tickCount < until;
    }

    public static boolean expireJumpPenalty(ServerPlayer player) {
        Integer until = JUMP_SLOW_UNTIL.get(player.getUUID());
        if (until == null || player.tickCount < until) return false;
        JUMP_SLOW_UNTIL.remove(player.getUUID());
        return true;
    }

    /** Refreshes vanilla penalties and applies the 10-second nausea pulse. */
    public static void tickConsequences(ServerPlayer player) {
        Snapshot injury = snapshot(player);
        boolean masked = player.hasEffect(ModEffects.PAIN_RELIEF);
        refreshEffect(player, BodyPart.LEFT_LEG);
        refreshEffect(player, BodyPart.RIGHT_LEG);
        refreshStatusEffects(player);
        applyAttributeConsequences(player, injury, masked);
        if (masked) {
            player.removeEffect(MobEffects.DIG_SLOWDOWN);
            player.removeEffect(MobEffects.BLINDNESS);
            player.removeEffect(MobEffects.CONFUSION);
            return;
        }
        boolean armBroken = injury.leftArm() >= 100.0F || injury.rightArm() >= 100.0F;
        if (armBroken && player.tickCount % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 0,
                false, true, true));
        }
        if (injury.head() >= 100.0F && player.tickCount % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0,
                false, true, true));
        }
        if ((injury.head() >= 100.0F || injury.chest() >= 100.0F || injury.abdomen() >= 100.0F)
            && player.tickCount % 200 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 1,
                false, true, true));
        }
        int bleeding = bleedingStacks(player);
        if (bleeding > 0 && player.tickCount % 100 == 0) {
            player.hurt(player.damageSources().magic(), bleeding);
        }
        if (player.isSprinting() && player.tickCount % 20 == 0) {
            maybeCreateLegFissure(player, injury, 0.15F);
        }
    }

    private static void refreshVanillaConsequences(ServerPlayer player) {
        if (player.hasEffect(ModEffects.PAIN_RELIEF)) return;
        Snapshot injury = snapshot(player);
        if (injury.leftArm() >= 100.0F || injury.rightArm() >= 100.0F) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 0,
                false, true, true));
        }
        if (injury.head() >= 100.0F) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0,
                false, true, true));
        }
    }

    public static boolean applyTreatment(ServerPlayer player, MedicalTreatment treatment) {
        boolean applied = switch (treatment) {
            case TRAUMA_MINOR -> healOneTrauma(player);
            case TRAUMA_MAJOR -> healAllTrauma(player);
            case BLEEDING_MINOR -> reduceBleeding(player, 1);
            case BLEEDING_MAJOR -> reduceBleeding(player, MAX_BLEEDING_STACKS);
            case TRAUMA_AND_BLEEDING_MINOR ->
                healOneTrauma(player) | reduceBleeding(player, 1);
            case TRAUMA_AND_BLEEDING_MAJOR ->
                healAllTrauma(player) | reduceBleeding(player, MAX_BLEEDING_STACKS);
            case PAIN_RELIEF_SHORT -> addBeneficial(player, ModEffects.PAIN_RELIEF, 20 * 180);
            case PAIN_RELIEF_LONG -> addBeneficial(player, ModEffects.PAIN_RELIEF, 20 * 480);
            case WEIGHT_BOOST -> addBeneficial(player, ModEffects.WEIGHT_BOOST, 20 * 300);
            case HEARING_BOOST -> addBeneficial(player, ModEffects.HEARING_BOOST, 20 * 300);
            case STAMINA_ATTRIBUTE_BOOST ->
                addBeneficial(player, ModEffects.STAMINA_ATTRIBUTE_BOOST, 20 * 300);
            case STAMINA_CAPACITY_BOOST ->
                addBeneficial(player, ModEffects.STAMINA_CAPACITY_BOOST, 20 * 300);
        };
        if (!applied) {
            player.displayClientMessage(Component.translatable(
                "medical.xero_delta.nothing_to_treat"), true);
            return false;
        }
        refreshAllEffects(player);
        player.displayClientMessage(Component.translatable("medical.xero_delta.used"), true);
        return true;
    }

    public static boolean hasArmInjury(ServerPlayer player) {
        return armWound(snapshot(player)) != null;
    }

    static BodyPart armWound(Snapshot injury) {
        if (injury.leftArm() >= 100.0F) return BodyPart.LEFT_ARM;
        if (injury.rightArm() >= 100.0F) return BodyPart.RIGHT_ARM;
        return null;
    }

    public static boolean treatOneArmWound(ServerPlayer player) {
        BodyPart part = armWound(snapshot(player));
        if (part == null) return false;
        setPoints(player, part, 0.0F);
        refreshAllEffects(player);
        return true;
    }

    public static boolean applyTimedTreatment(ServerPlayer player, MedicalTreatment treatment, int ticks) {
        Holder<MobEffect> effect = switch (treatment) {
            case PAIN_RELIEF_SHORT, PAIN_RELIEF_LONG -> ModEffects.PAIN_RELIEF;
            case WEIGHT_BOOST -> ModEffects.WEIGHT_BOOST;
            case HEARING_BOOST -> ModEffects.HEARING_BOOST;
            case STAMINA_ATTRIBUTE_BOOST -> ModEffects.STAMINA_ATTRIBUTE_BOOST;
            case STAMINA_CAPACITY_BOOST -> ModEffects.STAMINA_CAPACITY_BOOST;
            default -> null;
        };
        if (effect == null) return false;
        addBeneficial(player, effect, ticks);
        refreshAllEffects(player);
        return true;
    }

    public static boolean hasBleeding(ServerPlayer player) {
        return bleedingStacks(player) > 0;
    }

    public static boolean canApplyTreatment(ServerPlayer player, MedicalTreatment treatment) {
        return switch (treatment) {
            case TRAUMA_MINOR -> hasTreatableTrauma(player);
            case TRAUMA_MAJOR -> hasAnyTrauma(player);
            case BLEEDING_MINOR, BLEEDING_MAJOR -> hasBleeding(player);
            case TRAUMA_AND_BLEEDING_MINOR ->
                hasTreatableTrauma(player) || hasBleeding(player);
            case TRAUMA_AND_BLEEDING_MAJOR ->
                hasAnyTrauma(player) || hasBleeding(player);
            case PAIN_RELIEF_SHORT, PAIN_RELIEF_LONG, WEIGHT_BOOST,
                 HEARING_BOOST, STAMINA_ATTRIBUTE_BOOST,
                 STAMINA_CAPACITY_BOOST -> true;
        };
    }

    private static boolean hasTreatableTrauma(ServerPlayer player) {
        for (BodyPart part : BodyPart.LOCAL_PARTS) {
            if (points(player, part) >= 100.0F) return true;
        }
        return hasLegFissure(player);
    }

    private static boolean hasAnyTrauma(ServerPlayer player) {
        for (BodyPart part : BodyPart.LOCAL_PARTS) {
            if (points(player, part) > 0.0F) return true;
        }
        return points(player, BodyPart.WHOLE_BODY) > 0.0F || hasLegFissure(player);
    }

    private static boolean addBeneficial(ServerPlayer player, Holder<MobEffect> effect, int duration) {
        // Medicine bonuses do not emit particles, but remain visible in the
        // vanilla effect list and the Delta status HUD.
        player.addEffect(new MobEffectInstance(effect, duration, 0, false, false, true));
        return true;
    }

    private static boolean healOneTrauma(ServerPlayer player) {
        BodyPart selected = null;
        float severity = 0.0F;
        for (BodyPart part : BodyPart.LOCAL_PARTS) {
            float value = points(player, part);
            if (value >= 100.0F && value >= severity) {
                selected = part;
                severity = value;
            }
        }
        if (selected == null && !hasLegFissure(player)) return false;
        if (selected != null) {
            setPoints(player, selected, 0.0F);
            if (selected == BodyPart.LEFT_LEG) setLegFissure(player, true, false);
            if (selected == BodyPart.RIGHT_LEG) setLegFissure(player, false, false);
        } else {
            clearOneLegFissure(player);
        }
        return true;
    }

    private static boolean healAllTrauma(ServerPlayer player) {
        boolean changed = false;
        for (BodyPart part : BodyPart.LOCAL_PARTS) {
            if (points(player, part) > 0.0F) {
                setPoints(player, part, 0.0F);
                changed = true;
            }
        }
        if (points(player, BodyPart.WHOLE_BODY) > 0.0F) {
            setPoints(player, BodyPart.WHOLE_BODY, 0.0F);
            changed = true;
        }
        if (hasLegFissure(player)) {
            setLegFissure(player, true, false);
            setLegFissure(player, false, false);
            changed = true;
        }
        return changed;
    }

    private static boolean reduceBleeding(ServerPlayer player, int amount) {
        int before = bleedingStacks(player);
        if (before <= 0) return false;
        setBleedingStacks(player, before - Math.max(1, amount));
        return true;
    }

    public static void tickHotbarSwitch(ServerPlayer player) {
        UUID id = player.getUUID();
        int current = player.getInventory().selected;
        PendingSwitch pending = PENDING_SWITCH.get(id);
        if (player.hasEffect(ModEffects.PAIN_RELIEF)) {
            if (pending != null) {
                setSelected(player, pending.targetSlot());
                current = pending.targetSlot();
                PENDING_SWITCH.remove(id);
            }
            OBSERVED_SLOT.put(id, current);
            return;
        }
        if (pending != null) {
            if (player.tickCount >= pending.releaseTick()) {
                setSelected(player, pending.targetSlot());
                OBSERVED_SLOT.put(id, pending.targetSlot());
                PENDING_SWITCH.remove(id);
            } else if (current != pending.previousSlot()) {
                setSelected(player, pending.previousSlot());
            }
            return;
        }

        Integer previous = OBSERVED_SLOT.putIfAbsent(id, current);
        if (previous == null || previous == current) return;
        Snapshot injury = snapshot(player);
        float armInjury = Math.max(injury.leftArm(), injury.rightArm());
        if (armInjury < 100.0F) {
            OBSERVED_SLOT.put(id, current);
            return;
        }
        int delay = 10;
        PENDING_SWITCH.put(id, new PendingSwitch(previous, current, player.tickCount + delay));
        setSelected(player, previous);
    }

    public static void clear(ServerPlayer player) {
        OBSERVED_SLOT.remove(player.getUUID());
        PENDING_SWITCH.remove(player.getUUID());
        JUMP_SLOW_UNTIL.remove(player.getUUID());
    }

    /** Applies the configured death rule to persistent injuries and harmful status effects. */
    public static void restoreAfterDeath(ServerPlayer original, ServerPlayer replacement,
                                         boolean retainEffects) {
        clear(original);
        clear(replacement);
        replacement.getPersistentData().remove(ROOT);
        if (!retainEffects) {
            replacement.removeAllEffects();
            return;
        }

        CompoundTag injuries = original.getPersistentData().getCompound(ROOT);
        if (!injuries.isEmpty()) {
            replacement.getPersistentData().put(ROOT, injuries.copy());
        }
        for (MobEffectInstance effect : original.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                replacement.addEffect(new MobEffectInstance(effect));
            }
        }
        refreshAllEffects(replacement);
    }

    /** Applies the strict >5-block, 90% leg-break rule. */
    public static boolean onFall(ServerPlayer player, float distance) {
        if (!FallInjuryRule.shouldBreakLeg(distance, player.getRandom().nextDouble())) return false;
        Snapshot injury = snapshot(player);
        LegInjuryRule.Leg target = LegInjuryRule.selectBreakTarget(
            injury.leftLeg() >= 100.0F, injury.rightLeg() >= 100.0F, player.getRandom().nextBoolean());
        if (target == LegInjuryRule.Leg.NONE) return false;
        BodyPart part = target == LegInjuryRule.Leg.LEFT ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
        setPoints(player, part, 100.0F);
        refreshEffect(player, part);
        refreshStatusEffects(player);
        return true;
    }

    static BodyPart resolveHitPart(ServerPlayer player, Entity direct, Entity attacker) {
        double normalizedY;
        if (direct instanceof Projectile) {
            normalizedY = (direct.getY() - player.getY()) / Math.max(0.1D, player.getBbHeight());
        } else {
            int seed = player.tickCount * 31 + (attacker == null ? 0 : attacker.getId() * 17);
            normalizedY = 0.20D + Math.floorMod(seed, 70) / 100.0D;
        }
        normalizedY = Math.max(0.0D, Math.min(1.0D, normalizedY));
        if (normalizedY >= 0.80D) return BodyPart.HEAD;
        boolean left = isLeftSide(player, direct != null ? direct : attacker);
        if (normalizedY < 0.34D) return left ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
        if (normalizedY < 0.72D) {
            int selector = Math.floorMod(player.tickCount + (direct == null ? 0 : direct.getId()), 5);
            if (selector < 2) return left ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM;
        }
        return normalizedY < 0.54D ? BodyPart.ABDOMEN : BodyPart.CHEST;
    }

    public static boolean isHeadshot(ServerPlayer player, DamageSource source) {
        return player != null && source != null
            && resolveHitPart(player, source.getDirectEntity(), source.getEntity()) == BodyPart.HEAD;
    }

    private static boolean isLeftSide(ServerPlayer player, Entity source) {
        if (source == null) return false;
        Vec3 delta = source.position().subtract(player.position());
        double yaw = Math.toRadians(player.getYRot());
        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        return delta.dot(right) < 0.0D;
    }

    private static void addPoints(ServerPlayer player, BodyPart part, float amount) {
        setPoints(player, part, points(player, part) + amount);
    }

    private static float points(ServerPlayer player, BodyPart part) {
        return Math.max(0.0F, Math.min(100.0F,
            player.getPersistentData().getCompound(ROOT).getFloat(part.key)));
    }

    private static void setPoints(ServerPlayer player, BodyPart part, float value) {
        var data = player.getPersistentData().getCompound(ROOT);
        data.putFloat(part.key, Math.max(0.0F, Math.min(100.0F, value)));
        player.getPersistentData().put(ROOT, data);
    }

    private static int bleedingStacks(ServerPlayer player) {
        return Math.max(0, Math.min(MAX_BLEEDING_STACKS,
            player.getPersistentData().getCompound(ROOT).getInt("bleeding_stacks")));
    }

    private static void setBleedingStacks(ServerPlayer player, int value) {
        var data = player.getPersistentData().getCompound(ROOT);
        data.putInt("bleeding_stacks", Math.max(0, Math.min(MAX_BLEEDING_STACKS, value)));
        player.getPersistentData().put(ROOT, data);
    }

    private static boolean legFissure(ServerPlayer player, boolean left) {
        return player.getPersistentData().getCompound(ROOT)
            .getBoolean(left ? "left_leg_fissure" : "right_leg_fissure");
    }

    private static void setLegFissure(ServerPlayer player, boolean left, boolean value) {
        var data = player.getPersistentData().getCompound(ROOT);
        data.putBoolean(left ? "left_leg_fissure" : "right_leg_fissure", value);
        player.getPersistentData().put(ROOT, data);
    }

    private static boolean hasLegFissure(ServerPlayer player) {
        return legFissure(player, true) || legFissure(player, false);
    }

    private static void clearOneLegFissure(ServerPlayer player) {
        if (legFissure(player, true)) setLegFissure(player, true, false);
        else setLegFissure(player, false, false);
    }

    private static void maybeCreateLegFissure(ServerPlayer player, Snapshot injury, float chance) {
        if (player.getRandom().nextFloat() >= chance) return;
        boolean leftCandidate = injury.leftLeg() >= 100.0F && !legFissure(player, true);
        boolean rightCandidate = injury.rightLeg() >= 100.0F && !legFissure(player, false);
        if (!leftCandidate && !rightCandidate) return;
        boolean left = leftCandidate && (!rightCandidate || player.getRandom().nextBoolean());
        setLegFissure(player, left, true);
        refreshStatusEffects(player);
    }

    static boolean isBulletLike(Entity direct) {
        if (direct == null) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType());
        String className = direct.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        String path = id == null ? "" : id.getPath().toLowerCase(java.util.Locale.ROOT);
        String namespace = id == null ? "" : id.getNamespace().toLowerCase(java.util.Locale.ROOT);
        return className.contains("bullet") || path.contains("bullet")
            || "tacz".equals(namespace) || namespace.contains("tactical");
    }

    private static void refreshAllEffects(ServerPlayer player) {
        for (BodyPart part : BodyPart.values()) refreshEffect(player, part);
        refreshStatusEffects(player);
        refreshVanillaConsequences(player);
    }

    private static void refreshStatusEffects(ServerPlayer player) {
        Snapshot injury = snapshot(player);
        setDerivedEffect(player, ModEffects.HEAD_PAIN, injury.head() >= 100.0F, 0);
        setDerivedEffect(player, ModEffects.CHEST_PAIN, injury.chest() >= 100.0F, 0);
        setDerivedEffect(player, ModEffects.ABDOMINAL_PAIN, injury.abdomen() >= 100.0F, 0);
        int brokenArms = (injury.leftArm() >= 100.0F ? 1 : 0)
            + (injury.rightArm() >= 100.0F ? 1 : 0);
        setDerivedEffect(player, ModEffects.ARM_FRACTURE, brokenArms > 0,
            Math.max(0, brokenArms - 1));
        // Legs are represented independently by LEFT_LEG_INJURY and RIGHT_LEG_INJURY.
        // Remove the legacy aggregate effects so one injured leg never produces
        // duplicate "leg fracture/fissure" HUD entries.
        player.removeEffect(ModEffects.LEG_FRACTURE);
        player.removeEffect(ModEffects.LEG_FISSURE);
        int bleeding = bleedingStacks(player);
        setDerivedEffect(player, ModEffects.BLEEDING, bleeding > 0,
            Math.max(0, bleeding - 1));
    }

    private static void setDerivedEffect(ServerPlayer player, Holder<MobEffect> effect,
                                         boolean active, int amplifier) {
        if (!active) {
            player.removeEffect(effect);
            return;
        }
        MobEffectInstance existing = player.getEffect(effect);
        if (existing != null && existing.getAmplifier() == amplifier
            && existing.getDuration() == -1) {
            return;
        }
        player.addEffect(new MobEffectInstance(effect, -1, amplifier,
            false, true, true));
    }

    private static void applyAttributeConsequences(ServerPlayer player, Snapshot injury, boolean masked) {
        // Remove the old fixed-point modifiers so existing worlds migrate cleanly.
        setAttributeModifier(player, Attributes.MAX_HEALTH, HEAD_PAIN_HEALTH, 0.0D,
            AttributeModifier.Operation.ADD_VALUE);
        setAttributeModifier(player, Attributes.MAX_HEALTH, CHEST_PAIN_HEALTH, 0.0D,
            AttributeModifier.Operation.ADD_VALUE);
        setAttributeModifier(player, Attributes.MAX_HEALTH, ABDOMINAL_PAIN_HEALTH, 0.0D,
            AttributeModifier.Operation.ADD_VALUE);
        HealthPenaltyRulesData healthRules = HealthPenaltyRulesData.get(player.server);
        double healthPenalty = HealthPenaltyRule.fraction(masked,
            player.hasEffect(MobEffects.WEAKNESS),
            DownedManager.hasYellowRescueHealthPenalty(player),
            injury.head(), injury.chest(), injury.abdomen(),
            healthRules.chestFraction(), healthRules.yellowRescueFraction());
        setAttributeModifier(player, Attributes.MAX_HEALTH, WEAKNESS_HEALTH, -healthPenalty,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        boolean armBroken = injury.leftArm() >= 100.0F || injury.rightArm() >= 100.0F;
        setAttributeModifier(player, Attributes.BLOCK_BREAK_SPEED, ARM_FRACTURE_BREAK_SPEED,
            !masked && armBroken ? -0.20D : 0.0D,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        boolean legBroken = injury.leftLeg() >= 100.0F || injury.rightLeg() >= 100.0F;
        setAttributeModifier(player, Attributes.JUMP_STRENGTH, LEG_FRACTURE_JUMP,
            !masked && legBroken ? -0.10D : 0.0D,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void setAttributeModifier(ServerPlayer player, Holder<Attribute> attribute,
                                             ResourceLocation modifierId, double amount,
                                             AttributeModifier.Operation operation) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(modifierId);
        if (Math.abs(amount) > 0.0001D) {
            instance.addTransientModifier(new AttributeModifier(modifierId, amount, operation));
        }
    }

    private static void refreshEffect(ServerPlayer player, BodyPart part) {
        if (points(player, part) < 100.0F) {
            player.removeEffect(part.effect());
            return;
        }
        boolean showInHud = part == BodyPart.LEFT_LEG || part == BodyPart.RIGHT_LEG;
        MobEffectInstance existing = player.getEffect(part.effect());
        if (existing != null && existing.showIcon() == showInHud
            && existing.getDuration() == -1) return;
        if (existing != null) player.removeEffect(part.effect());
        player.addEffect(new MobEffectInstance(part.effect(), -1, 0,
            false, false, showInHud));
    }

    private static void setSelected(ServerPlayer player, int slot) {
        player.getInventory().selected = slot;
        player.connection.send(new ClientboundSetCarriedItemPacket(slot));
    }

    enum BodyPart {
        HEAD("head") { @Override net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect() { return ModEffects.HEAD_INJURY; } },
        CHEST("chest") { @Override net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect() { return ModEffects.CHEST_INJURY; } },
        ABDOMEN("abdomen") { @Override net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect() { return ModEffects.ABDOMEN_INJURY; } },
        LEFT_ARM("left_arm") { @Override net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect() { return ModEffects.LEFT_ARM_INJURY; } },
        RIGHT_ARM("right_arm") { @Override net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect() { return ModEffects.RIGHT_ARM_INJURY; } },
        LEFT_LEG("left_leg") { @Override net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect() { return ModEffects.LEFT_LEG_INJURY; } },
        RIGHT_LEG("right_leg") { @Override net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect() { return ModEffects.RIGHT_LEG_INJURY; } },
        WHOLE_BODY("whole_body") { @Override net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect() { return ModEffects.WHOLE_BODY_INJURY; } };

        private static final BodyPart[] LOCAL_PARTS = {
            HEAD, CHEST, ABDOMEN, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG
        };

        private final String key;
        BodyPart(String key) { this.key = key; }
        abstract net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect();
    }

    public record Snapshot(float head, float chest, float abdomen, float leftArm, float rightArm,
                           float leftLeg, float rightLeg, float wholeBody) {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("xero_delta", path);
    }

    private record PendingSwitch(int previousSlot, int targetSlot, int releaseTick) {
    }
}
