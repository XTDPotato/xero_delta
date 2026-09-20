package com.xtdpotato.xero_delta.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.BoundItemPolicy;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.AutomaticItemValuation;
import com.xtdpotato.xero_delta.data.AutomaticItemSizing;
import com.xtdpotato.xero_delta.data.AutomaticItemWeight;
import com.xtdpotato.xero_delta.data.BulletArmorRulesData;
import com.xtdpotato.xero_delta.data.ItemSizeRule;
import com.xtdpotato.xero_delta.data.ServerItemRules;
import com.xtdpotato.xero_delta.data.ServerItemWeights;
import com.xtdpotato.xero_delta.data.PlayerLayoutRulesData;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.HealthSystemRulesData;
import com.xtdpotato.xero_delta.data.HealthPenaltyRulesData;
import com.xtdpotato.xero_delta.data.CorpseRulesData;
import com.xtdpotato.xero_delta.data.DownedManager;
import com.xtdpotato.xero_delta.data.RaidEarningsData;
import com.xtdpotato.xero_delta.data.PlayerFeatureAccessData;
import com.xtdpotato.xero_delta.data.PlayerStaminaManager;
import com.xtdpotato.xero_delta.data.KnifeAccessData;
import com.xtdpotato.xero_delta.data.TaczCompatibilityRules;
import com.xtdpotato.xero_delta.data.SafetyBoxAccessData;
import com.xtdpotato.xero_delta.data.LootSearchManager;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.data.DurabilityRange;
import com.xtdpotato.xero_delta.data.AutomaticCalculationSnapshot;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.ServerEvents;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.SyncDataPacket;
import com.xtdpotato.xero_delta.network.TradingSyncPacket;
import com.xtdpotato.xero_delta.network.XeroTitlePacket;
import com.xtdpotato.xero_delta.trading.TradingMarketData;
import com.xtdpotato.xero_delta.trading.TradingMarketService;
import com.xtdpotato.xero_delta.trading.TradingListing;
import com.xtdpotato.xero_delta.trading.TradingPriceSpec;
import com.xtdpotato.xero_delta.trading.TradingRules;
import com.xtdpotato.xero_delta.trading.TradingScreenOpener;
import com.xtdpotato.xero_delta.trading.TradingItemEligibility;
import com.xtdpotato.xero_delta.trading.TradingUploadRulesData;
import com.xtdpotato.xero_delta.mail.MailData;
import com.xtdpotato.xero_delta.mail.MailPayloadParser;
import com.xtdpotato.xero_delta.mail.MailService;
import com.xtdpotato.xero_delta.network.MailSyncPacket;
import com.xtdpotato.xero_delta.network.GuiOpenPacket;
import com.xtdpotato.xero_delta.network.DialogPacket;
import com.xtdpotato.xero_delta.gui.GuiScreenTarget;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import java.text.NumberFormat;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;

public class ModCommands {

