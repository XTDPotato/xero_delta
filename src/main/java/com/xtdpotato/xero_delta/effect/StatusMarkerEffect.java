package com.xtdpotato.xero_delta.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Visible marker used by the server-authoritative health and stimulant systems. */
public final class StatusMarkerEffect extends MobEffect {
    public StatusMarkerEffect(boolean beneficial, int color) {
        super(beneficial ? MobEffectCategory.BENEFICIAL : MobEffectCategory.HARMFUL, color);
    }
}
