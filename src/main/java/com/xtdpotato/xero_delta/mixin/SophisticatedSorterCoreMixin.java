package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.data.ContainerGridRules;
import com.xtdpotato.xero_delta.data.LootSearchManager;
import com.xtdpotato.xero_delta.grid.ContainerGridNormalizer;
import com.xtdpotato.xero_delta.menu.PersonalWarehouseMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional Tower-of-Sighs/SophisticatedSorter integration. */
@Pseudo
@Mixin(targets = "com.sighs.sophisticatedsorter.utils.CoreUtils", remap = false)
public abstract class SophisticatedSorterCoreMixin {
    @Inject(
        method = "sortContainer(Lnet/minecraft/server/level/ServerPlayer;Lnet/p3pp3rf1y/sophisticatedcore/common/gui/SortBy;Z)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 0
    )
    private static void xero$sortContainer(ServerPlayer player, @Coerce Object sortBy, boolean chinese,
                                           CallbackInfo ci) {
        if (xero$handleSort(player, false)) ci.cancel();
    }

    @Inject(
        method = "sortInventory(Lnet/minecraft/server/level/ServerPlayer;Lnet/p3pp3rf1y/sophisticatedcore/common/gui/SortBy;Z)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 0
    )
    private static void xero$sortInventory(ServerPlayer player, @Coerce Object sortBy, boolean chinese,
                                           CallbackInfo ci) {
        if (xero$handleSort(player, true)) ci.cancel();
    }

    @Inject(
        method = "transfer(Lnet/minecraft/world/entity/player/Player;ZZ)V",
        at = @At("RETURN"), remap = false, require = 0
    )
    private static void xero$normalizeTransfer(Player player, boolean toContainer, boolean filter,
                                               CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || !Config.INSTANCE.itemGridEnabled.get()) return;
        if (!xero$isGridContainer(player)) return;
        ContainerGridNormalizer.normalize(serverPlayer, player.containerMenu);
        LootSearchManager.contentsReordered(serverPlayer, player.containerMenu);
    }

    private static boolean xero$handleSort(Player player, boolean playerInventory) {
        if (!(player instanceof ServerPlayer serverPlayer) || !Config.INSTANCE.itemGridEnabled.get()) return false;
        if (!xero$isGridContainer(player)) return false;
        return ContainerGridNormalizer.handleSophisticatedSorter(serverPlayer, player.containerMenu, playerInventory);
    }

    private static boolean xero$isGridContainer(Player player) {
        return player.containerMenu instanceof PersonalWarehouseMenu
            || ContainerGridRules.isScreenEnabled(player.containerMenu.getClass().getName());
    }
}
