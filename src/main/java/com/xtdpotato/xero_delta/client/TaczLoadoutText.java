package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.data.TaczCompatibilityRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a compact ammunition label from TACZ's component-rich gun stack. */
public final class TaczLoadoutText {
    private static final Pattern AMMO_ID = Pattern.compile(
        "(?i)(?:ammo(?:id|_id)?|bullet(?:id|_id)?|calib(?:er|re))"
            + "[^a-z0-9_.:/-]+([a-z0-9_.-]+:[a-z0-9_./-]+)");
    private static final Pattern CALIBER = Pattern.compile(
        "(?i)(?:^|[^a-z0-9])("
            + "\\d{1,3}(?:\\.\\d+)?\\s*[x×]\\s*\\d{1,3}(?:\\.\\d+)?\\s*(?:mm)?"
            + "|\\.\\d{2,3}\\s*(?:acp|magnum)?"
            + "|\\d{1,2}\\s*gauge"
            + "|\\d{1,3}\\s*(?:bmg|nato))(?:$|[^a-z0-9])");

    private TaczLoadoutText() {
    }

    public static String ammunitionName(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !TaczCompatibilityRules.isTaczGun(stack)) {
            return "";
        }
        return ammunitionNameFromDescriptor(TaczCompatibilityRules.descriptor(stack));
    }

    static String ammunitionNameFromDescriptor(String descriptor) {
        String token = ammunitionTokenFromDescriptor(descriptor);
        if (token.isBlank()) return "";
        return token.indexOf(':') >= 0 ? displayAmmoId(token) : token;
    }

    static String ammunitionTokenFromDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isBlank()) return "";
        Matcher idMatcher = AMMO_ID.matcher(descriptor);
        if (idMatcher.find()) return idMatcher.group(1);
        Matcher caliberMatcher = CALIBER.matcher(descriptor);
        return caliberMatcher.find()
            ? caliberMatcher.group(1).replaceAll("\\s+", "").toUpperCase(Locale.ROOT)
            : "";
    }

    private static String displayAmmoId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            var item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR) return item.getDefaultInstance().getHoverName().getString();
        }
        String path = id == null ? value : id.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        return path.replace('_', ' ').replace('-', ' ').trim();
    }
}
