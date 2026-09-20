package com.xtdpotato.xero_delta.network;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Keeps Delta equipment cards, the player inventory and entity equipment in sync. */
public final class PlayerEquipmentSync {
    private PlayerEquipmentSync() {
    }

    public static boolean canEquip(LivingEntity entity, ItemStack stack,
                                   EquipmentSlot slot) {
        return entity != null && stack != null && !stack.isEmpty()
            && stack.getCount() == 1 && stack.canEquip(slot, entity);
    }

    public static void sync(ServerPlayer player, EquipmentSlot... slots) {
        if (player == null) return;
        player.getInventory().setChanged();
        List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        for (EquipmentSlot slot : slots) {
            if (slot == null) continue;
            ItemStack stack = player.getItemBySlot(slot).copy();
            equipment.add(Pair.of(slot, stack));
            int inventorySlot = inventorySlot(slot);
            if (inventorySlot >= 0) {
                player.connection.send(new ClientboundContainerSetSlotPacket(
                    -2, 0, inventorySlot, stack.copy()));
            }
        }
        if (!equipment.isEmpty()) {
            player.connection.send(new ClientboundSetEquipmentPacket(
                player.getId(), equipment));
        }
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
        player.containerMenu.sendAllDataToRemote();
    }

    private static int inventorySlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 39;
            case CHEST -> 38;
            case LEGS -> 37;
            case FEET -> 36;
            default -> -1;
        };
    }
}
