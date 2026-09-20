package com.xtdpotato.xero_delta.item;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(XeroDelta.MOD_ID);

    public static final DeferredItem<Item> SAFETY_BOX_2X1 = ITEMS.register("safety_box_2x1",
        () -> new SafetyBoxItem(2, 1, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
            .component(ModDataComponents.GRID_CONTENTS.get(), List.of())));

    public static final DeferredItem<Item> SAFETY_BOX_2X2 = ITEMS.register("safety_box_2x2",
        () -> new SafetyBoxItem(2, 2, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
            .component(ModDataComponents.GRID_CONTENTS.get(), List.of())));

    public static final DeferredItem<Item> SAFETY_BOX_3X2 = ITEMS.register("safety_box_3x2",
        () -> new SafetyBoxItem(3, 2, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            .component(ModDataComponents.GRID_CONTENTS.get(), List.of())));

    public static final DeferredItem<Item> SAFETY_BOX_3X3 = ITEMS.register("safety_box_3x3",
        () -> new SafetyBoxItem(3, 3, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            .component(ModDataComponents.GRID_CONTENTS.get(), List.of())));

    public static final DeferredItem<Item> SAFETY_BOX_4X2 = ITEMS.register("safety_box_4x2",
        () -> new SafetyBoxItem(4, 2, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            .component(ModDataComponents.GRID_CONTENTS.get(), List.of())));

    public static final DeferredItem<BlockItem> TRADING_MARKET = ITEMS.register("trading_market",
        () -> new BlockItem(ModBlocks.TRADING_MARKET.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredItem<BlockItem> PERSONAL_WAREHOUSE = ITEMS.register("personal_warehouse",
        () -> new BlockItem(ModBlocks.PERSONAL_WAREHOUSE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredItem<BlockItem> RECYCLING_STATION = ITEMS.register("recycling_station",
        () -> new BlockItem(ModBlocks.RECYCLING_STATION.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredItem<Item> DAR_ASSAULT_CHEST_RIG = ITEMS.register("dar_assault_chest_rig",
        () -> new DeltaPackItem("chest_rig", 4, 6, 0.01D,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
                .component(ModDataComponents.GRID_CONTENTS.get(), List.of())));
    public static final DeferredItem<Item> GTO_HEAVY_TACTICAL_PACK = ITEMS.register("gto_heavy_tactical_pack",
        () -> new DeltaPackItem("backpack", 5, 9, 0.01D,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
                .component(ModDataComponents.GRID_CONTENTS.get(), List.of())));
    public static final DeferredItem<Item> DELTA_CARD_HOLDER = ITEMS.register("delta_card_holder",
        () -> new DeltaPackItem("card_holder", 3, 3, 0.0D,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
                .component(ModDataComponents.GRID_CONTENTS.get(), List.of())));

    public static final DeferredItem<Item> BATTLEFIELD_MEDICAL_KIT =
        ITEMS.register("battlefield_medical_kit", () -> new MedicalItem(
            MedicalTreatment.TRAUMA_MINOR, new Item.Properties()
                .durability(BattlefieldMedicalKitRules.MAX_DURABILITY)));
    public static final DeferredItem<Item> OUTDOOR_MEDICAL_KIT =
        ITEMS.register("outdoor_medical_kit", () -> new MedicalItem(
            MedicalTreatment.TRAUMA_MINOR, new Item.Properties()
                .durability(OutdoorMedicalKitRules.MAX_DURABILITY)));
    public static final DeferredItem<Item> DEK_FIELD_SURGERY_KIT =
        medical("dek_field_surgery_kit", MedicalTreatment.TRAUMA_MAJOR);
    public static final DeferredItem<Item> DVE_PAINKILLERS =
        medical("dve_painkillers", MedicalTreatment.PAIN_RELIEF_SHORT);
    public static final DeferredItem<Item> FIELD_FIRST_AID_KIT =
        medical("field_first_aid_kit", MedicalTreatment.TRAUMA_AND_BLEEDING_MINOR);
    public static final DeferredItem<Item> TACTICAL_QUICK_RELEASE_SURGERY_KIT =
        medical("tactical_quick_release_surgery_kit", MedicalTreatment.TRAUMA_MAJOR);
    public static final DeferredItem<Item> BOTTLED_ANTIBIOTICS =
        medical("bottled_antibiotics", MedicalTreatment.PAIN_RELIEF_SHORT);
    public static final DeferredItem<Item> STRONG_INJECTOR =
        medical("strong_injector", MedicalTreatment.PAIN_RELIEF_LONG);
    public static final DeferredItem<Item> VEHICLE_FIRST_AID_KIT =
        medical("vehicle_first_aid_kit", MedicalTreatment.TRAUMA_AND_BLEEDING_MAJOR);
    public static final DeferredItem<Item> CAT_TOURNIQUET =
        medical("cat_tourniquet", MedicalTreatment.BLEEDING_MAJOR);
    public static final DeferredItem<Item> SIMPLE_SURGERY_KIT =
        medical("simple_surgery_kit", MedicalTreatment.TRAUMA_MINOR);
    public static final DeferredItem<Item> EXTENDED_RELEASE_PAINKILLERS =
        medical("extended_release_painkillers", MedicalTreatment.PAIN_RELIEF_LONG);
    public static final DeferredItem<Item> SIMPLE_INJECTOR =
        medical("simple_injector", MedicalTreatment.PAIN_RELIEF_SHORT);
    public static final DeferredItem<Item> ELASTIC_BANDAGE =
        medical("elastic_bandage", MedicalTreatment.BLEEDING_MINOR);

    public static final DeferredItem<Item> ADVANCED_ARMOR_REPAIR_COMBO =
        repair("advanced_armor_repair_combo", RepairKitItem.Target.ARMOR, 1.00F);
    public static final DeferredItem<Item> ADVANCED_HELMET_REPAIR_COMBO =
        repair("advanced_helmet_repair_combo", RepairKitItem.Target.HELMET, 1.00F);
    public static final DeferredItem<Item> PRECISION_ARMOR_REPAIR_KIT =
        repair("precision_armor_repair_kit", RepairKitItem.Target.ARMOR, 0.75F);
    public static final DeferredItem<Item> PRECISION_HELMET_REPAIR_KIT =
        repair("precision_helmet_repair_kit", RepairKitItem.Target.HELMET, 0.75F);
    public static final DeferredItem<Item> STANDARD_ARMOR_REPAIR_KIT =
        repair("standard_armor_repair_kit", RepairKitItem.Target.ARMOR, 0.50F);
    public static final DeferredItem<Item> STANDARD_HELMET_REPAIR_KIT =
        repair("standard_helmet_repair_kit", RepairKitItem.Target.HELMET, 0.50F);
    public static final DeferredItem<Item> HOMEMADE_ARMOR_REPAIR_KIT =
        repair("homemade_armor_repair_kit", RepairKitItem.Target.ARMOR, 0.25F);
    public static final DeferredItem<Item> HOMEMADE_HELMET_REPAIR_KIT =
        repair("homemade_helmet_repair_kit", RepairKitItem.Target.HELMET, 0.25F);

    public static final DeferredItem<Item> STAMINA_BOOSTER =
        medical("stamina_booster", MedicalTreatment.STAMINA_ATTRIBUTE_BOOST);
    public static final DeferredItem<Item> PERCEPTION_BOOSTER =
        medical("perception_booster", MedicalTreatment.HEARING_BOOST);
    public static final DeferredItem<Item> M2_MUSCLE_INJECTOR =
        medical("m2_muscle_injector", MedicalTreatment.WEIGHT_BOOST);
    public static final DeferredItem<Item> STAMINA_ACTIVATION_INJECTION =
        medical("stamina_activation_injection", MedicalTreatment.STAMINA_ATTRIBUTE_BOOST);
    public static final DeferredItem<Item> OE2_COMBAT_STIMULANT =
        medical("oe2_combat_stimulant", MedicalTreatment.STAMINA_CAPACITY_BOOST);
    public static final DeferredItem<Item> PERCEPTION_ACTIVATION_INJECTION =
        medical("perception_activation_injection", MedicalTreatment.HEARING_BOOST);
    public static final DeferredItem<Item> M1_MUSCLE_BOOSTER =
        medical("m1_muscle_booster", MedicalTreatment.WEIGHT_BOOST);
    public static final DeferredItem<Item> NOREPINEPHRINE =
        medical("norepinephrine", MedicalTreatment.STAMINA_CAPACITY_BOOST);

    public static final List<DeferredItem<? extends Item>> EXTRA_ITEMS = List.of(
        DAR_ASSAULT_CHEST_RIG, GTO_HEAVY_TACTICAL_PACK, DELTA_CARD_HOLDER,
        BATTLEFIELD_MEDICAL_KIT, OUTDOOR_MEDICAL_KIT, DEK_FIELD_SURGERY_KIT,
        DVE_PAINKILLERS, FIELD_FIRST_AID_KIT, TACTICAL_QUICK_RELEASE_SURGERY_KIT,
        BOTTLED_ANTIBIOTICS, STRONG_INJECTOR, VEHICLE_FIRST_AID_KIT, CAT_TOURNIQUET,
        SIMPLE_SURGERY_KIT, EXTENDED_RELEASE_PAINKILLERS, SIMPLE_INJECTOR, ELASTIC_BANDAGE,
        ADVANCED_ARMOR_REPAIR_COMBO, ADVANCED_HELMET_REPAIR_COMBO,
        PRECISION_ARMOR_REPAIR_KIT, PRECISION_HELMET_REPAIR_KIT,
        STANDARD_ARMOR_REPAIR_KIT, STANDARD_HELMET_REPAIR_KIT,
        HOMEMADE_ARMOR_REPAIR_KIT, HOMEMADE_HELMET_REPAIR_KIT,
        STAMINA_BOOSTER, PERCEPTION_BOOSTER, M2_MUSCLE_INJECTOR,
        STAMINA_ACTIVATION_INJECTION, OE2_COMBAT_STIMULANT,
        PERCEPTION_ACTIVATION_INJECTION, M1_MUSCLE_BOOSTER, NOREPINEPHRINE
    );

    public static final List<DeferredItem<? extends Item>> OFFICIAL_ITEMS =
        OfficialItemCatalog.registerAll(ITEMS);

    private static DeferredItem<Item> repair(String id, RepairKitItem.Target target,
                                             float repairFraction) {
        return ITEMS.register(id, () -> new RepairKitItem(target, repairFraction,
            new Item.Properties().durability(ConsumableProfile.get(id).capacity())));
    }

    private static DeferredItem<Item> medical(String id, MedicalTreatment treatment) {
        return ITEMS.register(id, () -> new MedicalItem(treatment,
            ConsumableProfile.get(id).capacity() > 0
                ? new Item.Properties().durability(ConsumableProfile.get(id).capacity())
                : new Item.Properties().stacksTo(16)));
    }

    public static void register(IEventBus eventBus) { ITEMS.register(eventBus); }
}
