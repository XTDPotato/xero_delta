package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.data.ContainerGridRules;
import com.xtdpotato.xero_delta.grid.ContainerGridNormalizer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional ClientSort 2.x integration. ClientSort normally applies a client
 * generated permutation directly to physical slots; covered grid cells are
 * physically empty, so that operation can overlap or erase multi-cell items.
 */
@Pseudo
@Mixin(targets = "dev.terminalmc.clientsort.network.handler.SortHandler", remap = false)
public abstract class ClientSortHandlerMixin {
    @Inject(
        method = "sort(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/world/inventory/AbstractContainerMenu;[I)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void xero$sortLogicalGrid(MinecraftServer server, AbstractContainerMenu menu,
                                             int[] slotMapping, CallbackInfo ci) {
        if (!Config.INSTANCE.itemGridEnabled.get()
            || !ContainerGridRules.isScreenEnabled(menu.getClass().getName())) return;
        ServerPlayer player = null;
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (candidate.containerMenu == menu) {
                player = candidate;
                break;
            }
        }
        if (player != null && ContainerGridNormalizer.handleClientSort(player, menu, slotMapping)) {
            ci.cancel();
        }
    }
}
