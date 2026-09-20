package com.xtdpotato.xero_delta.data;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Values that depend on components, so they cannot be represented by one item-id auto rule. */
public final class DynamicItemValuation {
    public record Rule(String quality, long price) {
    }

    private DynamicItemValuation() {
    }

    public static Rule resolve(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(Items.ENCHANTED_BOOK)) return enchantmentBook(stack);
        if (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)
            || stack.is(Items.TIPPED_ARROW)) {
            return potion(stack);
        }
        if (stack.is(Items.FILLED_MAP) && isTreasureMap(stack)) {
            return new Rule("gold", MaterialValueDefaults.TREASURE_MAP);
        }
        return null;
    }

    private static boolean isTreasureMap(ItemStack stack) {
        MapDecorations decorations = stack.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
        return decorations.decorations().values().stream()
            .anyMatch(decoration -> decoration.type().value().explorationMapElement());
    }

    private static Rule enchantmentBook(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        String quality = "green";
        long price = 5_000L;
        for (var entry : enchantments.entrySet()) {
            String id = entry.getKey().unwrapKey().map(key -> key.location().getPath()).orElse("");
            int level = entry.getIntValue();
            Rule candidate = enchantmentRule(id, level);
            if (rank(candidate.quality()) > rank(quality) || candidate.price() > price) {
                quality = candidate.quality();
                price = Math.max(price, candidate.price());
            }
        }
        return new Rule(quality, price);
    }

    private static Rule enchantmentRule(String id, int level) {
        // Covers vanilla Binding Curse plus Soul Binding/Soulbound enchantments
        // supplied by content mods.
        if (id.equals("binding_curse") || id.contains("soul_binding") || id.contains("soulbound")) {
            return new Rule("red", 1_000_000L);
        }
        if (id.equals("mending")) return new Rule("red", 1_000_000L);
        if (id.equals("soul_speed") && level >= 3) return new Rule("red", 800_000L);
        if (id.equals("swift_sneak") && level >= 3) return new Rule("red", 800_000L);
        if (id.equals("frost_walker") && level >= 2) return new Rule("red", 500_000L);
        if (id.equals("riptide") && level >= 3) return new Rule("red", 700_000L);
        if (id.equals("channeling")) return new Rule("red", 600_000L);
        if (id.equals("infinity")) return new Rule("gold", 400_000L);
        if (id.equals("protection") && level >= 4) return new Rule("gold", 250_000L);
        if (id.equals("sharpness") && level >= 5) return new Rule("gold", 200_000L);
        if (id.equals("looting") && level >= 3) return new Rule("gold", 250_000L);
        if (id.equals("thorns") && level >= 3) return new Rule("gold", 300_000L);
        if (id.equals("power") && level >= 5) return new Rule("gold", 200_000L);
        if (id.equals("efficiency") && level >= 5) return new Rule("purple", 100_000L);
        if (id.equals("unbreaking") && level >= 3) return new Rule("purple", 150_000L);
        if (id.equals("multishot")) return new Rule("purple", 100_000L);
        if (id.equals("piercing") && level >= 4) return new Rule("purple", 100_000L);
        if (id.equals("knockback") && level >= 2) return new Rule("purple", 80_000L);
        if (id.equals("fire_aspect") && level >= 2) return new Rule("purple", 70_000L);
        if (id.equals("respiration") && level >= 3) return new Rule("blue", 30_000L);
        if (id.equals("depth_strider") && level >= 3) return new Rule("blue", 50_000L);
        if (id.equals("aqua_affinity")) return new Rule("blue", 30_000L);
        if (level >= 3) return new Rule("blue", 30_000L);
        if (level == 2) return new Rule("green", 15_000L);
        return new Rule("green", 5_000L);
    }

    private static Rule potion(ItemStack stack) {
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        String quality = stack.is(Items.LINGERING_POTION) ? "purple" : "green";
        long price = stack.is(Items.LINGERING_POTION) ? 80_000L : stack.is(Items.SPLASH_POTION) ? 2_000L : 3_000L;
        int strongestAmplifier = 0;
        int longestDuration = 0;
        boolean rareEffect = false;
        boolean strength = false;
        boolean regeneration = false;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            String path = id == null ? "" : id.getPath();
            strongestAmplifier = Math.max(strongestAmplifier, effect.getAmplifier());
            longestDuration = Math.max(longestDuration, effect.getDuration());
            rareEffect |= path.equals("invisibility") || path.equals("regeneration");
            strength |= path.equals("strength");
            regeneration |= path.equals("regeneration");
        }
        if (rareEffect) {
            quality = "blue";
            price = Math.max(price, 20_000L);
        }
        if (strongestAmplifier > 0) {
            quality = rank(quality) < rank("blue") ? "blue" : quality;
            price = Math.max(price, strength ? 15_000L : regeneration ? 20_000L : 10_000L);
        }
        if (longestDuration >= 3_600) {
            quality = "purple";
            price = Math.max(price, strength ? 50_000L : 40_000L);
        }
        return new Rule(quality, price);
    }

    private static int rank(String quality) {
        return switch (quality) {
            case "red" -> 5;
            case "gold" -> 4;
            case "purple" -> 3;
            case "blue" -> 2;
            case "green" -> 1;
            default -> 0;
        };
    }
}
