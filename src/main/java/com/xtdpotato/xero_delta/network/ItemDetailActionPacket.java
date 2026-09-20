package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.MedicalUseManager;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.grid.DeltaQuickMoveService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative actions for the exact source selected by the detail card. */
public record ItemDetailActionPacket(String sourceId, int action)
    implements CustomPacketPayload {
    public static final int DISCARD = 0;
    public static final int USE = 1;
    public static final int EQUIP_WEAPON = 2;
    public static final Type<ItemDetailActionPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "item_detail_action"));
    public static final StreamCodec<FriendlyByteBuf, ItemDetailActionPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ItemDetailActionPacket::sourceId,
            ByteBufCodecs.VAR_INT, ItemDetailActionPacket::action,
            ItemDetailActionPacket::new);

    @Override public Type<ItemDetailActionPacket> type() { return TYPE; }

    public static void handle(ItemDetailActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (packet.action == USE && (packet.sourceId == null
                || packet.sourceId.isBlank() || packet.sourceId.length() > 256
                || !player.containerMenu.getCarried().isEmpty())) {
                PacketDistributor.sendToPlayer(player,
                    new ItemDetailActionResultPacket(false));
                return;
            }
            if (packet.sourceId == null || packet.sourceId.isBlank()
                || packet.sourceId.length() > 256
                || (packet.action != DISCARD && packet.action != USE
                    && packet.action != EQUIP_WEAPON)
                || !player.containerMenu.getCarried().isEmpty()) return;
            InventoryTransferSource.Handle source =
                InventoryTransferSource.find(player, packet.sourceId);
            ItemStack sample = source == null ? ItemStack.EMPTY : source.peek();
            if (sample.isEmpty()) {
                if (packet.action == USE) PacketDistributor.sendToPlayer(player,
                    new ItemDetailActionResultPacket(false));
                return;
            }
            if (packet.action == USE) {
                boolean success = MedicalUseManager.startFromSource(player, source);
                PacketDistributor.sendToPlayer(player,
                    new ItemDetailActionResultPacket(success));
                return;
            }
            if (packet.action == EQUIP_WEAPON) {
                if (!PlayerLayoutSlotRules.isTaczGun(sample)) return;
                ItemStack extracted = source.extract(1);
                if (extracted.isEmpty()) return;
                boolean equipped = DeltaQuickMoveService.equipWeaponLoadout(player, extracted);
                if (!equipped || !extracted.isEmpty()) {
                    source.restore(extracted);
                    return;
                }
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                if (player.containerMenu != player.inventoryMenu) {
                    player.containerMenu.broadcastChanges();
                }
                return;
            }
            ItemStack extracted = source.extract(sample.getCount());
            if (extracted.isEmpty()) return;
            if (player.drop(extracted, false) == null) {
                source.restore(extracted);
                return;
            }
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastChanges();
            }
        });
    }
}
