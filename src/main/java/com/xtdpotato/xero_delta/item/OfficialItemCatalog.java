package com.xtdpotato.xero_delta.item;

import com.xtdpotato.xero_delta.ModDataComponents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/** Items sourced from the official Delta icon collection. */
public final class OfficialItemCatalog {
    private static final String[] HELMETS = {
        "d6_tactical_helmet", "das_ballistic_helmet", "dich_training_helmet",
        "dich_1_tactical_helmet", "dich_9_heavy_helmet", "dro_tactical_helmet",
        "gn_veteran_heavy_night_vision_helmet", "gn_heavy_helmet",
        "gn_heavy_night_vision_helmet", "gt1_tactical_helmet", "gt5_commander_helmet",
        "h01_tactical_helmet", "h07_tactical_helmet", "h09_riot_helmet",
        "h70_elite_helmet", "mask_1_ironwall_helmet", "mc201_ballistic_helmet",
        "mc_ballistic_helmet", "mhs_tactical_helmet", "security_helmet",
        "boonie_hat", "riot_helmet", "retro_motorcycle_helmet",
        "outdoor_baseball_cap", "old_steel_helmet"
    };

    private static final String[] CHESTPLATES = {
        "dt_avs_chestplate", "fs_composite_chestplate", "ha_2_heavy_chestplate",
        "hmp_special_service_chestplate", "ht_tactical_chestplate", "hvk_2_chestplate",
        "hvk_quick_release_chestplate", "mk_2_tactical_chestplate", "tg_h_chestplate",
        "tg_tactical_chestplate", "security_chestplate", "simple_stab_chestplate",
        "king_kong_chestplate", "elite_ballistic_chestplate", "motorcycle_vest_chestplate",
        "nylon_chestplate", "lightweight_chestplate", "marksman_tactical_chestplate",
        "trick_mas_2_0_chestplate", "universal_tactical_chestplate",
        "assault_ballistic_chestplate", "samurai_ballistic_chestplate",
        "rhino_heavy_chestplate", "standard_ballistic_chestplate",
        "heavy_assault_chestplate"
    };

    private static final String[] CHEST_RIGS = {
        "d01_light_chest_rig", "drc_advanced_recon_chest_rig", "dsa_tactical_chest_rig",
        "g01_tactical_chest_rig", "gir_field_chest_rig", "hd3_tactical_chest_rig",
        "hk3_portable_chest_rig", "portable_chest_bag", "simple_attachment_chest_rig",
        "simple_load_bearing_chest_rig", "quick_recon_chest_rig",
        "assault_tactical_chest_rig", "light_tactical_chest_rig",
        "universal_tactical_chest_rig"
    };

    private static final String[] BACKPACKS = {
        "3h_tactical_backpack", "als_load_bearing_backpack", "d2_tactical_hiking_backpack",
        "d3_tactical_hiking_backpack", "d7_tactical_backpack", "dash_tactical_backpack",
        "dg_sports_backpack", "ga_field_backpack", "gt1_outdoor_hiking_backpack",
        "gt5_field_backpack", "hls_2_heavy_backpack", "map_recon_backpack",
        "large_hiking_backpack", "canvas_backpack", "camping_backpack", "travel_backpack",
        "nylon_sling_backpack", "light_outdoor_backpack", "survival_tactical_backpack",
        "raid_tactical_backpack", "sling_backpack", "field_hiking_backpack",
        "jungle_hunter_backpack", "sports_backpack", "tactical_quick_release_backpack",
        "heavy_hiking_backpack"
    };

    private OfficialItemCatalog() {
    }

    public static List<DeferredItem<? extends Item>> registerAll(DeferredRegister.Items items) {
        List<DeferredItem<? extends Item>> registered = new ArrayList<>();
        for (String id : HELMETS) {
            var material = OfficialArmorMaterials.register(id, ArmorItem.Type.HELMET);
            registered.add(items.register(id, () -> new TacticalArmorItem(
                material, ArmorItem.Type.HELMET,
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))));
        }
        for (String id : CHESTPLATES) {
            var material = OfficialArmorMaterials.register(id, ArmorItem.Type.CHESTPLATE);
            registered.add(items.register(id, () -> new TacticalArmorItem(
                material, ArmorItem.Type.CHESTPLATE,
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))));
        }
        for (String id : CHEST_RIGS) {
            registered.add(pack(items, id, "chest_rig", 4, 6));
        }
        for (String id : BACKPACKS) {
            registered.add(pack(items, id, "backpack", 5, 9));
        }
        registered.add(items.register("proteus_jammer",
            () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE))));
        return List.copyOf(registered);
    }

    private static DeferredItem<Item> pack(DeferredRegister.Items items, String id,
                                            String slot, int width, int height) {
        return items.register(id, () -> new DeltaPackItem(slot, width, height, 0.01D,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
                .component(ModDataComponents.GRID_CONTENTS.get(), List.of())));
    }
}
