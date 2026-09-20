package com.xtdpotato.xero_delta.bedrock.animation;

import java.util.Map;

public record BedrockAnimation(String name, double length, boolean loop,
                               Map<String, BedrockBoneAnimation> bones) {
}
