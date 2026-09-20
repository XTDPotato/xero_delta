package com.xtdpotato.xero_delta.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandAliasesTest {
    @Test
    void retainsInheritedPermissionsAndLegacyExecutors() throws Exception {
        var dispatcher = new CommandDispatcher<Integer>();
        dispatcher.register(literal("xero_set").requires(level -> level >= 2)
            .then(literal("layout").then(RequiredArgumentBuilder.<Integer, Boolean>argument(
                "enabled", BoolArgumentType.bool()).executes(ctx -> 7))));
        var root = dispatcher.register(literal("xero"));
        CommandAliases.addRoute(dispatcher.getRoot(), root,
            new CommandNames.Route("xero_set layout", "layout"));

        assertEquals(7, dispatcher.execute("xero layout true", 2));
        assertEquals(7, dispatcher.execute("xero_set layout false", 2));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("xero layout true", 0));
        assertFalse(root.getChild("layout").getChild("enabled").canUse(0));
    }

    @Test
    void mergesMarketRootExecutorWithoutDroppingPolicyPermissionsOrSuggestions() throws Exception {
        var dispatcher = new CommandDispatcher<Integer>();
        dispatcher.register(literal("xero_set").requires(level -> level >= 2)
            .then(literal("policy").executes(ctx -> 11)));
        dispatcher.register(literal("xero_trading").executes(ctx -> 12)
            .then(literal("buy").then(RequiredArgumentBuilder.<Integer, String>argument(
                "listing", StringArgumentType.word())
                .suggests((ctx, builder) -> builder.suggest("example").buildFuture())
                .executes(ctx -> 13))));
        dispatcher.register(literal("xero_trading_detail").executes(ctx -> 14));
        var root = dispatcher.register(literal("xero"));
        for (var route : new CommandNames.Route[] {
            new CommandNames.Route("xero_set policy", "market policy"),
            new CommandNames.Route("xero_trading", "market"),
            new CommandNames.Route("xero_trading_detail", "market detail")
        }) CommandAliases.addRoute(dispatcher.getRoot(), root, route);

        assertEquals(12, dispatcher.execute("xero market", 0));
        assertEquals(13, dispatcher.execute("xero market buy example", 0));
        assertEquals(14, dispatcher.execute("xero market detail", 0));
        assertEquals(11, dispatcher.execute("xero market policy", 2));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("xero market policy", 0));
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("xero market buy ", 0)).get();
        assertTrue(suggestions.getList().stream().anyMatch(value -> value.getText().equals("example")));
    }

    private static LiteralArgumentBuilder<Integer> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }
}
