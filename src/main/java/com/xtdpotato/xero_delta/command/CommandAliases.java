package com.xtdpotato.xero_delta.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import java.util.function.Predicate;

/** Copies command trees, including executors, suggestions and inherited permission gates. */
public final class CommandAliases {
    private CommandAliases() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("xero");
        if (root == null) {
            root = dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("xero"));
        }
        for (CommandNames.Route route : CommandNames.ROUTES) {
            addRoute(dispatcher.getRoot(), root, route);
        }
        final CommandNode<CommandSourceStack> commandRoot = root;
        commandRoot.addChild(LiteralArgumentBuilder.<CommandSourceStack>literal("help")
            .executes(context -> {
                for (var child : commandRoot.getChildren()) {
                    if (!child.getName().equals("help")) {
                        context.getSource().sendSuccess(
                            () -> Component.literal("/xero " + child.getName()), false);
                    }
                }
                return commandRoot.getChildren().size() - 1;
            }).build());

        // Convenience aliases such as /market and /weight mirror /xero market and /xero weight.
        for (CommandNode<CommandSourceStack> child : commandRoot.getChildren()) {
            if (child.getName().equals("help")) continue;
            if (dispatcher.getRoot().getChild(child.getName()) == null) {
                dispatcher.getRoot().addChild(copy(child));
            }
        }
    }

    static <S> void addRoute(CommandNode<S> sourceRoot, CommandNode<S> targetRoot,
                             CommandNames.Route route) {
        CommandNode<S> node = sourceRoot;
        Predicate<S> requirement = node.getRequirement();
        for (String part : route.legacy().split(" ")) {
            node = node.getChild(part);
            if (node == null) throw new IllegalStateException("Missing command: " + route.legacy());
            requirement = requirement.and(node.getRequirement());
        }
        addMappedBranch(targetRoot, route.modern(), node, requirement);
    }

    private static <S> void addMappedBranch(CommandNode<S> targetRoot,
                                        String targetPath,
                                        CommandNode<S> source, Predicate<S> requirement) {
        String[] parts = targetPath.split(" ");
        CommandNode<S> parent = targetRoot;
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            CommandNode<S> existing = parent.getChild(part);
            if (index == parts.length - 1) {
                // Brigadier merges both the executor and children into an existing branch.
                parent.addChild(copyWithName(source, part, requirement));
                return;
            }
            if (existing == null) {
                existing = LiteralArgumentBuilder.<S>literal(part).build();
                parent.addChild(existing);
            }
            parent = existing;
        }
    }

    private static <S> CommandNode<S> copy(CommandNode<S> node) {
        return copy(node, source -> true);
    }

    private static <S> CommandNode<S> copy(CommandNode<S> node, Predicate<S> inherited) {
        var builder = node.createBuilder();
        Predicate<S> requirement = inherited.and(node.getRequirement());
        builder.requires(requirement);
        for (CommandNode<S> child : node.getChildren()) {
            builder.then(copy(child, requirement));
        }
        return builder.build();
    }

    private static <S> CommandNode<S> copyWithName(
            CommandNode<S> node, String name, Predicate<S> requirement) {
        if (!(node instanceof LiteralCommandNode<?>)) {
            throw new IllegalArgumentException("Only literal roots can be renamed: " + node.getName());
        }
        var builder = LiteralArgumentBuilder.<S>literal(name)
            .requires(requirement);
        if (node.getCommand() != null) builder.executes(node.getCommand());
        for (CommandNode<S> child : node.getChildren()) {
            builder.then(copy(child, requirement));
        }
        return builder.build();
    }
}
