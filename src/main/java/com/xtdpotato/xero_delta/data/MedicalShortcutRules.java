package com.xtdpotato.xero_delta.data;

/** Pure ordering and timing rules shared by the medical shortcut implementation. */
public final class MedicalShortcutRules {
    public static final double DEFAULT_HOLD_SECONDS = 0.75D;

    private MedicalShortcutRules() {
    }

    public static int holdTicks(double seconds) {
        return Math.max(1, (int) Math.ceil(Math.max(0.05D, seconds) * 20.0D));
    }

    public static int candidateScore(boolean injuryNeeded, boolean healthNeeded,
                                     boolean preferred, boolean surgery,
                                     boolean applicableInjuryTreatment,
                                     boolean healthItem, int itemPriority) {
        int priority = Math.max(0, Math.min(99, itemPriority));
        if (injuryNeeded) {
            if (preferred && surgery && applicableInjuryTreatment) return priority;
            if (surgery && applicableInjuryTreatment) return 100 + priority;
            if (preferred && applicableInjuryTreatment) return 200 + priority;
            if (applicableInjuryTreatment) return 250 + priority;
            if (healthNeeded && healthItem) return 300 + priority;
            return Integer.MAX_VALUE;
        }
        if (healthNeeded && healthItem) {
            return (preferred ? 0 : 100) + priority;
        }
        return Integer.MAX_VALUE;
    }
}
