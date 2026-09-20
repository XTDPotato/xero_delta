package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Blockbench-style tactical additions fitted to Minecraft's animated humanoid bones. */
public final class TacticalArmorModel<T extends LivingEntity> extends HumanoidModel<T> {
    public static final ModelLayerLocation HELMET_LAYER = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "tactical_helmet"), "main");
    public static final ModelLayerLocation CHESTPLATE_LAYER = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "tactical_chestplate"), "main");

    public TacticalArmorModel(ModelPart root) {
        super(root);
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HELMET_LAYER, TacticalArmorModel::helmetLayer);
        event.registerLayerDefinition(CHESTPLATE_LAYER, TacticalArmorModel::chestplateLayer);
    }

    private static LayerDefinition helmetLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.24F), 0.0F);
        PartDefinition head = mesh.getRoot().getChild("head");
        head.addOrReplaceChild("shell",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.55F, -8.55F, -4.55F, 9.1F, 5.2F, 9.1F),
            PartPose.ZERO);
        head.addOrReplaceChild("visor",
            CubeListBuilder.create().texOffs(32, 0)
                .addBox(-4.7F, -4.7F, -5.15F, 9.4F, 2.0F, 1.25F),
            PartPose.ZERO);
        head.addOrReplaceChild("left_ear_cover",
            CubeListBuilder.create().texOffs(0, 16)
                .addBox(-5.2F, -5.5F, -2.6F, 1.1F, 3.4F, 5.2F),
            PartPose.ZERO);
        head.addOrReplaceChild("right_ear_cover",
            CubeListBuilder.create().texOffs(12, 16)
                .addBox(4.1F, -5.5F, -2.6F, 1.1F, 3.4F, 5.2F),
            PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition chestplateLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.08F), 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("front_plate",
            CubeListBuilder.create().texOffs(16, 16)
                .addBox(-4.35F, 0.2F, -3.15F, 8.7F, 9.6F, 1.35F),
            PartPose.ZERO);
        body.addOrReplaceChild("left_pouch",
            CubeListBuilder.create().texOffs(0, 16)
                .addBox(-4.45F, 5.7F, -3.85F, 3.7F, 4.2F, 1.7F),
            PartPose.ZERO);
        body.addOrReplaceChild("right_pouch",
            CubeListBuilder.create().texOffs(40, 16)
                .addBox(0.75F, 5.7F, -3.85F, 3.7F, 4.2F, 1.7F),
            PartPose.ZERO);
        root.getChild("right_arm").addOrReplaceChild("right_shoulder_pad",
            CubeListBuilder.create().texOffs(40, 0)
                .addBox(-3.45F, -2.45F, -2.45F, 4.9F, 3.0F, 4.9F),
            PartPose.ZERO);
        root.getChild("left_arm").addOrReplaceChild("left_shoulder_pad",
            CubeListBuilder.create().texOffs(40, 0).mirror()
                .addBox(-1.45F, -2.45F, -2.45F, 4.9F, 3.0F, 4.9F),
            PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }
}
