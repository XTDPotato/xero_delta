package com.xtdpotato.xero_delta.screen;

import net.minecraft.client.gui.screens.Screen;

/** Compatibility entry point for the native quality settings page. */
public final class SafetyBoxSettingsScreen extends XeroDeltaMaterialSettingsScreen {
    public SafetyBoxSettingsScreen(Screen parent) {
        super(parent, "quality");
    }
}
