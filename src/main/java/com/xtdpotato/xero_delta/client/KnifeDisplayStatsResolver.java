package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.data.KnifeDisplayStats;
import com.xtdpotato.xero_delta.data.KnifeDisplayStatsParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/** Client-side bridge that feeds real item tooltip/component data into the pure parser. */
public final class KnifeDisplayStatsResolver {
    private KnifeDisplayStatsResolver() {
    }

    public static KnifeDisplayStats resolve(Minecraft minecraft, ItemStack stack) {
        List<String> tooltip = new ArrayList<>();
        try {
            stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player,
                    TooltipFlag.NORMAL).stream()
                .skip(1)
                .map(Component::getString)
                .forEach(tooltip::add);
        } catch (RuntimeException ignored) {
            // A third-party item may fail while building its tooltip. Components remain usable.
        }
        addTranslatedDescription(stack, tooltip, stack.getDescriptionId() + ".description");
        addTranslatedDescription(stack, tooltip, stack.getDescriptionId() + ".desc");
        String raw = stack.getDescriptionId() + " " + stack.getComponents();
        return KnifeDisplayStatsParser.parse(tooltip, raw);
    }

    private static void addTranslatedDescription(ItemStack stack, List<String> lines, String key) {
        if (!I18n.exists(key)) return;
        String translated = Component.translatable(key).getString();
        if (!translated.isBlank() && !lines.contains(translated)
            && !translated.equals(stack.getHoverName().getString())) lines.add(translated);
    }
}
