package com.xtdpotato.xero_delta.bedrock.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.xtdpotato.xero_delta.bedrock.molang.MolangExpression;
import com.xtdpotato.xero_delta.bedrock.molang.MolangParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BedrockAnimationParser {
    private final MolangParser molang = new MolangParser();

    public Map<String, BedrockAnimation> parse(JsonObject root) {
        Map<String, BedrockAnimation> result = new LinkedHashMap<>();
        for (var animationEntry : root.getAsJsonObject("animations").entrySet()) {
            JsonObject json = animationEntry.getValue().getAsJsonObject();
            Map<String, BedrockBoneAnimation> bones = new LinkedHashMap<>();
            if (json.has("bones")) {
                for (var boneEntry : json.getAsJsonObject("bones").entrySet()) {
                    JsonObject bone = boneEntry.getValue().getAsJsonObject();
                    bones.put(boneEntry.getKey(), new BedrockBoneAnimation(
                        track(bone.get("position")), track(bone.get("rotation")), track(bone.get("scale"))));
                }
            }
            boolean loop = json.has("loop") && (json.get("loop").isJsonPrimitive()
                && json.get("loop").getAsJsonPrimitive().isBoolean() && json.get("loop").getAsBoolean());
            result.put(animationEntry.getKey(), new BedrockAnimation(animationEntry.getKey(),
                json.has("animation_length") ? json.get("animation_length").getAsDouble() : 0, loop,
                Map.copyOf(bones)));
        }
        return Map.copyOf(result);
    }

    private BedrockAnimationTrack track(JsonElement element) {
        if (element == null) return null;
        List<BedrockKeyFrame> frames = new ArrayList<>();
        if (element.isJsonArray() || element.isJsonPrimitive()) {
            BedrockVectorExpression value = vector(element);
            frames.add(new BedrockKeyFrame(0, value, value, InterpolationMode.LINEAR));
        } else {
            for (var entry : element.getAsJsonObject().entrySet()) {
                double time = Double.parseDouble(entry.getKey());
                JsonElement frameElement = entry.getValue();
                BedrockVectorExpression pre;
                BedrockVectorExpression post;
                InterpolationMode mode = InterpolationMode.LINEAR;
                if (frameElement.isJsonObject()) {
                    JsonObject frame = frameElement.getAsJsonObject();
                    post = vector(frame.has("post") ? frame.get("post") : frame.get("pre"));
                    pre = vector(frame.has("pre") ? frame.get("pre") : frame.get("post"));
                    if (frame.has("lerp_mode") && "catmullrom".equalsIgnoreCase(frame.get("lerp_mode").getAsString())) {
                        mode = InterpolationMode.CATMULLROM;
                    }
                } else {
                    pre = post = vector(frameElement);
                }
                frames.add(new BedrockKeyFrame(time, pre, post, mode));
            }
        }
        return new BedrockAnimationTrack(frames);
    }

    private BedrockVectorExpression vector(JsonElement element) {
        if (element == null) return new BedrockVectorExpression(MolangExpression.ZERO,
            MolangExpression.ZERO, MolangExpression.ZERO);
        if (element.isJsonArray()) {
            var array = element.getAsJsonArray();
            return new BedrockVectorExpression(expression(array.get(0)), expression(array.get(1)), expression(array.get(2)));
        }
        MolangExpression expression = expression(element);
        return new BedrockVectorExpression(expression, expression, expression);
    }

    private MolangExpression expression(JsonElement element) {
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            double value = primitive.getAsDouble();
            return context -> value;
        }
        return molang.parse(primitive.getAsString());
    }
}
