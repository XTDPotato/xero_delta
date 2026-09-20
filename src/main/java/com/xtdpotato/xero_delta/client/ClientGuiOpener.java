package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.gui.GuiScreenTarget;
import com.xtdpotato.xero_delta.screen.CardHolderPickerScreen;
import com.xtdpotato.xero_delta.screen.KnifePickerScreen;
import com.xtdpotato.xero_delta.screen.PlayerStatusScreen;
import com.xtdpotato.xero_delta.screen.SafetyBoxPickerScreen;
import com.xtdpotato.xero_delta.screen.StatusEffectHudConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client-side half of the server-authorized /xero_gui command. */
public final class ClientGuiOpener {
    private ClientGuiOpener() {
    }

    public static void open(String screenId) {
        Minecraft minecraft = Minecraft.getInstance();
        GuiScreenTarget.parse(screenId).ifPresent(target -> {
            Screen parent = minecraft.screen;
            switch (target) {
                case SAFETY_BOX -> minecraft.setScreen(new SafetyBoxPickerScreen(parent));
                case KNIFE -> minecraft.setScreen(new KnifePickerScreen(parent));
                case CARD_HOLDER -> minecraft.setScreen(new CardHolderPickerScreen(parent));
                case PLAYER_STATUS -> {
                    if (minecraft.player != null && !minecraft.player.isCreative()) {
                        PlayerStatusScreenState.enable();
                        minecraft.setScreen(new PlayerStatusScreen(parent));
                    }
                }
                case EFFECT_HUD -> minecraft.setScreen(new StatusEffectHudConfigScreen(parent));
                default -> { }
            }
        });
    }
}
