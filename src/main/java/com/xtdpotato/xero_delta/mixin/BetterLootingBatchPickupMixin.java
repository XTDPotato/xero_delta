package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.compat.BetterLootingPickupCompat;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adds equipped safety-box stacking to Better Looting's batch pickup path. */
@Pseudo
@Mixin(
    targets = "com.mohuia.better_looting.network.C2S.PacketBatchPickup",
    remap = false
)
public abstract class BetterLootingBatchPickupMixin {
    @Redirect(
        method = "lambda$handle$2",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z",
            remap = true
        ),
        remap = false,
        require = 0
    )
    private boolean xero$stackPickupIntoSafetyBox(Inventory inventory,
                                                   ItemStack remainder) {
        return BetterLootingPickupCompat.addToInventoryAndExistingSafetyBox(
            inventory, remainder);
    }
}
