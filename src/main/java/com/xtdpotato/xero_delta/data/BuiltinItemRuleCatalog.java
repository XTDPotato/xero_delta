package com.xtdpotato.xero_delta.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Built-in, recipe-independent baseline for the automatic item rule commands. */
public final class BuiltinItemRuleCatalog {
    public record Rule(ItemSize size, String quality, long price,
                       boolean stretchTexture, boolean fixedAutomaticPrice) {
        public Rule(ItemSize size, String quality, long price) {
            this(size, quality, price, false, false);
        }
    }

    private static final Map<String, Rule> EXACT = new HashMap<>();

    static {
        putExact("xero_delta:dar_assault_chest_rig", 2, 2, "gold", 243_000L);
        putExact("xero_delta:gto_heavy_tactical_pack", 3, 3, "red", 1_084_000L);

        putScaledProduct("tactical_quick_release_surgery_kit", 2, 1, "green", 9_300L);
        putScaledProduct("advanced_armor_repair_combo", 2, 2, "red", 370_000L);
        putScaledProduct("advanced_helmet_repair_combo", 2, 2, "red", 350_000L);
        putScaledProduct("battlefield_medical_kit", 2, 2, "gold", 240_000L);
        putScaledProduct("dek_field_surgery_kit", 3, 1, "purple", 30_000L);
        putScaledProduct("field_first_aid_kit", 2, 1, "blue", 14_000L);
        putScaledProduct("homemade_armor_repair_kit", 2, 1, "blue", 20_000L);
        putScaledProduct("homemade_helmet_repair_kit", 2, 1, "blue", 16_000L);
        putScaledProduct("outdoor_medical_kit", 3, 1, "purple", 79_000L);
        putScaledProduct("precision_armor_repair_kit", 2, 2, "gold", 180_000L);
        putScaledProduct("precision_helmet_repair_kit", 3, 1, "gold", 130_000L);
        putScaledProduct("standard_armor_repair_kit", 3, 1, "purple", 74_000L);
        putScaledProduct("standard_helmet_repair_kit", 2, 1, "purple", 60_000L);

        mythic("nether_star", 1, 1, 30_301_010L);
        mythic("heart_of_the_sea", 1, 1, 20_260_100L);
        mythic("conduit", 2, 2, 23_333_330L);
        mythic("dragon_egg", 2, 2, MaterialValueDefaults.DRAGON_EGG);
        mythic("dragon_head", 1, 1, 2_500_000L);
        mythic("elytra", 3, 2, 4_500_000L);
        mythic("trident", 2, 3, 2_800_000L);
        mythic("enchanted_golden_apple", 2, 2, 2_600_000L);
        mythic("totem_of_undying", 2, 2, 2_200_000L);
        mythic("dragon_breath", 1, 2, 1_800_000L);
        mythic("ancient_debris", 2, 2, 800_000L);
        mythic("netherite_scrap", 1, 1, 400_000L);
        mythic("netherite_ingot", 2, 1, 1_200_000L);
        mythic("netherite_upgrade_smithing_template", 2, 2, 1_500_000L);
        mythic("echo_shard", 1, 1, 1_000_000L);
        mythic("heavy_core", 2, 2, 3_500_000L);
        mythic("mace", 2, 3, 3_200_000L);
        mythic("silence_armor_trim_smithing_template", 1, 1, 2_000_000L);
        mythic("ward_armor_trim_smithing_template", 1, 1, 1_500_000L);
        mythic("flow_armor_trim_smithing_template", 1, 1, 1_000_000L);

        legendary("diamond", 1, 1, MaterialValueDefaults.DIAMOND);
        legendary("diamond_block", 2, 2, 450_000L);
        legendary("emerald", 1, 1, 40_000L);
        legendary("emerald_block", 2, 2, 350_000L);
        legendary("golden_apple", 2, 2, 150_000L);
        legendary("diamond_horse_armor", 2, 2, 300_000L);
        legendary("shulker_shell", 1, 1, 60_000L);
        legendary("shulker_box", 2, 2, 120_000L);
        legendary("sponge", 2, 2, 100_000L);
        legendary("nautilus_shell", 1, 1, 30_000L);
        legendary("music_disc_pigstep", 2, 1, 300_000L);
        legendary("music_disc_otherside", 2, 1, 250_000L);
        legendary("music_disc_relic", 2, 1, 300_000L);
        legendary("music_disc_creator", 2, 1, 300_000L);
        legendary("music_disc_precipice", 2, 1, 250_000L);

        epic("iron_ingot", 2, 1, MaterialValueDefaults.IRON_INGOT);
        epic("gold_ingot", 2, 1, MaterialValueDefaults.GOLD_INGOT);
        epic("iron_block", 2, 2, MaterialValueDefaults.IRON_BLOCK);
        epic("gold_block", 2, 2, 180_000L);
        epic("iron_nugget", 1, 1, MaterialValueDefaults.IRON_NUGGET);
        epic("gold_nugget", 1, 1, 2_000L);
        epic("raw_iron", 1, 1, MaterialValueDefaults.RAW_IRON);
        rare("raw_gold", 1, 1, MaterialValueDefaults.RAW_GOLD);
        epic("lapis_lazuli", 1, 1, 8_000L);
        epic("lapis_block", 2, 2, 70_000L);
        epic("amethyst_shard", 1, 1, MaterialValueDefaults.AMETHYST_SHARD);
        epic("amethyst_block", 2, 2, 40_000L);
        epic("crying_obsidian", 2, 2, 25_000L);
        epic("obsidian", 2, 2, 10_000L);
        epic("blaze_rod", 1, 2, 15_000L);
        epic("breeze_rod", 1, 2, 20_000L);
        epic("blaze_powder", 1, 1, 8_000L);
        epic("magma_cream", 1, 1, 8_000L);
        epic("phantom_membrane", 2, 1, 30_000L);
        epic("prismarine_shard", 1, 1, 5_000L);
        epic("prismarine_crystals", 1, 1, 5_000L);
        epic("goat_horn", 2, 1, 50_000L);
        epic("decorated_pot_sherd", 1, 1, 10_000L);
        epic("ghast_tear", 1, 1, 20_000L);

        rare("redstone", 1, 1, 3_000L);
        rare("redstone_block", 2, 2, 27_000L);
        rare("redstone_torch", 1, 1, 3_000L);
        rare("repeater", 1, 1, 8_000L);
        rare("comparator", 1, 1, 10_000L);
        rare("piston", 2, 2, 10_000L);
        rare("sticky_piston", 2, 2, 15_000L);
        rare("copper_ingot", 2, 1, MaterialValueDefaults.COPPER_INGOT);
        rare("copper_block", 2, 2, 18_000L);
        rare("raw_copper", 1, 1, 1_500L);
        rare("quartz", 1, 1, 3_000L);
        rare("quartz_block", 2, 2, 27_000L);

        common("dirt", 1, 1, 1L);
        common("sand", 1, 1, 2L);
        common("cobblestone", 1, 1, 2L);
        common("oak_planks", 1, 1, 5L);
        common("oak_log", 1, 1, 10L);
        common("glass", 1, 1, 20L);
        common("coal", 1, 1, 100L);
        common("music_disc_13", 2, 1, 5_000L);
        common("music_disc_cat", 2, 1, 5_000L);
        common("music_disc_blocks", 2, 1, 5_000L);
        common("music_disc_chirp", 2, 1, 5_000L);
        common("music_disc_far", 2, 1, 5_000L);
        common("music_disc_mall", 2, 1, 5_000L);
        common("music_disc_mellohi", 2, 1, 5_000L);
        common("music_disc_stal", 2, 1, 5_000L);
        common("music_disc_strad", 2, 1, 5_000L);
        common("music_disc_ward", 2, 1, 5_000L);
        common("music_disc_wait", 2, 1, 5_000L);
        rare("music_disc_11", 2, 1, 20_000L);
        epic("music_disc_5", 2, 1, 80_000L);
        rare("coast_armor_trim_smithing_template", 1, 1, 50_000L);
        rare("dune_armor_trim_smithing_template", 1, 1, 60_000L);
        epic("eye_armor_trim_smithing_template", 1, 1, 150_000L);
        epic("rib_armor_trim_smithing_template", 1, 1, 200_000L);
        epic("spire_armor_trim_smithing_template", 1, 1, 200_000L);
        epic("vex_armor_trim_smithing_template", 1, 1, 250_000L);
    }

