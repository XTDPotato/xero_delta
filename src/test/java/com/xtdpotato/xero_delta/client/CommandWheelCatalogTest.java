package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandWheelCatalogTest {
    @Test
    void keepsOverloadsWithTheSameShapeButDifferentArguments() {
        var root = com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("xero")
            .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("price")
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("set")
                    .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<Object, Integer>argument(
                        "value", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<Object, String>argument(
                            "type", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> 1)))
                    .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<Object, String>argument(
                        "targets", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<Object, Integer>argument(
                            "currency", com.mojang.brigadier.arguments.IntegerArgumentType.integer()).executes(ctx -> 1)))))
            .build();
        assertEquals(2, CommandWheelCatalog.entries(root).size());
    }

    @Test
    void collectsOptionalFormsAndDeduplicatesPresetParameterNames() {
        var root = com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("xero")
            .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("loot")
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("search")
                    .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<Object, Boolean>argument(
                        "required", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> 1))))
            .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("market").executes(ctx -> 1)
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("buy")
                    .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<Object, String>argument(
                        "listingId", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> 1)
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<Object, Integer>argument(
                            "amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 640))
                            .executes(ctx -> 1)))))
            .build();
        var entries = CommandWheelCatalog.entries(root);
        assertEquals(4, entries.size());
        assertEquals(1, entries.stream().filter(entry -> entry.command().startsWith("/xero loot search")).count());
        assertEquals(2, entries.stream().filter(entry -> entry.command().startsWith("/xero market buy")).count());
        assertTrue(entries.stream().noneMatch(entry -> entry.command().startsWith("/xero gui")));
    }

    @Test
    void previewsMissingRequiredArgumentsAndPreservesJsonValues() {
        var entry = new CommandWheelCatalog.Entry("json", CommandWheelCatalog.Category.MAIL,
            "/xero mail send @s {payload}", java.util.List.of(new CommandWheelCatalog.Parameter(
                "payload", CommandWheelCatalog.ParameterKind.TEXT, java.util.List.of(), "", true)));
        assertEquals("", entry.buildCommand(java.util.Map.of()));
        assertEquals("/xero mail send @s {payload}", entry.previewCommand(java.util.Map.of()));
        assertEquals("/xero mail send @s {\"title\":\"test\"}",
            entry.buildCommand(java.util.Map.of("payload", "{\"title\":\"test\"}")));
    }

    @Test
    void filtersByCategoryAndCommandText() {
        var gui = CommandWheelCatalog.filter(CommandWheelCatalog.Category.GUI, "safety");
        assertEquals(1, gui.size());
        assertEquals("safety_box", gui.getFirst().id());
        assertTrue(CommandWheelCatalog.entries().stream()
            .filter(entry -> entry.category() == CommandWheelCatalog.Category.GUI)
            .allMatch(entry -> entry.command().matches("/xero gui open \\S+ \\S+")));
    }

    @Test
    void removedMisspellingIsNotPresentInPresets() {
        assertTrue(CommandWheelCatalog.entries().stream()
            .noneMatch(entry -> entry.command().contains("evacutate")));
        assertTrue(CommandWheelCatalog.entries().stream()
            .noneMatch(entry -> entry.command().contains("player_evacuate_failed")));
    }

    @Test
    void exposesLootSearchToggleInTheWheel() {
        var entries = CommandWheelCatalog.filter(CommandWheelCatalog.Category.SYSTEM, "loot_search");
        assertEquals(1, entries.size());
        var entry = entries.getFirst();
        assertEquals("/xero loot search {enabled}", entry.command());
        assertEquals("/xero loot search false",
            entry.buildCommand(java.util.Map.of("enabled", "false")));
    }

    @Test
    void exposesCorpseRulesInTheWheel() {
        var lifetime = CommandWheelCatalog.filter(
            CommandWheelCatalog.Category.SYSTEM, "corpse_lifetime").getFirst();
        assertEquals("/xero corpse lifetime 5",
            lifetime.buildCommand(java.util.Map.of()));

        var attackable = CommandWheelCatalog.filter(
            CommandWheelCatalog.Category.SYSTEM, "mob_corpse_attackable").getFirst();
        assertEquals("/xero corpse attackable false",
            attackable.buildCommand(java.util.Map.of("enabled", "false")));
    }

    @Test
    void acceptsNamedPlayersForItemBinding() {
        var binding = CommandWheelCatalog.filter(
            CommandWheelCatalog.Category.ITEM, "item_bound").getFirst();
        assertEquals(CommandWheelCatalog.ParameterKind.TEXT,
            binding.parameters().getFirst().kind());
        assertEquals("/xero bind abc true",
            binding.buildCommand(java.util.Map.of("target", "abc", "enabled", "true")));
    }
}
