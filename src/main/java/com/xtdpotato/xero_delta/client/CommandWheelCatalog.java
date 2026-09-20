package com.xtdpotato.xero_delta.client;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.xtdpotato.xero_delta.command.CommandNames;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Searchable command presets used by the in-game command panel. */
public final class CommandWheelCatalog {
    public enum Category { ALL, GUI, PLAYER, ITEM, TRADING, SYSTEM, MAIL }

    public enum ParameterKind { TOGGLE, DROPDOWN, TEXT, NUMBER }

    public record Parameter(String id, ParameterKind kind, List<String> options,
                            String defaultValue, boolean required) {
        public String labelKey() {
            return "command_wheel.xero_delta.parameter." + id;
        }
    }

    public record Entry(String id, Category category, String command,
                        List<Parameter> parameters) {
        public String titleKey() { return "command_wheel.xero_delta.entry." + id; }
        public String descriptionKey() { return titleKey() + ".description"; }

        public String buildCommand(Map<String, String> values) {
            String result = command;
            for (Parameter parameter : parameters) {
                String value = values.getOrDefault(parameter.id(), parameter.defaultValue()).trim();
                if (parameter.required() && value.isBlank()) return "";
                result = result.replace("{" + parameter.id() + "}", value);
            }
            return result.trim();
        }

        public String previewCommand(Map<String, String> values) {
            String result = command;
            for (Parameter parameter : parameters) {
                String value = values.getOrDefault(parameter.id(), parameter.defaultValue()).trim();
                if (parameter.required() && value.isBlank()) continue;
                result = result.replace("{" + parameter.id() + "}", value);
            }
            return result.trim();
        }
    }

    private static final List<Entry> ENTRIES = List.of(
        entry("market", Category.GUI, "/xero_gui open @s trading_market"),
        entry("recycling", Category.GUI, "/xero_gui open @s recycling"),
        entry("safety_box", Category.GUI, "/xero_gui open @s safety_box"),
        entry("knife", Category.GUI, "/xero_gui open @s knife"),
        entry("card_holder", Category.GUI, "/xero_gui open @s card_holder"),
        entry("player_status", Category.GUI, "/xero_gui open @s player_status"),
        entry("effect_hud", Category.GUI, "/xero_gui open @s effect_hud"),
        entry("mail", Category.GUI, "/xero_gui open @s mail"),
        entry("layout", Category.PLAYER, "/xero_set layout {enabled}",
            toggle("enabled", "true")),
        entry("stamina", Category.PLAYER, "/xero_set stamina {target} {amount}",
            target(), number("amount", "180")),
        entry("feature_access", Category.PLAYER,
            "/xero_set allow_change_bc {target} {enabled}",
            target(), toggle("enabled", "true")),
        entry("evacuate", Category.PLAYER, "/xero_set player_evacuate {target}",
            target()),
        entry("effect_scale", Category.SYSTEM, "/xero_set effect {scale}",
            dropdown("scale", "1.0", "0.5", "0.75", "1.0", "1.25", "1.5", "2.0")),
        entry("retain_effects", Category.SYSTEM, "/xero_set re_effect {enabled}",
            toggle("enabled", "false")),
        entry("corpse_lifetime", Category.SYSTEM, "/xero_set corpse_lifetime {minutes}",
            number("minutes", "5")),
        entry("mob_corpse_attackable", Category.SYSTEM,
            "/xero_set mob_corpse_attackable {enabled}",
            toggle("enabled", "true")),
        entry("loot_search", Category.SYSTEM, "/xero_loot_search {enabled}",
            toggle("enabled", "true")),
        entry("auto_all", Category.ITEM, "/xero_all qsq"),
        entry("auto_price", Category.ITEM, "/xero_price auto"),
        entry("auto_size", Category.ITEM, "/xero_size auto"),
        entry("auto_quality", Category.ITEM, "/xero_quality auto"),
        entry("auto_weight", Category.ITEM, "/xero_weight auto"),
        entry("trading_item_policy", Category.ITEM, "/xero_set item trading upload {policy} {item_id}",
            dropdown("policy", "up", "up", "recycle", "none"), text("item_id", "", true)),
        entry("item_bound", Category.ITEM, "/xero_set item_bound {target} {enabled}",
            target(), toggle("enabled", "true")),
        entry("unlock_box", Category.ITEM,
            "/xero_safety_box unlock {target} {item_id} {days}",
            target(), text("item_id", "", true), number("days", "30")),
        entry("unlock_knife", Category.ITEM, "/xero_knife unlock {target} {item_id}",
            target(), text("item_id", "", false)),
        entry("lock_knife", Category.ITEM, "/xero_knife lock {target} {item_id}",
            target(), text("item_id", "", false)),
        entry("coin_give", Category.TRADING,
            "/xero_set coin_give give {target} {amount}",
            target(), number("amount", "1000")),
        entry("trading_root", Category.TRADING, "/xero_trading"),
        entry("mail_root", Category.MAIL, "/xero_mail")
    );

