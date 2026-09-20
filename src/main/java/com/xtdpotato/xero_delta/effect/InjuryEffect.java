package com.xtdpotato.xero_delta.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Marker effect; gameplay penalties are applied by the server injury manager. */
public final class InjuryEffect extends MobEffect {
    public InjuryEffect(int color) {
        super(MobEffectCategory.HARMFUL, color);
    }
}
