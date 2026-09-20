package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.data.PlayerFeatureAccessData;
import com.xtdpotato.xero_delta.data.SafetyBoxAccessData;
import com.xtdpotato.xero_delta.data.SafetyBoxSkinCatalog;
import com.xtdpotato.xero_delta.grid.SafetyBoxTransferService;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.UUID;

public record SafetyBoxSelectPacket(String itemId) implements CustomPacketPayload {
    public static final Type<SafetyBoxSelectPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "safety_box_select"));
    public static final StreamCodec<FriendlyByteBuf, SafetyBoxSelectPacket> STREAM_CODEC =
        StreamCodec.of((buf, packet) -> buf.writeUtf(packet.itemId, 256),
            buf -> new SafetyBoxSelectPacket(buf.readUtf(256)));

    @Override public Type<SafetyBoxSelectPacket> type() { return TYPE; }

    public static void handle(SafetyBoxSelectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!PlayerFeatureAccessData.get(player.server).allowChangeBc(player)) {
                sendResult(player, false, "safety_box.xero_delta.change_disabled");
                return;
            }
            ResourceLocation id = ResourceLocation.tryParse(packet.itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)
                || !(BuiltInRegistries.ITEM.get(id) instanceof SafetyBoxItem)) return;
            SafetyBoxAccessData access = SafetyBoxAccessData.get(player.server);
            if (!access.isUnlocked(player.getUUID(), packet.itemId, System.currentTimeMillis())) {
                sendResult(player, false, "safety_box.xero_delta.locked");
                access.sync(player);
                return;
            }
            CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
                var handler = curios.getStacksHandler("safety_box").orElse(null);
                if (handler == null || handler.getStacks().getSlots() < 1) return;
                ItemStack equipped = handler.getStacks().getStackInSlot(0);
                if (!equipped.isEmpty()
                    && BuiltInRegistries.ITEM.getKey(equipped.getItem()).equals(id)) {
                    sendResult(player, true, "safety_box.xero_delta.selected_popup");
                    return;
                }

                if (!equipped.isEmpty()
                    && SafetyBoxSkinCatalog.TOP_BOX_ID.equals(
                        BuiltInRegistries.ITEM.getKey(equipped.getItem()).toString())) {
                    String previousSkin = equipped.getOrDefault(
                        ModDataComponents.SAFETY_BOX_SKIN.get(), SafetyBoxSkinCatalog.DEFAULT_SKIN);
                    player.getPersistentData().putString(SafetyBoxSkinCatalog.PERSISTENT_KEY,
                        SafetyBoxSkinCatalog.normalize(previousSkin));
                }
                if (!equipped.isEmpty()
                    && !SafetyBoxTransferService.tryEvacuate(player, equipped).success()) {
                    sendResult(player, false,
                        "safety_box.xero_delta.replace_contents_full");
                    return;
                }
                ItemStack selected = BuiltInRegistries.ITEM.get(id).getDefaultInstance();
                selected.set(ModDataComponents.BOX_UUID.get(), UUID.randomUUID());
                if (SafetyBoxSkinCatalog.TOP_BOX_ID.equals(packet.itemId)) {
                    String savedSkin = player.getPersistentData().getString(
                        SafetyBoxSkinCatalog.PERSISTENT_KEY);
                    selected.set(ModDataComponents.SAFETY_BOX_SKIN.get(),
                        SafetyBoxSkinCatalog.normalize(savedSkin));
                }
                curios.setEquippedCurio("safety_box", 0, selected);
                handler.update();
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                if (player.containerMenu != player.inventoryMenu) {
                    player.containerMenu.broadcastChanges();
                }
                sendResult(player, true, "safety_box.xero_delta.selected_popup");
            });
            access.sync(player);
        });
    }

    private static void sendResult(ServerPlayer player, boolean success, String messageKey) {
        PacketDistributor.sendToPlayer(player,
            new SafetyBoxSelectionResultPacket(success, messageKey));
    }
}
