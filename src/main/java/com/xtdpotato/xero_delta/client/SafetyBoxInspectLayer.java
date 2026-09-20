package com.xtdpotato.xero_delta.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xtdpotato.xero_delta.bedrock.render.BedrockRenderTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public final class SafetyBoxInspectLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public SafetyBoxInspectLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!SafetyBoxInspectAnimation.isPlaying(player.getId())
            || (minecraft.player == player && minecraft.options.getCameraType().isFirstPerson())) return;
        SafetyBoxInspectAnimation.View view = SafetyBoxInspectAnimation.thirdPerson(player.getId());
        if (view == null) return;
        view.update(SafetyBoxInspectAnimation.elapsedSeconds(player.getId()), player.isCrouching());
        pose.pushPose();
        // The vanilla arm has already received the Bedrock arm animation through
        // HumanoidModelMixin. Make it the parent matrix for the box subtree too.
        ModelPart arm = getParentModel().leftArm;
        arm.translateAndRotate(pose);
        // The live arm supplies its own shoulder position (including crouching
        // and slim skins). Only remove the AUTHORED shoulder from the absolute
        // Bedrock cube coordinates, otherwise those two origins are mixed.
        var offset = BedrockRenderTransform.attachmentOffset(view.model(), "left_arm");
        pose.translate(offset.x(), offset.y(), offset.z());
        SafetyBoxInspectRenderer.renderThirdPersonItemModel(pose, view.model(), buffers, light);
        pose.popPose();
    }
}
