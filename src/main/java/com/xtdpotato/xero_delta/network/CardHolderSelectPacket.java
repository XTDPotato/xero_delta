package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.PlayerFeatureAccessData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Equips an owned card holder selected from the full-slot picker. The source
 * slot is revalidated server-side so the picker cannot create arbitrary items.
 */
public record CardHolderSelectPacket(int sourceSlot, String itemId)
    implements CustomPacketPayload {
    private static final int CURSOR_SOURCE = -1;
    private static final TagKey<Item> CARD_HOLDERS = TagKey.create(Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath("curios", "card_holder"));

    public static final Type<CardHolderSelectPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "card_holder_select"));
    public static final StreamCodec<FriendlyByteBuf, CardHolderSelectPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CardHolderSelectPacket::sourceSlot,
            ByteBufCodecs.STRING_UTF8, CardHolderSelectPacket::itemId,
            CardHolderSelectPacket::new);

    @Override
    public Type<CardHolderSelectPacket> type() {
        return TYPE;
    }

    public static void handle(CardHolderSelectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !PlayerFeatureAccessData.get(player.server)
                    .allowChangeBc(player)) return;
            ResourceLocation requested = ResourceLocation.tryParse(packet.itemId);
            if (requested == null || !BuiltInRegistries.ITEM.containsKey(requested)) return;

            ItemStack candidate;
            if (packet.sourceSlot == CURSOR_SOURCE) {
                candidate = player.containerMenu.getCarried();
            } else {
                if (packet.sourceSlot < 0
                    || packet.sourceSlot >= player.getInventory().getContainerSize()) return;
                candidate = player.getInventory().getItem(packet.sourceSlot);
            }
            if (candidate.isEmpty() || candidate.getCount() != 1
                || !candidate.is(CARD_HOLDERS)
                || !BuiltInRegistries.ITEM.getKey(candidate.getItem()).equals(requested)) return;

            CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
                var handler = curios.getStacksHandler("card_holder").orElse(null);
                if (handler == null || handler.getSlots() <= 0
                    || !curios.isSlotActive("card_holder", 0)
                    || !handler.getStacks().isItemValid(0, candidate)) return;
                ItemStack equipped = handler.getStacks().getStackInSlot(0).copy();
                curios.setEquippedCurio("card_holder", 0, candidate.copy());
                if (packet.sourceSlot == CURSOR_SOURCE) {
                    player.containerMenu.setCarried(equipped);
                } else {
                    player.getInventory().setItem(packet.sourceSlot, equipped);
                }
                handler.update();
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                if (player.containerMenu != player.inventoryMenu) {
                    player.containerMenu.broadcastChanges();
                }
                player.displayClientMessage(Component.translatable(
                    "card_holder.xero_delta.selected", candidate.getHoverName()), true);
            });
        });
    }
}
