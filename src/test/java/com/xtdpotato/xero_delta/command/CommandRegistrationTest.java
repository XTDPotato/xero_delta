package com.xtdpotato.xero_delta.command;

import com.mojang.brigadier.CommandDispatcher;
import com.xtdpotato.xero_delta.client.CommandWheelCatalog;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandRegistrationTest {
    @Test
    void exportsAllCommandFormsAndParameterTypes() throws Exception {
        var dispatcher = dispatcher();
        var root = dispatcher.getRoot().getChild("xero");
        StringBuilder output = new StringBuilder("# Xero Delta: Complete Command Reference\n\n")
            .append("Generated from the registered Minecraft 1.21.1 command tree.\n")
            .append("Each line is an executable form. <name> is a required argument; longer forms list optional tails separately.\n")
            .append("Permission gates are unchanged from the legacy commands. Game execution can impose additional validation.\n\n")
            .append("## Compatibility Mapping\n\n| Old prefix | Current prefix |\n| --- | --- |\n");
        for (var route : CommandNames.ROUTES) {
            output.append("| `/").append(route.legacy()).append("` | `/xero ")
                .append(route.modern()).append("` |\n");
        }
        output.append("\n## Short Aliases\n\n");
        for (var child : root.getChildren()) {
            if (!child.getName().equals("help")) output.append("- `/").append(child.getName())
                .append("` = `/xero ").append(child.getName()).append("` (when no other mod owns the short name)\n");
        }
        output.append("\n## All Executable Forms\n");
        appendReference(root, "/xero", List.of(), output);
        Path report = Path.of("build/reports/commands.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, output);
        assertTrue(output.toString().contains("/xero market buy <listingId> <amount>"));
        assertTrue(output.toString().contains("/xero market policy <uploadPolicy> <uploadItem>"));
    }

    private static void appendReference(CommandNode<CommandSourceStack> node, String command,
                                        List<String> parameters, StringBuilder output) {
        if (node.getCommand() != null) {
            output.append("\n### `").append(command).append("`\n");
            for (String parameter : parameters) output.append("- ").append(parameter).append('\n');
        }
        for (var child : node.getChildren()) {
            var next = new ArrayList<>(parameters);
            String token = child.getName();
            if (child instanceof ArgumentCommandNode<CommandSourceStack, ?> argument) {
                var type = argument.getType();
                String description = type.getClass().getPackageName().equals("com.mojang.brigadier.arguments")
                    ? type.toString() : type.getClass().getSimpleName();
                if (type instanceof com.mojang.brigadier.arguments.BoolArgumentType) description = "true | false";
                next.add("`" + token + "`: `" + description + "`");
                token = "<" + token + ">";
            }
            appendReference(child, command + " " + token, next, output);
        }
    }

    @Test
    void registersEveryRouteAndParsesModernAndLegacyForms() {
        var dispatcher = dispatcher();
        var source = source(4);
        for (String command : new String[] {
            "xero layout true", "xero layout click false", "xero weight auto",
            "xero market", "xero market detail minecraft:paper", "xero market buy example 2",
            "xero gui open @s trading_market", "xero loot search true",
            "xero_set layout true", "xero_weight auto", "market", "weight auto",
            "xero mail send @s {\"title\":\"test\"}"
        }) {
            var parsed = dispatcher.parse(command, source);
            assertFalse(parsed.getReader().canRead(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
        }
        for (String command : new String[] {"xero layout true", "layout true", "xero bind @s true"}) {
            var parsed = dispatcher.parse(command, source(0));
            assertTrue(parsed.getReader().canRead() || parsed.getContext().getCommand() == null, command);
        }
        assertTrue(CommandWheelCatalog.entries(dispatcher.getRoot().getChild("xero")).size() > 100);
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        ModCommands.register(dispatcher, CommandBuildContext.simple(registries, FeatureFlags.DEFAULT_FLAGS));
        return dispatcher;
    }

    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null,
            permission, "test", Component.literal("test"), null, null);
    }
}
