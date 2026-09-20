package com.xtdpotato.xero_delta.bedrock.animation;

public record BedrockKeyFrame(double time, BedrockVectorExpression pre,
                              BedrockVectorExpression post, InterpolationMode interpolation) {
}
