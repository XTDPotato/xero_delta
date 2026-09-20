package com.xtdpotato.xero_delta.item;

import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.client.GridPreviewData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SafetyBoxItem extends Item implements ICurioItem {
    private final int gridWidth, gridHeight;

    public SafetyBoxItem(int gw, int gh, Properties props) { super(props); this.gridWidth = gw; this.gridHeight = gh; }
    public int getGridWidth() { return gridWidth; }
    public int getGridHeight() { return gridHeight; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && !stack.has(ModDataComponents.BOX_UUID))
            stack.set(ModDataComponents.BOX_UUID.get(), UUID.randomUUID());
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        if (!level.isClientSide && !stack.has(ModDataComponents.BOX_UUID))
            stack.set(ModDataComponents.BOX_UUID.get(), UUID.randomUUID());
    }

    @Override public boolean canEquip(SlotContext ctx, ItemStack stack) { return "safety_box".equals(ctx.identifier()); }
    @Override public boolean canUnequip(SlotContext ctx, ItemStack stack) { return true; }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        List<ItemStack> contents = stack.getOrDefault(ModDataComponents.GRID_CONTENTS.get(), List.of());
        if (!contents.isEmpty() && !contents.stream().allMatch(ItemStack::isEmpty)) {
            return Optional.of(new GridPreviewData(stack.copy(), contents, gridWidth, gridHeight));
        }
        return Optional.empty();
    }
}
