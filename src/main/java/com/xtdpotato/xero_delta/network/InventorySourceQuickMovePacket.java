package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.grid.DeltaQuickMoveService;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Quick-moves an exact source into legal Delta carried storage. */
public record InventorySourceQuickMovePacket(String sourceId)
    implements CustomPacketPayload {
    public static final Type<InventorySourceQuickMovePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID,
            "inventory_source_quick_move"));
    public static final StreamCodec<FriendlyByteBuf, InventorySourceQuickMovePacket> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8,
            InventorySourceQuickMovePacket::sourceId,
            InventorySourceQuickMovePacket::new);

    @Override public Type<InventorySourceQuickMovePacket> type() { return TYPE; }

    public static void handle(InventorySourceQuickMovePacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || packet.sourceId == null || packet.sourceId.length() > 256
                || !player.containerMenu.getCarried().isEmpty()) return;
            // Worn corpse carriers reject ordinary pickup. Only this explicit
            // quick-move request may use the menu's carrier transfer path.
            int carrierSlot = corpseCarrierSlot(packet.sourceId);
            if (carrierSlot >= 0 && player.containerMenu instanceof CorpseMenu menu) {
                if (menu.corpseEntity() == null || !menu.stillValid(player)
                    || com.xtdpotato.xero_delta.data.KnifeSkinRules.locked(
                        menu.slots.get(carrierSlot).getItem())) return;
                if (menu.quickMoveStack(player, carrierSlot).isEmpty()) {
                    ModNetwork.sendTranslatedNoticePlain(player,
                        "storage.xero_delta.no_space_move");
                }
                menu.broadcastChanges();
                player.inventoryMenu.broadcastChanges();
                return;
            }
            InventoryTransferSource.Handle source = InventoryTransferSource.find(
                player, packet.sourceId);
            ItemStack sample = source == null ? ItemStack.EMPTY : source.peek();
            if (sample.isEmpty()) return;
            ItemStack extracted = source.extract(sample.getCount());
            if (extracted.isEmpty()) return;
            boolean changed = isExternalSource(player, packet.sourceId)
                ? DeltaQuickMoveService.moveIntoPlayerInventory(player, extracted)
                : DeltaQuickMoveService.moveIntoExternalContainer(player, extracted);
            if (!extracted.isEmpty() && !source.restore(extracted)) {
                player.containerMenu.setCarried(extracted);
            }
            if (!changed) {
                ModNetwork.sendTranslatedNoticePlain(player,
                    "storage.xero_delta.no_space_move");
            }
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        });
    }

    private static boolean isExternalSource(ServerPlayer player, String sourceId) {
        if (sourceId == null) return false;
        if (sourceId.startsWith("corpse_storage|")) return true;
        if (!sourceId.startsWith("container|")) return false;
        try {
            int index = Integer.parseInt(sourceId.substring("container|".length()));
            if (index < 0 || index >= player.containerMenu.slots.size()) return false;
            Slot slot = player.containerMenu.slots.get(index);
            return slot != null && !(slot.container instanceof Inventory);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    static int corpseCarrierSlot(String sourceId) {
        if (("container|" + CorpseMenu.CHEST_RIG_SLOT).equals(sourceId)) {
            return CorpseMenu.CHEST_RIG_SLOT;
        }
        if (("container|" + CorpseMenu.BACKPACK_SLOT).equals(sourceId)) {
            return CorpseMenu.BACKPACK_SLOT;
        }
        return -1;
    }
}
