package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Full-card cursor swap for the enlarged restricted-layout hotbar equipment slots. */
public record PlayerLayoutSlotClickPacket(int inventoryIndex) implements CustomPacketPayload {
    public static final Type<PlayerLayoutSlotClickPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "layout_slot_click"));
    public static final StreamCodec<FriendlyByteBuf, PlayerLayoutSlotClickPacket> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, PlayerLayoutSlotClickPacket::inventoryIndex,
            PlayerLayoutSlotClickPacket::new);

    @Override public Type<PlayerLayoutSlotClickPacket> type() { return TYPE; }

    public static void handle(PlayerLayoutSlotClickPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !PlayerLayoutSlotRules.enabled(player)
                || packet.inventoryIndex < 0 || packet.inventoryIndex > 8
                || packet.inventoryIndex == 3) return;
            var cursorMenu = player.containerMenu;
            ItemStack carried = cursorMenu.getCarried();
            ItemStack stored = player.getInventory().getItem(packet.inventoryIndex);
            if (!carried.isEmpty()
                && !PlayerLayoutSlotRules.canPlace(player, packet.inventoryIndex, carried)) return;
            player.getInventory().setItem(packet.inventoryIndex, carried.copy());
            cursorMenu.setCarried(stored.copy());
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (cursorMenu != player.inventoryMenu) cursorMenu.broadcastChanges();
        });
    }
}
