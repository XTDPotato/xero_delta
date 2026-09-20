package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.WheelMouseController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Confines hidden hold-wheel input before the hardware pointer can leave it. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onMove", at = @At("TAIL"))
    private void xeroDelta$constrainWheelPointer(long window, double x, double y,
                                                  CallbackInfo callback) {
        WheelMouseController.constrainOnMouseMove(Minecraft.getInstance());
    }
}
