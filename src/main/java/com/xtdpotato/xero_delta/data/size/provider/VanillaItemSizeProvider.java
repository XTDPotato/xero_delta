package com.xtdpotato.xero_delta.data.size.provider;

import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.ItemSizeRule;
import com.xtdpotato.xero_delta.data.size.ItemSizeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;

import java.util.Locale;

public final class VanillaItemSizeProvider implements ItemSizeProvider {
    @Override
    public String id() {
        return "vanilla";
    }

    @Override
    public Tier tier() {
        return Tier.AUTOMATIC;
    }

    @Override
    public ItemSizeRule resolve(ItemStack stack, ResourceLocation itemId, Integer recipeIngredientCount) {
        String path = itemId.getPath().toLowerCase(Locale.ROOT);
        if (stack.getItem() instanceof AnimalArmorItem armor
            && armor.getBodyType() == AnimalArmorItem.BodyType.EQUESTRIAN) {
            return rule(2, 2);
        }
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof DoorBlock) {
            return rule(2, 3);
        }
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CropBlock) {
            return ItemSizeRule.DEFAULT;
        }
        return classifyPath(path, stack.getItem() instanceof BlockItem);
    }

    static ItemSizeRule classifyPath(String path, boolean blockItem) {
        path = path.toLowerCase(Locale.ROOT);

        if (containsAny(path, "lilac", "rose_bush", "peony")) return rule(1, 2);
        if (isOneByOne(path)) return ItemSizeRule.DEFAULT;

        if (path.contains("horse_armor")) return rule(2, 2);
        if (path.equals("boat") || path.endsWith("_boat")
            || path.equals("raft") || path.endsWith("_raft")) return rule(2, 2);
        if (path.endsWith("_door") || path.equals("door")) return rule(2, 3);
        if (path.endsWith("_bed") || path.equals("bed")) return rule(3, 2);
        if (path.equals("amethyst_cluster")) return rule(2, 2);
        if (path.equals("leather")) return rule(2, 2);
        if (path.contains("minecart")) return rule(2, 2);
        if (path.endsWith("_smithing_template")) return rule(2, 2);
        if (path.equals("golden_apple") || path.equals("enchanted_golden_apple")) return rule(2, 2);
        if (path.contains("sword")) return rule(2, 3);

        if (path.contains("potion")
            || path.equals("honey_bottle") || path.equals("ominous_bottle")
            || path.endsWith("_torch") || path.equals("torch")
            || path.endsWith("_lever") || path.equals("lever")
            || path.endsWith("_lightning_rod") || path.equals("lightning_rod")
            || path.endsWith("_chain") || path.equals("chain")
            || path.equals("end_rod")) {
            return rule(1, 2);
        }
        if (path.endsWith("_wire") || path.equals("wire")
            || path.endsWith("_cable") || path.equals("cable")) {
            return ItemSizeRule.DEFAULT;
        }
        if (blockItem) return rule(2, 2);
        if (path.equals("apple") || path.equals("string") || path.endsWith("_string") || path.equals("snowball")
            || (path.endsWith("_egg") && !path.equals("dragon_egg"))
            || containsAny(path, "seed", "wheat", "melon_slice", "carrot", "beetroot", "potato", "radish",
                "berries", "nether_wart", "chorus_fruit")) {
            return ItemSizeRule.DEFAULT;
        }
        if (path.endsWith("_ingot") || path.equals("brick") || path.endsWith("_brick")) return rule(2, 1);
        if (containsAny(path, "chestplate", "leggings")) return rule(2, 3);
        if (containsAny(path, "helmet", "boots")) return rule(2, 2);
        if (path.contains("fishing_rod")) return rule(1, 2);
        if (containsAny(path, "trident", "mace", "bow", "crossbow")) return rule(1, 3);
        if (containsAny(path, "pickaxe", "_axe", "shovel", "_hoe", "shield", "shears")) return rule(2, 3);
        if (containsAny(path, "bucket", "bottle", "book", "map", "bundle")) return rule(2, 1);
        if (containsAny(path, "nugget", "gem", "diamond", "emerald", "quartz", "coal",
            "dust", "powder", "dye", "seed", "shard", "fragment")) return ItemSizeRule.DEFAULT;
        return null;
    }

    private static boolean isOneByOne(String path) {
        if (path.equals("ender_pearl") || path.equals("redstone") || path.equals("string") || path.equals("egg")
            || path.equals("bowl") || path.equals("fermented_spider_eye")
            || path.endsWith("_banner_pattern")
            || path.equals("sweet_berries") || path.equals("glow_berries")
            || path.equals("mushroom_stew") || path.equals("beetroot_soup")
            || path.equals("rabbit_stew") || path.equals("suspicious_stew")
            || path.equals("arrow") || path.endsWith("_arrow")
            || path.endsWith("_pressure_plate") || path.equals("pressure_plate")
            || path.equals("tripwire_hook")
            || path.endsWith("_trapdoor") || path.equals("trapdoor")
            || path.endsWith("_button") || path.equals("button")
            || path.equals("glow_lichen") || path.equals("painting")
            || path.equals("item_frame") || path.equals("glow_item_frame")
            || path.endsWith("_candle") || path.equals("candle")
            || path.endsWith("_head") || path.endsWith("_skull")
            || path.equals("snow") || path.equals("moss_carpet") || path.endsWith("_moss_carpet")
            || path.equals("pointed_dripstone")
            || path.equals("small_amethyst_bud") || path.equals("medium_amethyst_bud")
            || path.equals("large_amethyst_bud")
            || path.endsWith("_sapling") || path.equals("mangrove_propagule")
            || path.equals("azalea") || path.equals("flowering_azalea")
            || path.equals("bamboo") || path.equals("sugar_cane")
            || path.endsWith("_seeds") || path.endsWith("_seed") || path.equals("pitcher_pod")
            || path.equals("sculk_vein")) {
            return true;
        }
        return isFlowerOrMushroom(path);
    }

    private static boolean isFlowerOrMushroom(String path) {
        return path.equals("red_mushroom") || path.equals("brown_mushroom") || path.endsWith("_fungus")
            || path.equals("dandelion") || path.equals("poppy") || path.equals("blue_orchid")
            || path.equals("allium") || path.equals("azure_bluet") || path.endsWith("_tulip")
            || path.equals("oxeye_daisy") || path.equals("cornflower")
            || path.equals("lily_of_the_valley") || path.equals("wither_rose")
            || path.equals("torchflower") || path.equals("sunflower")
            || path.equals("pitcher_plant") || path.equals("pink_petals")
            || path.endsWith("_flower") || path.endsWith("_blossom") || path.endsWith("_eyeblossom");
    }

    private static ItemSizeRule rule(int width, int height) {
        return new ItemSizeRule(new ItemSize(width, height), true, false);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
