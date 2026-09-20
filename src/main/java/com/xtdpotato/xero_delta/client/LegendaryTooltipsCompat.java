package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.RuleFormulaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/** Optional, reflection-only bridge for Legendary Tooltips 1.5.x. */
public final class LegendaryTooltipsCompat {
    private static volatile boolean initialized;
    private static volatile boolean unavailable;
    private static Method getInstance;
    private static Method getFrameDefinition;
    private static Method frameIndex;

    private LegendaryTooltipsCompat() {
    }

    /**
     * Resolves an automatic quality from Legendary Tooltips' selected frame.
     * A standard common frame is not an opinion and falls through to Xero's
     * normal formula. Manual and explicit catalogue rules are checked before
     * this bridge by {@link ClientDataCache}.
     */
    public static String resolveQuality(ItemStack stack) {
        if (stack == null || stack.isEmpty() || unavailable
            || !ModList.get().isLoaded("legendarytooltips")) return null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !initialize()) return null;
        try {
            Object config = getInstance.invoke(null);
            if (config == null) return null;
            HolderLookup.Provider provider = minecraft.level.registryAccess();
            Object definition = getFrameDefinition.invoke(config, stack, provider);
            if (definition == null) return null;
            int index = ((Number) frameIndex.invoke(definition)).intValue();
            return LegendaryTooltipQualityMap.resolve(index, rarityQuality(stack));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            unavailable = true;
            XeroDelta.LOGGER.warn("Legendary Tooltips quality bridge was disabled", exception);
            return null;
        }
    }

    private static String rarityQuality(ItemStack stack) {
        String nameQuality = RuleFormulaConfig.qualityFromName(stack);
        if (nameQuality != null) return nameQuality;
        Rarity rarity = stack.getRarity();
        if (rarity == Rarity.EPIC) return "purple";
        if (rarity == Rarity.RARE) return "blue";
        if (rarity == Rarity.UNCOMMON) return "green";
        return "gray";
    }

    private static boolean initialize() {
        if (initialized) return !unavailable;
        synchronized (LegendaryTooltipsCompat.class) {
            if (initialized) return !unavailable;
            initialized = true;
            try {
                Class<?> configClass = Class.forName(
                    "com.anthonyhilyard.legendarytooltips.config.LegendaryTooltipsConfig");
                Class<?> definitionClass = Class.forName(
                    "com.anthonyhilyard.legendarytooltips.config.LegendaryTooltipsConfig$FrameDefinition");
                getInstance = configClass.getMethod("getInstance");
                getFrameDefinition = configClass.getMethod("getFrameDefinition",
                    ItemStack.class, HolderLookup.Provider.class);
                frameIndex = definitionClass.getMethod("index");
            } catch (ReflectiveOperationException | LinkageError exception) {
                unavailable = true;
                XeroDelta.LOGGER.warn("Legendary Tooltips API was not compatible with the quality bridge", exception);
            }
            return !unavailable;
        }
    }
}
