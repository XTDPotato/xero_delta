package com.xtdpotato.xero_delta.bedrock.model;

import java.util.Map;

public record BedrockCube(BedrockVec3 origin, BedrockVec3 size, BedrockVec3 pivot,
                          BedrockVec3 rotation, double inflate, boolean mirror,
                          Map<String, BedrockFace> faces) {
}
