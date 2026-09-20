package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.BetterLootingLongPressState;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.OpenGroundPackPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Marks only Better Looting's user-triggered long-press batch actions. */
@Pseudo
@Mixin(targets = "com.mohuia.better_looting.client.core.pipeline.ActionDispatcher",
    remap = false)
public abstract class BetterLootingLongPressMixin {
    @Inject(method = "sendBatchPickup(Ljava/util/List;Z)V", at = @At("HEAD"),
        cancellable = true, require = 0, remap = false)
    private static void xero$markBatch(List<?> items, boolean automatic,
                                       CallbackInfo ci) {
        if (automatic) return;
        int carrierEntity = xero$groundCarrierEntity(items);
        if (carrierEntity >= 0) {
            ModNetwork.sendToServer(new OpenGroundPackPacket(carrierEntity));
            ci.cancel();
            return;
        }
        BetterLootingLongPressState.markBatchPickup();
    }

    @Inject(method = "sendRowPickup", at = @At("HEAD"),
        require = 0, remap = false)
    private static void xero$markRow(CallbackInfo ci) {
        BetterLootingLongPressState.markBatchPickup();
    }

    private static int xero$groundCarrierEntity(List<?> entries) {
        if (entries == null) return -1;
        for (Object entry : entries) {
            try {
                Object stackValue = entry instanceof ItemEntity item
                    ? item.getItem() : entry.getClass().getMethod("getItem").invoke(entry);
                if (!(stackValue instanceof ItemStack stack)
                    || !(stack.getItem() instanceof DeltaPackItem pack)
                    || (!"chest_rig".equals(pack.slotIdentifier())
                        && !"backpack".equals(pack.slotIdentifier()))) continue;
                if (entry instanceof ItemEntity item) return item.getId();
                Object sources = entry.getClass().getMethod("getSourceEntities").invoke(entry);
                if (!(sources instanceof Iterable<?> iterable)) continue;
                for (Object source : iterable) {
                    if (source instanceof ItemEntity item && item.isAlive()
                        && item.getItem().getItem() instanceof DeltaPackItem sourcePack
                        && sourcePack.slotIdentifier().equals(pack.slotIdentifier())) {
                        return item.getId();
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return -1;
    }
}
