package com.xtdpotato.xero_delta.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.bedrock.model.BedrockBone;
import com.xtdpotato.xero_delta.bedrock.model.BedrockModel;
import com.xtdpotato.xero_delta.bedrock.render.BedrockRenderFilter;
import com.xtdpotato.xero_delta.bedrock.render.BedrockRenderTransform;
import com.xtdpotato.xero_delta.bedrock.render.BedrockRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(modid = XeroDelta.MOD_ID, value = Dist.CLIENT)
public final class SafetyBoxInspectRenderer {
    private static final BedrockRenderer RENDERER = new BedrockRenderer();
    private static long renderedFrameTimeNanos = Long.MIN_VALUE;

    private SafetyBoxInspectRenderer() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void renderFirstPerson(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.getCameraType().isFirstPerson() || !SafetyBoxInspectAnimation.isLocalPlaying()) return;
        event.setCanceled(true);
        if (minecraft.player == null) return;
        long frameTimeNanos = minecraft.getFrameTimeNs();
        if (renderedFrameTimeNanos == frameTimeNanos) return;
        renderedFrameTimeNanos = frameTimeNanos;
        SafetyBoxInspectAnimation.View view = SafetyBoxInspectAnimation.firstPerson(minecraft.player.getId());
        if (view == null) return;
        view.update(SafetyBoxInspectAnimation.elapsedSeconds(minecraft.player.getId()), minecraft.player.isCrouching());
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            BedrockRenderTransform.applyCameraSpaceBasis(pose);
            renderPlayerModel(pose, view.model(), minecraft.player, event.getMultiBufferSource(), event.getPackedLight());
            renderItemModel(pose, view.model(), event.getMultiBufferSource(), event.getPackedLight());
        } finally {
            pose.popPose();
        }
    }

    private static void renderPlayerModel(PoseStack pose, BedrockModel model, AbstractClientPlayer player,
                                          net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        BedrockRenderFilter playerPart = bone -> !isItemBranch(model, bone);
        RENDERER.render(pose, model, player.getSkin().texture(), buffers, light, playerPart);
    }

    static void renderItemModel(PoseStack pose, BedrockModel model,
                                net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        RENDERER.render(pose, model, SafetyBoxInspectAnimation.BOX_TEXTURE, buffers, light,
            bone -> isItemBranch(model, bone));
    }

    static void renderThirdPersonItemModel(PoseStack pose, BedrockModel model,
                                           net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        // The vanilla arm supplies the attachment transform. Preserve any
        // authored item/group bones between that arm and the safety-box branch.
        RENDERER.renderAttachedSubtree(pose, model, "left_arm", "safety_box",
            SafetyBoxInspectAnimation.BOX_TEXTURE, buffers, light, bone -> true);
    }

    static boolean isItemBranch(BedrockModel model, BedrockBone bone) {
        BedrockBone current = bone;
        while (current != null) {
            if (current.name().equals("safety_box") || current.name().equals("item")) return true;
            current = current.parentName() == null ? null : model.bones().get(current.parentName());
        }
        return false;
    }
}
