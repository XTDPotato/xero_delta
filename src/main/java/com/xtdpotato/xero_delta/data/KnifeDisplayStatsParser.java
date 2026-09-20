package com.xtdpotato.xero_delta.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tolerant parser for LR workshop data supplied as tooltip text or item components. */
public final class KnifeDisplayStatsParser {
    private static final Pattern FORMAT_CODE = Pattern.compile("§.");
    private static final List<Field> FIELDS = List.of(
        new Field("level", "level", "rank", "grade", "等级", "级别"),
        new Field("armorDamage", "armor damage", "armour damage", "armor_damage", "护甲伤害", "盔甲伤害"),
        new Field("penetration", "penetration", "penetration level", "armor penetration", "穿甲等级", "穿透等级", "穿甲"),
        new Field("headshotMultiplier", "headshot multiplier", "headshot", "headshot_multiplier", "爆头倍率", "爆头伤害"),
        new Field("sprintSpeed", "sprint speed", "running speed", "sprint_speed", "装备时移速（奔跑）", "装备时移速(奔跑)", "奔跑移速"),
        new Field("walkSpeed", "walk speed", "walking speed", "walk_speed", "装备时移速（步行）", "装备时移速(步行)", "步行移速"),
        new Field("attackSpeed", "attack speed", "attack_speed", "攻击速度", "攻速"),
        new Field("attackRange", "attack range", "attack_range", "攻击范围", "攻击距离"),
        new Field("damage", "damage", "base damage", "base_damage", "伤害", "基础伤害")
    );

    private KnifeDisplayStatsParser() {
    }

    public static KnifeDisplayStats parse(List<String> tooltipLines, String rawData) {
        List<String> lines = tooltipLines == null ? List.of() : tooltipLines.stream()
            .map(KnifeDisplayStatsParser::stripFormatting)
            .map(String::strip)
            .filter(line -> !line.isBlank())
            .toList();
        String raw = stripFormatting(rawData == null ? "" : rawData);

        String level = value("level", lines, raw);
        String damage = value("damage", lines, raw);
        String armorDamage = value("armorDamage", lines, raw);
        String penetration = value("penetration", lines, raw);
        String headshot = value("headshotMultiplier", lines, raw);
        String sprintSpeed = value("sprintSpeed", lines, raw);
        String walkSpeed = value("walkSpeed", lines, raw);
        String attackSpeed = value("attackSpeed", lines, raw);
        String attackRange = value("attackRange", lines, raw);

        Set<String> description = new LinkedHashSet<>();
        for (String line : lines) {
            if (isFieldLine(line) || isBoilerplate(line)) continue;
            description.add(line);
            if (description.size() >= 6) break;
        }
        return new KnifeDisplayStats(level, damage, armorDamage, penetration, headshot,
            sprintSpeed, walkSpeed, attackSpeed, attackRange, new ArrayList<>(description));
    }

    private static String value(String key, List<String> lines, String raw) {
        Field field = FIELDS.stream().filter(value -> value.key.equals(key)).findFirst().orElseThrow();
        for (String line : lines) {
            String found = fieldValue(line, field);
            if (!found.isBlank()) return cleanValue(found);
        }
        for (String alias : field.aliases) {
            String expression = "(?i)(?:^|[\\s,{;])['\"]?" + aliasPattern(alias)
                + "['\"]?\\s*[:=：]\\s*(['\"]?\\[[^]]+]|['\"]?[^,;\\n}]+)";
            Matcher matcher = Pattern.compile(expression).matcher(raw);
            if (matcher.find()) return cleanValue(matcher.group(1));
        }
        return "";
    }

    private static String fieldValue(String line, Field field) {
        int separator = firstSeparator(line);
        if (separator >= 0) {
            String label = normalizeLabel(line.substring(0, separator));
            for (String alias : field.aliases) {
                if (label.equals(normalizeLabel(alias))) return line.substring(separator + 1);
            }
        }
        String lower = line.toLowerCase(Locale.ROOT);
        for (String alias : field.aliases) {
            String normalizedAlias = alias.toLowerCase(Locale.ROOT);
            if (lower.endsWith(normalizedAlias)) {
                String prefix = line.substring(0, line.length() - alias.length()).strip();
                if (prefix.matches("[+\\-]?\\d[\\d./%×xX→~\\- ]*")) return prefix;
            }
        }
        return "";
    }

    private static boolean isFieldLine(String line) {
        for (Field field : FIELDS) if (!fieldValue(line, field).isBlank()) return true;
        return false;
    }

    private static boolean isBoilerplate(String line) {
        String value = line.toLowerCase(Locale.ROOT);
        return value.startsWith("when in ") || value.startsWith("按住")
            || value.startsWith("hold ") || value.startsWith("minecraft:")
            || value.startsWith("tacz:") || value.startsWith("lr_");
    }

    private static int firstSeparator(String value) {
        int colon = value.indexOf(':');
        int chineseColon = value.indexOf('：');
        int equals = value.indexOf('=');
        int found = -1;
        if (colon >= 0) found = colon;
        if (chineseColon >= 0 && (found < 0 || chineseColon < found)) found = chineseColon;
        if (equals >= 0 && (found < 0 || equals < found)) found = equals;
        return found;
    }

    private static String normalizeLabel(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_()（）\\-]", "");
    }

    private static String aliasPattern(String alias) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < alias.length(); index++) {
            char character = alias.charAt(index);
            if (Character.isWhitespace(character) || character == '_' || character == '-') {
                result.append("[\\s_\\-]*");
            } else {
                result.append(Pattern.quote(String.valueOf(character)));
            }
        }
        return result.toString();
    }

    private static String cleanValue(String value) {
        String cleaned = value == null ? "" : value.strip();
        while (!cleaned.isEmpty() && "'\"[".indexOf(cleaned.charAt(0)) >= 0) {
            cleaned = cleaned.substring(1).strip();
        }
        while (!cleaned.isEmpty() && "'\"]".indexOf(cleaned.charAt(cleaned.length() - 1)) >= 0) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).strip();
        }
        return cleaned.replaceAll("\\s*,\\s*", " / ");
    }

    private static String stripFormatting(String value) {
        return FORMAT_CODE.matcher(value == null ? "" : value).replaceAll("");
    }

    private record Field(String key, List<String> aliases) {
        private Field(String key, String... aliases) {
            this(key, List.of(aliases));
        }
    }
}
