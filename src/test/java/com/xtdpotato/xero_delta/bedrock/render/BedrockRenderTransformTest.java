package com.xtdpotato.xero_delta.bedrock.render;

import com.xtdpotato.xero_delta.bedrock.BedrockLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockRenderTransformTest {
    private static final Path THIRD_MODEL = Path.of(
        "src/main/resources/assets/xero_delta/models/inspect/safety_box_3x3_thirdperson.geo.json");

    @Test
    void firstPersonBasisFacesTheJavaCamera() {
        var offset = BedrockRenderTransform.cameraSpaceOffset();
        var rotation = BedrockRenderTransform.cameraSpaceRotation();

        assertEquals(0.2, offset.x(), 0.0001);
        assertEquals(-0.65, offset.y(), 0.0001);
        assertEquals(-2.5, offset.z(), 0.0001);
        assertEquals(180.0, rotation.x(), 0.0001);
        assertEquals(0.0, rotation.y(), 0.0001);
        assertEquals(0.0, rotation.z(), 0.0001);
    }

    @Test
    void derivesThirdPersonOriginFromItemBranchRoot() throws IOException {
        BedrockLoader loader = new BedrockLoader();
        try (var reader = Files.newBufferedReader(THIRD_MODEL)) {
            var model = loader.loadGeometry(reader);
            double rootY = BedrockRenderTransform.rootPivotY(model, "safety_box");
            assertEquals(24.0, rootY, 0.0001);
            var cube = model.bones().get("safety_box").cubes().getFirst();
            assertTrue(rootY - (cube.origin().y() + cube.size().y()) >= 0,
                "the box must not be placed above the Java model origin");
        }
    }

    @Test
    void authoredShoulderStaysOnTheLiveArmForStandingCrouchingAndSlimSkins() throws IOException {
        try (var reader = Files.newBufferedReader(THIRD_MODEL)) {
            var model = new BedrockLoader().loadGeometry(reader);
            var pivot = model.bones().get("left_arm").pivot();
            var offset = BedrockRenderTransform.attachmentOffset(model, "left_arm");
            for (float y : new float[]{2.0f, 2.5f, 5.2f}) {
                var live = new org.joml.Matrix4f().translate(5 / 16f, y / 16f, 4 / 16f)
                    .rotateZYX(0.2f, -0.3f, -0.7f);
                var attached = new org.joml.Matrix4f(live).translate(
                    (float)offset.x(), (float)offset.y(), (float)offset.z());
                var actual = attached.transformPosition(new org.joml.Vector3f(
                    (float)pivot.x() / 16f, -(float)pivot.y() / 16f, (float)pivot.z() / 16f));
                var expected = live.transformPosition(new org.joml.Vector3f());
                assertEquals(expected.x, actual.x, 0.000001);
                assertEquals(expected.y, actual.y, 0.000001);
                assertEquals(expected.z, actual.z, 0.000001);
            }
        }
    }

    @Test
    void bothWristsFollowTheNewCaseThroughoutTheDisplay() throws IOException {
        var loader = new BedrockLoader();
        try (var reader = Files.newBufferedReader(THIRD_MODEL);
             var animation = Files.newBufferedReader(Path.of(
                 "src/main/resources/assets/xero_delta/animations/safety_box_3x3_thirdperson.animation.json"))) {
            var model = loader.loadGeometry(reader);
            var clips = loader.loadAnimations(animation);
            var controller = new com.xtdpotato.xero_delta.bedrock.animation.BedrockAnimationController(clips);
            controller.play(clips.keySet().iterator().next(),
                com.xtdpotato.xero_delta.bedrock.animation.BedrockAnimationController.State.CUSTOM, 0);
            for (int frame = 12; frame <= 252; frame++) {
                final double time = frame / 60.0;
                controller.apply(model, new com.xtdpotato.xero_delta.bedrock.molang.MolangContext() {
                    public double animTime() { return time; }
                    public double lifeTime() { return time; }
                    public boolean isSneaking() { return false; }
                });
                var left = shoulder(5, model.bones().get("left_arm"));
                var offset = BedrockRenderTransform.attachmentOffset(model, "left_arm");
                var box = new org.joml.Matrix4f(left).translate((float)offset.x(), (float)offset.y(), (float)offset.z());
                var b = model.bones().get("safety_box");
                var p = b.pivot(); var d = b.position(); var r = b.bindRotation().add(b.rotation());
                box.translate((float)d.x()/16, -(float)d.y()/16, (float)d.z()/16)
                    .translate((float)p.x()/16, -(float)p.y()/16, (float)p.z()/16)
                    .rotateZYX((float)Math.toRadians(r.z()), (float)Math.toRadians(r.y()), (float)Math.toRadians(r.x()))
                    .scale((float)b.scale().x(), (float)b.scale().y(), (float)b.scale().z())
                    .translate(-(float)p.x()/16, (float)p.y()/16, -(float)p.z()/16);
                var leftContact = box.transformPosition(new org.joml.Vector3f(-7.3f/16, -12f/16, -9f/16));
                var rightContact = box.transformPosition(new org.joml.Vector3f(7.3f/16, -12f/16, -9f/16));
                var leftWrist = left.transformPosition(new org.joml.Vector3f(0, 10f/16, 0));
                var rightWrist = shoulder(-5, model.bones().get("right_arm"))
                    .transformPosition(new org.joml.Vector3f(0, 10f/16, 0));
                assertTrue(leftContact.distance(leftWrist) < 0.01, "left wrist at " + time);
                assertTrue(rightContact.distance(rightWrist) < 1.5/16, "right wrist at " + time);
            }
        }
    }

    private org.joml.Matrix4f shoulder(float x, com.xtdpotato.xero_delta.bedrock.model.BedrockBone bone) {
        var r = bone.bindRotation().add(bone.rotation());
        return new org.joml.Matrix4f().translate(x/16, 2f/16, 0)
            .rotateZYX((float)Math.toRadians(r.z()), (float)Math.toRadians(r.y()), (float)Math.toRadians(r.x()));
    }
}
