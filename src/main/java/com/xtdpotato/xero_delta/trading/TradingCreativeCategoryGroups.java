package com.xtdpotato.xero_delta.trading;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side index of the real vanilla/modded creative tabs.
 * Creative tabs are first-level groups and the semantic trading categories that
 * actually occur inside each tab are their second-level children.
 */
@OnlyIn(Dist.CLIENT)
public final class TradingCreativeCategoryGroups {
    private record CreativeSection(String id, Component label, ItemStack icon,
                                   Set<ResourceLocation> itemIds,
                                   Set<TradingCategory> categories,
                                   Map<TradingCategory, ItemStack> categoryIcons) {
    }

    private static List<CreativeSection> cachedSections = List.of();
    private static List<ItemStack> cachedStacks = List.of();

    private TradingCreativeCategoryGroups() {
    }

    public static void refresh(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            cachedSections = List.of();
            cachedStacks = List.of();
            return;
        }
        CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(),
            minecraft.player.canUseGameMasterBlocks(), minecraft.level.registryAccess());
        List<CreativeSection> rebuilt = new ArrayList<>();
        Map<CatalogKey, ItemStack> allStacks = new LinkedHashMap<>();
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY || !tab.hasAnyItems()) continue;
            ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (key == null) continue;
            Set<ResourceLocation> itemIds = new LinkedHashSet<>();
            Set<TradingCategory> categories = new LinkedHashSet<>();
            Map<TradingCategory, ItemStack> categoryIcons = new EnumMap<>(TradingCategory.class);
            LinkedHashSet<ItemStack> tabStacks = new LinkedHashSet<>();
            tabStacks.addAll(tab.getDisplayItems());
            tabStacks.addAll(tab.getSearchTabDisplayItems());
            for (ItemStack stack : tabStacks) {
                if (stack == null || stack.isEmpty()) continue;
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (!TradingItemEligibility.canList(itemId)) continue;
                ItemStack sample = stack.copyWithCount(1);
                allStacks.putIfAbsent(new CatalogKey(itemId, sample.getComponentsPatch()), sample);
                itemIds.add(itemId);
                TradingCategory category = TradingItemCategory.classify(stack);
                categories.add(category);
                categoryIcons.putIfAbsent(category, stack.copyWithCount(1));
            }
            if (!itemIds.isEmpty()) {
                rebuilt.add(new CreativeSection(key.toString(), tab.getDisplayName(),
                    tab.getIconItem().copy(), Set.copyOf(itemIds), Set.copyOf(categories),
                    Map.copyOf(categoryIcons)));
            }
        }
        cachedSections = List.copyOf(rebuilt);
        cachedStacks = allStacks.values().stream().map(ItemStack::copy).toList();
    }

    /** All concrete stacks exposed by creative tabs, including component-based mod variants. */
    public static List<ItemStack> allStacks() {
        return cachedStacks.stream().map(ItemStack::copy).toList();
    }

    public static List<TradingHtmlThemeParser.CategorySection> sections(List<TradingCategory> configured) {
        List<TradingHtmlThemeParser.CategorySection> result = new ArrayList<>();
        for (CreativeSection section : cachedSections) {
            List<String> children = configured.stream()
                .filter(category -> category != TradingCategory.ALL && category != TradingCategory.FAVORITES)
                .filter(section.categories()::contains)
                .map(category -> category.name().toLowerCase(java.util.Locale.ROOT))
                .toList();
            if (!children.isEmpty()) {
                result.add(new TradingHtmlThemeParser.CategorySection(section.id(), children, false));
            }
        }
        return List.copyOf(result);
    }

    public static Component label(String sectionId) {
        CreativeSection section = find(sectionId);
        return section == null ? Component.literal(sectionId == null ? "" : sectionId) : section.label();
    }

    public static ItemStack icon(String sectionId) {
        CreativeSection section = find(sectionId);
        return section == null ? ItemStack.EMPTY : section.icon().copy();
    }

    public static ItemStack categoryIcon(String sectionId, TradingCategory category) {
        if (category == TradingCategory.ALL) return Items.CHEST.getDefaultInstance();
        if (category == TradingCategory.FAVORITES) return Items.NETHER_STAR.getDefaultInstance();
        CreativeSection section = find(sectionId);
        if (section != null) {
            ItemStack icon = section.categoryIcons().get(category);
            if (icon != null && !icon.isEmpty()) return icon.copy();
        }
        for (CreativeSection candidate : cachedSections) {
            ItemStack icon = candidate.categoryIcons().get(category);
            if (icon != null && !icon.isEmpty()) return icon.copy();
        }
        return ItemStack.EMPTY;
    }

    public static boolean matches(String sectionId, ItemStack stack) {
        if (sectionId == null || sectionId.isBlank()) return true;
        if (stack == null || stack.isEmpty()) return false;
        CreativeSection section = find(sectionId);
        // Unknown ids are user-defined visual category groups from trading HTML.
        // Their child TradingCategory performs the actual filtering.
        if (section == null) return true;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && section.itemIds().contains(itemId);
    }

    public static boolean hasSection(String sectionId) {
        return find(sectionId) != null;
    }

    private static CreativeSection find(String sectionId) {
        if (sectionId == null || sectionId.isBlank()) return null;
        for (CreativeSection section : cachedSections) {
            if (section.id().equals(sectionId)) return section;
        }
        return null;
    }

    private record CatalogKey(ResourceLocation itemId, DataComponentPatch components) {
    }
}
