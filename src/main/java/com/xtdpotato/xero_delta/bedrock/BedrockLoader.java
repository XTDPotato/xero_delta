package com.xtdpotato.xero_delta.bedrock;

import com.google.gson.JsonParser;
import com.xtdpotato.xero_delta.bedrock.animation.BedrockAnimation;
import com.xtdpotato.xero_delta.bedrock.animation.BedrockAnimationParser;
import com.xtdpotato.xero_delta.bedrock.model.BedrockGeometryParser;
import com.xtdpotato.xero_delta.bedrock.model.BedrockModel;

import java.io.Reader;
import java.util.Map;

public final class BedrockLoader {
    private final BedrockGeometryParser geometryParser = new BedrockGeometryParser();
    private final BedrockAnimationParser animationParser = new BedrockAnimationParser();

    public BedrockModel loadGeometry(Reader reader) {
        return geometryParser.parse(JsonParser.parseReader(reader).getAsJsonObject());
    }

    public Map<String, BedrockAnimation> loadAnimations(Reader reader) {
        return animationParser.parse(JsonParser.parseReader(reader).getAsJsonObject());
    }
}
