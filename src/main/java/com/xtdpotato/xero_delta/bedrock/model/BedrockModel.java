package com.xtdpotato.xero_delta.bedrock.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BedrockModel {
    private final String identifier;
    private final int textureWidth;
    private final int textureHeight;
    private final Map<String, BedrockBone> bones;
    private final List<BedrockBone> roots;

    public BedrockModel(String identifier, int textureWidth, int textureHeight,
                        List<BedrockBone> definitions) {
        this.identifier = identifier;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.bones = new LinkedHashMap<>();
        definitions.forEach(bone -> bones.put(bone.name(), bone));
        List<BedrockBone> rootBones = new ArrayList<>();
        for (BedrockBone bone : definitions) {
            BedrockBone parent = bone.parentName() == null ? null : bones.get(bone.parentName());
            if (parent == null) rootBones.add(bone);
            else parent.addChild(bone);
        }
        roots = List.copyOf(rootBones);
    }

    public BedrockModel instantiate() {
        return new BedrockModel(identifier, textureWidth, textureHeight,
            bones.values().stream().map(BedrockBone::copy).toList());
    }

    public void resetPose() { roots.forEach(BedrockBone::resetPose); }
    public String identifier() { return identifier; }
    public int textureWidth() { return textureWidth; }
    public int textureHeight() { return textureHeight; }
    public Map<String, BedrockBone> bones() { return bones; }
    public List<BedrockBone> roots() { return roots; }
}
