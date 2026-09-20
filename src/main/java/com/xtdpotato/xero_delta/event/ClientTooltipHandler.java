package com.xtdpotato.xero_delta.event;

import com.mojang.datafixers.util.Either;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.CoinPriceData;
import com.xtdpotato.xero_delta.client.ItemSizeTooltipData;
import com.xtdpotato.xero_delta.client.TooltipTitleData;
import com.xtdpotato.xero_delta.client.XeroDeltaClient;
import com.xtdpotato.xero_delta.data.BallisticArmorRules;
import com.xtdpotato.xero_delta.data.TaczCompatibilityRules;
import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArrowItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

@EventBusSubscriber(modid = "xero_delta", value = Dist.CLIENT)
public class ClientTooltipHandler {
    private static final ResourceLocation QUALITY_FONT = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "quality_icons");

    @SubscribeEvent
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        insertSizeComponent(event, stack);
        replaceTitleWithQualityComponent(event, stack);
        appendBallisticDetails(event, stack);
        if (stack.is(ModTags.SAFETY_BOX)) return;

        long value = ClientDataCache.INSTANCE.getPrice(stack);
        if (value > 0) {
            event.getTooltipElements().add(Either.<FormattedText, TooltipComponent>right(new CoinPriceData(value)));
        }
        appendManualRuleSources(event, stack, value);
    }

    /** Labels explicit administrator overrides while leaving automatic defaults clean. */
    private static void appendManualRuleSources(RenderTooltipEvent.GatherComponents event,
                                                ItemStack stack, long value) {
        ClientDataCache cache = ClientDataCache.INSTANCE;
        if (cache.isManualQuality(stack)) {
            String quality = normalizedQuality(cache.getQuality(stack));
            event.getTooltipElements().add(Either.left(Component.translatable(
                "tooltip.xero_delta.manual_quality",
                Component.translatable("quality.xero_delta." + quality))
                .withStyle(qualityFormatting(quality))));
        }
        if (cache.isManualPrice(stack)) {
            event.getTooltipElements().add(Either.left(Component.translatable(
                "tooltip.xero_delta.manual_price", String.format(Locale.ROOT, "%,d", value))
                .withStyle(ChatFormatting.GOLD)));
        }
        if (cache.isManualSize(stack)) {
            var size = cache.getSize(stack);
            event.getTooltipElements().add(Either.left(Component.translatable(
                "tooltip.xero_delta.manual_size", size.width(), size.height())
                .withStyle(ChatFormatting.AQUA)));
        }
    }

    private static void insertSizeComponent(RenderTooltipEvent.GatherComponents event, ItemStack stack) {
        Config.TooltipSizeMode mode = Config.INSTANCE.getTooltipSizeMode();
        if (mode == Config.TooltipSizeMode.OFF) return;
        var elements = event.getTooltipElements();
        int titleIndex = -1;
        for (int index = 0; index < elements.size(); index++) {
            if (elements.get(index).left().isPresent()) {
                titleIndex = index;
                break;
            }
        }
        elements.add(Math.min(elements.size(), titleIndex + 1), Either.right(new ItemSizeTooltipData(
            stack.copy(), ClientDataCache.INSTANCE.getSize(stack),
            ClientDataCache.INSTANCE.getWeight(stack), mode)));
    }

    private static void replaceTitleWithQualityComponent(RenderTooltipEvent.GatherComponents event, ItemStack stack) {
        var elements = event.getTooltipElements();
        for (int index = 0; index < elements.size(); index++) {
            FormattedText text = elements.get(index).left().orElse(null);
            if (text == null) continue;
            MutableComponent original = text instanceof Component component
                ? component.copy()
                : Component.literal(text.getString());
            String quality = ClientDataCache.INSTANCE.getQuality(stack);
            MutableComponent icon = Component.literal(qualityGlyph(quality))
                .withStyle(style -> style.withFont(QUALITY_FONT).withColor(0xFFFFFF));
            MutableComponent tier = Component.translatable("quality.xero_delta." + normalizedQuality(quality))
                .withStyle(qualityFormatting(quality));
            MutableComponent tierLabel = Component.literal(" [")
                .withStyle(qualityFormatting(quality))
                .append(tier)
                .append(Component.literal("] ").withStyle(qualityFormatting(quality)));
            MutableComponent coloredName = original.copy().withStyle(qualityFormatting(quality));
            MutableComponent title = Component.empty().append(icon).append(tierLabel).append(coloredName);
            elements.set(index, Either.right(new TooltipTitleData(title)));
            return;
        }
    }

    private static void appendBallisticDetails(RenderTooltipEvent.GatherComponents event, ItemStack stack) {
        List<Double> custom = BallisticArmorRules.clientRule(stack);
        if (!isBallisticDetailItem(stack, custom)) return;
        if (!XeroDeltaClient.BULLET_DETAILS_KEY.isDown()) {
            event.getTooltipElements().add(Either.left(Component.translatable(
                "tooltip.xero_delta.bullet.hold_details",
                XeroDeltaClient.BULLET_DETAILS_KEY.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY)));
            return;
        }

        String quality = ClientDataCache.INSTANCE.getQuality(stack);
        int penetration = BallisticArmorRules.tierIndex(quality) + 1;
        List<Double> multipliers = custom != null
            ? custom : BallisticArmorRules.defaultsForProjectileQuality(quality);
        event.getTooltipElements().add(Either.left(Component.translatable(
            "tooltip.xero_delta.bullet.penetration", penetration,
            Component.literal(penetrationMeter(penetration)).withStyle(qualityFormatting(quality))
        ).withStyle(ChatFormatting.GRAY)));
        event.getTooltipElements().add(Either.left(Component.translatable(
            "tooltip.xero_delta.bullet.base_damage_ratio", "100%").withStyle(ChatFormatting.GRAY)));
        event.getTooltipElements().add(Either.left(Component.translatable(
            "tooltip.xero_delta.bullet.applicable", Component.translatable(
                "tooltip.xero_delta.bullet.applicable.dynamic")).withStyle(ChatFormatting.GRAY)));
        event.getTooltipElements().add(Either.left(Component.translatable(
            "tooltip.xero_delta.bullet.armor_decay").withStyle(ChatFormatting.DARK_GRAY)));
        String[] tiers = {"gray", "green", "blue", "purple", "gold", "red"};
        for (int index = 0; index < tiers.length; index++) {
            String tier = tiers[index];
            MutableComponent icon = Component.literal(qualityGlyph(tier))
                .withStyle(style -> style.withFont(QUALITY_FONT).withColor(0xFFFFFF));
            MutableComponent line = Component.literal("  ").append(icon).append(Component.literal(" "))
                .append(Component.translatable("quality.xero_delta." + tier))
                .append(Component.literal(": " + percent(multipliers.get(index))))
                .withStyle(qualityFormatting(tier));
            event.getTooltipElements().add(Either.left(line));
        }
        if (custom != null) {
            event.getTooltipElements().add(Either.left(Component.translatable(
                "tooltip.xero_delta.bullet.custom_rule").withStyle(ChatFormatting.GOLD)));
        }
    }

    private static boolean isBallisticDetailItem(ItemStack stack, List<Double> custom) {
        if (custom != null || stack.getItem() instanceof ArrowItem) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (containsAny(path, "ammo", "bullet", "cartridge", "shell", "round")) return true;
        String descriptor = TaczCompatibilityRules.descriptor(stack);
        String className = stack.getItem().getClass().getName().toLowerCase(Locale.ROOT);
        return containsAny(className, "ammoitem", "bulletitem", "cartridgeitem")
            || containsAny(descriptor, " ammo_id", "bullet_id", "cartridge_id", "12_gauge", "bmg", "nato");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String penetrationMeter(int level) {
        int clamped = Math.max(1, Math.min(6, level));
        return "[" + "■".repeat(clamped) + "□".repeat(6 - clamped) + "]";
    }

    private static String percent(double multiplier) {
        return BigDecimal.valueOf(multiplier * 100.0D).stripTrailingZeros().toPlainString() + "%";
    }
    private static String normalizedQuality(String quality) {
        return switch (quality == null ? "gray" : quality.toLowerCase(java.util.Locale.ROOT)) {
            case "red", "gold", "purple", "blue", "green" -> quality.toLowerCase(java.util.Locale.ROOT);
            default -> "gray";
        };
    }

    private static ChatFormatting qualityFormatting(String quality) {
        return switch (normalizedQuality(quality)) {
            case "red" -> ChatFormatting.RED;
            case "gold" -> ChatFormatting.GOLD;
            case "purple" -> ChatFormatting.LIGHT_PURPLE;
            case "blue" -> ChatFormatting.BLUE;
            case "green" -> ChatFormatting.GREEN;
            default -> ChatFormatting.GRAY;
        };
    }

    private static String qualityGlyph(String quality) {
        return switch (quality == null ? "gray" : quality.toLowerCase(java.util.Locale.ROOT)) {
            case "green" -> "\uE001";
            case "blue" -> "\uE002";
            case "purple" -> "\uE003";
            case "gold" -> "\uE004";
            case "red" -> "\uE005";
            default -> "\uE000";
        };
    }
}
