package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.grid.DeltaQuickMoveService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cursor swap for armor cards shown beside non-inventory container screens. */
public record PlayerEquipmentSlotClickPacket(int equipmentIndex)
    implements CustomPacketPayload {
    public static final Type<PlayerEquipmentSlotClickPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "equipment_slot_click"));
    public static final StreamCodec<FriendlyByteBuf, PlayerEquipmentSlotClickPacket> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT,
            PlayerEquipmentSlotClickPacket::equipmentIndex,
            PlayerEquipmentSlotClickPacket::new);

    @Override
    public Type<PlayerEquipmentSlotClickPacket> type() {
        return TYPE;
    }

    public static void handle(PlayerEquipmentSlotClickPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !PlayerLayoutSlotRules.enabled(player)) return;
            boolean quickMove = packet.equipmentIndex >= 2;
            EquipmentSlot slot = switch (Math.floorMod(packet.equipmentIndex, 2)) {
                case 0 -> EquipmentSlot.HEAD;
                case 1 -> EquipmentSlot.CHEST;
                default -> null;
            };
            if (slot == null) return;
            AbstractContainerMenu cursorMenu = player.containerMenu;
            ItemStack carried = cursorMenu.getCarried();
            ItemStack equipped = player.getItemBySlot(slot);
            if (quickMove) {
                if (equipped.isEmpty()
                    || !DeltaQuickMoveService.moveIntoExternalContainer(player, equipped)) return;
                player.setItemSlot(slot, equipped.isEmpty() ? ItemStack.EMPTY : equipped.copy());
                PlayerEquipmentSync.sync(player, slot);
                return;
            }
            if (!carried.isEmpty()
                && !PlayerEquipmentSync.canEquip(player, carried, slot)) return;
            player.setItemSlot(slot, carried.copy());
            cursorMenu.setCarried(equipped.copy());
            PlayerEquipmentSync.sync(player, slot);
        });
    }
}
