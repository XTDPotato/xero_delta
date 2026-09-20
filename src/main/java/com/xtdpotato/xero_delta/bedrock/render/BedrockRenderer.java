package com.xtdpotato.xero_delta.bedrock.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xtdpotato.xero_delta.bedrock.model.BedrockBone;
import com.xtdpotato.xero_delta.bedrock.model.BedrockCube;
import com.xtdpotato.xero_delta.bedrock.model.BedrockFace;
import com.xtdpotato.xero_delta.bedrock.model.BedrockModel;
import com.xtdpotato.xero_delta.bedrock.model.BedrockVec3;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BedrockRenderer {
    private static final float PIXEL = 1.0f / 16.0f;

    public void render(PoseStack pose, BedrockModel model, ResourceLocation texture,
                       MultiBufferSource buffers, int light, BedrockRenderFilter filter) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        for (BedrockBone root : model.roots()) {
            renderBone(pose, root, model, consumer, light, filter);
        }
    }

    /**
     * Renders a bone and all of its descendants without replaying its ancestors.
     * Callers that attach a Bedrock branch to a vanilla model part must provide the
     * parent transform before calling this method.
     */
    public void renderSubtree(PoseStack pose, BedrockModel model, String boneName,
                              ResourceLocation texture, MultiBufferSource buffers,
                              int light, BedrockRenderFilter filter) {
        BedrockBone bone = model.bones().get(boneName);
        if (bone == null) return;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        renderBone(pose, bone, model, consumer, light, filter);
    }

    /**
     * Renders a target branch after an external model part has supplied the
     * attachment bone transform. Intermediate Bedrock bones between the
     * attachment and target are retained, so an authored item/group bone is
     * not silently skipped.
     */
    public void renderAttachedSubtree(PoseStack pose, BedrockModel model,
                                      String attachmentBoneName, String targetBoneName,
                                      ResourceLocation texture, MultiBufferSource buffers,
                                      int light, BedrockRenderFilter filter) {
        BedrockBone target = model.bones().get(targetBoneName);
        if (target == null) return;
        List<BedrockBone> path = new ArrayList<>();
        BedrockBone current = target;
        while (current != null && !attachmentBoneName.equals(current.name())) {
            path.add(current);
            current = current.parentName() == null ? null : model.bones().get(current.parentName());
        }
        if (current == null || path.isEmpty()) return;
        Collections.reverse(path);

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        pose.pushPose();
        for (int index = 0; index < path.size() - 1; index++) {
            applyBoneTransform(pose, path.get(index));
        }
        renderBone(pose, path.getLast(), model, consumer, light, filter);
        pose.popPose();
    }

    private void renderBone(PoseStack pose, BedrockBone bone, BedrockModel model,
                            VertexConsumer consumer, int light, BedrockRenderFilter filter) {
        pose.pushPose();
        applyBoneTransform(pose, bone);
        if (filter.renderCubes(bone)) {
            for (BedrockCube cube : bone.cubes()) {
                renderCube(pose, cube, model, consumer, light);
            }
        }
        for (BedrockBone child : bone.children()) {
            renderBone(pose, child, model, consumer, light, filter);
        }
        pose.popPose();
    }

    private void applyBoneTransform(PoseStack pose, BedrockBone bone) {
        BedrockVec3 pivot = bone.pivot();
        BedrockVec3 position = bone.position();
        pose.translate(position.x() * PIXEL, -position.y() * PIXEL, position.z() * PIXEL);
        pose.translate(pivot.x() * PIXEL, -pivot.y() * PIXEL, pivot.z() * PIXEL);
        rotate(pose, bone.bindRotation().add(bone.rotation()));
        pose.scale((float) bone.scale().x(), (float) bone.scale().y(), (float) bone.scale().z());
        pose.translate(-pivot.x() * PIXEL, pivot.y() * PIXEL, -pivot.z() * PIXEL);
    }

    private void renderCube(PoseStack pose, BedrockCube cube, BedrockModel model,
                            VertexConsumer consumer, int light) {
        pose.pushPose();
        BedrockVec3 pivot = cube.pivot();
        if (cube.rotation() != BedrockVec3.ZERO) {
            pose.translate(pivot.x() * PIXEL, -pivot.y() * PIXEL, pivot.z() * PIXEL);
            rotate(pose, cube.rotation());
            pose.translate(-pivot.x() * PIXEL, pivot.y() * PIXEL, -pivot.z() * PIXEL);
        }
        double inflate = cube.inflate();
        double x0 = (cube.origin().x() - inflate) * PIXEL;
        double y0 = -(cube.origin().y() - inflate) * PIXEL;
        double z0 = (cube.origin().z() - inflate) * PIXEL;
        double x1 = (cube.origin().x() + cube.size().x() + inflate) * PIXEL;
        double y1 = -(cube.origin().y() + cube.size().y() + inflate) * PIXEL;
        double z1 = (cube.origin().z() + cube.size().z() + inflate) * PIXEL;
        emitFace(pose, consumer, cube.faces().get("north"), model, light,
            x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, 0, 0, -1, cube.mirror());
        emitFace(pose, consumer, cube.faces().get("south"), model, light,
            x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, 0, 0, 1, cube.mirror());
        emitFace(pose, consumer, cube.faces().get("west"), model, light,
            x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, -1, 0, 0, cube.mirror());
        emitFace(pose, consumer, cube.faces().get("east"), model, light,
            x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, 1, 0, 0, cube.mirror());
        emitFace(pose, consumer, cube.faces().get("up"), model, light,
            x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, -1, 0, cube.mirror());
        emitFace(pose, consumer, cube.faces().get("down"), model, light,
            x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, 1, 0, cube.mirror());
        pose.popPose();
    }

    private void emitFace(PoseStack pose, VertexConsumer consumer, BedrockFace face,
                          BedrockModel model, int light,
                          double ax, double ay, double az, double bx, double by, double bz,
                          double cx, double cy, double cz, double dx, double dy, double dz,
                          float normalX, float normalY, float normalZ, boolean mirror) {
        if (face == null) return;
        float u0 = (float) (face.u() / model.textureWidth());
        float v0 = (float) (face.v() / model.textureHeight());
        float u1 = (float) ((face.u() + face.width()) / model.textureWidth());
        float v1 = (float) ((face.v() + face.height()) / model.textureHeight());
        if (mirror) { float swap = u0; u0 = u1; u1 = swap; }
        vertex(pose, consumer, ax, ay, az, u1, v0, light, normalX, normalY, normalZ);
        vertex(pose, consumer, bx, by, bz, u0, v0, light, normalX, normalY, normalZ);
        vertex(pose, consumer, cx, cy, cz, u0, v1, light, normalX, normalY, normalZ);
        vertex(pose, consumer, dx, dy, dz, u1, v1, light, normalX, normalY, normalZ);
    }

    private void vertex(PoseStack pose, VertexConsumer consumer, double x, double y, double z,
                        float u, float v, int light, float normalX, float normalY, float normalZ) {
        consumer.addVertex(pose.last(), (float) x, (float) y, (float) z)
            .setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(pose.last(), normalX, normalY, normalZ);
    }

    private void rotate(PoseStack pose, BedrockVec3 rotation) {
        if (rotation.z() != 0) pose.mulPose(Axis.ZP.rotationDegrees((float) rotation.z()));
        if (rotation.y() != 0) pose.mulPose(Axis.YP.rotationDegrees((float) rotation.y()));
        if (rotation.x() != 0) pose.mulPose(Axis.XP.rotationDegrees((float) rotation.x()));
    }
}
