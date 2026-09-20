package com.xtdpotato.xero_delta.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Captures projectile hits before vanilla wear and applies Delta percentage armor wear afterwards. */
public final class BallisticArmorManager {
    private static final Map<UUID, PendingHit> PENDING = new HashMap<>();
    private static final String[] AMMO_ACCESSORS = {
        "getAmmoItem", "getAmmo", "getAmmoStack", "getProjectileItem",
        "getAmmoId", "getAmmoItemId", "getBulletId"
    };

    private BallisticArmorManager() {
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity direct = event.getSource().getDirectEntity();
        if (!PlayerInjuryManager.isBulletLike(direct)) return;
        PlayerInjuryManager.BodyPart part = PlayerInjuryManager.resolveHitPart(
            player, direct, event.getSource().getEntity());
        EquipmentSlot slot = part == PlayerInjuryManager.BodyPart.HEAD ? EquipmentSlot.HEAD
            : (part == PlayerInjuryManager.BodyPart.CHEST || part == PlayerInjuryManager.BodyPart.ABDOMEN)
                ? EquipmentSlot.CHEST : null;
        if (slot == null) return;
        ItemStack armor = player.getItemBySlot(slot);
        if (armor.isEmpty() || !armor.isDamageableItem() || armor.getMaxDamage() <= 1) return;
        PENDING.put(player.getUUID(), new PendingHit(slot, armor.getItem(), armor.getDamageValue(),
            event.getOriginalAmount(), resolveProjectileItem(direct), player.tickCount));
    }

    public static void onDamageApplied(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PendingHit hit = PENDING.remove(player.getUUID());
        if (hit == null || player.tickCount - hit.tick() > 1) return;
        ItemStack armor = player.getItemBySlot(hit.slot());
        if (armor.isEmpty() || armor.getItem() != hit.armorItem()
            || !armor.isDamageableItem() || armor.getMaxDamage() <= 1) return;
        if (hit.originalArmorDamage() >= armor.getMaxDamage() - 1) {
            armor.setDamageValue(armor.getMaxDamage() - 1);
            return;
        }

        String projectileQuality = hit.projectile().isEmpty()
            ? "gray" : ServerItemRules.getQualityFor(hit.projectile());
        String armorQuality = ServerItemRules.getQualityFor(armor);
        List<Double> custom = hit.projectile().isEmpty() ? null
            : BulletArmorRulesData.get(player.server).resolve(hit.projectile());
        double multiplier = BallisticArmorRules.multiplier(projectileQuality, armorQuality, custom);
        int wear = BallisticArmorRules.durabilityWear(armor.getMaxDamage(),
            hit.originalDamage(), multiplier);
        int nextDamage = Math.min(armor.getMaxDamage() - 1,
            hit.originalArmorDamage() + wear);
        armor.setDamageValue(Math.max(hit.originalArmorDamage(), nextDamage));
    }

    public static boolean isBrokenEquipment(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isDamageableItem()
            && stack.getMaxDamage() > 1 && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    private static ItemStack resolveProjectileItem(Entity direct) {
        if (direct == null) return ItemStack.EMPTY;
        if (direct instanceof AbstractArrow arrow) return arrow.getPickupItemStackOrigin().copy();
        ItemStack pick = direct.getPickResult();
        if (pick != null && !pick.isEmpty()) return pick.copy();
        for (String name : AMMO_ACCESSORS) {
            try {
                Method method = direct.getClass().getMethod(name);
                Object value = method.invoke(direct);
                ItemStack resolved = asItemStack(value);
                if (!resolved.isEmpty()) return resolved;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack asItemStack(Object value) {
        if (value instanceof ItemStack stack) return stack.copy();
        if (value instanceof Item item) return item.getDefaultInstance();
        ResourceLocation id = value instanceof ResourceLocation location ? location
            : value instanceof String text ? ResourceLocation.tryParse(text) : null;
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.get(id).getDefaultInstance();
    }

    private record PendingHit(EquipmentSlot slot, Item armorItem, int originalArmorDamage,
                              float originalDamage, ItemStack projectile, int tick) {
    }
}