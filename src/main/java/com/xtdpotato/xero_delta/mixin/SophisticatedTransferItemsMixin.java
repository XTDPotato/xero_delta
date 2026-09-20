package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.data.ContainerGridRules;
import com.xtdpotato.xero_delta.grid.ContainerGridNormalizer;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Repairs the batch transfer button, which bypasses menu quickMove entirely. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.network.TransferItemsPayload", remap = false)
public final class SophisticatedTransferItemsMixin {
    @Inject(method = "handlePayload", at = @At("RETURN"), remap = false)
    private static void xero$normalizeBatchTransfer(@Coerce Object payload,
                                                     IPayloadContext context, CallbackInfo ci) {
        if (!Config.INSTANCE.itemGridEnabled.get()) return;
        Player player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (!ContainerGridHelper.usesStorageAdapter(menu)
            || !ContainerGridRules.isScreenEnabled(menu.getClass().getName())) return;
        // Core writes directly through Inventory.setItem when transferring to
        // the player, bypassing Inventory.add and therefore the grid inserter.
        // Normalize the main inventory afterwards; hotbar slots are excluded
        // by ContainerGridNormalizer and remain vanilla 1x1 cells.
        ContainerGridNormalizer.normalize(serverPlayer, menu);
    }
}
