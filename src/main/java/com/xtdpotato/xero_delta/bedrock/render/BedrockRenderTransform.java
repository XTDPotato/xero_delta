package com.xtdpotato.xero_delta.bedrock.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xtdpotato.xero_delta.bedrock.model.BedrockBone;
import com.xtdpotato.xero_delta.bedrock.model.BedrockModel;
import com.xtdpotato.xero_delta.bedrock.model.BedrockVec3;

/** Coordinate conversions shared by the Bedrock render adapters. */
public final class BedrockRenderTransform {
    private BedrockRenderTransform() {
    }

    /** Convert absolute authored cube coordinates to the attachment's local space. */
    public static BedrockVec3 attachmentOffset(BedrockModel model, String boneName) {
        BedrockBone bone = model.bones().get(boneName);
        if (bone == null) throw new IllegalArgumentException("Missing attachment bone: " + boneName);
        BedrockVec3 pivot = bone.pivot();
        return new BedrockVec3(-pivot.x() / 16.0, pivot.y() / 16.0, -pivot.z() / 16.0);
    }

    /**
     * Convert Bedrock model coordinates to the first-person camera basis.
     * Bedrock's model preview uses Y-up and a forward +Z camera convention,
     * while the Java hand renderer uses Y-up with the camera looking down -Z.
     * Only the camera is positioned and rotated here. Model bones, animation
     * angles, UVs and handedness remain exactly as authored.
     */
    public static void applyCameraSpaceBasis(PoseStack pose) {
        BedrockVec3 offset = cameraSpaceOffset();
        pose.translate(offset.x(), offset.y(), offset.z());
        pose.mulPose(Axis.XP.rotationDegrees((float) cameraSpaceRotation().x()));
    }

    static BedrockVec3 cameraSpaceOffset() {
        return new BedrockVec3(0.2, -0.65, -2.5);
    }

    static BedrockVec3 cameraSpaceRotation() {
        return new BedrockVec3(180.0, 0.0, 0.0);
    }

    /** Finds the authored root pivot for a branch without assuming a root name. */
    public static double rootPivotY(BedrockModel model, String boneName) {
        BedrockBone bone = model.bones().get(boneName);
        if (bone == null) return 0.0;
        while (bone.parentName() != null) {
            BedrockBone parent = model.bones().get(bone.parentName());
            if (parent == null) break;
            bone = parent;
        }
        return bone.pivot().y();
    }
}
