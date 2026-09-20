package com.xtdpotato.xero_delta.item;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/** One material per wearable so its player texture is never the vanilla iron plate. */
public final class OfficialArmorMaterials {
    private static final DeferredRegister<ArmorMaterial> MATERIALS =
        DeferredRegister.create(Registries.ARMOR_MATERIAL, XeroDelta.MOD_ID);

    private OfficialArmorMaterials() {
    }

    static Holder<ArmorMaterial> register(String id, ArmorItem.Type type) {
        DeferredHolder<ArmorMaterial, ArmorMaterial> holder = MATERIALS.register(id,
            () -> new ArmorMaterial(
                Map.of(type, type == ArmorItem.Type.CHESTPLATE ? 6 : 2),
                9,
                SoundEvents.ARMOR_EQUIP_IRON,
                () -> Ingredient.of(Items.IRON_INGOT),
                List.of(new ArmorMaterial.Layer(
                    ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, id))),
                0.0F,
                0.0F));
        return holder;
    }

    public static void register(IEventBus eventBus) {
        MATERIALS.register(eventBus);
    }
}
