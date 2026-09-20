package com.xtdpotato.xero_delta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Supplies the animated helmet or vest geometry for all tactical wearable items. */
public final class TacticalArmorClientExtensions implements IClientItemExtensions {
    public static final TacticalArmorClientExtensions INSTANCE = new TacticalArmorClientExtensions();

    private HumanoidModel<?> helmet;
    private HumanoidModel<?> chestplate;

    private TacticalArmorClientExtensions() {
    }

    @Override
    public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                                                   EquipmentSlot slot,
                                                   HumanoidModel<?> original) {
        return switch (slot) {
            case HEAD -> helmet();
            case CHEST -> chestplate();
            default -> original;
        };
    }

    private HumanoidModel<?> helmet() {
        if (helmet == null) {
            helmet = new TacticalArmorModel<>(Minecraft.getInstance().getEntityModels()
                .bakeLayer(TacticalArmorModel.HELMET_LAYER));
        }
        return helmet;
    }

    private HumanoidModel<?> chestplate() {
        if (chestplate == null) {
            chestplate = new TacticalArmorModel<>(Minecraft.getInstance().getEntityModels()
                .bakeLayer(TacticalArmorModel.CHESTPLATE_LAYER));
        }
        return chestplate;
    }
}
