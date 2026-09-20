package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Resolves equivalent medical status icons to one bundled texture. */
public final class EffectIconResources {
    private static final Map<String, String> CANONICAL_PATHS = Map.ofEntries(
        Map.entry("chest_pain", "chest_injury"),
        Map.entry("whole_body_injury", "chest_injury"),
        Map.entry("head_pain", "head_injury"),
        Map.entry("abdominal_pain", "abdomen_injury"),
        Map.entry("arm_fracture", "left_arm_injury"),
        Map.entry("leg_fracture", "left_leg_injury"),
        Map.entry("leg_fissure", "right_leg_injury"),
        Map.entry("stamina_capacity_boost", "weight_boost")
    );

    private EffectIconResources() {}

    public static ResourceLocation icon(String effectPath) {
        String canonicalPath = CANONICAL_PATHS.getOrDefault(effectPath, effectPath);
        return ResourceLocation.fromNamespaceAndPath(
            XeroDelta.MOD_ID, "textures/mob_effect/" + canonicalPath + ".png");
    }
}