    private CommandWheelCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** Uses the server-synchronized tree, so optional argument forms cannot go missing. */
    public static <S> List<Entry> entries(CommandNode<S> root) {
        if (root == null) return ENTRIES;
        Map<String, Entry> tree = new LinkedHashMap<>();
        collect(root, "/xero", List.of(), tree);
        Map<String, Entry> entries = new LinkedHashMap<>();
        Set<String> replaced = new HashSet<>();
        for (Entry entry : ENTRIES) {
            var candidate = tree.values().stream().filter(value -> matches(entry, value)).findFirst();
            if (candidate.isPresent()) {
                entries.put(entry.command(), entry);
                if (signature(entry.command()).equals(signature(candidate.get().command()))) {
                    replaced.add(candidate.get().command());
                }
            }
        }
        for (Entry entry : tree.values()) {
            if (!replaced.contains(entry.command())) entries.putIfAbsent(entry.command(), entry);
        }
        return List.copyOf(entries.values());
    }

    private static String signature(String command) {
        return command.replaceAll("\\{[^{}]+}", "{}");
    }

    private static boolean matches(Entry preset, Entry candidate) {
        String[] wanted = candidate.command().split(" ");
        String[] actual = preset.command().split(" ");
        if (wanted.length != actual.length) return false;
        for (int index = 0; index < wanted.length; index++) {
            if (!wanted[index].startsWith("{") && !wanted[index].equals(actual[index])) return false;
        }
        return true;
    }

    private static <S> void collect(CommandNode<S> node, String command,
                                    List<Parameter> parameters, Map<String, Entry> entries) {
        if (node.getCommand() != null && !command.equals("/xero help")) {
            entries.putIfAbsent(command, new Entry("tree:" + command, categoryFor(command),
                command, List.copyOf(parameters)));
        }
        for (CommandNode<S> child : node.getChildren()) {
            List<Parameter> next = new ArrayList<>(parameters);
            String token = child.getName();
            if (child instanceof ArgumentCommandNode<S, ?> argument) {
                next.add(parameter(argument));
                token = "{" + token + "}";
            }
            collect(child, command + " " + token, next, entries);
        }
    }

    private static Parameter parameter(ArgumentCommandNode<?, ?> node) {
        String id = node.getName();
        ArgumentType<?> type = node.getType();
        if (type instanceof BoolArgumentType) return toggle(id, "true");
        if (type instanceof IntegerArgumentType value) {
            return number(id, Integer.toString(Math.max(value.getMinimum(), Math.min(value.getMaximum(), 1))));
        }
        if (type instanceof LongArgumentType value) {
            return number(id, Long.toString(Math.max(value.getMinimum(), Math.min(value.getMaximum(), 1L))));
        }
        if (type instanceof DoubleArgumentType value) {
            return number(id, Double.toString(Math.max(value.getMinimum(), Math.min(value.getMaximum(), 1.0))));
        }
        if (type instanceof FloatArgumentType value) {
            return number(id, Float.toString(Math.max(value.getMinimum(), Math.min(value.getMaximum(), 1.0F))));
        }
        return switch (id) {
            case "targets", "target" -> text(id, "@s", true);
            case "type", "autoType", "weightMode", "weightGetMode" ->
                dropdown(id, "this_type", "this_type", "all_type", "this_durability");
            case "rotate", "autoRotate" -> dropdown(id, "rotate", "rotate", "false");
            case "stretch", "autoStretch" -> dropdown(id, "stretch", "stretch", "false", "prop1", "prop2");
            case "durability", "autoDurability" -> text(id, "0..max", true);
            case "price_or_offset" -> text(id, "1000", true);
            default -> text(id, "", true);
        };
    }

    private static Category categoryFor(String command) {
        String[] words = command.split(" ");
        return switch (words.length > 1 ? words[1] : "") {
            case "xero" -> Category.SYSTEM;
            case "gui", "dialog", "notice" -> Category.GUI;
            case "layout", "stamina", "equipment", "evacuate", "bind" -> Category.PLAYER;
            case "price", "size", "quality", "weight", "safety", "knife", "bullet", "itemrules" -> Category.ITEM;
            case "market", "reward" -> Category.TRADING;
            case "mail" -> Category.MAIL;
            default -> Category.SYSTEM;
        };
    }

    public static List<Entry> filter(Category category, String query) {
        return filter(ENTRIES, category, query);
    }

    public static List<Entry> filter(List<Entry> entries, Category category, String query) {
        String needle = normalize(query);
        return entries.stream()
            .filter(entry -> category == null || category == Category.ALL
                || entry.category() == category)
            .filter(entry -> needle.isBlank() || normalize(entry.id()).contains(needle)
                || normalize(entry.command()).contains(needle))
            .toList();
    }

    private static Entry entry(String id, Category category, String command) {
        return new Entry(id, category, CommandNames.modernize(command), List.of());
    }

    private static Entry entry(String id, Category category, String command,
                               Parameter... parameters) {
        return new Entry(id, category, CommandNames.modernize(command), List.of(parameters));
    }

    private static Parameter target() {
        return text("target", "@s", true);
    }

    private static Parameter toggle(String id, String defaultValue) {
        return new Parameter(id, ParameterKind.TOGGLE,
            List.of("true", "false"), defaultValue, true);
    }

    private static Parameter dropdown(String id, String defaultValue, String... options) {
        return new Parameter(id, ParameterKind.DROPDOWN,
            List.of(options), defaultValue, true);
    }

    private static Parameter text(String id, String defaultValue, boolean required) {
        return new Parameter(id, ParameterKind.TEXT, List.of(), defaultValue, required);
    }

    private static Parameter number(String id, String defaultValue) {
        return new Parameter(id, ParameterKind.NUMBER, List.of(), defaultValue, true);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
