package com.xtdpotato.xero_delta.item;

import com.xtdpotato.xero_delta.client.TacticalArmorClientExtensions;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** Wearable tactical armor with an item-specific texture and non-vanilla client model. */
final class TacticalArmorItem extends ArmorItem {
    TacticalArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(TacticalArmorClientExtensions.INSTANCE);
    }
}
