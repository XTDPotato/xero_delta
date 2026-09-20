package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.ChatHudAvoidance;
import com.xtdpotato.xero_delta.client.PlayerHudRenderer;
import com.xtdpotato.xero_delta.client.PlayerStatusClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps lower-left chat clear of the independently positioned player health HUD. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Unique
    private int xero$verticalOffset;

    @Shadow public abstract int getWidth();
    @Shadow public abstract int getHeight();

    @Inject(method = "render", at = @At("HEAD"))
    private void xero$avoidPlayerHealthHud(GuiGraphics graphics, int ticks, int mouseX,
                                           int mouseY, boolean focused, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        xero$verticalOffset = 0;
        if (!PlayerStatusClientState.INSTANCE.layoutEnabled() || minecraft.player == null
            || minecraft.options.hideGui) return;
        xero$verticalOffset = ChatHudAvoidance.offset(
            minecraft.getWindow().getGuiScaledHeight(), getWidth(), getHeight(),
            PlayerHudRenderer.bounds(minecraft));
        if (xero$verticalOffset > 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, -xero$verticalOffset, 0.0F);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void xero$restoreChatPose(GuiGraphics graphics, int ticks, int mouseX,
                                      int mouseY, boolean focused, CallbackInfo ci) {
        if (xero$verticalOffset > 0) graphics.pose().popPose();
    }

    @ModifyVariable(method = "screenToChatY", at = @At("HEAD"), argsOnly = true)
    private double xero$translateChatHitTest(double screenY) {
        return screenY + xero$verticalOffset;
    }
}
