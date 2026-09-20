package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.data.ContainerGridRules;
import com.xtdpotato.xero_delta.grid.ContainerGridNormalizer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/** Repairs Inventory Sorter's direct one-item wheel transfers after mutation. */
@Pseudo
@Mixin(targets = "cpw.mods.inventorysorter.ScrollWheelHandler", remap = false)
public abstract class InventorySorterScrollWheelMixin {
    @Inject(
        method = "accept(Lcpw/mods/inventorysorter/ContainerContext;)V",
        at = @At("RETURN"), remap = false, require = 0
    )
    private void xero$normalizeWheelTransfer(@Coerce Object context, CallbackInfo ci) {
        if (context == null || !Config.INSTANCE.itemGridEnabled.get()) return;
        try {
            Field playerField = context.getClass().getDeclaredField("player");
            playerField.setAccessible(true);
            if (!(playerField.get(context) instanceof ServerPlayer player)) return;
            if (!ContainerGridRules.isScreenEnabled(player.containerMenu.getClass().getName())) return;
            ContainerGridNormalizer.normalize(player, player.containerMenu);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Best-effort across Inventory Sorter versions.
        }
    }
}
