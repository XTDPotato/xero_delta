package com.xtdpotato.xero_delta.event;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.KnifeSkinRules;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;

@EventBusSubscriber(modid = XeroDelta.MOD_ID)
public final class KnifeSkinProtection {
    @SubscribeEvent
    public static void preventHandSwap(LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof Player player && player.isCreative()) return;
        if (KnifeSkinRules.locked(event.getItemSwappedToMainHand())
            || KnifeSkinRules.locked(event.getItemSwappedToOffHand())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void preventKnifeToss(ItemTossEvent event) {
        ItemEntity entity = event.getEntity();
        Player player = event.getPlayer();
        if (!KnifeSkinRules.blocksPlayerInventoryAction(player, entity.getItem())) return;

        // ItemTossEvent is fired after vanilla has removed the stack. Restore it
        // to the authoritative knife slot before cancelling the entity spawn.
        // This covers Q and drag-out paths used by other inventory screens.
        event.setCanceled(true);
        if (player == null) return;
        if (player.getInventory().getItem(KnifeSkinRules.SLOT).isEmpty()) {
            player.getInventory().setItem(KnifeSkinRules.SLOT,
                entity.getItem().copyWithCount(1));
            player.getInventory().setChanged();
        }
    }
}
