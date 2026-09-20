package com.xtdpotato.xero_delta.data;

import java.util.List;

/** Display-only knife properties resolved from an item's own data and tooltip. */
public record KnifeDisplayStats(
    String level,
    String damage,
    String armorDamage,
    String penetration,
    String headshotMultiplier,
    String sprintSpeed,
    String walkSpeed,
    String attackSpeed,
    String attackRange,
    List<String> description
) {
    public KnifeDisplayStats {
        level = clean(level);
        damage = valueOrDash(damage);
        armorDamage = valueOrDash(armorDamage);
        penetration = valueOrDash(penetration);
        headshotMultiplier = valueOrDash(headshotMultiplier);
        sprintSpeed = valueOrDash(sprintSpeed);
        walkSpeed = valueOrDash(walkSpeed);
        attackSpeed = valueOrDash(attackSpeed);
        attackRange = valueOrDash(attackRange);
        description = description == null ? List.of() : List.copyOf(description);
    }

    public boolean hasLevel() {
        return !level.isBlank();
    }

    private static String valueOrDash(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? "—" : cleaned;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
