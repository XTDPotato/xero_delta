package com.xtdpotato.xero_delta.bedrock.animation;

import com.xtdpotato.xero_delta.bedrock.model.BedrockBone;
import com.xtdpotato.xero_delta.bedrock.model.BedrockModel;
import com.xtdpotato.xero_delta.bedrock.model.BedrockVec3;
import com.xtdpotato.xero_delta.bedrock.molang.MolangContext;

import java.util.Map;

public final class BedrockAnimationController {
    public enum State { IDLE, WALK, ATTACK, DEATH, CUSTOM }

    private final Map<String, BedrockAnimation> animations;
    private BedrockAnimation current;
    private State state = State.IDLE;
    private double startedAt;

    public BedrockAnimationController(Map<String, BedrockAnimation> animations) {
        this.animations = animations;
    }

    public void play(String animationName, State nextState, double lifeTime) {
        BedrockAnimation animation = animations.get(animationName);
        if (animation == null) throw new IllegalArgumentException("Unknown Bedrock animation: " + animationName);
        current = animation;
        state = nextState;
        startedAt = lifeTime;
    }

    public void apply(BedrockModel model, MolangContext baseContext) {
        model.resetPose();
        if (current == null) return;
        double elapsed = Math.max(0, baseContext.lifeTime() - startedAt);
        double animationTime = current.loop() && current.length() > 0 ? elapsed % current.length()
            : Math.min(elapsed, current.length());
        MolangContext context = new MolangContext() {
            public double animTime() { return animationTime; }
            public double lifeTime() { return baseContext.lifeTime(); }
            public boolean isSneaking() { return baseContext.isSneaking(); }
        };
        for (var entry : current.bones().entrySet()) {
            BedrockBone bone = model.bones().get(entry.getKey());
            if (bone == null) continue;
            BedrockBoneAnimation animation = entry.getValue();
            if (animation.position() != null) bone.setPosition(animation.position().sample(animationTime, context, BedrockVec3.ZERO));
            if (animation.rotation() != null) bone.setRotation(animation.rotation().sample(animationTime, context, BedrockVec3.ZERO));
            if (animation.scale() != null) bone.setScale(animation.scale().sample(animationTime, context, BedrockVec3.ONE));
        }
    }

    public State state() { return state; }
    public BedrockAnimation current() { return current; }
}
