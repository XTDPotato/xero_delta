package com.xtdpotato.xero_delta.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Draws disabled body parts directly on the health-screen player model. */
public final class HealthInjuryRenderLayer
    extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace(
        "textures/misc/white.png");
    private static final int INJURY_RED_40_PERCENT = 0x66FF0000;

    public HealthInjuryRenderLayer(
        RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light,
                       AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != player
            || !(minecraft.screen instanceof com.xtdpotato.xero_delta.screen.PlayerStatusScreen screen)
            || !screen.isHealthTabOpen()) return;

        PlayerStatusClientState injury = PlayerStatusClientState.INSTANCE;
        boolean wholeBody = injury.wholeBody() >= 100.0F;
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        var consumer = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        renderPart(pose, consumer, light, model.head,
            wholeBody || injury.head() >= 100.0F);
        renderPart(pose, consumer, light, model.hat,
            wholeBody || injury.head() >= 100.0F);
        boolean torso = wholeBody || injury.chest() >= 100.0F
            || injury.abdomen() >= 100.0F;
        renderPart(pose, consumer, light, model.body, torso);
        renderPart(pose, consumer, light, model.jacket, torso);
        renderPart(pose, consumer, light, model.leftArm,
            wholeBody || injury.leftArm() >= 100.0F);
        renderPart(pose, consumer, light, model.leftSleeve,
            wholeBody || injury.leftArm() >= 100.0F);
        renderPart(pose, consumer, light, model.rightArm,
            wholeBody || injury.rightArm() >= 100.0F);
        renderPart(pose, consumer, light, model.rightSleeve,
            wholeBody || injury.rightArm() >= 100.0F);
        renderPart(pose, consumer, light, model.leftLeg,
            wholeBody || injury.leftLeg() >= 100.0F);
        renderPart(pose, consumer, light, model.leftPants,
            wholeBody || injury.leftLeg() >= 100.0F);
        renderPart(pose, consumer, light, model.rightLeg,
            wholeBody || injury.rightLeg() >= 100.0F);
        renderPart(pose, consumer, light, model.rightPants,
            wholeBody || injury.rightLeg() >= 100.0F);
    }

    private static void renderPart(PoseStack pose,
                                   com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                   int light, ModelPart part, boolean injured) {
        if (injured && part.visible && !part.skipDraw) {
            part.render(pose, consumer, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                INJURY_RED_40_PERCENT);
        }
    }
}
