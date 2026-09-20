package com.xtdpotato.xero_delta.bedrock;

import com.google.gson.JsonParser;
import com.xtdpotato.xero_delta.bedrock.animation.BedrockAnimationController;
import com.xtdpotato.xero_delta.bedrock.model.BedrockModel;
import com.xtdpotato.xero_delta.bedrock.molang.MolangContext;
import com.xtdpotato.xero_delta.bedrock.molang.MolangParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BedrockRuntimeTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/xero_delta");

    @Test
    void parsesCurrentGeometryAndBuildsBoneTree() throws IOException {
        BedrockLoader loader = new BedrockLoader();
        try (var reader = Files.newBufferedReader(ASSETS.resolve(
            "models/inspect/safety_box_3x3_firstperson.geo.json"))) {
            BedrockModel model = loader.loadGeometry(reader);
            assertEquals(9, model.bones().size());
            assertEquals("root", model.roots().getFirst().name());
            assertTrue(model.bones().get("root").children().stream()
                .anyMatch(bone -> bone.name().equals("left_arm")));
            assertTrue(model.bones().get("root").children().stream()
                .anyMatch(bone -> bone.name().equals("item")));
            assertTrue(model.bones().get("item").children().stream()
                .anyMatch(bone -> bone.name().equals("safety_box")));
            assertEquals("safety_box", model.bones().get("lid").parentName());
            assertEquals("lid", model.bones().get("grip").parentName());
            assertTrue(model.bones().get("safety_box").cubes().size() > 1000);
            assertEquals(64, model.textureWidth());
            // The high-resolution box atlas is normalized to the player atlas basis.
            assertEquals(248.0 / 32, model.bones().get("safety_box").cubes().getFirst().faces().get("north").width());
            assertEquals(4, model.bones().get("right_arm").cubes().getFirst().faces().get("down").width());
        }
    }

    @Test
    void samplesCurrentAnimationAtKnownKeyframe() throws IOException {
        BedrockLoader loader = new BedrockLoader();
        BedrockModel model;
        try (var reader = Files.newBufferedReader(ASSETS.resolve(
            "models/inspect/safety_box_3x3_firstperson.geo.json"))) {
            model = loader.loadGeometry(reader);
        }
        var animations = loader.loadAnimations(Files.newBufferedReader(ASSETS.resolve(
            "animations/safety_box_3x3_firstperson.animation.json")));
        BedrockAnimationController controller = new BedrockAnimationController(animations);
        controller.play("animation.xero_delta.safety_box_3x3.first_person",
            BedrockAnimationController.State.CUSTOM, 0);
        controller.apply(model, context(1.15));
        assertEquals(18, model.bones().get("safety_box").rotation().x(), 0.001);
        assertEquals(0, model.bones().get("safety_box").position().y(), 0.001);
        assertEquals(0.7, model.bones().get("safety_box").scale().x(), 0.001);
    }

    @Test
    void evaluatesRequiredMolangFunctionsAndQueries() {
        MolangParser parser = new MolangParser();
        MolangContext context = new MolangContext() {
            public double animTime() { return 2; }
            public double lifeTime() { return 5; }
            public boolean isSneaking() { return true; }
        };
        assertEquals(3, parser.parse("query.anim_time + query.is_sneaking").evaluate(context), 0.0001);
        assertEquals(1, parser.parse("math.sin(90)").evaluate(context), 0.0001);
        assertEquals(4, parser.parse("math.clamp(query.life_time, 0, 4)").evaluate(context), 0.0001);
    }

    @Test
    void interpolatesLinearTrack() {
        var parser = new com.xtdpotato.xero_delta.bedrock.animation.BedrockAnimationParser();
        var animations = parser.parse(JsonParser.parseString("""
            {"animations":{"test":{"animation_length":1,"bones":{"root":{"position":{"0":[0,0,0],"1":[10,0,0]}}}}}}
            """).getAsJsonObject());
        BedrockModel model = new BedrockModel("test", 16, 16, java.util.List.of(
            new com.xtdpotato.xero_delta.bedrock.model.BedrockBone("root", null,
                com.xtdpotato.xero_delta.bedrock.model.BedrockVec3.ZERO,
                com.xtdpotato.xero_delta.bedrock.model.BedrockVec3.ZERO, java.util.List.of())));
        BedrockAnimationController controller = new BedrockAnimationController(animations);
        controller.play("test", BedrockAnimationController.State.CUSTOM, 0);
        controller.apply(model, context(0.5));
        assertEquals(5, model.bones().get("root").position().x(), 0.0001);
    }

    @Test
    void parsesCurrentInspectionModels() throws IOException {
        BedrockLoader loader = new BedrockLoader();
        try (var firstReader = Files.newBufferedReader(ASSETS.resolve(
                 "models/inspect/safety_box_3x3_firstperson.geo.json"));
             var thirdReader = Files.newBufferedReader(ASSETS.resolve(
                 "models/inspect/safety_box_3x3_thirdperson.geo.json"))) {
            BedrockModel first = loader.loadGeometry(firstReader);
            BedrockModel third = loader.loadGeometry(thirdReader);
            assertEquals("root", first.bones().get("item").parentName());
            assertTrue(first.bones().get("root").children().stream()
                .anyMatch(bone -> bone.name().equals("item")));
            assertEquals("item", first.bones().get("safety_box").parentName());
            assertEquals("left_arm", third.bones().get("safety_box").parentName());
            assertEquals(9, first.bones().size());
            assertEquals(10, third.bones().size());
            assertTrue(third.bones().keySet().containsAll(java.util.Set.of(
                "player", "body", "head", "left_arm", "right_arm", "left_leg", "right_leg")));
        }
    }

    @Test
    void inspectionKeepsCaseRigidAndFinishesBothViewsAtTheSameTime() throws IOException {
        BedrockLoader loader = new BedrockLoader();
        for (String view : java.util.List.of("firstperson", "thirdperson")) {
            try (var geometry = Files.newBufferedReader(ASSETS.resolve("models/inspect/safety_box_3x3_" + view + ".geo.json"));
                 var animation = Files.newBufferedReader(ASSETS.resolve("animations/safety_box_3x3_" + view + ".animation.json"))) {
                var model = loader.loadGeometry(geometry);
                var animations = loader.loadAnimations(animation);
                var clip = animations.values().iterator().next();
                assertEquals(4.5, clip.length());
                var controller = new BedrockAnimationController(animations);
                controller.play(animations.keySet().iterator().next(), BedrockAnimationController.State.CUSTOM, 0);
                for (int frame = 0; frame <= 270; frame++) {
                    double time = frame / 60.0;
                    controller.apply(model, context(time));
                    var box = model.bones().get("safety_box");
                    assertTrue(Double.isFinite(box.position().x()));
                    assertTrue(Double.isFinite(box.rotation().x()));
                    assertTrue(box.scale().x() >= 0 && box.scale().x() <= 0.700001);
                    if (time >= 0.2 && time <= 4.2) assertEquals(0.7, box.scale().x(), 0.000001);
                }
                if (view.equals("thirdperson")) {
                    assertEquals(0, model.bones().get("left_arm").rotation().x(), 0.000001);
                    assertEquals(0, model.bones().get("right_arm").rotation().x(), 0.000001);
                    assertEquals(0, model.bones().get("safety_box").scale().x(), 0.000001);
                } else {
                    assertTrue(model.bones().get("root").position().y() < -25, "withdraw below the camera before restoring held items");
                }
            }
        }
        assertEquals(-1, Files.mismatch(ASSETS.resolve("textures/uv/safety_box_3x3_box.png"),
            Path.of("docs/models/safety_box_3x3/safety_box_3x3_default2.png")));
    }

    private MolangContext context(double lifeTime) {
        return new MolangContext() {
            public double animTime() { return lifeTime; }
            public double lifeTime() { return lifeTime; }
            public boolean isSneaking() { return false; }
        };
    }
}
