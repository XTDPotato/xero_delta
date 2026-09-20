package com.xtdpotato.xero_delta.grid;

public final class SafetyBoxGridInteraction {
    private SafetyBoxGridInteraction() {
    }

    public static Target targetAt(double mouseX, double mouseY,
                                  int gridX, int gridY, int cellSize,
                                  int columns, int rows) {
        if (cellSize <= 0 || columns <= 0 || rows <= 0) return null;
        double localX = mouseX - gridX;
        double localY = mouseY - gridY;
        if (localX < 0.0D || localY < 0.0D
            || localX >= columns * (double) cellSize
            || localY >= rows * (double) cellSize) return null;
        int column = (int) (localX / cellSize);
        int row = (int) (localY / cellSize);
        double fractionX = clampFraction(
            (localX - column * cellSize) / cellSize);
        double fractionY = clampFraction(
            (localY - row * cellSize) / cellSize);
        return new Target(column, row, row * columns + column,
            fractionX, fractionY);
    }

    private static double clampFraction(double value) {
        return Math.max(0.0D, Math.min(0.999D, value));
    }

    public record Target(int column, int row, int cell,
                         double fractionX, double fractionY) {
    }
}
