package com.xtdpotato.xero_delta.command;

import java.util.Comparator;
import java.util.List;

/** Single naming table shared by server registration, command presets and documentation. */
public final class CommandNames {
    public record Route(String legacy, String modern) {}

    public static final List<Route> ROUTES = List.of(
        new Route("xero_set layout", "layout"),
        new Route("xero_set layout_click", "layout click"),
        new Route("xero_set effect", "health effects"),
        new Route("xero_set re_effect", "health retain"),
        new Route("xero_set health_penalty", "health penalty"),
        new Route("xero_set corpse_lifetime", "corpse lifetime"),
        new Route("xero_set mob_corpse_attackable", "corpse attackable"),
        new Route("xero_loot_search", "loot search"),
        new Route("xero_set block_search", "loot search"),
        new Route("xero_set stamina", "stamina set"),
        new Route("xero_set coin_give give", "reward give"),
        new Route("xero_set coin_give remove", "reward clear"),
        new Route("xero_set player_evacuate", "evacuate"),
        new Route("xero_set allow_change_bc", "equipment access"),
        new Route("xero_set item_bound", "bind"),
        new Route("xero_set item trading upload", "market policy"),
        new Route("xero_set bullet", "bullet"),
        new Route("xero_price", "price"),
        new Route("xero_price reset_all", "price reset"),
        new Route("xero_size", "size"),
        new Route("xero_size reset_all", "size reset"),
        new Route("xero_quality", "quality"),
        new Route("xero_quality reset_all", "quality reset"),
        new Route("xero_weight", "weight"),
        new Route("xero_safety_box", "safety"),
        new Route("xero_knife", "knife"),
        new Route("xero_all qsq", "itemrules auto"),
        new Route("xero_all reset", "itemrules reset"),
        new Route("xero_trading", "market"),
        new Route("xero_trading_detail", "market detail"),
        new Route("xero_trade info", "market balance"),
        new Route("xero_mail", "mail"),
        new Route("xero_gui", "gui"),
        new Route("xero_dialog", "dialog"),
        new Route("xero_title", "notice")
    );
    private static final List<Route> LONGEST_FIRST = ROUTES.stream()
        .sorted(Comparator.comparingInt((Route route) -> route.legacy().length()).reversed()).toList();

    private CommandNames() {}

    public static String modernize(String command) {
        boolean slash = command.startsWith("/");
        String value = slash ? command.substring(1) : command;
        for (Route route : LONGEST_FIRST) {
            if (value.equals(route.legacy()) || value.startsWith(route.legacy() + " ")) {
                return (slash ? "/" : "") + "xero " + route.modern()
                    + value.substring(route.legacy().length());
            }
        }
        return command;
    }
}
