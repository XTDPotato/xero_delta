package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.KnifeAccessData;
import com.xtdpotato.xero_delta.data.PlayerFeatureAccessData;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.TaczCompatibilityRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Selects one previously unlocked LR Tactical Workshop knife skin. */
public record KnifeSelectPacket(String itemId) implements CustomPacketPayload {
    public static final Type<KnifeSelectPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "knife_select"));
    public static final StreamCodec<FriendlyByteBuf, KnifeSelectPacket> STREAM_CODEC =
        StreamCodec.of((buf, packet) -> buf.writeUtf(packet.itemId, 256),
            buf -> new KnifeSelectPacket(buf.readUtf(256)));

    @Override public Type<KnifeSelectPacket> type() { return TYPE; }

    public static void handle(KnifeSelectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !PlayerLayoutSlotRules.enabled(player)
                || !PlayerFeatureAccessData.get(player.server).allowChangeBc(player)) return;
            ResourceLocation id = ResourceLocation.tryParse(packet.itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
            ItemStack selected = KnifeAccessData.get(player.server).stack(
                player.getUUID(), packet.itemId(), player.registryAccess());
            if (selected.isEmpty()) selected = BuiltInRegistries.ITEM.get(id).getDefaultInstance();
            if (!TaczCompatibilityRules.isLrTacticalMelee(selected)) return;
            KnifeAccessData access = KnifeAccessData.get(player.server);
            if (!access.isUnlocked(player.getUUID(), packet.itemId)) return;
            ItemStack current = player.getInventory().getItem(3);
            if (com.xtdpotato.xero_delta.data.KnifeSkinRules.matches(current, selected)) {
                access.select(player.getUUID(), packet.itemId);
                access.sync(player);
                player.inventoryMenu.broadcastChanges();
                return;
            }
            // Do not destroy an unrelated item from an old or externally modified save.
            if (!current.isEmpty() && !TaczCompatibilityRules.isLrTacticalMelee(current)) return;
            if (!access.select(player.getUUID(), packet.itemId)) return;
            player.getInventory().setItem(3, selected.copyWithCount(1));
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
            access.sync(player);
            player.displayClientMessage(Component.translatable(
                "knife.xero_delta.selected", selected.getHoverName()), true);
        });
    }
}
