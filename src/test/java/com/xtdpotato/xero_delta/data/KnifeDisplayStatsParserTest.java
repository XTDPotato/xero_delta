package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnifeDisplayStatsParserTest {
    @Test
    void parsesChineseTooltipInRequestedDisplayOrder() {
        KnifeDisplayStats stats = KnifeDisplayStatsParser.parse(List.of(
            "等级：3",
            "伤害：18 / 22 / 30",
            "护甲伤害：12",
            "穿甲等级：4",
            "爆头倍率：1.5x",
            "装备时移速（奔跑）：+4%",
            "装备时移速（步行）：+2%",
            "攻击速度：1.2",
            "攻击范围：3.5",
            "快速而平衡的战术刀。"
        ), "");

        assertTrue(stats.hasLevel());
        assertEquals("3", stats.level());
        assertEquals("18 / 22 / 30", stats.damage());
        assertEquals("12", stats.armorDamage());
        assertEquals("4", stats.penetration());
        assertEquals("1.5x", stats.headshotMultiplier());
        assertEquals(List.of("快速而平衡的战术刀。"), stats.description());
    }

    @Test
    void parsesComponentStyleDataAndHidesMissingLevel() {
        KnifeDisplayStats stats = KnifeDisplayStatsParser.parse(List.of(),
            "{damage:[8, 12, 16], armor_damage:6, penetration_level:2, "
                + "headshot_multiplier:1.25, attack_speed:1.6, attack_range:3.0}");

        assertFalse(stats.hasLevel());
        assertEquals("8 / 12 / 16", stats.damage());
        assertEquals("6", stats.armorDamage());
        assertEquals("1.6", stats.attackSpeed());
        assertEquals("3.0", stats.attackRange());
    }

    @Test
    void absentPropertiesUseConsistentPlaceholder() {
        KnifeDisplayStats stats = KnifeDisplayStatsParser.parse(List.of("A silent blade."), "");

        assertEquals("—", stats.damage());
        assertEquals("—", stats.penetration());
        assertEquals(List.of("A silent blade."), stats.description());
    }
}
