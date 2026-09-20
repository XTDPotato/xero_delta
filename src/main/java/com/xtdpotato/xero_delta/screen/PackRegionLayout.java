package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.item.DeltaPackItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Maps a pack's logical cells to separated visual pouch regions. */
public final class PackRegionLayout {
    public record LogicalRegion(int x, int y, int width, int height) {
        boolean contains(int itemX, int itemY, int itemWidth, int itemHeight) {
            return itemX >= x && itemY >= y
                && itemX + itemWidth <= x + width
                && itemY + itemHeight <= y + height;
        }
    }

    public record Rect(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
        }
    }

    public record Cell(int column, int row) {}

    private final List<Region> regions;
    private final int cellSize;
    private final int width;
    private final int height;

    public PackRegionLayout(DeltaPackItem pack, int cellSize, int gap) {
        this(pack.regions().stream().map(region -> new LogicalRegion(
            region.x(), region.y(), region.width(), region.height())).toList(),
            cellSize, gap);
    }

    public PackRegionLayout(List<LogicalRegion> logicalRegions, int cellSize, int gap) {
        this.cellSize = cellSize;
        List<LogicalRegion> ordered = new ArrayList<>(logicalRegions);
        ordered.sort(Comparator.comparingInt(LogicalRegion::y)
            .thenComparingInt(LogicalRegion::x));
        this.regions = new ArrayList<>(ordered.size());
        int maximumRight = 0;
        int maximumBottom = 0;
        for (LogicalRegion region : ordered) {
            int horizontalGaps = 0;
            int verticalGaps = 0;
            for (LogicalRegion other : ordered) {
                if (other == region) continue;
                if (overlaps(other.y(), other.y() + other.height(),
                    region.y(), region.y() + region.height())
                    && other.x() + other.width() <= region.x()) {
                    horizontalGaps++;
                }
                if (other.y() + other.height() <= region.y()
                    && overlaps(other.x(), other.x() + other.width(),
                    region.x(), region.x() + region.width())) {
                    verticalGaps++;
                }
            }
            int visualX = region.x() * cellSize + horizontalGaps * gap;
            int visualY = region.y() * cellSize + verticalGaps * gap;
            Rect bounds = new Rect(visualX, visualY,
                region.width() * cellSize, region.height() * cellSize);
            regions.add(new Region(region, bounds));
            maximumRight = Math.max(maximumRight, bounds.x() + bounds.width());
            maximumBottom = Math.max(maximumBottom, bounds.y() + bounds.height());
        }
        this.width = maximumRight;
        this.height = maximumBottom;
    }

    public int width() { return width; }
    public int height() { return height; }

    public List<Rect> regionBounds() {
        return regions.stream().map(Region::bounds).toList();
    }

    public Rect cellBounds(int column, int row) {
        Region region = regionAt(column, row);
        if (region == null) return null;
        return new Rect(region.bounds().x() + (column - region.logical().x()) * cellSize,
            region.bounds().y() + (row - region.logical().y()) * cellSize,
            cellSize, cellSize);
    }

    public Rect footprintBounds(int column, int row, int columns, int rows) {
        for (Region region : regions) {
            if (!region.logical().contains(column, row, columns, rows)) continue;
            return new Rect(region.bounds().x() + (column - region.logical().x()) * cellSize,
                region.bounds().y() + (row - region.logical().y()) * cellSize,
                columns * cellSize, rows * cellSize);
        }
        return null;
    }

    public Cell cellAt(double mouseX, double mouseY) {
        for (Region region : regions) {
            if (!region.bounds().contains(mouseX, mouseY)) continue;
            int column = region.logical().x()
                + (int) ((mouseX - region.bounds().x()) / cellSize);
            int row = region.logical().y()
                + (int) ((mouseY - region.bounds().y()) / cellSize);
            return new Cell(column, row);
        }
        return null;
    }

    private Region regionAt(int column, int row) {
        for (Region region : regions) {
            if (region.logical().contains(column, row, 1, 1)) return region;
        }
        return null;
    }

    private static boolean overlaps(int firstStart, int firstEnd, int secondStart, int secondEnd) {
        return firstStart < secondEnd && secondStart < firstEnd;
    }

    private record Region(LogicalRegion logical, Rect bounds) {}
}
