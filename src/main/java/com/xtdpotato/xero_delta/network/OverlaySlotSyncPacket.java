package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.grid.SafetyBoxContainer;
import com.xtdpotato.xero_delta.grid.SafetyBoxSlot;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record OverlaySlotSyncPacket(boolean activate, int gridW, int gridH, String menuKey) implements CustomPacketPayload {

    public static final Type<OverlaySlotSyncPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "overlay_slot_sync"));

    public static final StreamCodec<FriendlyByteBuf, OverlaySlotSyncPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBoolean(p.activate); buf.writeInt(p.gridW); buf.writeInt(p.gridH); buf.writeUtf(p.menuKey); },
        buf -> new OverlaySlotSyncPacket(buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readUtf())
    );

    @Override public Type<OverlaySlotSyncPacket> type() { return TYPE; }

    // Track injected slots per player for removal
    private static final Map<UUID, List<Slot>> injectedSlots = new ConcurrentHashMap<>();

    public static void handle(OverlaySlotSyncPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            AbstractContainerMenu menu = player.containerMenu;
            UUID pid = player.getUUID();

            if (p.activate) {
                // Find equipped safety box
                ItemStack box = findBox(player);
                if (box.isEmpty() || !(box.getItem() instanceof SafetyBoxItem sbi)) return;

                // Remove any previously injected slots
                List<Slot> oldSlots = injectedSlots.remove(pid);
                if (oldSlots != null) menu.slots.removeAll(oldSlots);

                // Create container + slots
                SafetyBoxContainer container = new SafetyBoxContainer(box, p.gridW, p.gridH);
                container.loadFromComponent();

                List<Slot> newSlots = new ArrayList<>();
                for (int y = 0; y < p.gridH; y++)
                    for (int x = 0; x < p.gridW; x++) {
                        SafetyBoxSlot slot = new SafetyBoxSlot(container, x, y, 0, 0);
                        slot.index = menu.slots.size();
                        menu.slots.add(slot);
                        newSlots.add(slot);
                    }
                injectedSlots.put(pid, newSlots);

                // Save on menu close
                menu.addSlotListener(new net.minecraft.world.inventory.ContainerListener() {
                    @Override public void slotChanged(AbstractContainerMenu m, int i, ItemStack s) {}
                    @Override public void dataChanged(AbstractContainerMenu m, int i, int v) {}
                });
            } else {
                // Remove injected slots
                List<Slot> slots = injectedSlots.remove(pid);
                if (slots != null) {
                    // Save before removing
                    if (!slots.isEmpty() && slots.get(0).container instanceof SafetyBoxContainer sc) {
                        sc.saveToComponent();
                    }
                    menu.slots.removeAll(slots);
                }
            }
        });
    }

    private static ItemStack findBox(ServerPlayer player) {
        try {
            var o = CuriosApi.getCuriosInventory(player);
            if (o.isPresent()) {
                var r = o.get().findFirstCurio(s -> s.is(ModTags.SAFETY_BOX));
                if (r.isPresent()) return r.get().stack();
            }
        } catch (Exception ignored) {}
        for (var h : net.minecraft.world.InteractionHand.values()) {
            ItemStack s = player.getItemInHand(h);
            if (s.getItem() instanceof SafetyBoxItem) return s;
        }
        return ItemStack.EMPTY;
    }
}