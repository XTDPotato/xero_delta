package com.xtdpotato.xero_delta.bedrock.animation;

import com.xtdpotato.xero_delta.bedrock.model.BedrockVec3;
import com.xtdpotato.xero_delta.bedrock.molang.MolangContext;

import java.util.Comparator;
import java.util.List;

public final class BedrockAnimationTrack {
    private final List<BedrockKeyFrame> frames;

    public BedrockAnimationTrack(List<BedrockKeyFrame> frames) {
        this.frames = frames.stream().sorted(Comparator.comparingDouble(BedrockKeyFrame::time)).toList();
    }

    public BedrockVec3 sample(double time, MolangContext context, BedrockVec3 fallback) {
        if (frames.isEmpty()) return fallback;
        if (time <= frames.getFirst().time()) return frames.getFirst().post().evaluate(context);
        if (time >= frames.getLast().time()) return frames.getLast().post().evaluate(context);
        int upperIndex = 1;
        while (upperIndex < frames.size() && frames.get(upperIndex).time() < time) upperIndex++;
        int lowerIndex = upperIndex - 1;
        BedrockKeyFrame lower = frames.get(lowerIndex);
        BedrockKeyFrame upper = frames.get(upperIndex);
        double amount = (time - lower.time()) / Math.max(0.000001, upper.time() - lower.time());
        BedrockVec3 start = lower.post().evaluate(context);
        BedrockVec3 end = upper.pre().evaluate(context);
        if (lower.interpolation() != InterpolationMode.CATMULLROM) return start.lerp(end, amount);
        BedrockVec3 before = frames.get(Math.max(0, lowerIndex - 1)).post().evaluate(context);
        BedrockVec3 after = frames.get(Math.min(frames.size() - 1, upperIndex + 1)).pre().evaluate(context);
        return catmull(before, start, end, after, amount);
    }

    private BedrockVec3 catmull(BedrockVec3 p0, BedrockVec3 p1, BedrockVec3 p2,
                                BedrockVec3 p3, double amount) {
        return new BedrockVec3(catmull(p0.x(), p1.x(), p2.x(), p3.x(), amount),
            catmull(p0.y(), p1.y(), p2.y(), p3.y(), amount),
            catmull(p0.z(), p1.z(), p2.z(), p3.z(), amount));
    }

    private double catmull(double p0, double p1, double p2, double p3, double amount) {
        double square = amount * amount;
        double cube = square * amount;
        return 0.5 * ((2 * p1) + (-p0 + p2) * amount
            + (2 * p0 - 5 * p1 + 4 * p2 - p3) * square
            + (-p0 + 3 * p1 - 3 * p2 + p3) * cube);
    }
}
