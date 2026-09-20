package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.ModEffects;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.MedicalUseRules;
import com.xtdpotato.xero_delta.item.MedicalItem;
import com.xtdpotato.xero_delta.network.MedicalUseStatePacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/** Client presentation state for a server-authoritative stored medical use. */
public final class MedicalUseClientState {
    public static final MedicalUseClientState INSTANCE = new MedicalUseClientState();

    private boolean active;
    private String itemId = "";
    private int remainingTicks;
    private int durationTicks = 1;
    private String sourceId = "";
    private ItemStack displayStack = ItemStack.EMPTY;

    MedicalUseClientState() {
    }

    public void update(MedicalUseStatePacket packet) {
        active = packet.active();
        itemId = packet.itemId();
        remainingTicks = Math.max(0, packet.remainingTicks());
        durationTicks = Math.max(1, packet.durationTicks());
        sourceId = active ? packet.sourceId() : "";
        displayStack = active ? packet.displayStack().copy() : ItemStack.EMPTY;
        if (!active) itemId = "";
    }

    public void tick() {
        if (active && remainingTicks > 0) {
            remainingTicks--;
        }
    }

    public boolean active() {
        return active;
    }

    public boolean medicalActive() {
        return active && displayedStack().getItem() instanceof MedicalItem;
    }

    /** Hides the local use immediately while the server processes cancellation. */
    public boolean cancelLocally() {
        if (!active) return false;
        active = false;
        remainingTicks = 0;
        itemId = "";
        sourceId = "";
        displayStack = ItemStack.EMPTY;
        return true;
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public ItemStack displayedStack() {
        if (!displayStack.isEmpty()) return displayStack.copy();
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.get(id).getDefaultInstance();
    }

    public float progress() {
        return Math.clamp(1.0F - remainingTicks / (float) durationTicks, 0.0F, 1.0F);
    }

    public boolean isUsing(MedicalWheelClient.Entry entry) {
        return active && entry != null && !sourceId.isBlank() && sourceId.equals(entry.sourceId())
            && ItemStack.isSameItem(entry.stack(), displayedStack());
    }

    /** Presentation only: the reserved stack must never be reinserted into client inventory. */
    public List<MedicalWheelClient.Entry> withReservedEntry(List<MedicalWheelClient.Entry> entries) {
        if (!active || sourceId.isBlank() || !(displayedStack().getItem() instanceof MedicalItem)) return entries;
        if (entries.stream().anyMatch(this::isUsing)) return entries;
        var result = new java.util.ArrayList<>(entries);
        ItemStack stack = displayedStack();
        result.addFirst(new MedicalWheelClient.Entry(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack, sourceId));
        return List.copyOf(result);
    }

    public boolean canAutoUse(Player player) {
        if (player == null || (!player.hasEffect(ModEffects.LEFT_ARM_INJURY)
            && !player.hasEffect(ModEffects.RIGHT_ARM_INJURY))) return false;
        if (hasCandidateInChestRig(player)) return true;
        for (int slot = 4; slot <= 8; slot++) {
            if (isApplicable(player, player.getInventory().getItem(slot))) return true;
        }
        return false;
    }

    private static boolean hasCandidateInChestRig(Player player) {
        final boolean[] found = {false};
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            var handler = curios.getStacksHandler("chest_rig").orElse(null);
            if (handler == null || handler.getSlots() <= 0
                || !curios.isSlotActive("chest_rig", 0)) return;
            ItemStack carrier = handler.getStacks().getStackInSlot(0);
            if (!(carrier.getItem() instanceof DeltaPackItem pack)
                || !"chest_rig".equals(pack.slotIdentifier())) return;
            List<ItemStack> contents = carrier.getOrDefault(
                ModDataComponents.GRID_CONTENTS.get(), List.of());
            for (ItemStack stack : contents) {
                if (isApplicable(player, stack)) {
                    found[0] = true;
                    return;
                }
            }
        });
        return found[0];
    }

    private static boolean isApplicable(Player player, ItemStack stack) {
        return MedicalUseRules.canUseWheelItem(player, stack);
    }
}
