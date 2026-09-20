package com.xtdpotato.xero_delta.trading;

import java.util.Set;

/** Pure string rules kept separate so the deny list can be unit-tested without Minecraft runtime classes. */
final class TradingForbiddenItemPaths {
    private static final Set<String> VANILLA_PATHS = Set.of(
        "air", "barrier", "bedrock", "budding_amethyst",
        "command_block", "chain_command_block", "repeating_command_block",
        "command_block_minecart", "debug_stick", "end_portal_frame",
        "infested_stone", "infested_cobblestone", "infested_stone_bricks",
        "infested_mossy_stone_bricks", "infested_cracked_stone_bricks",
        "infested_chiseled_stone_bricks", "infested_deepslate",
        "jigsaw", "knowledge_book", "light", "reinforced_deepslate",
        "spawner", "structure_block", "structure_void", "test_block",
        "test_instance_block", "trial_spawner", "vault"
    );

    private TradingForbiddenItemPaths() {
    }

    static boolean canList(String namespace, String path) {
        if (namespace == null || path == null) return false;
        return !"minecraft".equals(namespace)
            || (!VANILLA_PATHS.contains(path) && !path.endsWith("_spawn_egg"));
    }
}
