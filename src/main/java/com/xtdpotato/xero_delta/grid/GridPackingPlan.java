package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.data.ItemSize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Pure logical packing used before a container sort mutates physical slots. */
public final class GridPackingPlan {
    private GridPackingPlan() {
    }

    public record Cell(int column, int row) {
    }

    public record Entry(int index, ItemSize size, boolean preferredRotated) {
    }

    public record Placement(int index, Cell anchor, boolean rotated, Set<Cell> cells) {
    }

    public static Optional<List<Placement>> pack(Set<Cell> allowedCells, List<Entry> entries) {
        List<Cell> anchors = allowedCells.stream()
            .sorted(Comparator.comparingInt(Cell::row).thenComparingInt(Cell::column))
            .toList();
        Set<Cell> occupied = new HashSet<>();
        List<Placement> placements = new ArrayList<>();
        for (Entry entry : entries) {
            Placement placement = find(entry, anchors, allowedCells, occupied);
            if (placement == null) return Optional.empty();
            occupied.addAll(placement.cells());
            placements.add(placement);
        }
        return Optional.of(List.copyOf(placements));
    }

    private static Placement find(Entry entry, List<Cell> anchors, Set<Cell> allowed, Set<Cell> occupied) {
        boolean[] rotations = entry.size().width() == entry.size().height()
            ? new boolean[]{entry.preferredRotated()}
            : new boolean[]{entry.preferredRotated(), !entry.preferredRotated()};
        for (boolean rotated : rotations) {
            ItemSize oriented = rotated ? entry.size().rotated() : entry.size();
            for (Cell anchor : anchors) {
                Set<Cell> footprint = footprint(anchor, oriented);
                if (allowed.containsAll(footprint) && java.util.Collections.disjoint(occupied, footprint)) {
                    return new Placement(entry.index(), anchor, rotated, Set.copyOf(footprint));
                }
            }
        }
        return null;
    }

    private static Set<Cell> footprint(Cell anchor, ItemSize size) {
        Set<Cell> cells = new LinkedHashSet<>();
        for (int row = 0; row < size.height(); row++) {
            for (int column = 0; column < size.width(); column++) {
                cells.add(new Cell(anchor.column() + column, anchor.row() + row));
            }
        }
        return cells;
    }
}