    private BuiltinItemRuleCatalog() {
    }

    public static Rule find(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Rule exact = EXACT.get(id);
        if (exact != null) return withSmithingTemplateSize(id, exact);

        String path = id.substring(id.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        if (path.endsWith("_smithing_template")) return new Rule(new ItemSize(2, 2), "blue", 60_000L);
        if (path.startsWith("music_disc_")) return new Rule(new ItemSize(2, 1), "gray", 5_000L);
        if (path.contains("enchanted_book")) return new Rule(new ItemSize(2, 1), "blue", 20_000L);
        if (path.contains("potion") || path.endsWith("_bottle")) return new Rule(new ItemSize(1, 2), "green", 3_000L);
        if (path.contains("horse_armor")) return new Rule(new ItemSize(2, 2), "green", 5_000L);
        if (path.contains("shulker_box") || path.endsWith("_chest")) return new Rule(new ItemSize(2, 2), "gold", 120_000L);
        if (path.contains("backpack")) return backpackRule(path);
        if (id.startsWith("tacz:")) return taczRule(path);
        if (id.startsWith("create:")) return createRule(path);
        if (id.startsWith("ae2:")) return ae2Rule(path);
        if (containsAny(path, "mekanism", "thermal", "industrial", "techreborn")) return technologyRule(path);
        return materialOrEquipmentRule(path);
    }

    /** Returns only an explicit catalogue entry, never a heuristic fallback. */
    public static Rule explicit(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return explicitId(id);
    }

    /** Pure id lookup used by formula tooling and unit tests. */
    public static Rule explicitId(String id) {
        if (id == null || id.isBlank()) return null;
        Rule exact = EXACT.get(id);
        if (exact != null) return withSmithingTemplateSize(id, exact);
        ResourceLocation itemId = ResourceLocation.tryParse(id);
        if (itemId != null && "sophisticatedbackpacks".equals(itemId.getNamespace())
            && itemId.getPath().toLowerCase(Locale.ROOT).contains("backpack")) {
            return backpackRule(itemId.getPath().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    /**
     * Built-in physical dimensions that must override stale automatic rules.
     * Explicit administrator rules remain authoritative.
     */
    public static ItemSize fixedSize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return fixedSizeId(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** New Xero equipment, medicine and repair artwork follows the grid footprint. */
    public static boolean shouldFitTextureAspect(ItemStack stack) {
        return false;
    }

    public static ItemSize fixedSizeId(String id) {
        if (id == null || id.isBlank()) return null;
        int separator = id.indexOf(':');
        String path = (separator >= 0 ? id.substring(separator + 1) : id)
            .toLowerCase(Locale.ROOT);
        return path.contains("painkiller") || path.contains("pain_killer")
            ? ItemSize.ONE : null;
    }

    private static Rule withSmithingTemplateSize(String id, Rule rule) {
        return id.endsWith("_smithing_template")
            ? new Rule(new ItemSize(2, 2), rule.quality(), rule.price())
            : rule;
    }

    public static Rule defaultRule(ItemStack stack) {
        Rule rule = find(stack);
        if (rule != null) return rule;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return new Rule(ItemSize.ONE, "gray", defaultPrice(id));
    }

    public static long defaultPrice(String itemId) {
        return 1L + Math.floorMod(itemId.hashCode(), 100);
    }

    private static Rule materialOrEquipmentRule(String path) {
        if (path.startsWith("netherite_")) {
            if (containsAny(path, "helmet", "chestplate", "leggings", "boots")) return armor(path, "red", 2_000_000L);
            if (containsAny(path, "sword", "pickaxe", "_axe", "shovel", "_hoe")) {
                return new Rule(new ItemSize(2, 3), "red", 2_000_000L);
            }
        }
        if (containsAny(path, "diamond_sword", "diamond_pickaxe", "diamond_axe", "diamond_shovel", "diamond_hoe")) {
            long value = path.endsWith("sword") ? 180_000L : path.endsWith("pickaxe") ? 150_000L
                : path.endsWith("axe") ? 160_000L : path.endsWith("shovel") ? 100_000L : 80_000L;
            return new Rule(new ItemSize(2, 3), "gold", value);
        }
        if (containsAny(path, "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots")) {
            long value = path.endsWith("helmet") ? 120_000L : path.endsWith("chestplate") ? 300_000L
                : path.endsWith("leggings") ? 220_000L : 100_000L;
            return armor(path, "gold", value);
        }
        if (path.startsWith("iron_")) return ironRule(path);
        if (path.startsWith("golden_")) return goldRule(path);
        if (path.startsWith("wooden_")) return toolRule(path, "green", 500L);
        if (path.startsWith("stone_")) return toolRule(path, "green", 1_500L);
        if (containsAny(path, "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots")) {
            long value = path.endsWith("helmet") || path.endsWith("boots") ? 1_000L
                : path.endsWith("chestplate") ? 3_000L : 2_500L;
            return armor(path, "green", value);
        }
        if (path.contains("chainmail_")) return armor(path, "green", 10_000L);
        if (path.endsWith("_ingot")) return new Rule(new ItemSize(2, 1), "purple", 15_000L);
        if (containsAny(path, "sword", "pickaxe", "_axe", "shovel", "_hoe", "trident", "mace")) {
            return new Rule(new ItemSize(2, 3), "green", 2_000L);
        }
        if (path.contains("shears")) return new Rule(new ItemSize(1, 2), "green", 1_500L);
        if (path.contains("brush")) return new Rule(new ItemSize(2, 2), "green", 3_000L);
        return null;
    }

    private static Rule ironRule(String path) {
        if (containsAny(path, "helmet", "chestplate", "leggings", "boots")) {
            long value = path.endsWith("helmet") ? 25_000L : path.endsWith("chestplate") ? 60_000L
                : path.endsWith("leggings") ? 50_000L : 20_000L;
            return armor(path, "purple", value);
        }
        if (containsAny(path, "sword", "pickaxe", "_axe", "shovel", "_hoe")) {
            long value = path.endsWith("sword") || path.endsWith("pickaxe") ? 30_000L
                : path.endsWith("axe") ? 28_000L : path.endsWith("shovel") ? 20_000L : 18_000L;
            return new Rule(new ItemSize(2, 3), "purple", value);
        }
        return null;
    }

    private static Rule goldRule(String path) {
        if (containsAny(path, "sword", "pickaxe", "_axe", "shovel", "_hoe")) {
            long value = path.endsWith("sword") || path.endsWith("pickaxe") ? 20_000L
                : path.endsWith("axe") ? 18_000L : path.endsWith("shovel") ? 15_000L : 12_000L;
            return new Rule(new ItemSize(2, 3), "blue", value);
        }
        return null;
    }

    private static Rule armor(String path, String quality, long price) {
        return new Rule(new ItemSize(path.endsWith("chestplate") ? 3 : 2,
            path.endsWith("leggings") ? 3 : 2), quality, price);
    }

    private static Rule toolRule(String path, String quality, long price) {
        return containsAny(path, "sword", "pickaxe", "_axe", "shovel", "_hoe")
            ? new Rule(new ItemSize(2, 3), quality, price) : null;
    }

    private static Rule taczRule(String path) {
        if (containsAny(path, "ammo", "bullet", "cartridge")) return new Rule(ItemSize.ONE, "gray", 100L);
        if (containsAny(path, "pistol", "revolver", "glock", "deagle", "m1911")) return new Rule(new ItemSize(2, 1), "blue", 50_000L);
        if (containsAny(path, "smg", "mp5", "mp7", "vector", "ump", "p90", "uzi")) return new Rule(new ItemSize(2, 2), "purple", 200_000L);
        if (containsAny(path, "sniper", "awm", "awp", "m24", "m700", "mosin")) return new Rule(new ItemSize(2, 4), "gold", 1_000_000L);
        if (containsAny(path, "rifle", "ak", "m4", "m16", "hk416", "g36")) return new Rule(new ItemSize(2, 3), "purple", 500_000L);
        if (path.contains("gun")) return new Rule(new ItemSize(2, 3), "purple", 500_000L);
        return new Rule(ItemSize.ONE, "gray", 100L);
    }

    private static Rule backpackRule(String path) {
        if (path.contains("netherite")) return new Rule(new ItemSize(3, 3), "red", 2_000_000L);
        if (path.contains("diamond")) return new Rule(new ItemSize(3, 3), "gold", 800_000L);
        if (path.contains("gold")) return new Rule(new ItemSize(2, 2), "purple", 300_000L);
        if (path.contains("iron")) return new Rule(new ItemSize(2, 2), "blue", 150_000L);
        return new Rule(new ItemSize(2, 2), "green", 50_000L);
    }

    private static Rule createRule(String path) {
        if (containsAny(path, "precision_mechanism", "precision_component")) return new Rule(ItemSize.ONE, "gold", 100_000L);
        if (containsAny(path, "windmill", "water_wheel", "large_")) return new Rule(new ItemSize(3, 3), "purple", 200_000L);
        if (containsAny(path, "shaft", "rod")) return new Rule(new ItemSize(1, 2), "purple", 10_000L);
        if (containsAny(path, "mechanical", "mixer", "press", "roller")) return new Rule(new ItemSize(2, 2), "purple", 80_000L);
        if (path.contains("brass")) return new Rule(new ItemSize(path.contains("ingot") ? 2 : 1, 1), "blue", 30_000L);
        return new Rule(ItemSize.ONE, "blue", 5_000L);
    }

    private static Rule ae2Rule(String path) {
        if (path.contains("quantum")) return new Rule(new ItemSize(3, 3), "gold", 1_000_000L);
        if (path.contains("64k")) return new Rule(ItemSize.ONE, "gold", 800_000L);
        if (path.contains("16k")) return new Rule(ItemSize.ONE, "gold", 300_000L);
        if (containsAny(path, "4k", "1k", "drive", "terminal", "interface", "receiver")) return new Rule(new ItemSize(2, 2), "purple", 150_000L);
        return new Rule(ItemSize.ONE, "blue", 10_000L);
    }

    private static Rule technologyRule(String path) {
        if (containsAny(path, "ultimate", "fusion", "fission", "elite")) return new Rule(new ItemSize(3, 3), "gold", 2_000_000L);
        if (containsAny(path, "machine", "battery", "energy", "cell")) return new Rule(new ItemSize(2, 2), "purple", 150_000L);
        if (path.endsWith("_ingot")) return new Rule(new ItemSize(2, 1), "blue", 20_000L);
        return new Rule(ItemSize.ONE, "blue", 5_000L);
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static void mythic(String path, int width, int height, long price) { put(path, width, height, "red", price); }
    private static void legendary(String path, int width, int height, long price) { put(path, width, height, "gold", price); }
    private static void epic(String path, int width, int height, long price) { put(path, width, height, "purple", price); }
    private static void rare(String path, int width, int height, long price) { put(path, width, height, "blue", price); }
    private static void common(String path, int width, int height, long price) { put(path, width, height, "gray", price); }
    private static void put(String path, int width, int height, String quality, long price) {
        putExact("minecraft:" + path, width, height, quality, price);
    }

    private static void putExact(String id, int width, int height, String quality, long price) {
        EXACT.put(id, new Rule(new ItemSize(width, height), quality, price));
    }

    private static void putScaledProduct(String path, int width, int height,
                                         String quality, long price) {
        EXACT.put("xero_delta:" + path,
            new Rule(new ItemSize(width, height), quality, price, true, true));
    }
}
