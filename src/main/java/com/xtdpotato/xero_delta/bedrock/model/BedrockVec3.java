package com.xtdpotato.xero_delta.bedrock.model;

public record BedrockVec3(double x, double y, double z) {
    public static final BedrockVec3 ZERO = new BedrockVec3(0, 0, 0);
    public static final BedrockVec3 ONE = new BedrockVec3(1, 1, 1);

    public BedrockVec3 add(BedrockVec3 other) {
        return new BedrockVec3(x + other.x, y + other.y, z + other.z);
    }

    public BedrockVec3 lerp(BedrockVec3 other, double amount) {
        return new BedrockVec3(x + (other.x - x) * amount,
            y + (other.y - y) * amount, z + (other.z - z) * amount);
    }
}
