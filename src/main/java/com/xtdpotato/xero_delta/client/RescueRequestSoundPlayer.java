package com.xtdpotato.xero_delta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.Util;

/** Plays a rescue call exactly when its shared HUD pulse begins. */
public final class RescueRequestSoundPlayer {
    private RescueRequestSoundPlayer() {
    }

    public static void play(String soundId) {
        if (soundId == null || soundId.isBlank()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        if (id == null) return;
        BuiltInRegistries.SOUND_EVENT.getOptional(id).ifPresent(sound ->
            minecraft.getSoundManager().play(new TimedRescueSound(sound)));
    }

    private static final class TimedRescueSound extends AbstractTickableSoundInstance {
        private final long stopAt = Util.getMillis() + RescueRequestClientState.DURATION_MS;

        private TimedRescueSound(SoundEvent sound) {
            super(sound, SoundSource.PLAYERS, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0F;
            this.pitch = 1.0F;
            this.relative = true;
        }

        @Override
        public void tick() {
            if (Util.getMillis() >= stopAt) stop();
        }
    }
}
