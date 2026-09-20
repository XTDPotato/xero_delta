package com.xtdpotato.xero_delta.data;

import java.util.Set;

/** The bundled player rig, animation clips and atlas must update together. */
public final class SafetyBoxInspectResources {
    private static final Set<String> PATHS = Set.of(
        "models/inspect/safety_box_3x3_firstperson.geo.json",
        "models/inspect/safety_box_3x3_thirdperson.geo.json",
        "animations/safety_box_3x3_firstperson.animation.json",
        "animations/safety_box_3x3_thirdperson.animation.json",
        "textures/uv/safety_box_3x3_box.png");

    private SafetyBoxInspectResources() {}

    public static boolean isBundledRig(String path) {
        return PATHS.contains(path);
    }
}
