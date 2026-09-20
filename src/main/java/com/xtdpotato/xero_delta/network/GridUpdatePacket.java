package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.data.SafetyBoxAccessData;
import com.xtdpotato.xero_delta.data.SafetyBoxReadOnlyPolicy;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.network.RegistryFriendlyByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.CuriosApi;

public record GridUpdatePacket(int action, int gridX, int gridY,
                                boolean shiftToInventory,
                                ItemStack stack) implements CustomPacketPayload {

    public static final int ACTION_PICKUP = 0;
    public static final int ACTION_PLACE = 1;
    public static final int ACTION_SWAP = 2;
    public static final int ACTION_RIGHT_CLICK = 3;

    public static final Type<GridUpdatePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "grid_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GridUpdatePacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeByte(p.action);
            buf.writeInt(p.gridX); buf.writeInt(p.gridY);
            buf.writeBoolean(p.shiftToInventory);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.stack);
        },
        buf -> new GridUpdatePacket(
            buf.readByte(), buf.readInt(), buf.readInt(),
            buf.readBoolean(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)
        )
    );

    @Override public Type<GridUpdatePacket> type() { return TYPE; }

    public static void handle(GridUpdatePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack boxStack = findSafetyBox(player);
            if (boxStack.isEmpty() || !(boxStack.getItem() instanceof SafetyBoxItem sbi)) return;
            GridBackingStore store = new GridBackingStore(boxStack, sbi.getGridWidth(), sbi.getGridHeight());
            ItemStack carried = player.containerMenu.getCarried();
            boolean unlocked = SafetyBoxAccessData.get(player.server).isUnlocked(
                player.getUUID(), boxStack.getItemHolder().getKey().location().toString(),
                System.currentTimeMillis());
            boolean carriedEmpty = carried.isEmpty() && p.stack().isEmpty();
            if (!unlocked && !SafetyBoxReadOnlyPolicy.allows(
                p.action(), carriedEmpty, p.shiftToInventory())) {
                ModNetwork.sendTranslatedNotice(player,
                    "safety_box.xero_delta.expired_read_only");
                player.containerMenu.broadcastChanges();
                return;
            }

            switch (p.action) {
                case ACTION_PICKUP -> {
                    ItemStack taken = store.remove(p.gridX, p.gridY);
                    if (taken.isEmpty()) return;
                    if (p.shiftToInventory) {
                        player.getInventory().add(taken);
                        if (!taken.isEmpty()) player.drop(taken, false);
                    } else {
                        player.containerMenu.setCarried(taken);
                    }
                }
                case ACTION_PLACE -> {
                    ItemStack toPlace = p.stack.copy();
                    if (toPlace.isEmpty()) return;
                    if (store.place(p.gridX, p.gridY, toPlace))
                        player.containerMenu.setCarried(ItemStack.EMPTY);
                }
                case ACTION_SWAP -> {
                    ItemStack existing = store.remove(p.gridX, p.gridY);
                    ItemStack toPlace = p.stack.copy();
                    if (!toPlace.isEmpty()) store.place(p.gridX, p.gridY, toPlace);
                    player.containerMenu.setCarried(existing);
                }
                case ACTION_RIGHT_CLICK -> {
                    ItemStack cur = store.getItemRaw(p.gridX, p.gridY);
                    if (cur.isEmpty() && !p.stack.isEmpty()) {
                        if (store.place(p.gridX, p.gridY, p.stack.copyWithCount(1))) {
                            ItemStack cursor = player.containerMenu.getCarried();
                            cursor.shrink(1);
                            player.containerMenu.setCarried(cursor);
                        }
                    } else if (!cur.isEmpty() && p.stack.isEmpty()) {
                        int half = cur.getCount() / 2;
                        int anchor = store.findAnchorIndexAt(p.gridX, p.gridY);
                        ItemStack split = store.splitStackToCursor(anchor, half);
                        if (!split.isEmpty()) player.containerMenu.setCarried(split);
                    } else if (!cur.isEmpty() && !p.stack.isEmpty()
                               && GridBackingStore.isSameItemIgnoringRotation(cur, p.stack)) {
                        ItemStack grown = cur.copy();
                        grown.grow(1);
                        store.remove(p.gridX, p.gridY);
                        store.place(p.gridX, p.gridY, grown);
                        p.stack.shrink(1);
                        player.containerMenu.setCarried(p.stack);
                    }
                }
            }
        });
    }

    private static ItemStack findSafetyBox(ServerPlayer player) {
        try {
            var opt = CuriosApi.getCuriosInventory(player);
            if (opt.isPresent()) {
                var res = opt.get().findFirstCurio(s -> s.is(ModTags.SAFETY_BOX));
                if (res.isPresent()) return res.get().stack();
            }
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }
}
