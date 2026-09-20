package com.xtdpotato.xero_delta.trading;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Optional equipment/attachment bridges kept reflection-only so backpack mods remain optional. */
public final class TradingOptionalBackpackIntegrations {
    private static final String ACCESSORIES_CAPABILITY =
        "io.wispforest.accessories.api.AccessoriesCapability";
    private static final String TRAVELERS_ATTACHMENT =
        "com.tiviacz.travelersbackpack.capability.AttachmentUtils";

    private TradingOptionalBackpackIntegrations() {
    }

    static List<ItemStack> accessoryStacks(ServerPlayer player) {
        try {
            Class<?> capabilityClass = Class.forName(ACCESSORIES_CAPABILITY);
            Object optionalValue = capabilityClass
                .getMethod("getOptionally", LivingEntity.class).invoke(null, player);
            if (!(optionalValue instanceof Optional<?> optional)) return List.of();
            Object capability = optional.orElse(null);
            if (capability == null) return List.of();
            Object entries = capabilityClass.getMethod("getAllEquipped").invoke(capability);
            if (!(entries instanceof Iterable<?> iterable)) return List.of();
            List<ItemStack> result = new ArrayList<>();
            for (Object entry : iterable) {
                if (entry == null) continue;
                Method stackMethod = entry.getClass().getMethod("stack");
                Object stack = stackMethod.invoke(entry);
                if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) result.add(itemStack);
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return List.of();
        }
    }

    static ItemStack accessoryStack(ServerPlayer player, int index) {
        List<ItemStack> stacks = accessoryStacks(player);
        return index >= 0 && index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
    }

    public static ItemStack travelersAttachmentStack(ServerPlayer player) {
        try {
            Class<?> attachmentClass = Class.forName(TRAVELERS_ATTACHMENT);
            Object stack = attachmentClass.getMethod("getWearingBackpack", Player.class)
                .invoke(null, player);
            return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static void synchroniseTravelersAttachment(ServerPlayer player) {
        try {
            Class<?> attachmentClass = Class.forName(TRAVELERS_ATTACHMENT);
            attachmentClass.getMethod("synchronise", Player.class).invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Traveler's Backpack is optional and may use another equipment integration.
        }
    }
}