    private static final NumberFormat FMT = NumberFormat.getIntegerInstance(Locale.US);
    private static final AtomicBoolean AUTO_CALCULATION_RUNNING = new AtomicBoolean();
    private static final ExecutorService AUTO_CALCULATION_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "XeroDelta-AutoCalculation");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private static final SuggestionProvider<CommandSourceStack> VALUE_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"100", "500", "1000", "5000", "10000", "50000", "100000"}, builder);
    private static final SuggestionProvider<CommandSourceStack> ITEM_ID_SUGGESTIONS = (ctx, builder) -> {
        addItemIdSuggestions(builder, true);
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> SAFETY_BOX_ID_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        int suggested = 0;
        for (var itemId : BuiltInRegistries.ITEM.keySet()) {
            if (!(BuiltInRegistries.ITEM.get(itemId) instanceof SafetyBoxItem)) continue;
            String fullId = itemId.toString().toLowerCase(Locale.ROOT);
            String path = itemId.getPath().toLowerCase(Locale.ROOT);
            if (!remaining.isBlank() && !fullId.startsWith(remaining) && !path.startsWith(remaining)) continue;
            builder.suggest(itemId.toString());
            if (++suggested >= 64) break;
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> KNIFE_ID_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        int suggested = 0;
        for (var itemId : BuiltInRegistries.ITEM.keySet()) {
            ItemStack stack = BuiltInRegistries.ITEM.get(itemId).getDefaultInstance();
            if (!TaczCompatibilityRules.isLrTacticalMelee(stack)) continue;
            String fullId = itemId.toString().toLowerCase(Locale.ROOT);
            String path = itemId.getPath().toLowerCase(Locale.ROOT);
            if (!remaining.isBlank() && !fullId.startsWith(remaining)
                && !path.startsWith(remaining)) continue;
            builder.suggest(itemId.toString(), stack.getHoverName());
            if (++suggested >= 128) break;
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> ROTATE_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"rotate", "false"}, builder);
    private static final SuggestionProvider<CommandSourceStack> STRETCH_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"stretch", "false", "prop1", "prop2"}, builder);
    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"this_type", "all_type", "this_durability"}, builder);
    private static final SuggestionProvider<CommandSourceStack> DURABILITY_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"0..max", "min..max", "0..1_3", "100..200"}, builder);
    private static final SuggestionProvider<CommandSourceStack> WEIGHT_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"0.01", "0.1", "0.27", "0.5", "1", "5", "10"}, builder);
    private static final SuggestionProvider<CommandSourceStack> WEIGHT_MODE_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"this_type", "all_type", "0..max", "min..max",
            "0..500", "min..900", "500..max"}, builder);
    private static final SuggestionProvider<CommandSourceStack> TRADING_SELLER_OR_ITEM_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        suggestCandidate(builder, "@s", remaining);
        suggestCandidate(builder, "@a", remaining);
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            suggestCandidate(builder, player.getGameProfile().getName(), remaining);
        }
        if (!remaining.isBlank() && !remaining.startsWith("@")) addItemIdSuggestions(builder, false);
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> TRADING_UP_ITEM_SUGGESTIONS = (ctx, builder) -> {
        addItemIdSuggestions(builder, false);
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> TRADING_UP_PRICE_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"30000", "+1000", "-1200..2000", "0..1000"}, builder);
    private static final SuggestionProvider<CommandSourceStack> LISTING_ID_SUGGESTIONS = (ctx, builder) -> {
        for (TradingListing listing : TradingMarketData.get(ctx.getSource().getServer()).listings()) {
            builder.suggest(listingReference(listing), listing.stack().getHoverName());
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> AUTO_ITEM_SUGGESTIONS = (ctx, builder) -> {
        builder.suggest("all");
        return ITEM_ID_SUGGESTIONS.getSuggestions(ctx, builder);
    };
    private static final SuggestionProvider<CommandSourceStack> GUI_TARGET_SUGGESTIONS =
        (ctx, builder) -> SharedSuggestionProvider.suggest(
            java.util.Arrays.stream(GuiScreenTarget.values()).map(GuiScreenTarget::id), builder);
    private static void addItemIdSuggestions(SuggestionsBuilder builder, boolean includePatternsWhenEmpty) {
        String remaining = builder.getRemainingLowerCase();
        if (remaining.isBlank()) {
            if (!includePatternsWhenEmpty) return;
            builder.suggest("*");
            Set<String> namespaces = new java.util.TreeSet<>();
            for (var itemId : BuiltInRegistries.ITEM.keySet()) namespaces.add(itemId.getNamespace());
            for (String namespace : namespaces) builder.suggest(namespace + "*");
            return;
        }
        int suggested = 0;
        for (var itemId : BuiltInRegistries.ITEM.keySet()) {
            String fullId = itemId.toString().toLowerCase(Locale.ROOT);
            String path = itemId.getPath().toLowerCase(Locale.ROOT);
            if (!fullId.startsWith(remaining) && !path.startsWith(remaining)) continue;
            builder.suggest(itemId.toString());
            if (++suggested >= 64) break;
        }
    }

    private static void suggestCandidate(SuggestionsBuilder builder, String candidate, String remaining) {
        if (remaining.isBlank() || candidate.toLowerCase(Locale.ROOT).startsWith(remaining)) {
            builder.suggest(candidate);
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {

        dispatcher.register(Commands.literal("xero_set")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("layout")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(ModCommands::setPlayerLayout)))
            .then(Commands.literal("layout_click")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(ModCommands::setLayoutClick)))
            .then(Commands.literal("effect")
                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0D, 10.0D))
                    .executes(ModCommands::setHealthEffectMultiplier)))
            .then(Commands.literal("re_effect")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(ModCommands::setRetainHealthEffects)))
            .then(Commands.literal("health_penalty")
                .then(Commands.literal("chest")
                    .then(Commands.argument("percent", DoubleArgumentType.doubleArg(
                            HealthPenaltyRulesData.MIN_PERCENT,
                            HealthPenaltyRulesData.MAX_PERCENT))
                        .executes(ctx -> setHealthPenalty(ctx, true))))
                .then(Commands.literal("yellow_rescue")
                    .then(Commands.argument("percent", DoubleArgumentType.doubleArg(
                            HealthPenaltyRulesData.MIN_PERCENT,
                            HealthPenaltyRulesData.MAX_PERCENT))
                        .executes(ctx -> setHealthPenalty(ctx, false)))))
            .then(Commands.literal("corpse_lifetime")
                .then(Commands.argument("minutes", IntegerArgumentType.integer(
                        CorpseRulesData.MIN_LIFETIME_MINUTES,
                        CorpseRulesData.MAX_LIFETIME_MINUTES))
                    .executes(ModCommands::setCorpseLifetime)))
            .then(Commands.literal("mob_corpse_attackable")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(ModCommands::setMobCorpsesAttackable)))
            .then(lootSearchCommand("block_search"))
            .then(Commands.literal("stamina")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("maximum", DoubleArgumentType.doubleArg(1.0D, 100_000.0D))
                        .executes(ModCommands::setMaximumStamina))))
            .then(Commands.literal("coin_give")
                .then(Commands.literal("give")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("currency",
                            LongArgumentType.longArg(1L, TradingRules.MAX_CURRENCY))
                            .executes(ModCommands::giveRaidEarnings))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ModCommands::removeRaidEarnings))))
            .then(Commands.literal("player_evacuate")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ModCommands::evacuatePlayers)))
            .then(itemTradingUploadCommand())
            .then(bulletArmorCommand())
            .then(Commands.literal("allow_change_bc")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ModCommands::setFeatureAccess))))
            .then(Commands.literal("item_bound")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ModCommands::setHeldItemBound)))));

        dispatcher.register(Commands.literal("xero_title")
            .requires(source -> source.hasPermission(2))
            .then(xeroTitleActionbarCommand()));

        dispatcher.register(lootSearchCommand("xero_loot_search")
            .requires(source -> source.hasPermission(2)));

        dispatcher.register(guiCommand());
        dispatcher.register(dialogCommand());

        var priceSetValue = Commands.argument("value", IntegerArgumentType.integer(-1, 999999999))
            .suggests(VALUE_SUGGESTIONS)
            .then(priceSetMode("true"))
            .then(priceSetMode("false"))
            .then(priceSetMode("*"));

        dispatcher.register(Commands.literal("xero_price")
            .then(Commands.literal("auto").executes(ModCommands::autoPrice))
            .then(Commands.literal("reset_all").executes(ModCommands::resetAllPrices))
            .then(currencyAddCommand())
            .then(currencySetCommand())
            .then(newPriceSet())
            .then(Commands.literal("get")
                .then(currencyGetTargets())
                .then(priceGetMode("true"))
                .then(priceGetMode("false"))
                .then(priceGetMode("*")))
            .then(Commands.literal("remove")
                .then(priceRemoveMode("true"))
                .then(priceRemoveMode("false"))
                .then(priceRemoveMode("*"))));

        dispatcher.register(Commands.literal("xero_size")
            .then(newSizeAuto())
            .then(Commands.literal("reset_all").executes(ModCommands::resetAllSizes))
            .then(newSizeSet())
            .then(Commands.literal("get")
                .then(sizeGetMode("true"))
                .then(sizeGetMode("false"))
                .then(sizeGetMode("*")))
            .then(Commands.literal("remove")
                .then(sizeRemoveMode("true"))
                .then(sizeRemoveMode("false"))
                .then(sizeRemoveMode("*"))));

        var qualitySet = newQualitySet();

        var qualityGet = Commands.literal("get")
            .then(qualityGetMode("true"))
            .then(qualityGetMode("false"))
            .then(qualityGetMode("*"));

        var qualityRemove = Commands.literal("remove")
            .then(qualityRemoveMode("true"))
            .then(qualityRemoveMode("false"))
            .then(qualityRemoveMode("*"));

        dispatcher.register(Commands.literal("xero_quality")
            .then(Commands.literal("auto").executes(ModCommands::autoQuality))
            .then(Commands.literal("reset_all").executes(ModCommands::resetAllQualities))
            .then(qualitySet)
            .then(qualityGet)
            .then(qualityRemove));

        dispatcher.register(Commands.literal("xero_weight")
            .then(weightSetCommand())
            .then(weightGetCommand())
            .then(Commands.literal("auto").requires(source -> source.hasPermission(2))
                .executes(ModCommands::autoWeight)));

        // Safety-box pack reload remains scoped to the safety-box command.
        dispatcher.register(Commands.literal("xero_safety_box")
            .then(Commands.literal("reload")
                .executes(ModCommands::reloadSafetyBox))
            .then(Commands.literal("unlock").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("item_id", ResourceLocationArgument.id())
                        .suggests(SAFETY_BOX_ID_SUGGESTIONS)
                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 36500))
                            .executes(ModCommands::unlockSafetyBox)))))
            .then(Commands.literal("lock").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("item_id", ResourceLocationArgument.id())
                        .suggests(SAFETY_BOX_ID_SUGGESTIONS)
                        .executes(ModCommands::lockSafetyBox)))));

        dispatcher.register(Commands.literal("xero_knife")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("unlock")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ModCommands::unlockHeldKnife)
                    .then(Commands.argument("item_id", ResourceLocationArgument.id())
                        .suggests(KNIFE_ID_SUGGESTIONS)
                        .executes(ModCommands::unlockKnife))))
            .then(Commands.literal("lock")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ModCommands::lockHeldKnife)
                    .then(Commands.argument("item_id", ResourceLocationArgument.id())
                        .suggests(KNIFE_ID_SUGGESTIONS)
                        .executes(ModCommands::lockKnife)))));

        // Global quality/size/value operations live under /xero_all.
        dispatcher.register(Commands.literal("xero_all")
            .then(Commands.literal("qsq")
                .executes(ModCommands::autoAllQualitySizePrice))
            .then(Commands.literal("reset")
                .executes(ModCommands::resetAllQualitySizePrice)));

        dispatcher.register(tradingCommand());
        dispatcher.register(mailCommand());
        dispatcher.register(Commands.literal("xero_trade")
            .then(Commands.literal("info").executes(ModCommands::worldTradingInfo)));
        dispatcher.register(Commands.literal("xero_trading_detail")
            .executes(ctx -> openTradingDetail(ctx, null))
            .then(Commands.argument("item", ResourceLocationArgument.id()).suggests(ITEM_ID_SUGGESTIONS)
                .executes(ctx -> openTradingDetail(ctx, resourceId(ctx, "item")))));
        CommandAliases.register(dispatcher);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dialogCommand() {
        return Commands.literal("xero_dialog")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("open")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("dialog_payload", StringArgumentType.greedyString())
                        .executes(ModCommands::openDialog))))
            .then(Commands.literal("close")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ModCommands::closeDialog)));
    }

    private static int openDialog(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String payload = StringArgumentType.getString(context, "dialog_payload").trim();
        int separator = firstWhitespace(payload);
        if (separator <= 0 || separator >= payload.length() - 1) {
            context.getSource().sendFailure(Component.translatable(
                "command.xero_dialog.invalid_payload"));
            return 0;
        }
        String options = payload.substring(0, separator).trim();
        String data = payload.substring(separator).trim();
        if (options.length() > DialogPacket.MAX_OPTIONS_LENGTH
            || data.length() > DialogPacket.MAX_DATA_LENGTH) {
            context.getSource().sendFailure(Component.translatable(
                "command.xero_dialog.too_large", DialogPacket.MAX_DATA_LENGTH));
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer target : targets) {
            PacketDistributor.sendToPlayer(target, DialogPacket.open(options, data));
        }
        context.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_dialog.opened", targets.size()), true);
        return targets.size();
    }

    private static int closeDialog(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer target : targets) {
            PacketDistributor.sendToPlayer(target, DialogPacket.close());
        }
        context.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_dialog.closed", targets.size()), true);
        return targets.size();
    }

    private static int firstWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) return index;
        }
        return -1;
    }
    private static LiteralArgumentBuilder<CommandSourceStack> guiCommand() {
        return Commands.literal("xero_gui")
            .then(Commands.literal("open")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("screen", StringArgumentType.word())
                        .suggests(GUI_TARGET_SUGGESTIONS)
                        .executes(ModCommands::openGui))));
    }

    private static int openGui(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String requested = StringArgumentType.getString(context, "screen");
        GuiScreenTarget screen = GuiScreenTarget.parse(requested).orElse(null);
        if (screen == null) {
            context.getSource().sendFailure(Component.translatable(
                "command.xero_gui.unknown", requested));
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        if (targets.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.xero_gui.no_target"));
            return 0;
        }
        ServerPlayer sourcePlayer = context.getSource().getEntity() instanceof ServerPlayer player
            ? player : null;
        boolean openingOtherPlayer = sourcePlayer == null
            || targets.stream().anyMatch(target -> target != sourcePlayer);
        if ((openingOtherPlayer || screen == GuiScreenTarget.TRADING_OPERATOR)
            && !context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.translatable("command.xero_gui.no_permission"));
            return 0;
        }
        for (ServerPlayer target : targets) {
            switch (screen) {
                case TRADING_MARKET -> TradingScreenOpener.openMarket(target);
                case RECYCLING -> TradingScreenOpener.openRecycling(target);
                case TRADING_OPERATOR -> TradingScreenOpener.openOperator(target);
                case MAIL -> PacketDistributor.sendToPlayer(target,
                    MailSyncPacket.snapshot(target, true, null, "", true, 0L));
                default -> PacketDistributor.sendToPlayer(target, new GuiOpenPacket(screen.id()));
            }
        }
        context.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_gui.opened", targets.size(), screen.id()), false);
        return targets.size();
    }

    private static int setPlayerLayout(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        PlayerLayoutRulesData.get(context.getSource().getServer()).setEnabled(enabled);
        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            if (enabled) PlayerLayoutSlotRules.ejectDisabledMainInventory(player);
            ServerEvents.syncPlayerStatus(player, true);
        }
        context.getSource().sendSuccess(() -> Component.translatable(
            enabled ? "command.xero_delta.layout.enabled" : "command.xero_delta.layout.disabled"), true);
        return 1;
    }

    private static int setLayoutClick(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerFeatureAccessData.get(context.getSource().getServer())
            .setLayoutClick(player.getUUID(), enabled);
        ServerEvents.syncPlayerStatus(player, true);
        context.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_delta.layout_click", enabled), false);
        return 1;
    }

    private static int setHealthEffectMultiplier(CommandContext<CommandSourceStack> context) {
        double multiplier = DoubleArgumentType.getDouble(context, "multiplier");
        HealthSystemRulesData.get(context.getSource().getServer()).setEffectMultiplier(multiplier);
        context.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_delta.effect.set", multiplier), true);
        return 1;
    }

    private static int setRetainHealthEffects(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        HealthSystemRulesData.get(context.getSource().getServer())
            .setRetainEffectsAfterDeath(enabled);
        context.getSource().sendSuccess(() -> Component.translatable(
            enabled ? "command.xero_delta.re_effect.enabled"
                : "command.xero_delta.re_effect.disabled"), true);
        return 1;
    }

    private static int setHealthPenalty(CommandContext<CommandSourceStack> context,
                                        boolean chest) {
        double percent = DoubleArgumentType.getDouble(context, "percent");
        HealthPenaltyRulesData rules = HealthPenaltyRulesData.get(
            context.getSource().getServer());
        if (chest) rules.setChestPercent(percent);
        else rules.setYellowRescuePercent(percent);
        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            ServerEvents.syncPlayerStatus(player, true);
        }
        String formatted = BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString();
        context.getSource().sendSuccess(() -> Component.translatable(
            chest ? "command.xero_delta.health_penalty.chest"
                : "command.xero_delta.health_penalty.yellow_rescue", formatted), true);
        return 1;
    }

    private static int setCorpseLifetime(CommandContext<CommandSourceStack> context) {
        int minutes = IntegerArgumentType.getInteger(context, "minutes");
        CorpseRulesData.get(context.getSource().getServer()).setLifetimeMinutes(minutes);
        context.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_delta.corpse_lifetime.set", minutes), true);
        return 1;
    }

    private static int setMobCorpsesAttackable(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        CorpseRulesData.get(context.getSource().getServer())
            .setMobCorpsesAttackable(enabled);
        context.getSource().sendSuccess(() -> Component.translatable(
            enabled ? "command.xero_delta.mob_corpse_attackable.enabled"
                : "command.xero_delta.mob_corpse_attackable.disabled"), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lootSearchCommand(String name) {
        return Commands.literal(name)
            .then(Commands.argument("required", BoolArgumentType.bool())
                .executes(ModCommands::setFacingLootSearch)
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ModCommands::setBlockLootSearch))
                .then(Commands.argument("entity_target", EntityArgument.entity())
                    .executes(context -> setEntityLootSearch(context, "entity_target")))
                .then(Commands.literal("entity")
                    .then(Commands.argument("target", EntityArgument.entity())
                        .executes(context -> setEntityLootSearch(context, "target")))));
    }

    private static int setBlockLootSearch(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean required = BoolArgumentType.getBool(context, "required");
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        ServerLevel level = context.getSource().getLevel();
        LootSearchManager.setBlockSearchRequired(level, pos, required);
        sendLootSearchRuleResult(context, required,
            Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        return 1;
    }

    private static int setEntityLootSearch(CommandContext<CommandSourceStack> context,
                                           String argumentName)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean required = BoolArgumentType.getBool(context, "required");
        Entity entity = EntityArgument.getEntity(context, argumentName);
        LootSearchManager.setEntitySearchRequired(entity, required);
        sendLootSearchRuleResult(context, required, entity.getDisplayName());
        return 1;
    }

    private static int setFacingLootSearch(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean required = BoolArgumentType.getBool(context, "required");
        double reach = 8.0D;
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(reach));
        HitResult blockHit = player.pick(reach, 1.0F, false);
        double blockDistance = blockHit.getType() == HitResult.Type.BLOCK
            ? start.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        AABB area = player.getBoundingBox().expandTowards(
            player.getViewVector(1.0F).scale(reach)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            player, start, end, area,
            entity -> entity != player && entity.isPickable(), reach * reach);
        if (entityHit != null
            && start.distanceToSqr(entityHit.getLocation()) <= blockDistance) {
            Entity entity = entityHit.getEntity();
            LootSearchManager.setEntitySearchRequired(entity, required);
            sendLootSearchRuleResult(context, required, entity.getDisplayName());
            return 1;
        }
        if (blockHit.getType() == HitResult.Type.BLOCK
            && blockHit instanceof net.minecraft.world.phys.BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            LootSearchManager.setBlockSearchRequired(player.serverLevel(), pos, required);
            sendLootSearchRuleResult(context, required,
                Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            return 1;
        }
        context.getSource().sendFailure(Component.translatable(
            "command.xero_delta.block_search.no_target"));
        return 0;
    }

    private static void sendLootSearchRuleResult(CommandContext<CommandSourceStack> context,
                                                 boolean required, Component target) {
        context.getSource().sendSuccess(() -> Component.translatable(
            required ? "command.xero_delta.block_search.enabled"
                : "command.xero_delta.block_search.disabled", target), true);
    }
    private static int setMaximumStamina(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        float maximum = (float) DoubleArgumentType.getDouble(context, "maximum");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : targets) PlayerStaminaManager.setMaximum(player, maximum);
        context.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_delta.stamina.set", targets.size(),
            BigDecimal.valueOf(maximum).stripTrailingZeros().toPlainString()), true);
        return targets.size();
    }

    private static int setFeatureAccess(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        PlayerFeatureAccessData data = PlayerFeatureAccessData.get(
            context.getSource().getServer());
        int changed = 0;
        try {
            for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
                data.setAllowChangeBc(player.getUUID(), enabled);
                ServerEvents.syncPlayerStatus(player, true);
                changed++;
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            context.getSource().sendFailure(Component.literal(
                exception.getRawMessage().getString()));
            return 0;
        }
        int count = changed;
        context.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_delta.allow_change_bc", count, enabled), true);
        return changed;
    }

    private static int setHeldItemBound(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        int changed = 0;
        try {
            for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty()) continue;
                if (enabled) BoundItemPolicy.setBound(held, player.getUUID());
                else BoundItemPolicy.setBound(held, false);
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                if (player.containerMenu != player.inventoryMenu) {
                    player.containerMenu.broadcastChanges();
                }
                changed++;
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            context.getSource().sendFailure(Component.literal(
                exception.getRawMessage().getString()));
            return 0;
        }
        if (changed == 0) {
            context.getSource().sendFailure(Component.translatable(
                "command.xero_delta.item_bound.no_item"));
            return 0;
        }
        int count = changed;
        context.getSource().sendSuccess(() -> Component.translatable(
            enabled ? "command.xero_delta.item_bound.enabled"
                : "command.xero_delta.item_bound.disabled", count), true);
        return changed;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mailCommand() {
        return Commands.literal("xero_mail")
            .executes(ModCommands::openOwnMailbox)
            .then(Commands.literal("send").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("mailJson", StringArgumentType.greedyString())
                        .executes(ModCommands::sendMailCommand))))
            .then(Commands.literal("read").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("mailId", StringArgumentType.word())
                        .executes(ModCommands::readMailCommand))))
            .then(Commands.literal("limit").requires(source -> source.hasPermission(2))
                .then(Commands.literal("get").executes(ModCommands::getMailLimitCommand))
                .then(Commands.literal("set")
                    .then(Commands.argument("max", IntegerArgumentType.integer(1, MailData.MAX_MAIL_LIMIT))
                        .executes(ModCommands::setMailLimitCommand))));
    }

    private static int getMailLimitCommand(CommandContext<CommandSourceStack> ctx) {
        int value = MailData.get(ctx.getSource().getServer()).mailboxLimit();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_mail.limit", value), false);
        return value;
    }

    private static int setMailLimitCommand(CommandContext<CommandSourceStack> ctx) {
        int value = MailData.get(ctx.getSource().getServer())
            .setMailboxLimit(IntegerArgumentType.getInteger(ctx, "max"));
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player,
                MailSyncPacket.snapshot(player, false, null, "", true, 0L));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_mail.limit_set", value), true);
        return value;
    }

    private static int openOwnMailbox(CommandContext<CommandSourceStack> ctx)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PacketDistributor.sendToPlayer(player, MailSyncPacket.snapshot(player, true, null, "", true, 0L));
        return 1;
    }

    private static int sendMailCommand(CommandContext<CommandSourceStack> ctx)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String json = StringArgumentType.getString(ctx, "mailJson");
        try {
            MailPayloadParser.Draft draft = MailPayloadParser.parse(json, ctx.getSource().getTextName());
            int sent = MailService.sendCustom(ctx.getSource().getServer(), targets, draft);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_mail.sent", sent), true);
            return sent;
        } catch (RuntimeException error) {
            ctx.getSource().sendFailure(Component.translatable("mail.xero_delta.error.invalid_json"));
            return 0;
        }
    }

    private static int readMailCommand(CommandContext<CommandSourceStack> ctx)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        UUID mailId;
        try {
            mailId = UUID.fromString(StringArgumentType.getString(ctx, "mailId"));
        } catch (IllegalArgumentException error) {
            ctx.getSource().sendFailure(Component.translatable("mail.xero_delta.error.invalid_id"));
            return 0;
        }
        int opened = 0;
        for (ServerPlayer target : EntityArgument.getPlayers(ctx, "targets")) {
            MailData data = MailData.get(ctx.getSource().getServer());
            if (!data.markRead(target.getUUID(), mailId)) continue;
            PacketDistributor.sendToPlayer(target,
                MailSyncPacket.snapshot(target, true, mailId, "", true, 0L));
            opened++;
        }
        final int count = opened;
        if (opened == 0) ctx.getSource().sendFailure(Component.translatable("mail.xero_delta.error.missing"));
        else ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_mail.read", count), false);
        return opened;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tradingCommand() {
        var up = Commands.literal("up").requires(source -> source.hasPermission(2))
            .then(tradingUpTail(null, null))
            .then(Commands.literal("world")
                .then(tradingUpTail(null, null))
                .then(Commands.argument("worldItem", ResourceLocationArgument.id())
                    .suggests(TRADING_UP_ITEM_SUGGESTIONS)
                    .then(tradingUpTail(null, "worldItem"))))
            .then(Commands.argument("sellerOrItem", StringArgumentType.word())
                .suggests(TRADING_SELLER_OR_ITEM_SUGGESTIONS)
                .then(tradingUpTail("sellerOrItem", null))
                .then(Commands.argument("upItem", ResourceLocationArgument.id()).suggests(TRADING_UP_ITEM_SUGGESTIONS)
                    .then(tradingUpTail("sellerOrItem", "upItem"))))
            .then(Commands.argument("directItem", ResourceLocationArgument.id())
                .suggests(TRADING_UP_ITEM_SUGGESTIONS)
                .then(tradingUpTail(null, "directItem")));

        var down = Commands.literal("down").requires(source -> source.hasPermission(2))
            .then(Commands.argument("listingId", StringArgumentType.word()).suggests(LISTING_ID_SUGGESTIONS)
                .executes(ModCommands::downTradingListing)
                .then(Commands.literal("to")
                    .then(Commands.argument("listingEnd", StringArgumentType.word()).suggests(LISTING_ID_SUGGESTIONS)
                        .executes(ctx -> operateTradingRange(ctx, "down")))));

        var relist = Commands.literal("relist").requires(source -> source.hasPermission(2))
            .then(Commands.argument("listingId", StringArgumentType.word()).suggests(LISTING_ID_SUGGESTIONS)
                .executes(ModCommands::relistTradingListing)
                .then(Commands.literal("to")
                    .then(Commands.argument("listingEnd", StringArgumentType.word()).suggests(LISTING_ID_SUGGESTIONS)
                        .executes(ctx -> operateTradingRange(ctx, "relist")))));

        var info = Commands.literal("info")
            .then(Commands.argument("listingId", StringArgumentType.word()).suggests(LISTING_ID_SUGGESTIONS)
                .executes(ModCommands::tradingListingInfo)
                .then(Commands.literal("to")
                    .then(Commands.argument("listingEnd", StringArgumentType.word()).suggests(LISTING_ID_SUGGESTIONS)
                        .executes(ctx -> operateTradingRange(ctx, "info")))));

        var config = Commands.literal("config").requires(source -> source.hasPermission(2))
            .then(Commands.literal("max_duration")
                .then(Commands.argument("days", IntegerArgumentType.integer(1, TradingRules.MAX_CONFIG_LISTING_DAYS))
                    .executes(ModCommands::setTradingMaxDuration)))
            .then(Commands.literal("max_listings")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, TradingRules.MAX_CONFIG_MARKET_LISTINGS))
                    .executes(ModCommands::setTradingMaxListings)))
            .then(Commands.literal("max_player_slots")
                .then(Commands.argument("count", IntegerArgumentType.integer(1,
                        TradingRules.MAX_CONFIG_PLAYER_LISTING_SLOTS))
                    .executes(ModCommands::setTradingMaxPlayerSlots)))
            .then(Commands.literal("slot_level_cost")
                .then(Commands.argument("levels", IntegerArgumentType.integer(0,
                        TradingRules.MAX_CONFIG_LISTING_SLOT_LEVEL_COST))
                    .executes(ModCommands::setTradingSlotLevelCost)))
            .then(Commands.literal("max_currency")
                .then(Commands.argument("amount", LongArgumentType.longArg(1L, TradingRules.MAX_CONFIG_CURRENCY))
                    .executes(ModCommands::setTradingMaxCurrency)));

        var auto = Commands.literal("auto").requires(source -> source.hasPermission(2))
            .then(Commands.literal("remove")
                .then(Commands.literal("all").executes(ModCommands::autoRemoveAllTrading)))
            .then(Commands.literal("add")
                .then(Commands.literal("all").then(autoTradingTail()))
                .then(Commands.argument("autoItem", ResourceLocationArgument.id())
                    .suggests(TRADING_UP_ITEM_SUGGESTIONS)
                    .then(autoTradingTail())));

        var buy = Commands.literal("buy")
            .then(Commands.argument("listingId", StringArgumentType.word()).suggests(LISTING_ID_SUGGESTIONS)
                .executes(ctx -> buyTradingListing(ctx, 1))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 640))
                    .executes(ctx -> buyTradingListing(ctx, IntegerArgumentType.getInteger(ctx, "amount")))));

        var slots = Commands.literal("slots").requires(source -> source.hasPermission(2))
            .then(Commands.literal("unlock")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ModCommands::unlockTradingSlots)))
            .then(Commands.literal("set")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("count", IntegerArgumentType.integer(1,
                            TradingRules.MAX_CONFIG_PLAYER_LISTING_SLOTS))
                        .executes(ModCommands::setTradingPlayerSlots))));

        return Commands.literal("xero_trading")
            .executes(ModCommands::openTradingOperator)
            .then(up)
            .then(down)
            .then(relist)
            .then(info)
            .then(Commands.literal("list").executes(ModCommands::listTradingListings))
            .then(config)
            .then(slots)
            .then(auto)
            .then(buy);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tradingUpTail(String sellerArgument,
                                                                             String itemArgument) {
        return Commands.literal("this_type")
            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 9_999))
                .then(Commands.argument("price_or_offset", StringArgumentType.word())
                    .suggests(TRADING_UP_PRICE_SUGGESTIONS)
                    .executes(ctx -> upTradingListings(ctx, sellerArgument, itemArgument, null))
                    .then(Commands.argument("durationDays", IntegerArgumentType.integer(1,
                            TradingRules.MAX_CONFIG_LISTING_DAYS))
                        .executes(ctx -> upTradingListings(ctx, sellerArgument, itemArgument,
                            "durationDays")))));
    }

    private record TradingSeller(UUID id, String name) {
    }

    private static int upTradingListings(CommandContext<CommandSourceStack> ctx, String sellerArgument,
                                         String itemArgument, String durationArgument)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String sellerToken = sellerArgument == null ? null : string(ctx, sellerArgument);
        String itemToken = itemArgument == null ? null : resourceId(ctx, itemArgument);
        if (sellerToken != null && itemToken == null
            && (!sellerToken.contains(":") || resolveItem(sellerToken).isEmpty())
            && !"world".equalsIgnoreCase(sellerToken)) {
            // One non-item argument is a seller and the item comes from the command source's hand.
        } else if (sellerToken != null && itemToken == null && !"world".equalsIgnoreCase(sellerToken)) {
            itemToken = sellerToken;
            sellerToken = null;
        }

        ItemStack sample = itemToken == null ? commandSourceHand(ctx) : resolveItem(itemToken);
        if (sample.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.no_item"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        TradingPriceSpec priceSpec = TradingPriceSpec.parse(string(ctx, "price_or_offset")).orElse(null);
        if (priceSpec == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_trading.error.price_format"));
            return 0;
        }
        var server = ctx.getSource().getServer();
        long itemValue = ModDataStorage.get(server.overworld()).getPriceFor(sample.copyWithCount(1));
        long price = priceSpec.resolve(itemValue, ThreadLocalRandom.current());
        TradingMarketData market = TradingMarketData.get(server);
        int durationDays = durationArgument == null
            ? Math.min(TradingRules.DEFAULT_LISTING_DAYS, market.maxListingDays())
            : IntegerArgumentType.getInteger(ctx, durationArgument);
        if (durationDays > market.maxListingDays()) {
            ctx.getSource().sendFailure(Component.translatable("market.xero_delta.error.duration_limit",
                market.maxListingDays()));
            return 0;
        }
        List<TradingSeller> sellers = resolveTradingSellers(ctx, sellerToken);
        if (sellers.isEmpty()) return 0;

        int created = 0;
        String lastId = "";
        for (TradingSeller seller : sellers) {
            int remaining = amount;
            int perListing = TradingRules.maxAmountPerListing(sample.getMaxStackSize());
            while (remaining > 0 && market.hasListingCapacity()) {
                int chunk = Math.min(remaining, perListing);
                TradingListing listing = TradingMarketService.adminList(server, seller.id(),
                    seller.name(), sample, chunk, price, durationDays);
                if (listing == null) break;
                created++;
                remaining -= chunk;
                lastId = listing.publicId();
            }
        }
        if (created <= 0) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_trading.error.create"));
            return 0;
        }
        syncTradingAll(ctx.getSource().getServer());
        int finalCreated = created;
        String finalLastId = lastId;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_trading.up.success",
            finalCreated, sample.getHoverName(), amount, FMT.format(price), finalLastId), true);
        return created;
    }

    private static List<TradingSeller> resolveTradingSellers(CommandContext<CommandSourceStack> ctx,
                                                              String sellerToken)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        if (sellerToken == null || sellerToken.isBlank() || "world".equalsIgnoreCase(sellerToken)) {
            return List.of(new TradingSeller(TradingMarketData.WORLD_ACCOUNT_ID,
                TradingMarketData.WORLD_ACCOUNT_NAME));
        }
        if (sellerToken.startsWith("@")) {
            var selector = EntityArgument.players().parse(new StringReader(sellerToken));
            List<TradingSeller> result = new ArrayList<>();
            for (ServerPlayer player : selector.findPlayers(ctx.getSource())) {
                market.claimUnresolvedAccount(player.getUUID(), player.getGameProfile().getName());
                result.add(new TradingSeller(player.getUUID(), player.getGameProfile().getName()));
            }
            return result;
        }
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(sellerToken);
        if (online != null) {
            market.claimUnresolvedAccount(online.getUUID(), online.getGameProfile().getName());
            return List.of(new TradingSeller(online.getUUID(), online.getGameProfile().getName()));
        }
        var cached = ctx.getSource().getServer().getProfileCache().get(sellerToken);
        if (cached.isPresent()) {
            return List.of(new TradingSeller(cached.get().getId(), cached.get().getName()));
        }
        return List.of(new TradingSeller(market.unresolvedAccount(sellerToken), sellerToken));
    }

    private static ItemStack resolveItem(String itemId) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
            ? BuiltInRegistries.ITEM.get(id).getDefaultInstance() : ItemStack.EMPTY;
    }

    private static ItemStack commandSourceHand(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        return player == null ? ItemStack.EMPTY : player.getMainHandItem().copy();
    }

    private static int downTradingListing(CommandContext<CommandSourceStack> ctx) {
        String reference = string(ctx, "listingId");
        TradingListing listing = findListing(TradingMarketData.get(ctx.getSource().getServer()), reference);
        String publicId = listing == null ? reference : listing.publicId();
        TradingMarketService.Result result = TradingMarketService.cancelByPublicId(
            ctx.getSource().getServer(), publicId);
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.translatable(result.message(), publicId));
            return 0;
        }
        syncTradingAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable(result.message(), publicId), true);
        return 1;
    }

    private static int relistTradingListing(CommandContext<CommandSourceStack> ctx) {
        String reference = string(ctx, "listingId");
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        TradingListing before = findListing(market, reference);
        String publicId = before == null ? reference : before.publicId();
        TradingMarketService.Result result = TradingMarketService.relistByPublicId(
            ctx.getSource().getServer(), publicId);
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.translatable(result.message(), publicId));
            return 0;
        }
        syncTradingAll(ctx.getSource().getServer());
        TradingListing after = before == null ? null : market.getListing(before.id());
        String newPublicId = after == null ? publicId : after.publicId();
        ctx.getSource().sendSuccess(() -> Component.translatable(result.message(), publicId, newPublicId), true);
        return 1;
    }

    private static int tradingListingInfo(CommandContext<CommandSourceStack> ctx) {
        String reference = string(ctx, "listingId");
        TradingListing listing = findListing(TradingMarketData.get(ctx.getSource().getServer()), reference);
        if (listing == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_trading.error.unknown_listing", reference));
            return 0;
        }
        long listedAt = TradingMarketData.publicIdCreatedAtMillis(listing.publicId());
        long ageMinutes = listedAt < 0L ? 0L : Math.max(0L, (System.currentTimeMillis() - listedAt) / 60_000L);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_trading.info",
            listing.publicId(), listing.stack().getHoverName(), listing.sellerName(), listing.stack().getCount(),
            FMT.format(listing.price()), ageMinutes), false);
        return 1;
    }

    private static int listTradingListings(CommandContext<CommandSourceStack> ctx) {
        List<TradingListing> listings = TradingMarketData.get(ctx.getSource().getServer()).listings();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_trading.list.header", listings.size()), false);
        for (TradingListing listing : listings.stream().limit(20).toList()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_trading.list.entry",
                listing.publicId(), listing.stack().getHoverName(), listing.sellerName(), listing.stack().getCount(),
                FMT.format(listing.price())), false);
        }
        return listings.size();
    }

    private static int buyTradingListing(CommandContext<CommandSourceStack> ctx, int amount) {
        ServerPlayer buyer = getPlayer(ctx);
        if (buyer == null) return 0;
        String publicId = string(ctx, "listingId");
        TradingListing listing = TradingMarketData.get(ctx.getSource().getServer()).getListing(publicId);
        if (listing == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_trading.error.unknown_listing", publicId));
            return 0;
        }
        TradingMarketService.Result result = TradingMarketService.buy(buyer, listing.id(), amount);
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.translatable(result.message()));
            return 0;
        }
        syncTradingAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_trading.buy.success",
            publicId, amount), false);
        return 1;
    }

    private static int worldTradingInfo(CommandContext<CommandSourceStack> ctx) {
        long balance = TradingMarketData.get(ctx.getSource().getServer()).worldBalance();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_trade.info", FMT.format(balance)), false);
        return 1;
    }

    private static int setTradingMaxDuration(CommandContext<CommandSourceStack> ctx) {
        int days = IntegerArgumentType.getInteger(ctx, "days");
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        market.setMaxListingDays(days);
        syncTradingAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_trading.config.max_duration", days), true);
        return days;
    }

    private static int setTradingMaxListings(CommandContext<CommandSourceStack> ctx) {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        market.setMaxMarketListings(count);
        syncTradingAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_trading.config.max_listings", count), true);
        return count;
    }

    private static int setTradingMaxPlayerSlots(CommandContext<CommandSourceStack> ctx) {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        market.setMaxPlayerListingSlots(count);
        syncTradingAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_trading.config.max_player_slots", count), true);
        return count;
    }

    private static int setTradingSlotLevelCost(CommandContext<CommandSourceStack> ctx) {
        int levels = IntegerArgumentType.getInteger(ctx, "levels");
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        market.setListingSlotLevelCost(levels);
        syncTradingAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_trading.config.slot_level_cost", levels), true);
        return levels + 1;
    }

    private static int unlockTradingSlots(CommandContext<CommandSourceStack> ctx)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        int changed = 0;
        for (ServerPlayer player : targets) {
            int before = market.playerListingSlots(player.getUUID());
            int after = market.unlockNextPlayerListingSlot(player.getUUID());
            if (after > before) changed++;
            syncTrading(player);
        }
        int finalChanged = changed;
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_trading.slots.unlocked", finalChanged), true);
        return changed;
    }

    private static int setTradingPlayerSlots(CommandContext<CommandSourceStack> ctx)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int count = IntegerArgumentType.getInteger(ctx, "count");
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        for (ServerPlayer player : targets) {
            market.setPlayerListingSlots(player.getUUID(), count);
            syncTrading(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_trading.slots.set", targets.size(), count), true);
        return targets.size();
    }

    private static int setTradingMaxCurrency(CommandContext<CommandSourceStack> ctx) {
        long amount = LongArgumentType.getLong(ctx, "amount");
        TradingMarketData.get(ctx.getSource().getServer()).setMaxCurrency(amount);
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_trading.config.max_currency", TradingRules.MAX_CURRENCY), true);
        return 1;
    }

    private static int operateTradingRange(CommandContext<CommandSourceStack> ctx, String operation) {
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        String startReference = string(ctx, "listingId");
        String endReference = string(ctx, "listingEnd");
        TradingListing start = findListing(market, startReference);
        TradingListing end = findListing(market, endReference);
        if (start == null || end == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_trading.error.range"));
            return 0;
        }
        String low = start.publicId().compareTo(end.publicId()) <= 0 ? start.publicId() : end.publicId();
        String high = start.publicId().compareTo(end.publicId()) <= 0 ? end.publicId() : start.publicId();
        List<TradingListing> selected = market.listings().stream()
            .filter(value -> value.sellerName().equalsIgnoreCase(start.sellerName()))
            .filter(value -> value.stack().is(start.stack().getItem()))
            .filter(value -> value.publicId().compareTo(low) >= 0 && value.publicId().compareTo(high) <= 0)
            .sorted(java.util.Comparator.comparing(TradingListing::publicId)).toList();
        int changed = 0;
        for (TradingListing listing : selected) {
            if ("info".equals(operation)) {
                sendListingInfo(ctx, listing);
                changed++;
                continue;
            }
            TradingMarketService.Result result = "down".equals(operation)
                ? TradingMarketService.cancelByPublicId(ctx.getSource().getServer(), listing.publicId())
                : TradingMarketService.relistByPublicId(ctx.getSource().getServer(), listing.publicId());
            if (result.success()) changed++;
        }
        if (!"info".equals(operation)) syncTradingAll(ctx.getSource().getServer());
        int finalChanged = changed;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_trading.range.success",
            operation, finalChanged), true);
        return changed;
    }

    private static void sendListingInfo(CommandContext<CommandSourceStack> ctx, TradingListing listing) {
        long listedAt = TradingMarketData.publicIdCreatedAtMillis(listing.publicId());
        long ageMinutes = listedAt < 0L ? 0L : Math.max(0L, (System.currentTimeMillis() - listedAt) / 60_000L);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_trading.info",
            listing.publicId(), listing.stack().getHoverName(), listing.sellerName(), listing.stack().getCount(),
            FMT.format(listing.price()), ageMinutes), false);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> autoTradingTail() {
        return Commands.argument("baseAmount", IntegerArgumentType.integer(1, 9_999))
            .then(Commands.argument("priceOffset", StringArgumentType.word())
                .then(Commands.argument("amountOffset", StringArgumentType.word())
                    .then(Commands.argument("priceAdjust", DoubleArgumentType.doubleArg(-100.0D, 10_000.0D))
                        .executes(ModCommands::autoRestockTrading))));
    }

    private static int autoRestockTrading(CommandContext<CommandSourceStack> ctx) {
        String itemToken;
        try {
            itemToken = resourceId(ctx, "autoItem");
        } catch (IllegalArgumentException ignored) {
            itemToken = "all";
        }
        int baseAmount = IntegerArgumentType.getInteger(ctx, "baseAmount");
        LongRange priceOffset = parseLongRange(string(ctx, "priceOffset"));
        LongRange amountOffset = parseLongRange(string(ctx, "amountOffset"));
        double adjustment = DoubleArgumentType.getDouble(ctx, "priceAdjust");
        if (priceOffset == null || amountOffset == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_trading.error.auto_format"));
            return 0;
        }
        var server = ctx.getSource().getServer();
        TradingMarketData market = TradingMarketData.get(server);
        List<ItemStack> items = new ArrayList<>();
        if ("all".equalsIgnoreCase(itemToken)) {
            for (var item : BuiltInRegistries.ITEM) {
                ItemStack stack = item.getDefaultInstance();
                var itemId = BuiltInRegistries.ITEM.getKey(item);
                if (TradingItemEligibility.canList(itemId) && !stack.isEmpty()) {
                    items.add(stack);
                }
            }
            // The all-items command must not silently stop at the default 255-listing cap.
            market.setMaxMarketListings(TradingRules.MAX_CONFIG_MARKET_LISTINGS);
        } else {
            ItemStack stack = resolveItem(itemToken);
            if (TradingItemEligibility.canList(stack)) items.add(stack);
        }
        if (items.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.no_item"));
            return 0;
        }
        int created = 0;
        int durationDays = Math.min(TradingRules.DEFAULT_LISTING_DAYS, market.maxListingDays());
        var random = ThreadLocalRandom.current();
        for (ItemStack sample : items) {
            if (!market.hasListingCapacity()) break;
            long unitValue = TradingRules.normalizeItemValue(
                ModDataStorage.get(server.overworld()).getPriceFor(sample.copyWithCount(1)));
            long offset = randomLong(random, priceOffset.minimum, priceOffset.maximum);
            long unitPrice = Math.max(1L, Math.min(TradingRules.MAX_CURRENCY, unitValue + offset));
            unitPrice = TradingRules.adjustByPercent(unitPrice, adjustment);
            long variedAmount = baseAmount + randomLong(random, amountOffset.minimum, amountOffset.maximum);
            int remaining = (int) Math.max(1L, Math.min(9_999L, variedAmount));
            int perListing = TradingRules.maxAmountPerListing(sample.getMaxStackSize());
            while (remaining > 0 && market.hasListingCapacity()) {
                int chunk = Math.min(remaining, perListing);
                TradingListing listing = TradingMarketService.adminList(server, TradingMarketData.WORLD_ACCOUNT_ID,
                    TradingMarketData.WORLD_ACCOUNT_NAME, sample, chunk, unitPrice, durationDays);
                if (listing == null) break;
                created++;
                remaining -= chunk;
            }
        }
        syncTradingAll(server);
        int finalCreated = created;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_trading.auto.success",
            finalCreated), true);
        return created;
    }

    private static int autoRemoveAllTrading(CommandContext<CommandSourceStack> ctx) {
        int removed = TradingMarketService.clearAll(ctx.getSource().getServer());
        syncTradingAll(ctx.getSource().getServer());
        int count = removed;
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_trading.auto.remove_all.success", count), true);
        return removed;
    }

    private record LongRange(long minimum, long maximum) {
    }

    private static LongRange parseLongRange(String value) {
        String[] parts = value.replace("_", "").split("\\.\\.", -1);
        if (parts.length != 2) return null;
        try {
            long first = Long.parseLong(parts[0]);
            long second = Long.parseLong(parts[1]);
            return new LongRange(Math.min(first, second), Math.max(first, second));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long randomLong(ThreadLocalRandom random, long minimum, long maximum) {
        if (minimum >= maximum) return minimum;
        if (maximum == Long.MAX_VALUE) return random.nextLong(minimum, maximum) + random.nextInt(2);
        return random.nextLong(minimum, maximum + 1L);
    }

    private static TradingListing findListing(TradingMarketData market, String reference) {
        if (reference == null) return null;
        int separator = reference.lastIndexOf(':');
        String publicId = separator >= 0 ? reference.substring(separator + 1) : reference;
        return market.getListing(publicId);
    }

    private static String listingReference(TradingListing listing) {
        String itemPath = BuiltInRegistries.ITEM.getKey(listing.stack().getItem()).getPath();
        return itemPath + ":" + listing.sellerName() + ":" + listing.publicId();
    }

    private static void syncTradingAll(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) syncTrading(player);
    }

    private static int giveRaidEarnings(CommandContext<CommandSourceStack> ctx)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        long amount = LongArgumentType.getLong(ctx, "currency");
        RaidEarningsData data = RaidEarningsData.get(ctx.getSource().getServer());
        for (ServerPlayer player : targets) data.add(player.getUUID(), amount);
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_set.coin_give.give", targets.size(), FMT.format(amount)), true);
        return targets.size();
    }

    private static int removeRaidEarnings(CommandContext<CommandSourceStack> ctx)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        RaidEarningsData data = RaidEarningsData.get(ctx.getSource().getServer());
        for (ServerPlayer player : targets) data.clear(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_set.coin_give.remove", targets.size()), true);
        return targets.size();
    }

    private static int evacuatePlayers(CommandContext<CommandSourceStack> ctx)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        RaidEarningsData data = RaidEarningsData.get(ctx.getSource().getServer());
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        long settled = 0L;
        for (ServerPlayer player : targets) {
            long reward = data.take(player.getUUID());
            if (reward > 0L) settled += market.credit(player.getUUID(), reward);
            syncTrading(player);
        }
        long finalSettled = settled;
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_set.player_evacuate", targets.size(), FMT.format(finalSettled)), true);
        return targets.size();
    }

    private static int failPlayerEvacuation(CommandContext<CommandSourceStack> ctx)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        RaidEarningsData data = RaidEarningsData.get(ctx.getSource().getServer());
        int failed = 0;
        for (ServerPlayer player : targets) {
            long reward = data.amount(player.getUUID());
            if (DownedManager.failEvacuation(player, reward)) {
                data.clear(player.getUUID());
                failed++;
            }
        }
        int finalFailed = failed;
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_set.player_evacuate_failed", finalFailed), true);
        return failed;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> currencyAddCommand() {
        return Commands.literal("add").requires(source -> source.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("currency", LongArgumentType.longArg(0L, TradingRules.MAX_CURRENCY))
                    .executes(ModCommands::addCurrency)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> currencySetCommand() {
        return Commands.literal("set")
            .then(Commands.argument("targets", EntityArgument.players()).requires(source -> source.hasPermission(2))
                .then(Commands.argument("currency", LongArgumentType.longArg(0L, TradingRules.MAX_CURRENCY))
                    .executes(ModCommands::setCurrency)));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> currencyGetTargets() {
        var item = Commands.argument("currencyItem", ResourceLocationArgument.id()).suggests(ITEM_ID_SUGGESTIONS)
            .executes(ctx -> getCurrencyAndPrice(ctx, "this_type", resourceId(ctx, "currencyItem")));
        var targets = Commands.argument("targets", EntityArgument.players())
            .requires(source -> source.hasPermission(2))
            .executes(ctx -> getCurrencyAndPrice(ctx, "this_type", null))
            .then(Commands.literal("this_type")
                .executes(ctx -> getCurrencyAndPrice(ctx, "this_type", null))
                .then(Commands.argument("currencyItem", ResourceLocationArgument.id()).suggests(ITEM_ID_SUGGESTIONS)
                    .executes(ctx -> getCurrencyAndPrice(ctx, "this_type", resourceId(ctx, "currencyItem")))))
            .then(Commands.literal("all_type")
                .executes(ctx -> getCurrencyAndPrice(ctx, "all_type", null))
                .then(Commands.argument("currencyItem", ResourceLocationArgument.id()).suggests(ITEM_ID_SUGGESTIONS)
                    .executes(ctx -> getCurrencyAndPrice(ctx, "all_type", resourceId(ctx, "currencyItem")))))
            .then(item);
        return targets;
    }

    private static int addCurrency(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        long amount = LongArgumentType.getLong(ctx, "currency");
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        for (ServerPlayer player : targets) {
            market.claimUnresolvedAccount(player.getUUID(), player.getGameProfile().getName());
            market.credit(player.getUUID(), amount);
            syncTrading(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_price.currency.add",
            targets.size(), FMT.format(amount)), true);
        return targets.size();
    }

    private static int setCurrency(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        long amount = LongArgumentType.getLong(ctx, "currency");
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        for (ServerPlayer player : targets) {
            market.claimUnresolvedAccount(player.getUUID(), player.getGameProfile().getName());
            market.setBalance(player.getUUID(), amount);
            syncTrading(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_price.currency.set",
            targets.size(), FMT.format(amount)), true);
        return targets.size();
    }

    private static int getCurrencyAndPrice(CommandContext<CommandSourceStack> ctx, String type, String itemArgument)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        ModDataStorage data = getData(ctx);
        if (data == null) return noData(ctx);
        TradingMarketData market = TradingMarketData.get(ctx.getSource().getServer());
        for (ServerPlayer player : targets) {
            market.claimUnresolvedAccount(player.getUUID(), player.getGameProfile().getName());
            ItemStack stack = resolveCurrencyTarget(ctx, player, itemArgument);
            long price = 0L;
            Component itemName = Component.translatable("command.xero.error.no_item");
            if (!stack.isEmpty()) {
                itemName = stack.getHoverName();
                price = "all_type".equals(type)
                    ? data.getPriceFor(stack.getItem().getDefaultInstance())
                    : data.getPriceFor(stack);
            }
            long balance = market.balance(player.getUUID());
            long finalPrice = price;
            Component finalItemName = itemName;
            ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_price.currency.get",
                player.getDisplayName(), FMT.format(balance), finalItemName, FMT.format(finalPrice)), false);
        }
        return targets.size();
    }

    private static ItemStack resolveCurrencyTarget(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                                   String itemArgument) {
        if (itemArgument == null) return player.getMainHandItem().copy();
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemArgument);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.no_item"));
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.get(id).getDefaultInstance();
    }

    private static void syncTrading(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, TradingSyncPacket.snapshot(player, "", true));
    }

    private static int openTradingOperator(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        TradingScreenOpener.openOperator(player);
        return 1;
    }

    private static int openTradingDetail(CommandContext<CommandSourceStack> ctx, String itemArgument) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ItemStack target;
        boolean exactComponents = itemArgument == null;
        if (exactComponents) {
            target = player.getMainHandItem().copyWithCount(1);
        } else {
            var id = net.minecraft.resources.ResourceLocation.tryParse(itemArgument);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                ctx.getSource().sendFailure(Component.translatable("command.xero.error.no_item"));
                return 0;
            }
            target = BuiltInRegistries.ITEM.get(id).getDefaultInstance();
        }
        if (target.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.no_item"));
            return 0;
        }
        TradingScreenOpener.openMarketDetail(player, target, exactComponents);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> newSizeSet() {
        var item = Commands.argument("item", ResourceLocationArgument.id()).suggests(ITEM_ID_SUGGESTIONS)
            .executes(ctx -> executeNewSize(ctx, string(ctx, "rotate"), string(ctx, "stretch"), string(ctx, "type"),
                string(ctx, "rangeOrItem"), string(ctx, "item")));
        var rangeOrItem = Commands.argument("rangeOrItem", StringArgumentType.word()).suggests(DURABILITY_SUGGESTIONS)
            .executes(ctx -> executeNewSize(ctx, string(ctx, "rotate"), string(ctx, "stretch"), string(ctx, "type"),
                isDurabilityType(string(ctx, "type")) ? string(ctx, "rangeOrItem") : null,
                isDurabilityType(string(ctx, "type")) ? null : string(ctx, "rangeOrItem")))
            .then(item);
        var directItem = Commands.argument("directItem", ResourceLocationArgument.id()).suggests(ITEM_ID_SUGGESTIONS)
            .executes(ctx -> executeNewSize(ctx, string(ctx, "rotate"), string(ctx, "stretch"),
                string(ctx, "type"), null, resourceId(ctx, "directItem")));
        var type = Commands.argument("type", StringArgumentType.word()).suggests(TYPE_SUGGESTIONS)
            .executes(ctx -> executeNewSize(ctx, string(ctx, "rotate"), string(ctx, "stretch"), string(ctx, "type"), null, null))
            .then(rangeOrItem)
            .then(directItem);
        var stretch = Commands.argument("stretch", StringArgumentType.word()).suggests(STRETCH_SUGGESTIONS)
            .executes(ctx -> executeNewSize(ctx, string(ctx, "rotate"), string(ctx, "stretch"), "this_type", null, null))
            .then(type);
        var rotate = Commands.argument("rotate", StringArgumentType.word()).suggests(ROTATE_SUGGESTIONS)
            .executes(ctx -> executeNewSize(ctx, string(ctx, "rotate"), "stretch", "this_type", null, null))
            .then(stretch);
        var width = Commands.argument("width", IntegerArgumentType.integer(1, 10))
            .executes(ctx -> executeNewSize(ctx, "rotate", "stretch", "this_type", null, null))
            .then(rotate);
        return Commands.literal("set").then(Commands.argument("length", IntegerArgumentType.integer(1, 10)).then(width));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> newSizeAuto() {
        var type = Commands.argument("autoType", StringArgumentType.word()).suggests(TYPE_SUGGESTIONS)
            .executes(ctx -> autoSize(ctx, string(ctx, "autoRotate"), string(ctx, "autoStretch"),
                string(ctx, "autoType"), null))
            .then(Commands.argument("autoDurability", StringArgumentType.word()).suggests(DURABILITY_SUGGESTIONS)
                .executes(ctx -> autoSize(ctx, string(ctx, "autoRotate"), string(ctx, "autoStretch"),
                    string(ctx, "autoType"), string(ctx, "autoDurability"))));
        var stretch = Commands.argument("autoStretch", StringArgumentType.word()).suggests(STRETCH_SUGGESTIONS)
            .executes(ctx -> autoSize(ctx, string(ctx, "autoRotate"), string(ctx, "autoStretch"),
                "all_type", null))
            .then(type);
        var rotate = Commands.argument("autoRotate", StringArgumentType.word()).suggests(ROTATE_SUGGESTIONS)
            .executes(ctx -> autoSize(ctx, string(ctx, "autoRotate"), "false", "all_type", null))
            .then(stretch);
        return Commands.literal("auto").executes(ModCommands::autoSize).then(rotate);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> newPriceSet() {
        return Commands.literal("set")
            .then(Commands.argument("value", IntegerArgumentType.integer(-1, 999_999_999)).suggests(VALUE_SUGGESTIONS)
                .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPE_SUGGESTIONS)
                    .executes(ctx -> executeNewPrice(ctx, IntegerArgumentType.getInteger(ctx, "value"), string(ctx, "type"), null))
                    .then(Commands.argument("durability", StringArgumentType.word()).suggests(DURABILITY_SUGGESTIONS)
                        .executes(ctx -> executeNewPrice(ctx, IntegerArgumentType.getInteger(ctx, "value"), string(ctx, "type"),
                            string(ctx, "durability"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> newQualitySet() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("set");
        for (String quality : new String[]{"red", "gold", "purple", "blue", "green", "gray", "legendary", "rare", "common", "epic", "mythic"}) {
            root.then(Commands.literal(quality)
                .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPE_SUGGESTIONS)
                    .executes(ctx -> executeNewQuality(ctx, quality, string(ctx, "type"), null))
                    .then(Commands.argument("durability", StringArgumentType.word()).suggests(DURABILITY_SUGGESTIONS)
                        .executes(ctx -> executeNewQuality(ctx, quality, string(ctx, "type"), string(ctx, "durability"))))));
        }
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> xeroTitleActionbarCommand() {
        var targets = Commands.argument("targets", EntityArgument.players());
        for (String position : new String[]{"top|center", "top|left", "top|right",
                "center", "left", "right", "bottom|center", "bottom|left", "bottom|right"}) {
            targets.then(Commands.literal(position)
                .then(Commands.argument("content", StringArgumentType.greedyString())
                    .executes(ctx -> sendXeroTitle(ctx, position))));
        }
        return Commands.literal("actionbar").then(targets);
    }

    private static int sendXeroTitle(CommandContext<CommandSourceStack> ctx, String position)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!validTitlePosition(position)) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_title.invalid_position"));
            return 0;
        }
        String raw = string(ctx, "content");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer target : targets) {
            String content = resolveTitleContent(ctx, target, raw);
            PacketDistributor.sendToPlayer(target, new XeroTitlePacket(position, content, 60));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_title.success", targets.size()), true);
        return targets.size();
    }

    private static boolean validTitlePosition(String raw) {
        if (raw == null || raw.isBlank()) return false;
        boolean left = false, right = false, top = false, bottom = false;
        for (String token : raw.toLowerCase(Locale.ROOT).split("\\|")) {
            switch (token.trim()) {
                case "left" -> left = true;
                case "right" -> right = true;
                case "top" -> top = true;
                case "bottom" -> bottom = true;
                case "center" -> { }
                default -> { return false; }
            }
        }
        return !(left && right) && !(top && bottom);
    }

    private static String resolveTitleContent(CommandContext<CommandSourceStack> ctx,
                                              ServerPlayer target, String raw) {
        String content = raw.replace("{player}", target.getGameProfile().getName())
            .replace("[player:@s]", target.getGameProfile().getName())
            .replace("[attribute:health]", decimal(target.getHealth()))
            .replace("[attribute:max_health]", decimal(target.getMaxHealth()))
            .replace("[attribute:experience_level]", Integer.toString(target.experienceLevel))
            .replace("[attribute:uuid]", target.getUUID().toString());
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[player:([^]\\r\\n]+)]")
            .matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement;
            try {
                var selector = EntityArgument.players().parse(new StringReader(matcher.group(1).trim()));
                replacement = selector.findPlayers(ctx.getSource()).stream()
                    .map(player -> player.getGameProfile().getName())
                    .collect(java.util.stream.Collectors.joining(", "));
                if (replacement.isBlank()) replacement = matcher.group(1);
            } catch (Exception ignored) {
                replacement = matcher.group(1);
            }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
    private static LiteralArgumentBuilder<CommandSourceStack> itemTradingUploadCommand() {
        var durability = Commands.argument("uploadDurability", StringArgumentType.word())
            .suggests(DURABILITY_SUGGESTIONS)
            .executes(ctx -> setTradingUploadRule(ctx, string(ctx, "uploadMode"),
                string(ctx, "uploadDurability")));
        var mode = Commands.argument("uploadMode", StringArgumentType.word())
            .suggests(TYPE_SUGGESTIONS)
            .executes(ctx -> setTradingUploadRule(ctx, string(ctx, "uploadMode"), null))
            .then(durability);
        var item = Commands.argument("uploadItem", ResourceLocationArgument.id())
            .suggests(ITEM_ID_SUGGESTIONS)
            .executes(ctx -> setTradingUploadRule(ctx, "this_type", null))
            .then(mode);
        var policy = Commands.argument("uploadPolicy", StringArgumentType.word())
            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                new String[]{"up", "recycle", "none", "true", "false"}, builder))
            .then(item);
        return Commands.literal("item")
            .then(Commands.literal("trading")
                .then(Commands.literal("upload").then(policy)));
    }

    private static int setTradingUploadRule(CommandContext<CommandSourceStack> ctx,
                                            String type, String durabilityRange) {
        ItemStack target = resolveTarget(ctx, resourceId(ctx, "uploadItem"));
        if (target.isEmpty()) return 0;
        if ("this_durability".equalsIgnoreCase(type) && durabilityRange == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.invalid_durability"));
            return 0;
        }
        String key = newRuleKey(ctx, target, type, durabilityRange);
        if (key == null) return 0;
        var server = ctx.getSource().getServer();
        if (server == null) return noData(ctx);
        String policy = string(ctx, "uploadPolicy");
        if (!policy.equalsIgnoreCase("up") && !policy.equalsIgnoreCase("recycle")
            && !policy.equalsIgnoreCase("none") && !policy.equalsIgnoreCase("true")
            && !policy.equalsIgnoreCase("false")) {
            ctx.getSource().sendFailure(Component.literal("Policy must be up, recycle, or none"));
            return 0;
        }
        String modeValue = policy.equalsIgnoreCase("true")
            ? TradingUploadRulesData.UP
            : policy.equalsIgnoreCase("false") ? TradingUploadRulesData.NONE : policy.toLowerCase(Locale.ROOT);
        TradingUploadRulesData.get(server).set(key, modeValue);
        syncAll(server, ModDataStorage.get(server.overworld()));
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_set.item.trading.upload.success",
            target.getHoverName(), Component.literal(key), Component.literal(modeValue)), true);
        return 1;
    }
    private static LiteralArgumentBuilder<CommandSourceStack> bulletArmorCommand() {
        var durability = Commands.argument("bulletDurability", StringArgumentType.word())
            .suggests(DURABILITY_SUGGESTIONS)
            .executes(ctx -> setBulletArmorRule(ctx, "bulletItem", "this_durability",
                string(ctx, "bulletDurability")));
        var mode = Commands.argument("bulletMode", StringArgumentType.word())
            .suggests(TYPE_SUGGESTIONS)
            .executes(ctx -> setBulletArmorRule(ctx, "bulletItem", string(ctx, "bulletMode"), null))
            .then(durability);
        var item = Commands.argument("bulletItem", ResourceLocationArgument.id())
            .suggests(ITEM_ID_SUGGESTIONS)
            .executes(ctx -> setBulletArmorRule(ctx, "bulletItem", "this_type", null))
            .then(mode);
        var red = Commands.argument("armorRed", DoubleArgumentType.doubleArg(0.0D, 10.0D))
            .executes(ctx -> setBulletArmorRule(ctx, null, "this_type", null))
            .then(item);
        var gold = Commands.argument("armorGold", DoubleArgumentType.doubleArg(0.0D, 10.0D)).then(red);
        var purple = Commands.argument("armorPurple", DoubleArgumentType.doubleArg(0.0D, 10.0D)).then(gold);
        var blue = Commands.argument("armorBlue", DoubleArgumentType.doubleArg(0.0D, 10.0D)).then(purple);
        var green = Commands.argument("armorGreen", DoubleArgumentType.doubleArg(0.0D, 10.0D)).then(blue);
        var gray = Commands.argument("armorGray", DoubleArgumentType.doubleArg(0.0D, 10.0D)).then(green);
        return Commands.literal("bullet").then(gray);
    }

    private static int setBulletArmorRule(CommandContext<CommandSourceStack> ctx, String itemArgument,
                                          String type, String durabilityRange) {
        String itemId = itemArgument == null ? null : resourceId(ctx, itemArgument);
        ItemStack target = resolveTarget(ctx, itemId);
        if (target.isEmpty()) return 0;
        if ("this_durability".equalsIgnoreCase(type) && durabilityRange == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.invalid_durability"));
            return 0;
        }
        String key = newRuleKey(ctx, target, type, durabilityRange);
        if (key == null) return 0;
        List<Double> values = List.of(
            DoubleArgumentType.getDouble(ctx, "armorGray"),
            DoubleArgumentType.getDouble(ctx, "armorGreen"),
            DoubleArgumentType.getDouble(ctx, "armorBlue"),
            DoubleArgumentType.getDouble(ctx, "armorPurple"),
            DoubleArgumentType.getDouble(ctx, "armorGold"),
            DoubleArgumentType.getDouble(ctx, "armorRed")
        );
        var server = ctx.getSource().getServer();
        if (server == null) return noData(ctx);
        BulletArmorRulesData.get(server).set(key, values);
        syncAll(server, ModDataStorage.get(server.overworld()));
        String formatted = values.stream()
            .map(value -> BigDecimal.valueOf(value).stripTrailingZeros().toPlainString())
            .collect(java.util.stream.Collectors.joining(" / "));
        String targetId = ModDataStorage.getIdOnlyKey(target);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_set.bullet.success",
            Component.literal(targetId), Component.literal(key), Component.literal(formatted)), true);
        return 1;
    }
    private static LiteralArgumentBuilder<CommandSourceStack> weightSetCommand() {
        var item = Commands.argument("weightItem", ResourceLocationArgument.id()).suggests(ITEM_ID_SUGGESTIONS)
            .executes(ctx -> executeWeightSet(ctx, DoubleArgumentType.getDouble(ctx, "weight"),
                string(ctx, "weightMode"), resourceId(ctx, "weightItem")));
        var mode = Commands.argument("weightMode", StringArgumentType.word()).suggests(WEIGHT_MODE_SUGGESTIONS)
            .executes(ctx -> executeWeightSet(ctx, DoubleArgumentType.getDouble(ctx, "weight"),
                string(ctx, "weightMode"), null))
            .then(item);
        return Commands.literal("set").requires(source -> source.hasPermission(2))
            .then(Commands.argument("weight", DoubleArgumentType.doubleArg(0.001D, 1_000_000.0D))
                .suggests(WEIGHT_SUGGESTIONS).then(mode));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> weightGetCommand() {
        var item = Commands.argument("weightGetItem", ResourceLocationArgument.id()).suggests(ITEM_ID_SUGGESTIONS)
            .executes(ctx -> executeWeightGet(ctx, string(ctx, "weightGetMode"),
                resourceId(ctx, "weightGetItem")));
        var mode = Commands.argument("weightGetMode", StringArgumentType.word()).suggests(WEIGHT_MODE_SUGGESTIONS)
            .executes(ctx -> executeWeightGet(ctx, string(ctx, "weightGetMode"), null))
            .then(item);
        return Commands.literal("get").then(mode);
    }

    private static int executeWeightSet(CommandContext<CommandSourceStack> ctx, double kilograms,
                                        String mode, String itemArgument) {
        WeightTarget target = resolveWeightTarget(ctx, mode, itemArgument);
        if (target == null) return 0;
        String key = newRuleKey(ctx, target.stack(), target.type(), target.durabilityRange());
        if (key == null) return 0;
        ServerItemWeights.setWeight(key, kilograms);
        ModDataStorage data = getData(ctx);
        if (data != null) syncAll(ctx, data);
        String itemId = ModDataStorage.getIdOnlyKey(target.stack());
        String formatted = formatWeight(ServerItemWeights.getConfiguredWeight(key));
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_weight.set.success",
            Component.literal(itemId), Component.literal(formatted), Component.literal(key)), true);
        return 1;
    }

    private static int executeWeightGet(CommandContext<CommandSourceStack> ctx, String mode,
                                        String itemArgument) {
        WeightTarget target = resolveWeightTarget(ctx, mode, itemArgument);
        if (target == null) return 0;
        String requestedKey = newRuleKey(ctx, target.stack(), target.type(), target.durabilityRange());
        if (requestedKey == null) return 0;
        Double configured = ServerItemWeights.getConfiguredWeight(requestedKey);
        String matchedKey = configured == null ? ServerItemWeights.matchingRuleKey(target.stack()) : requestedKey;
        double kilograms = configured == null
            ? ServerItemWeights.getWeightFor(target.stack()) : configured;
        Component source = matchedKey == null
            ? Component.translatable("command.xero_weight.source.estimated")
            : Component.literal(matchedKey);
        String itemId = ModDataStorage.getIdOnlyKey(target.stack());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_weight.get.success",
            Component.literal(itemId), Component.literal(formatWeight(kilograms)), source), false);
        return 1;
    }

    private static WeightTarget resolveWeightTarget(CommandContext<CommandSourceStack> ctx, String mode,
                                                    String itemArgument) {
        boolean durabilityRange = mode != null && mode.contains("..");
        String type = durabilityRange ? "this_durability" : mode;
        String range = durabilityRange ? mode : null;
        if (!durabilityRange && !"this_type".equalsIgnoreCase(type)
            && !"all_type".equalsIgnoreCase(type)) {
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.invalid_type"));
            return null;
        }
        ItemStack stack = resolveTarget(ctx, itemArgument);
        if (stack.isEmpty()) return null;
        return new WeightTarget(stack, type, range);
    }

    private static int autoWeight(CommandContext<CommandSourceStack> ctx) {
        Map<String, Double> calculated = AutomaticItemWeight.calculateAll();
        ServerItemWeights.setAutoWeights(calculated);
        ModDataStorage data = getData(ctx);
        if (data != null) syncAll(ctx, data);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_weight.auto.success",
            calculated.size()), true);
        return calculated.size();
    }

    private static String formatWeight(Double kilograms) {
        if (kilograms == null) return "0 kg";
        return BigDecimal.valueOf(kilograms).stripTrailingZeros().toPlainString() + " kg";
    }

    private record WeightTarget(ItemStack stack, String type, String durabilityRange) {
    }

    private static int executeNewSize(CommandContext<CommandSourceStack> ctx, String rotateArgument, String stretchArgument,
                                      String type, String durabilityRange, String itemArgument) {
        Boolean rotate = parseRotate(ctx, rotateArgument);
        StretchMode stretch = parseStretch(ctx, stretchArgument);
        boolean allItems = "all".equalsIgnoreCase(itemArgument);
        ItemStack target = resolveTarget(ctx, itemArgument);
        if (rotate == null || stretch == null || (target.isEmpty() && !allItems)) return 0;
        String key = allItems ? "*" : newRuleKey(ctx, target, type, durabilityRange);
        if (key == null) return 0;
        var data = getData(ctx);
        if (data == null) return noData(ctx);
        int length = IntegerArgumentType.getInteger(ctx, "length");
        int width = IntegerArgumentType.getInteger(ctx, "width");
        data.setSize(key, new ItemSize(length, width), rotate, stretch.stretch(), stretch.proportionalScale());
        syncAll(ctx, data);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_size.success",
            Component.literal(allItems ? "all" : ModDataStorage.getIdOnlyKey(target)), Component.literal(String.valueOf(length)),
            Component.literal(String.valueOf(width))), true);
        return 1;
    }

    private static int executeNewPrice(CommandContext<CommandSourceStack> ctx, int value, String type, String durabilityRange) {
        ItemStack target = resolveTarget(ctx, null);
        if (target.isEmpty()) return 0;
        String key = newPriceRuleKey(ctx, target, type, durabilityRange);
        if (key == null) return 0;
        var data = getData(ctx);
        if (data == null) return noData(ctx);
        if (value < 0) data.removePrice(key); else data.setPrice(key, value);
        syncAll(ctx, data);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_price.success",
            Component.literal(ModDataStorage.getIdOnlyKey(target)), Component.literal(FMT.format(value))), true);
        return 1;
    }

    private static int executeNewQuality(CommandContext<CommandSourceStack> ctx, String quality, String type, String durabilityRange) {
        ItemStack target = resolveTarget(ctx, null);
        if (target.isEmpty()) return 0;
        String key = newRuleKey(ctx, target, type, durabilityRange);
        if (key == null) return 0;
        var data = getData(ctx);
        if (data == null) return noData(ctx);
        String normalized = normalizeQualityAlias(quality);
        data.setQuality(key, normalized);
        syncAll(ctx, data);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_quality.success",
            Component.literal(ModDataStorage.getIdOnlyKey(target)), qualityName(normalized)), true);
        return 1;
    }

    private record StretchMode(boolean stretch, int proportionalScale) { }

    private static Boolean parseRotate(CommandContext<CommandSourceStack> ctx, String value) {
        if ("rotate".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        ctx.getSource().sendFailure(Component.translatable("command.xero.error.invalid_rotate"));
        return null;
    }

    private static StretchMode parseStretch(CommandContext<CommandSourceStack> ctx, String value) {
        if ("stretch".equalsIgnoreCase(value)) return new StretchMode(true, 0);
        if ("false".equalsIgnoreCase(value)) return new StretchMode(false, 0);
        if (value != null && value.toLowerCase(Locale.ROOT).matches("prop[1-9][0-9]*")) {
            try { return new StretchMode(false, Math.min(31, Integer.parseInt(value.substring(4)))); }
            catch (NumberFormatException ignored) { }
        }
        ctx.getSource().sendFailure(Component.translatable("command.xero.error.invalid_stretch"));
        return null;
    }

    private static String newRuleKey(CommandContext<CommandSourceStack> ctx, ItemStack target, String type, String range) {
        if ("this_type".equalsIgnoreCase(type)) {
            return ServerItemRules.exactKey(ModDataStorage.getTypeKey(target));
        }
        if ("all_type".equalsIgnoreCase(type)) {
            return ServerItemRules.exactKey(ModDataStorage.getIdOnlyKey(target));
        }
        if (!isDurabilityType(type)) {
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.invalid_type"));
            return null;
        }
        if (DurabilityRange.parse(range, target.getMaxDamage()) == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.invalid_durability"));
            return null;
        }
        return DurabilityRange.key(ModDataStorage.getIdOnlyKey(target), range);
    }

    private static String newPriceRuleKey(CommandContext<CommandSourceStack> ctx, ItemStack target, String type, String range) {
        String key = newRuleKey(ctx, target, type, range);
        return key == null ? null : key.startsWith("item:") ? ModDataStorage.getIdOnlyKey(target) : key;
    }

    private static ItemStack resolveTarget(CommandContext<CommandSourceStack> ctx, String itemArgument) {
        if (itemArgument != null && "all".equalsIgnoreCase(itemArgument)) {
            return ItemStack.EMPTY;
        }
        if (itemArgument != null) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(itemArgument);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.get(id).getDefaultInstance();
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.no_item"));
            return ItemStack.EMPTY;
        }
        ServerPlayer player = getPlayer(ctx);
        if (player != null && !player.getMainHandItem().isEmpty()) return player.getMainHandItem().copy();
        ctx.getSource().sendFailure(Component.translatable("command.xero.error.no_item"));
        return ItemStack.EMPTY;
    }

    private static boolean isDurabilityType(String value) { return "this_durability".equalsIgnoreCase(value); }
    private static String string(CommandContext<CommandSourceStack> ctx, String name) { return StringArgumentType.getString(ctx, name); }
    private static String resourceId(CommandContext<CommandSourceStack> ctx, String name) {
        return ResourceLocationArgument.getId(ctx, name).toString();
    }
    private static int noData(CommandContext<CommandSourceStack> ctx) { ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data")); return 0; }
    private static String normalizeQualityAlias(String quality) {
        return switch (quality.toLowerCase(Locale.ROOT)) {
            case "legendary" -> "gold";
            case "rare" -> "green";
            case "common" -> "gray";
            case "epic" -> "purple";
            case "mythic" -> "red";
            default -> ModDataStorage.normalizeQuality(quality);
        };
    }

    private static int reloadSafetyBox(CommandContext<CommandSourceStack> ctx) {
        com.xtdpotato.xero_delta.screen.SafetyBoxLayoutPack.ensureDefaultPack();
        var data = getData(ctx);
        if (data != null) syncAll(ctx, data);
        var server = ctx.getSource().getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ModNetwork.reloadSafetyBoxPack(player);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.pack.reloaded"), true);
        return 1;
    }

    private static int unlockKnife(CommandContext<CommandSourceStack> context) {
        return setKnifeAccess(context, ResourceLocationArgument.getId(context, "item_id"), true, null);
    }

    private static int unlockHeldKnife(CommandContext<CommandSourceStack> context) {
        ItemStack held = heldKnifeStack(context);
        ResourceLocation heldId = held.isEmpty() ? null
            : BuiltInRegistries.ITEM.getKey(held.getItem());
        return heldId == null ? 0 : setKnifeAccess(context, heldId, true, held);
    }

    private static int lockKnife(CommandContext<CommandSourceStack> context) {
        return setKnifeAccess(context, ResourceLocationArgument.getId(context, "item_id"), false, null);
    }

    private static int lockHeldKnife(CommandContext<CommandSourceStack> context) {
        ResourceLocation heldId = heldKnifeId(context);
        return heldId == null ? 0 : setKnifeAccess(context, heldId, false, null);
    }

    private static ResourceLocation heldKnifeId(CommandContext<CommandSourceStack> context) {
        ItemStack held = heldKnifeStack(context);
        return held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem());
    }

    private static ItemStack heldKnifeStack(CommandContext<CommandSourceStack> context) {
        try {
            ItemStack held = context.getSource().getPlayerOrException().getMainHandItem();
            if (!held.isEmpty() && TaczCompatibilityRules.isLrTacticalMelee(held)) return held.copy();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            context.getSource().sendFailure(Component.translatable(
                "command.xero_delta.knife.held_required"));
            return ItemStack.EMPTY;
        }
        context.getSource().sendFailure(Component.translatable(
            "command.xero_delta.knife.held_required"));
        return ItemStack.EMPTY;
    }

    private static int setKnifeAccess(CommandContext<CommandSourceStack> context,
                                      ResourceLocation parsed, boolean unlock,
                                      ItemStack unlockStack) {
        if (!BuiltInRegistries.ITEM.containsKey(parsed)) {
            context.getSource().sendFailure(Component.translatable(
                "command.xero_delta.knife.invalid", parsed.toString()));
            return 0;
        }
        ItemStack stack = BuiltInRegistries.ITEM.get(parsed).getDefaultInstance();
        if (!TaczCompatibilityRules.isLrTacticalMelee(stack)) {
            context.getSource().sendFailure(Component.translatable(
                "command.xero_delta.knife.invalid", parsed.toString()));
            return 0;
        }
        KnifeAccessData data = KnifeAccessData.get(context.getSource().getServer());
        int changed = 0;
        try {
            for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
                boolean playerChanged = unlock
                    ? data.unlock(player.getUUID(), parsed.toString(), unlockStack,
                        player.registryAccess())
                    : data.lock(player.getUUID(), parsed.toString());
                if (playerChanged) changed++;
                data.sync(player);
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            context.getSource().sendFailure(Component.literal(
                exception.getRawMessage().getString()));
            return 0;
        }
        int count = changed;
        context.getSource().sendSuccess(() -> Component.translatable(
            unlock ? "command.xero_delta.knife.unlocked" : "command.xero_delta.knife.locked",
            count, stack.getHoverName()), true);
        return changed;
    }

    private static int unlockSafetyBox(CommandContext<CommandSourceStack> context) {
        var parsed = ResourceLocationArgument.getId(context, "item_id");
        String itemId = parsed.toString();
        if (!BuiltInRegistries.ITEM.containsKey(parsed)
            || !(BuiltInRegistries.ITEM.get(parsed) instanceof SafetyBoxItem)) {
            context.getSource().sendFailure(Component.translatable(
                "command.xero_delta.safety_box.invalid", itemId));
            return 0;
        }
        int days = IntegerArgumentType.getInteger(context, "days");
        long now = System.currentTimeMillis();
        long durationMillis = days * 86_400_000L;
        SafetyBoxAccessData data = SafetyBoxAccessData.get(context.getSource().getServer());
        int changed = 0;
        try {
            for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
                data.extend(player.getUUID(), itemId, durationMillis, now);
                data.sync(player);
                changed++;
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            context.getSource().sendFailure(Component.literal(exception.getRawMessage().getString()));
            return 0;
        }
        int count = changed;
        if (SafetyBoxAccessData.basicBoxId().equals(itemId)) {
            context.getSource().sendSuccess(() -> Component.translatable(
                "command.xero_delta.safety_box.basic_permanent", itemId, count), true);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable(
                "command.xero_delta.safety_box.unlocked", count, itemId, days), true);
        }
        return changed;
    }

    private static int lockSafetyBox(CommandContext<CommandSourceStack> context) {
        var parsed = ResourceLocationArgument.getId(context, "item_id");
        String itemId = parsed.toString();
        if (SafetyBoxAccessData.basicBoxId().equals(itemId)) {
            context.getSource().sendFailure(Component.translatable(
                "command.xero_delta.safety_box.basic_cannot_lock"));
            return 0;
        }
        if (!BuiltInRegistries.ITEM.containsKey(parsed)
            || !(BuiltInRegistries.ITEM.get(parsed) instanceof SafetyBoxItem)) {
            context.getSource().sendFailure(Component.translatable(
                "command.xero_delta.safety_box.invalid", itemId));
            return 0;
        }
        SafetyBoxAccessData data = SafetyBoxAccessData.get(context.getSource().getServer());
        int changed = 0;
        try {
            for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
                if (data.lock(player.getUUID(), itemId)) changed++;
                data.sync(player);
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            context.getSource().sendFailure(Component.literal(exception.getRawMessage().getString()));
            return 0;
        }
        int count = changed;
        context.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_delta.safety_box.locked", count, itemId), true);
        return changed;
    }

    private static int autoPrice(CommandContext<CommandSourceStack> ctx) {
        var data = getData(ctx);
        var server = ctx.getSource().getServer();
        if (data == null || server == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data"));
            return 0;
        }
        Map<String, Long> manualPrices = Map.copyOf(data.getManualPrices());
        return submitAutomaticCalculation(ctx, "price",
            snapshot -> AutomaticItemValuation.calculate(snapshot, manualPrices, true),
            result -> {
                data.setAutoPrices(result.prices());
                syncAll(server, data);
                ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.price.auto",
                    Component.literal(FMT.format(result.prices().size())),
                    Component.literal(FMT.format(result.recipeDerived())),
                    Component.literal(FMT.format(result.fallbackDerived()))), true);
            });
    }

    private static int autoQuality(CommandContext<CommandSourceStack> ctx) {
        var data = getData(ctx);
        var server = ctx.getSource().getServer();
        if (data == null || server == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data"));
            return 0;
        }
        Map<String, Long> manualPrices = Map.copyOf(data.getManualPrices());
        return submitAutomaticCalculation(ctx, "quality",
            snapshot -> {
                AutomaticItemValuation.Result valuation = AutomaticItemValuation.calculate(
                    snapshot, manualPrices, true);
                ServerItemRules.setAutoQualities(valuation.qualities());
                return valuation;
            },
            valuation -> {
                Map<String, String> qualities = valuation.qualities();
                syncAll(server, data);
                ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.quality.auto",
                    Component.literal(FMT.format(qualities.size()))), true);
            });
    }

    private static int autoSize(CommandContext<CommandSourceStack> ctx) {
        return autoSize(ctx, "rotate", "false", "all_type", null);
    }

    private static int autoSize(CommandContext<CommandSourceStack> ctx, String rotateArgument,
                                String stretchArgument, String type, String durabilityRange) {
        var data = getData(ctx);
        var server = ctx.getSource().getServer();
        if (data == null || server == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data"));
            return 0;
        }
        Boolean rotate = parseRotate(ctx, rotateArgument);
        StretchMode stretch = parseStretch(ctx, stretchArgument);
        if (rotate == null || stretch == null) return 0;
        if (!"all_type".equalsIgnoreCase(type) && !"this_type".equalsIgnoreCase(type)
            && !isDurabilityType(type)) {
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.invalid_type"));
            return 0;
        }
        if (isDurabilityType(type)
            && DurabilityRange.parse(durabilityRange, Integer.MAX_VALUE) == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.invalid_durability"));
            return 0;
        }
        if (!isDurabilityType(type) && durabilityRange != null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero.error.invalid_durability"));
            return 0;
        }
        return submitAutomaticCalculation(ctx, "size",
            snapshot -> {
                AutomaticItemSizing.Result result = AutomaticItemSizing.calculate(snapshot, rotate,
                    stretch.stretch(), stretch.proportionalScale(), type, durabilityRange);
                ServerItemRules.setAutoSizes(result.sizes());
                return result;
            },
            result -> {
                syncAll(server, data);
                ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.size.auto",
                    Component.literal(FMT.format(result.sizes().size())),
                    Component.literal(FMT.format(result.recipeDerived())),
                    Component.literal(FMT.format(result.categoryDerived()))), true);
            });
    }

    private static int autoAllQualitySizePrice(CommandContext<CommandSourceStack> ctx) {
        ModDataStorage data = getData(ctx);
        var server = ctx.getSource().getServer();
        if (data == null || server == null) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data"));
            return 0;
        }

        Map<String, Long> manualPrices = Map.copyOf(data.getManualPrices());
        return submitAutomaticCalculation(ctx, "all",
            snapshot -> {
                AutomaticItemSizing.Result sizes = AutomaticItemSizing.calculate(snapshot);
                AutomaticItemValuation.Result valuation = AutomaticItemValuation.calculate(
                    snapshot, manualPrices, true, sizes.sizes());
                ServerItemRules.setAutoQualityAndSize(valuation.qualities(), sizes.sizes());
                return new AutomaticAllResult(valuation, sizes);
            },
            result -> {
                // Rule-file persistence completed on the worker; world SavedData
                // and player synchronization stay on the server thread.
                data.setAutoPrices(result.valuation().prices());
                syncAll(server, data);
                ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_all.qsq.success",
                    Component.literal(FMT.format(result.valuation().qualities().size())),
                    Component.literal(FMT.format(result.sizes().sizes().size())),
                    Component.literal(FMT.format(result.valuation().prices().size()))), true);
            });
    }

    private record AutomaticAllResult(AutomaticItemValuation.Result valuation,
                                      AutomaticItemSizing.Result sizes) { }

    private static <T> int submitAutomaticCalculation(CommandContext<CommandSourceStack> ctx, String task,
                                                       Function<AutomaticCalculationSnapshot, T> calculation,
                                                       Consumer<T> commit) {
        var server = ctx.getSource().getServer();
        if (server == null) return 0;
        if (!AUTO_CALCULATION_RUNNING.compareAndSet(false, true)) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.auto.busy"));
            return 0;
        }
        long startedNanos = System.nanoTime();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.auto.started",
            Component.translatable("command.xero_delta.auto.task." + task)), false);

        AUTO_CALCULATION_EXECUTOR.execute(() -> {
            try {
                // Recipe implementations and registry traversal can be expensive.
                // Keep both snapshot capture and pure calculation off the server tick.
                AutomaticCalculationSnapshot snapshot = AutomaticCalculationSnapshot.capture(server);
                T result = calculation.apply(snapshot);
                server.execute(() -> {
                    try {
                        commit.accept(result);
                        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
                        XeroDelta.LOGGER.info("Automatic calculation '{}' completed in {} ms", task, elapsedMillis);
                    } catch (RuntimeException | LinkageError error) {
                        XeroDelta.LOGGER.error("Failed to commit automatic calculation result", error);
                        ctx.getSource().sendFailure(Component.translatable("command.xero_delta.auto.failed"));
                    } finally {
                        AUTO_CALCULATION_RUNNING.set(false);
                    }
                });
            } catch (RuntimeException | LinkageError error) {
                AUTO_CALCULATION_RUNNING.set(false);
                XeroDelta.LOGGER.error("Automatic calculation failed", error);
                server.execute(() -> ctx.getSource().sendFailure(
                    Component.translatable("command.xero_delta.auto.failed")));
            }
        });
        return 1;
    }

    private static int resetAllPrices(CommandContext<CommandSourceStack> ctx) {
        ModDataStorage data = getData(ctx);
        if (data == null) return 0;
        return submitAutomaticReset(ctx, () -> { }, () -> {
            data.clearPrices();
            syncAll(ctx, data);
            ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.xero_delta.price.reset_all"), true);
        });
    }

    private static int resetAllSizes(CommandContext<CommandSourceStack> ctx) {
        ModDataStorage data = getData(ctx);
        if (data == null) return 0;
        return submitAutomaticReset(ctx, ServerItemRules::clearSizes, () -> {
            syncAll(ctx, data);
            ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.xero_delta.size.reset_all"), true);
        });
    }

    private static int resetAllQualities(CommandContext<CommandSourceStack> ctx) {
        ModDataStorage data = getData(ctx);
        if (data == null) return 0;
        return submitAutomaticReset(ctx, ServerItemRules::clearQualities, () -> {
            syncAll(ctx, data);
            ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.xero_delta.quality.reset_all"), true);
        });
    }

    private static int resetAllQualitySizePrice(CommandContext<CommandSourceStack> ctx) {
        ModDataStorage data = getData(ctx);
        if (data == null) return 0;
        return submitAutomaticReset(ctx, () -> {
            ServerItemRules.clearSizes();
            ServerItemRules.clearQualities();
        }, () -> {
            data.clearPrices();
            syncAll(ctx, data);
            ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.xero_all.reset.success"), true);
        });
    }

    private static int submitAutomaticReset(CommandContext<CommandSourceStack> ctx,
                                            Runnable backgroundWork, Runnable serverCommit) {
        var server = ctx.getSource().getServer();
        if (server == null) return 0;
        if (!AUTO_CALCULATION_RUNNING.compareAndSet(false, true)) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.auto.busy"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
            "command.xero_delta.auto.started",
            Component.translatable("command.xero_delta.auto.task.reset")), false);
        AUTO_CALCULATION_EXECUTOR.execute(() -> {
            try {
                backgroundWork.run();
                server.execute(() -> {
                    try {
                        serverCommit.run();
                    } catch (RuntimeException | LinkageError error) {
                        XeroDelta.LOGGER.error("Failed to commit automatic rule reset", error);
                        ctx.getSource().sendFailure(Component.translatable(
                            "command.xero_delta.auto.failed"));
                    } finally {
                        AUTO_CALCULATION_RUNNING.set(false);
                    }
                });
            } catch (RuntimeException | LinkageError error) {
                AUTO_CALCULATION_RUNNING.set(false);
                XeroDelta.LOGGER.error("Automatic rule reset failed", error);
                server.execute(() -> ctx.getSource().sendFailure(
                    Component.translatable("command.xero_delta.auto.failed")));
            }
        });
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> priceSetMode(String typeSetting) {
        return Commands.literal(typeSetting)
            .executes(ctx -> setPriceHand(ctx, IntegerArgumentType.getInteger(ctx, "value"), typeSetting))
            .then(Commands.argument("item", ResourceLocationArgument.id())
                .suggests(ITEM_ID_SUGGESTIONS)
                .executes(ctx -> setPriceKey(ctx, IntegerArgumentType.getInteger(ctx, "value"),
                    itemArgument(ctx), typeSetting)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> priceGetMode(String typeSetting) {
        return Commands.literal(typeSetting)
            .executes(ctx -> getPriceHand(ctx, typeSetting))
            .then(Commands.argument("item", ResourceLocationArgument.id())
                .suggests(ITEM_ID_SUGGESTIONS)
                .executes(ctx -> getPriceKey(ctx, itemArgument(ctx), typeSetting)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> priceRemoveMode(String typeSetting) {
        return Commands.literal(typeSetting)
            .executes(ctx -> removePriceHand(ctx, typeSetting))
            .then(Commands.argument("item", ResourceLocationArgument.id())
                .suggests(ITEM_ID_SUGGESTIONS)
                .executes(ctx -> removePriceKey(ctx, itemArgument(ctx), typeSetting)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sizeSetMode(String typeSetting) {
        return Commands.literal(typeSetting)
            .then(Commands.argument("stretchTexture", BoolArgumentType.bool())
                .executes(ctx -> setSizeHand(ctx,
                    IntegerArgumentType.getInteger(ctx, "length"), IntegerArgumentType.getInteger(ctx, "width"),
                    BoolArgumentType.getBool(ctx, "rotateTexture"), typeSetting,
                    BoolArgumentType.getBool(ctx, "stretchTexture")))
                .then(Commands.argument("item", ResourceLocationArgument.id())
                    .suggests(ITEM_ID_SUGGESTIONS)
                    .executes(ctx -> setSizeKey(ctx,
                        IntegerArgumentType.getInteger(ctx, "length"), IntegerArgumentType.getInteger(ctx, "width"),
                        BoolArgumentType.getBool(ctx, "rotateTexture"), typeSetting,
                        BoolArgumentType.getBool(ctx, "stretchTexture"),
                        itemArgument(ctx)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sizeGetMode(String typeSetting) {
        return Commands.literal(typeSetting)
            .executes(ctx -> getSizeHand(ctx, typeSetting))
            .then(Commands.argument("item", ResourceLocationArgument.id())
                .suggests(ITEM_ID_SUGGESTIONS)
                .executes(ctx -> getSizeKey(ctx,
                    itemArgument(ctx), typeSetting)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sizeRemoveMode(String typeSetting) {
        return Commands.literal(typeSetting)
            .executes(ctx -> removeSizeHand(ctx, typeSetting))
            .then(Commands.argument("item", ResourceLocationArgument.id())
                .suggests(ITEM_ID_SUGGESTIONS)
                .executes(ctx -> removeSizeKey(ctx,
                    itemArgument(ctx), typeSetting)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> qualitySetValue(String quality) {
        return Commands.literal(quality)
            .then(qualitySetMode(quality, "true"))
            .then(qualitySetMode(quality, "false"))
            .then(qualitySetMode(quality, "*"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> qualitySetMode(String quality, String typeSetting) {
        return Commands.literal(typeSetting)
            .executes(ctx -> setQualityHand(ctx, quality, typeSetting))
            .then(Commands.argument("item", ResourceLocationArgument.id())
                .suggests(ITEM_ID_SUGGESTIONS)
                .executes(ctx -> setQualityKey(ctx, quality,
                    itemArgument(ctx), typeSetting)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> qualityGetMode(String typeSetting) {
        return Commands.literal(typeSetting)
            .executes(ctx -> getQualityHand(ctx, typeSetting))
            .then(Commands.argument("item", ResourceLocationArgument.id())
                .suggests(ITEM_ID_SUGGESTIONS)
                .executes(ctx -> getQualityKey(ctx, itemArgument(ctx), typeSetting)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> qualityRemoveMode(String typeSetting) {
        return Commands.literal(typeSetting)
            .executes(ctx -> removeQualityHand(ctx, typeSetting))
            .then(Commands.argument("item", ResourceLocationArgument.id())
                .suggests(ITEM_ID_SUGGESTIONS)
                .executes(ctx -> removeQualityKey(ctx, itemArgument(ctx), typeSetting)));
    }

    private static ModDataStorage getData(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        if (server == null) return null;
        return ModDataStorage.get(server.overworld());
    }

    private static void syncAll(CommandContext<CommandSourceStack> ctx, ModDataStorage data) {
        var server = ctx.getSource().getServer();
        if (server != null) syncAll(server, data);
    }

    private static void syncAll(net.minecraft.server.MinecraftServer server, ModDataStorage data) {
        var packet = SyncDataPacket.from(data, server);
        for (var player : server.getPlayerList().getPlayers()) {
            ModNetwork.sendSyncToPlayer(player, packet);
        }
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> ctx) {
        try { return ctx.getSource().getPlayerOrException(); } catch (Exception e) { return null; }
    }

    // ---- SET ----

    private static int setPriceHand(CommandContext<CommandSourceStack> ctx, int value, String typeSetting) {
        var data = getData(ctx);
        if (data == null) { ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data")); return 0; }
        var p = getPlayer(ctx);
        if (p == null) return 0;
        var held = p.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_item"));
            return 0;
        }
        String itemId = ModDataStorage.getIdOnlyKey(held);
        String key = priceRuleKeyForCommand(ctx, itemId, typeSetting);
        doSet(ctx, data, key, value, displayRuleName(itemId, typeSetting));
        return 1;
    }

    private static String itemArgument(CommandContext<CommandSourceStack> ctx) {
        return resourceId(ctx, "item");
    }

    private static int setPriceKey(CommandContext<CommandSourceStack> ctx, int value, String itemId, String typeSetting) {
        var data = getData(ctx);
        if (data == null) { ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data")); return 0; }
        String key = priceRuleKeyForCommand(ctx, itemId, typeSetting);
        doSet(ctx, data, key, value, displayRuleName(itemId, typeSetting));
        return 1;
    }

    private static void doSet(CommandContext<CommandSourceStack> ctx, ModDataStorage data, String key, int value, Component displayName) {
        if (value == -1) {
            data.removePrice(key);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.price.removed",
                displayName), true);
        } else {
            data.setPrice(key, value);
            String formatted = FMT.format(value);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.price.set",
                displayName, Component.literal(formatted)), true);
        }
        syncAll(ctx, data);
    }

    private static int setSizeHand(CommandContext<CommandSourceStack> ctx, int length, int width,
                                   boolean rotateTexture, String typeSetting, boolean stretchTexture) {
        var data = getData(ctx);
        if (data == null) { ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data")); return 0; }
        var p = getPlayer(ctx);
        if (p == null) return 0;
        var held = p.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_item"));
            return 0;
        }
        String itemId = ModDataStorage.getIdOnlyKey(held);
        String key = ruleKeyForCommand(ctx, itemId, typeSetting);
        return doSetSize(ctx, data, key, displayRuleName(itemId, typeSetting), length, width, rotateTexture, stretchTexture);
    }

    private static int setSizeKey(CommandContext<CommandSourceStack> ctx, int length, int width,
                                  boolean rotateTexture, String typeSetting, boolean stretchTexture, String itemId) {
        var data = getData(ctx);
        if (data == null) { ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data")); return 0; }
        String key = ruleKeyForCommand(ctx, itemId, typeSetting);
        return doSetSize(ctx, data, key, displayRuleName(itemId, typeSetting), length, width, rotateTexture, stretchTexture);
    }

    private static int doSetSize(CommandContext<CommandSourceStack> ctx, ModDataStorage data, String key, Component displayName,
                                 int length, int width, boolean rotateTexture, boolean stretchTexture) {
        data.setSize(key, new ItemSize(length, width), rotateTexture, stretchTexture);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.size.set",
            displayName, Component.translatable("command.xero_delta.size.details",
                Component.literal(length + "x" + width),
                Component.translatable(rotateTexture ? "command.xero_delta.enabled" : "command.xero_delta.disabled"),
                Component.translatable(stretchTexture ? "command.xero_delta.enabled" : "command.xero_delta.disabled"))), true);
        syncAll(ctx, data);
        return 1;
    }

    private static int setQualityHand(CommandContext<CommandSourceStack> ctx, String quality, String typeSetting) {
        var data = getData(ctx);
        if (data == null) { ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data")); return 0; }
        var p = getPlayer(ctx);
        if (p == null) return 0;
        var held = p.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_item"));
            return 0;
        }
        String normalized = ModDataStorage.normalizeQuality(quality);
        String itemId = ModDataStorage.getIdOnlyKey(held);
        data.setQuality(ruleKeyForCommand(ctx, itemId, typeSetting), normalized);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.quality.set",
            displayRuleName(itemId, typeSetting), qualityName(normalized)), true);
        syncAll(ctx, data);
        return 1;
    }

    private static int setQualityKey(CommandContext<CommandSourceStack> ctx, String quality, String itemId, String typeSetting) {
        var data = getData(ctx);
        if (data == null) { ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data")); return 0; }
        String normalized = ModDataStorage.normalizeQuality(quality);
        data.setQuality(ruleKeyForCommand(ctx, itemId, typeSetting), normalized);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.quality.set",
            displayRuleName(itemId, typeSetting), qualityName(normalized)), true);
        syncAll(ctx, data);
        return 1;
    }

    private static Component displayRuleName(String itemId, String typeSetting) {
        if (isNamespacePattern(itemId)) {
            String namespace = itemId.substring(0, itemId.length() - 1);
            return Component.translatable("command.xero_delta.rule.namespace", namespace);
        }
        return switch (typeSetting == null ? "true" : typeSetting.toLowerCase(Locale.ROOT)) {
            case "*" -> Component.translatable("command.xero_delta.rule.all");
            case "false" -> Component.translatable("command.xero_delta.rule.item", itemId);
            default -> Component.translatable("command.xero_delta.rule.stack", itemId);
        };
    }

    private static Component qualityName(String quality) {
        return Component.translatable("quality.xero_delta." + ModDataStorage.normalizeQuality(quality));
    }

    private static String ruleKeyForCommand(CommandContext<CommandSourceStack> ctx, String itemId, String typeSetting) {
        String setting = typeSetting == null ? "true" : typeSetting.toLowerCase(Locale.ROOT);
        if (isNamespacePattern(itemId)) return ServerItemRules.namespaceKey(itemId.substring(0, itemId.length() - 1));
        if (!"true".equals(setting)) return ServerItemRules.ruleKey(itemId, setting);
        ServerPlayer player = getPlayer(ctx);
        if (player != null) {
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty() && ModDataStorage.getIdOnlyKey(held).equals(itemId)) {
                return ServerItemRules.exactKey(ModDataStorage.getKey(held));
            }
        }
        return ServerItemRules.ruleKey(itemId, setting);
    }

    private static String priceRuleKeyForCommand(CommandContext<CommandSourceStack> ctx, String itemId, String typeSetting) {
        String setting = typeSetting.toLowerCase(Locale.ROOT);
        if (isNamespacePattern(itemId)) return ModDataStorage.wildcardKey(itemId.substring(0, itemId.length() - 1) + ":*");
        if ("*".equals(setting)) return ModDataStorage.wildcardKey("*");
        if ("false".equals(setting)) return itemId;
        ServerPlayer player = getPlayer(ctx);
        if (player != null) {
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty() && ModDataStorage.getIdOnlyKey(held).equals(itemId)) {
                return ModDataStorage.getKey(held);
            }
        }
        return itemId;
    }

    private static boolean isNamespacePattern(String itemId) {
        return itemId.length() > 1 && itemId.endsWith("*") && !itemId.contains(":");
    }

    // ---- GET ----

    private static int getPriceHand(CommandContext<CommandSourceStack> ctx, String typeSetting) {
        var p = getPlayer(ctx);
        if (p == null) return 0;
        var held = p.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_item"));
            return 0;
        }
        return getPriceKey(ctx, ModDataStorage.getIdOnlyKey(held), typeSetting);
    }

    private static int getPriceKey(CommandContext<CommandSourceStack> ctx, String itemId, String typeSetting) {
        var data = getData(ctx);
        if (data == null) { ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data")); return 0; }
        long price = data.getPrice(priceRuleKeyForCommand(ctx, itemId, typeSetting));
        Component name = displayRuleName(itemId, typeSetting);
        if (price == 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.price.none",
                name), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.price.get", name,
                Component.literal(FMT.format(price))), false);
        }
        return 1;
    }

    private static int getSizeHand(CommandContext<CommandSourceStack> ctx, String typeSetting) {
        var player = getPlayer(ctx);
        if (player == null || player.getMainHandItem().isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_item"));
            return 0;
        }
        return getSizeKey(ctx, ModDataStorage.getIdOnlyKey(player.getMainHandItem()), typeSetting);
    }

    private static int getSizeKey(CommandContext<CommandSourceStack> ctx, String itemId, String typeSetting) {
        var data = getData(ctx);
        if (data == null) return 0;
        Map<String, Long> sizes = data.getAllSizes();
        String key = existingSizeKey(ctx, itemId, typeSetting, sizes);
        Long packed = sizes.get(key);
        Component displayName = displayRuleName(itemId, typeSetting);
        if (packed == null) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.size.none",
                displayName), false);
        } else {
            ItemSizeRule rule = ItemSizeRule.unpack(packed);
            Component description = Component.translatable("command.xero_delta.size.details",
                Component.literal(rule.size().width() + "x" + rule.size().height()),
                Component.translatable(rule.rotateTexture() ? "command.xero_delta.enabled" : "command.xero_delta.disabled"),
                Component.translatable(rule.stretchTexture() ? "command.xero_delta.enabled" : "command.xero_delta.disabled"));
            ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.size.get",
                displayName, description), false);
        }
        return 1;
    }

    private static int getQualityHand(CommandContext<CommandSourceStack> ctx, String typeSetting) {
        var player = getPlayer(ctx);
        if (player == null || player.getMainHandItem().isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_item"));
            return 0;
        }
        return getQualityKey(ctx, ModDataStorage.getIdOnlyKey(player.getMainHandItem()), typeSetting);
    }

    private static int getQualityKey(CommandContext<CommandSourceStack> ctx, String itemId, String typeSetting) {
        var data = getData(ctx);
        if (data == null) return 0;
        Component displayName = displayRuleName(itemId, typeSetting);
        String quality = data.getAllQualities().get(ruleKeyForCommand(ctx, itemId, typeSetting));
        if (quality == null) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.quality.none",
                displayName), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.quality.get",
                displayName, qualityName(quality)), false);
        }
        return 1;
    }

    // ---- REMOVE ----

    private static int removePriceHand(CommandContext<CommandSourceStack> ctx, String typeSetting) {
        var p = getPlayer(ctx);
        if (p == null) return 0;
        var held = p.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_item"));
            return 0;
        }
        return removePriceKey(ctx, ModDataStorage.getIdOnlyKey(held), typeSetting);
    }

    private static int removePriceKey(CommandContext<CommandSourceStack> ctx, String itemId, String typeSetting) {
        var data = getData(ctx);
        if (data == null) { ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_data")); return 0; }
        String key = priceRuleKeyForCommand(ctx, itemId, typeSetting);
        data.removePrice(key);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.price.removed",
            displayRuleName(itemId, typeSetting)), true);
        syncAll(ctx, data);
        return 1;
    }

    private static int removeSizeHand(CommandContext<CommandSourceStack> ctx, String typeSetting) {
        var player = getPlayer(ctx);
        if (player == null || player.getMainHandItem().isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_item"));
            return 0;
        }
        return removeSizeKey(ctx, ModDataStorage.getIdOnlyKey(player.getMainHandItem()), typeSetting);
    }

    private static int removeSizeKey(CommandContext<CommandSourceStack> ctx, String itemId, String typeSetting) {
        var data = getData(ctx);
        if (data == null) return 0;
        String key = existingSizeKey(ctx, itemId, typeSetting, data.getAllSizes());
        data.removeSize(key);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.size.removed",
            displayRuleName(itemId, typeSetting)), true);
        syncAll(ctx, data);
        return 1;
    }

    private static String existingSizeKey(CommandContext<CommandSourceStack> ctx, String itemId,
                                          String typeSetting, Map<String, Long> sizes) {
        String key = ruleKeyForCommand(ctx, itemId, typeSetting);
        if (sizes.containsKey(key)) return key;
        if ("true".equalsIgnoreCase(typeSetting)) {
            String exactItemKey = ServerItemRules.exactKey(itemId);
            if (sizes.containsKey(exactItemKey)) return exactItemKey;
        }
        return key;
    }

    private static int removeQualityHand(CommandContext<CommandSourceStack> ctx, String typeSetting) {
        var player = getPlayer(ctx);
        if (player == null || player.getMainHandItem().isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.xero_delta.no_item"));
            return 0;
        }
        return removeQualityKey(ctx, ModDataStorage.getIdOnlyKey(player.getMainHandItem()), typeSetting);
    }

    private static int removeQualityKey(CommandContext<CommandSourceStack> ctx, String itemId, String typeSetting) {
        var data = getData(ctx);
        if (data == null) return 0;
        String key = ruleKeyForCommand(ctx, itemId, typeSetting);
        data.removeQuality(key);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.xero_delta.quality.removed",
            displayRuleName(itemId, typeSetting)), true);
        syncAll(ctx, data);
        return 1;
    }
}
