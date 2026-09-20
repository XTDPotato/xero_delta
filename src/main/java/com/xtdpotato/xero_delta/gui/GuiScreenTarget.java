package com.xtdpotato.xero_delta.gui;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Stable command-facing names for screens that can be opened without a world block. */
public enum GuiScreenTarget {
    TRADING_MARKET("trading_market", "market", "trading", "\u4ea4\u6613\u884c"),
    RECYCLING("recycling", "recycler", "recycle", "\u56de\u6536\u7ad9"),
    TRADING_OPERATOR("trading_operator", "operator", "\u519b\u9700\u5904"),
    SAFETY_BOX("safety_box", "safety_box_picker", "\u5b89\u5168\u7bb1", "\u5b89\u5168\u7bb1\u9009\u62e9"),
    KNIFE("knife", "knife_picker", "\u5200\u76ae", "\u5200\u76ae\u9009\u62e9"),
    CARD_HOLDER("card_holder", "card_holder_picker", "\u5361\u5305", "\u95e8\u7981\u5361\u5305"),
    PLAYER_STATUS("player_status", "inventory", "\u7269\u54c1\u680f", "\u73a9\u5bb6\u72b6\u6001"),
    EFFECT_HUD("effect_hud", "hud", "\u6548\u679chud", "\u6548\u679c\u754c\u9762"),
    MAIL("mail", "mailbox", "\u90ae\u4ef6", "\u90ae\u7bb1");

    private final String id;
    private final Set<String> aliases;

    GuiScreenTarget(String id, String... aliases) {
        this.id = id;
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.add(normalize(id));
        Arrays.stream(aliases).map(GuiScreenTarget::normalize).forEach(values::add);
        this.aliases = Set.copyOf(values);
    }

    public String id() {
        return id;
    }

    public Set<String> aliases() {
        return aliases;
    }

    public boolean clientOnly() {
        return this == SAFETY_BOX || this == KNIFE || this == CARD_HOLDER
            || this == PLAYER_STATUS || this == EFFECT_HUD;
    }

    public static Optional<GuiScreenTarget> parse(String value) {
        String normalized = normalize(value);
        return Arrays.stream(values()).filter(target -> target.aliases.contains(normalized)).findFirst();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
