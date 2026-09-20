package com.xtdpotato.xero_delta.tag;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class ModTags {
    public static final TagKey<Item> SAFETY_BOX = TagKey.create(Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "safety_box"));
    public static final TagKey<Item> CHEST_RIG = TagKey.create(Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath("curios", "chest_rig"));
    public static final TagKey<Item> BACKPACK = TagKey.create(Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath("curios", "backpack"));
}
