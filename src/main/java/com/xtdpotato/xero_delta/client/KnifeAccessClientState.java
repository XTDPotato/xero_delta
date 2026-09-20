package com.xtdpotato.xero_delta.client;

import java.util.Set;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Latest server-authoritative knife-skin unlock snapshot. */
public final class KnifeAccessClientState {
    public static final KnifeAccessClientState INSTANCE = new KnifeAccessClientState();
    private volatile Set<String> unlocked = Set.of();
    private volatile String selected = "";
    private volatile Map<String, CompoundTag> stacks = Map.of();

    private KnifeAccessClientState() {}

    public void update(Set<String> itemIds, String selectedItemId,
                       Map<String, CompoundTag> stackData) {
        unlocked = Set.copyOf(itemIds);
        selected = selectedItemId == null ? "" : selectedItemId;
        stacks = stackData == null ? Map.of() : Map.copyOf(stackData);
    }

    public Set<String> unlocked() { return unlocked; }
    public boolean isUnlocked(String itemId) { return unlocked.contains(itemId); }
    public String selected() { return selected; }

    public ItemStack stack(String itemId, RegistryAccess registries) {
        CompoundTag encoded = stacks.get(itemId);
        return encoded == null ? ItemStack.EMPTY
            : ItemStack.parseOptional(registries, encoded.copy());
    }
}
