package com.xtdpotato.xero_delta.trading;

import com.xtdpotato.xero_delta.data.BoundItemPolicy;
import com.xtdpotato.xero_delta.data.TaczCompatibilityRules;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** Central server/client policy checks for market listing and military-vendor recycle. */
public final class TradingItemEligibility {
    private static volatile Map<String, String> clientRules = Map.of();

    private TradingItemEligibility() {
    }

    public static boolean canList(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && !(stack.getItem() instanceof SafetyBoxItem)
            && !BoundItemPolicy.isBound(stack)
            && !isKnifeSkin(stack)
            && baseAllowed(BuiltInRegistries.ITEM.getKey(stack.getItem()))
            && TradingUploadRulesData.UP.equals(TradingUploadRulesData.resolve(clientRules, stack));
    }

    public static boolean canList(ResourceLocation id) {
        if (!baseAllowed(id) || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        return canList(BuiltInRegistries.ITEM.get(id).getDefaultInstance());
    }

    public static boolean canRecycle(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && !(stack.getItem() instanceof SafetyBoxItem)
            && baseAllowed(BuiltInRegistries.ITEM.getKey(stack.getItem()))
            && canRecycleMode(TradingUploadRulesData.resolve(clientRules, stack));
    }

    public static boolean canRecycle(MinecraftServer server, ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && !(stack.getItem() instanceof SafetyBoxItem)
            && baseAllowed(BuiltInRegistries.ITEM.getKey(stack.getItem()))
            && canRecycleMode(TradingUploadRulesData.get(server).mode(stack));
    }

    public static boolean canList(MinecraftServer server, ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && !(stack.getItem() instanceof SafetyBoxItem)
            && !BoundItemPolicy.isBound(stack)
            && !isKnifeSkin(stack)
            && baseAllowed(BuiltInRegistries.ITEM.getKey(stack.getItem()))
            && TradingUploadRulesData.get(server).canList(stack);
    }

    public static boolean canList(MinecraftServer server, ResourceLocation id) {
        if (!baseAllowed(id) || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        return canList(server, BuiltInRegistries.ITEM.get(id).getDefaultInstance());
    }

    public static void updateClientRules(Map<String, String> rules) {
        clientRules = rules == null ? Map.of() : Map.copyOf(rules);
    }

    public static boolean isKnifeSkin(ItemStack stack) {
        return TaczCompatibilityRules.isLrTacticalMelee(stack);
    }

    private static boolean canRecycleMode(String mode) {
        return TradingUploadRulesData.UP.equals(mode)
            || TradingUploadRulesData.RECYCLE.equals(mode);
    }

    private static boolean baseAllowed(ResourceLocation id) {
        if (id == null) return false;
        return TradingForbiddenItemPaths.canList(id.getNamespace(), id.getPath());
    }
}
