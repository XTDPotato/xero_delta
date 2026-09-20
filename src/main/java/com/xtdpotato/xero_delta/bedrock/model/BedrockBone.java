package com.xtdpotato.xero_delta.bedrock.model;

import java.util.ArrayList;
import java.util.List;

public final class BedrockBone {
    private final String name;
    private final String parentName;
    private final BedrockVec3 pivot;
    private final BedrockVec3 bindRotation;
    private final List<BedrockCube> cubes;
    private final List<BedrockLocator> locators;
    private final List<BedrockBone> children = new ArrayList<>();
    private BedrockVec3 position = BedrockVec3.ZERO;
    private BedrockVec3 rotation = BedrockVec3.ZERO;
    private BedrockVec3 scale = BedrockVec3.ONE;

    public BedrockBone(String name, String parentName, BedrockVec3 pivot,
                       BedrockVec3 bindRotation, List<BedrockCube> cubes) {
        this(name, parentName, pivot, bindRotation, cubes, List.of());
    }

    public BedrockBone(String name, String parentName, BedrockVec3 pivot,
                       BedrockVec3 bindRotation, List<BedrockCube> cubes,
                       List<BedrockLocator> locators) {
        this.name = name;
        this.parentName = parentName;
        this.pivot = pivot;
        this.bindRotation = bindRotation;
        this.cubes = List.copyOf(cubes);
        this.locators = List.copyOf(locators);
    }

    public BedrockBone copy() {
        return new BedrockBone(name, parentName, pivot, bindRotation, cubes, locators);
    }

    public void resetPose() {
        position = BedrockVec3.ZERO;
        rotation = BedrockVec3.ZERO;
        scale = BedrockVec3.ONE;
        children.forEach(BedrockBone::resetPose);
    }

    public String name() { return name; }
    public String parentName() { return parentName; }
    public BedrockVec3 pivot() { return pivot; }
    public BedrockVec3 bindRotation() { return bindRotation; }
    public List<BedrockCube> cubes() { return cubes; }
    public List<BedrockLocator> locators() { return locators; }
    public List<BedrockBone> children() { return children; }
    public BedrockVec3 position() { return position; }
    public BedrockVec3 rotation() { return rotation; }
    public BedrockVec3 scale() { return scale; }
    public void setPosition(BedrockVec3 value) { position = value; }
    public void setRotation(BedrockVec3 value) { rotation = value; }
    public void setScale(BedrockVec3 value) { scale = value; }
    void addChild(BedrockBone child) { children.add(child); }
}
