package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.screen.RecyclingScreen;
import com.xtdpotato.xero_delta.screen.TradingMarketScreen;
import com.xtdpotato.xero_delta.screen.TradingOperatorScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Keeps FTB Library's sidebar out of the custom trading/container screens. */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.sidebar.SidebarButtonManager", remap = false)
public abstract class FtbSidebarButtonManagerMixin {
    @Inject(method = {
        "getEnabledButtonList(Z)Ljava/util/List;",
        "getDisabledButtonList(Z)Ljava/util/List;"
    }, at = @At("HEAD"), cancellable = true, require = 0)
    private void xtd$hideTradingSidebar(boolean includeDisabled,
                                         CallbackInfoReturnable<List<?>> callback) {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof TradingMarketScreen
            || screen instanceof TradingOperatorScreen
            || screen instanceof RecyclingScreen) {
            callback.setReturnValue(List.of());
        }
    }
}
