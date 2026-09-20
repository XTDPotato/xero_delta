package com.xtdpotato.xero_delta.data;

/** Pure authored material values shared by the catalogue and unit tests. */
final class MaterialValueDefaults {
    static final long IRON_NUGGET = 550L;
    static final long IRON_INGOT = 40_000L;
    static final long IRON_BLOCK = IRON_INGOT * 9L;
    static final long GOLD_INGOT = 50_000L;
    static final long COPPER_INGOT = 10_000L;
    static final long RAW_IRON = IRON_INGOT * 2L / 3L;
    static final long RAW_GOLD = GOLD_INGOT * 2L / 3L;
    static final long DIAMOND = 90_000L;
    static final long AMETHYST_SHARD = 10_000L;
    static final long TREASURE_MAP = 160_000L;
    static final long DRAGON_EGG = 100_000_000L;

    private MaterialValueDefaults() {
    }
}
