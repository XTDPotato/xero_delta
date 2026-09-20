package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.PlayerStatusClientState;
import com.xtdpotato.xero_delta.client.BetterLootingLongPressState;
import com.xtdpotato.xero_delta.screen.PlayerStatusScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opens the Delta inventory when Better Looting reports a full backpack. */
@Mixin(Gui.class)
public abstract class GuiOverlayMessageMixin {
    @Inject(method = "setOverlayMessage", at = @At("TAIL"))
    private void xero$openInventoryForFullBetterLootingPickup(Component message,
                                                              boolean animate,
                                                              CallbackInfo ci) {
        if (!(message.getContents() instanceof TranslatableContents translated)
            || !"message.better_looting.inventory_full".equals(translated.getKey())) return;
        if (!BetterLootingLongPressState.consumeInventoryFullIntent()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null
            || !PlayerStatusClientState.INSTANCE.layoutEnabled()) return;
        minecraft.execute(() -> {
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new PlayerStatusScreen(null));
            }
        });
    }
}
