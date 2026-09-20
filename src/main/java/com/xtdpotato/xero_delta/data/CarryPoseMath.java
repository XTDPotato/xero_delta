package com.xtdpotato.xero_delta.data;

/** Pure horizontal offsets shared by server placement and client carry rendering. */
public final class CarryPoseMath {
    public static final double SHOULDER_SIDE_OFFSET = 0.48D;
    public static final double DROP_FORWARD_DISTANCE = 0.50D;

    private CarryPoseMath() {
    }

    public static HorizontalOffset shoulderOffset(float yawDegrees) {
        HorizontalOffset forward = forwardOffset(yawDegrees, 1.0D);
        return new HorizontalOffset(-forward.z() * SHOULDER_SIDE_OFFSET,
            forward.x() * SHOULDER_SIDE_OFFSET);
    }

    public static HorizontalOffset dropOffset(float yawDegrees) {
        return forwardOffset(yawDegrees, DROP_FORWARD_DISTANCE);
    }

    static HorizontalOffset forwardOffset(float yawDegrees, double distance) {
        double radians = Math.toRadians(yawDegrees);
        return new HorizontalOffset(-Math.sin(radians) * distance,
            Math.cos(radians) * distance);
    }

    public record HorizontalOffset(double x, double z) {
    }
}
