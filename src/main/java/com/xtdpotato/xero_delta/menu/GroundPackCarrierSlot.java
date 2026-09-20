package com.xtdpotato.xero_delta.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.xtdpotato.xero_delta.item.DeltaPackItem;

/** The ground carrier itself, exposed separately from the cells stored inside it. */
public final class GroundPackCarrierSlot extends Slot {
    private final String identifier;

    public GroundPackCarrierSlot(GroundPackContainer container, String identifier,
                                 int x, int y) {
        super(container, container.carrierSlot(), x, y);
        this.identifier = identifier;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof DeltaPackItem incoming)) return false;
        return identifier.equals(incoming.slotIdentifier());
    }
    @Override public boolean mayPickup(Player player) { return hasItem(); }
}
