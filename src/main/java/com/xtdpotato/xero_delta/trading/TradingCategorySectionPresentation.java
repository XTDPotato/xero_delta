package com.xtdpotato.xero_delta.trading;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Locale;

/** Resolves editable HTML category labels/icons with creative-tab fallbacks. */
@OnlyIn(Dist.CLIENT)
public final class TradingCategorySectionPresentation {
    private TradingCategorySectionPresentation() {
    }

    public static Component label(String sectionId,
                                  List<TradingHtmlThemeParser.CategorySection> sections) {
        TradingHtmlThemeParser.CategorySection configured = find(sectionId, sections);
        if (configured != null && !configured.label().isBlank()) {
            return Component.literal(configured.label());
        }
        return TradingCreativeCategoryGroups.label(sectionId);
    }

    public static ItemStack icon(String sectionId,
                                 List<TradingHtmlThemeParser.CategorySection> sections) {
        TradingHtmlThemeParser.CategorySection configured = find(sectionId, sections);
        if (configured != null && !configured.iconItemId().isBlank()) {
            ResourceLocation id = ResourceLocation.tryParse(configured.iconItemId());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)
                && BuiltInRegistries.ITEM.get(id) != Items.AIR) {
                return BuiltInRegistries.ITEM.get(id).getDefaultInstance();
            }
        }
        ItemStack creativeIcon = TradingCreativeCategoryGroups.icon(sectionId);
        if (!creativeIcon.isEmpty()) return creativeIcon;
        if (configured != null) {
            for (String categoryId : configured.categories()) {
                try {
                    TradingCategory category = TradingCategory.valueOf(categoryId.toUpperCase(Locale.ROOT));
                    ItemStack childIcon = TradingCreativeCategoryGroups.categoryIcon("", category);
                    if (!childIcon.isEmpty()) return childIcon;
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return Items.CHEST.getDefaultInstance();
    }

    private static TradingHtmlThemeParser.CategorySection find(String sectionId,
                                                                List<TradingHtmlThemeParser.CategorySection> sections) {
        if (sectionId == null || sections == null) return null;
        for (TradingHtmlThemeParser.CategorySection section : sections) {
            if (section.id().equals(sectionId)) return section;
        }
        return null;
    }
}
