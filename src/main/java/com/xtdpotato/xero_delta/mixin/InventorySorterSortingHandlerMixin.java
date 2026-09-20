package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ContainerGridRules;
import com.xtdpotato.xero_delta.grid.ContainerGridNormalizer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/** Optional cpw/InventorySorter integration for logical multi-cell grids. */
@Pseudo
@Mixin(targets = "cpw.mods.inventorysorter.SortingHandler", remap = false)
public abstract class InventorySorterSortingHandlerMixin {
    private static volatile boolean xero$reflectionWarningLogged;

    @Inject(
        method = "accept(Lcpw/mods/inventorysorter/ContainerContext;)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 0
    )
    private void xero$sortLogicalGrid(@Coerce Object context, CallbackInfo ci) {
        if (context == null || !Config.INSTANCE.itemGridEnabled.get()) return;
        try {
            Object playerValue = xero$field(context, "player");
            Object slotValue = xero$field(context, "slot");
            if (!(playerValue instanceof ServerPlayer player) || !(slotValue instanceof Slot slot)) return;
            AbstractContainerMenu menu = player.containerMenu;
            if (!ContainerGridRules.isScreenEnabled(menu.getClass().getName())) return;
            if (ContainerGridNormalizer.handleInventorySorter(player, menu, slot)) ci.cancel();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!xero$reflectionWarningLogged) {
                xero$reflectionWarningLogged = true;
                XeroDelta.LOGGER.warn("Inventory Sorter compatibility could not read ContainerContext", exception);
            }
        }
    }

    private static Object xero$field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
