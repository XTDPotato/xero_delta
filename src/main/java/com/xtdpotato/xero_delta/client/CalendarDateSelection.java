package com.xtdpotato.xero_delta.client;

import java.time.LocalDate;
import java.util.Set;

/** Pure Ctrl/Shift date selection behavior shared by the calendar UI and unit tests. */
public final class CalendarDateSelection {
    private CalendarDateSelection() {
    }

    public static LocalDate select(Set<LocalDate> selected, LocalDate anchor, LocalDate clicked,
                                   boolean controlDown, boolean shiftDown) {
        if (shiftDown && anchor != null) {
            if (!controlDown) selected.clear();
            addRange(selected, anchor, clicked);
            return anchor;
        }
        if (controlDown) {
            if (!selected.add(clicked)) selected.remove(clicked);
            return clicked;
        }
        selected.clear();
        selected.add(clicked);
        return clicked;
    }

    private static void addRange(Set<LocalDate> selected, LocalDate first, LocalDate second) {
        LocalDate start = first.isBefore(second) ? first : second;
        LocalDate end = first.isAfter(second) ? first : second;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1L)) {
            selected.add(date);
        }
    }
}
