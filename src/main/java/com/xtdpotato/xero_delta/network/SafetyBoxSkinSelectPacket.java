package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.PlayerFeatureAccessData;
import com.xtdpotato.xero_delta.data.SafetyBoxSkinCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.CuriosApi;

/** Applies one unlocked cosmetic to the currently equipped ultimate safety box. */
public record SafetyBoxSkinSelectPacket(String skinId) implements CustomPacketPayload {
    public static final Type<SafetyBoxSkinSelectPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "safety_box_skin_select"));
    public static final StreamCodec<FriendlyByteBuf, SafetyBoxSkinSelectPacket> STREAM_CODEC =
        StreamCodec.of((buf, packet) -> buf.writeUtf(packet.skinId, 64),
            buf -> new SafetyBoxSkinSelectPacket(buf.readUtf(64)));

    @Override
    public Type<SafetyBoxSkinSelectPacket> type() {
        return TYPE;
    }

    public static void handle(SafetyBoxSkinSelectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!PlayerFeatureAccessData.get(player.server).allowChangeBc(player)) {
                player.displayClientMessage(Component.translatable(
                    "safety_box.xero_delta.change_disabled"), true);
                return;
            }
            String skinId = SafetyBoxSkinCatalog.normalize(packet.skinId);
            if (!SafetyBoxSkinCatalog.isKnown(packet.skinId)
                || !SafetyBoxSkinCatalog.isUnlockedByDefault(skinId)) {
                player.displayClientMessage(Component.translatable(
                    "safety_box.xero_delta.skin.locked"), true);
                return;
            }
            CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
                var handler = curios.getStacksHandler("safety_box").orElse(null);
                if (handler == null || handler.getStacks().getSlots() < 1) return;
                ItemStack equipped = handler.getStacks().getStackInSlot(0);
                String itemId = equipped.isEmpty() ? ""
                    : BuiltInRegistries.ITEM.getKey(equipped.getItem()).toString();
                if (!SafetyBoxSkinCatalog.TOP_BOX_ID.equals(itemId)) {
                    player.displayClientMessage(Component.translatable(
                        "safety_box.xero_delta.skin.equip_top_first"), true);
                    return;
                }
                equipped.set(ModDataComponents.SAFETY_BOX_SKIN.get(), skinId);
                player.getPersistentData().putString(SafetyBoxSkinCatalog.PERSISTENT_KEY, skinId);
                handler.update();
                player.inventoryMenu.broadcastChanges();
                if (player.containerMenu != player.inventoryMenu) {
                    player.containerMenu.broadcastChanges();
                }
                player.displayClientMessage(Component.translatable(
                    "safety_box.xero_delta.skin.selected",
                    Component.translatable("safety_box.xero_delta.skin." + skinId)), true);
            });
        });
    }
}
