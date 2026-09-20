package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.SafetyBoxInspectAnimation;
import com.xtdpotato.xero_delta.client.PlayerStatusClientState;
import com.xtdpotato.xero_delta.client.MedicalUseClientState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void xero$applyInspectionPose(T entity, float limbSwing, float limbSwingAmount,
                                          float ageInTicks, float netHeadYaw, float headPitch,
                                          CallbackInfo callbackInfo) {
        if (entity instanceof AbstractClientPlayer player) {
            HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
            SafetyBoxInspectAnimation.applyThirdPersonPose(player, model);
            if (player == Minecraft.getInstance().player) {
                PlayerStatusClientState state = PlayerStatusClientState.INSTANCE;
                boolean leftBroken = state.leftLeg() >= 100.0F;
                boolean rightBroken = state.rightLeg() >= 100.0F;
                if (leftBroken || rightBroken) {
                    float movement = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
                    float limp = Mth.sin(limbSwing * 0.6662F) * movement * 0.14F;
                    if (leftBroken) model.leftLeg.xRot = model.leftLeg.xRot * 0.35F + 0.28F + limp;
                    if (rightBroken) model.rightLeg.xRot = model.rightLeg.xRot * 0.35F + 0.28F - limp;
                }
                if (MedicalUseClientState.INSTANCE.medicalActive()) {
                    float breathing = Mth.sin(ageInTicks * 0.32F) * 0.06F;
                    model.rightArm.xRot = -1.10F + breathing;
                    model.rightArm.yRot = -0.42F;
                    model.rightArm.zRot = 0.22F;
                    model.leftArm.xRot = -1.02F - breathing;
                    model.leftArm.yRot = 0.42F;
                    model.leftArm.zRot = -0.22F;
                }
            }
        }
    }
}
