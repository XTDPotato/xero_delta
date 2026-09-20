package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.bedrock.BedrockLoader;
import com.xtdpotato.xero_delta.bedrock.animation.BedrockAnimationController;
import com.xtdpotato.xero_delta.bedrock.model.BedrockBone;
import com.xtdpotato.xero_delta.bedrock.model.BedrockModel;
import com.xtdpotato.xero_delta.bedrock.model.BedrockVec3;
import com.xtdpotato.xero_delta.bedrock.molang.MolangContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class SafetyBoxInspectAnimation {
    public static final double DURATION_SECONDS = 4.5;
    public static final ResourceLocation BOX_TEXTURE = resource("textures/uv/safety_box_3x3_box.png");
    private static final ResourceLocation FIRST_MODEL = resource("models/inspect/safety_box_3x3_firstperson.geo.json");
    private static final ResourceLocation THIRD_MODEL = resource("models/inspect/safety_box_3x3_thirdperson.geo.json");
    private static final ResourceLocation FIRST_ANIMATION = resource("animations/safety_box_3x3_firstperson.animation.json");
    private static final ResourceLocation THIRD_ANIMATION = resource("animations/safety_box_3x3_thirdperson.animation.json");
    private static final String FIRST_NAME = "animation.xero_delta.safety_box_3x3.first_person";
    private static final String THIRD_NAME = "animation.xero_delta.safety_box_3x3.third_person";
    private static final Map<Integer, AnimationState> STATES = new HashMap<>();
    private static Resources resources;

    public record View(BedrockModel model, BedrockAnimationController controller) {
        public void update(double lifeTime, boolean sneaking) {
            controller.apply(model, new Context(lifeTime, sneaking));
        }
    }

    private record AnimationState(long startNanos, View firstPerson, View thirdPerson) {
    }

    private record Resources(BedrockModel firstModel, Map<String, com.xtdpotato.xero_delta.bedrock.animation.BedrockAnimation> firstAnimations,
                             BedrockModel thirdModel, Map<String, com.xtdpotato.xero_delta.bedrock.animation.BedrockAnimation> thirdAnimations) {
    }

    private record Context(double lifeTime, boolean isSneaking) implements MolangContext {
        public double animTime() { return 0; }
    }

    private SafetyBoxInspectAnimation() {
    }

    public static void start(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getEntity(entityId) instanceof AbstractClientPlayer)) return;
        Resources loaded = resources();
        if (loaded == null) return;
        long startNanos = System.nanoTime();
        View first = view(loaded.firstModel(), loaded.firstAnimations(), FIRST_NAME);
        View third = view(loaded.thirdModel(), loaded.thirdAnimations(), THIRD_NAME);
        STATES.put(entityId, new AnimationState(startNanos, first, third));
    }

    public static boolean isPlaying(int entityId) { return state(entityId) != null; }

    public static boolean isLocalPlaying() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && isPlaying(minecraft.player.getId());
    }

    public static boolean cancel(int entityId) {
        return STATES.remove(entityId) != null;
    }

    public static boolean cancelLocal() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && cancel(minecraft.player.getId());
    }

    public static View firstPerson(int entityId) {
        AnimationState state = state(entityId);
        return state == null ? null : state.firstPerson();
    }

    public static View thirdPerson(int entityId) {
        AnimationState state = state(entityId);
        return state == null ? null : state.thirdPerson();
    }

    public static void applyThirdPersonPose(AbstractClientPlayer player, HumanoidModel<?> model) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isPlaying(player.getId())
            || (minecraft.player == player && minecraft.options.getCameraType().isFirstPerson())) return;
        View view = thirdPerson(player.getId());
        if (view == null) return;
        view.update(elapsedSeconds(player.getId()), player.isCrouching());
        // The Bedrock `player` root is the attachment/model basis. It must not be
        // copied onto the vanilla PlayerModel, otherwise its authored -180 degree
        // root turns the visible player front-to-back. Apply only each body bone's
        // own authored bind and animated rotation; the Bedrock item layer keeps the
        // root transform and therefore remains aligned with the authored preview.
        applyRotation(model.body, view, "body");
        applyRotation(model.head, view, "head");
        applyRotation(model.leftArm, view, "left_arm");
        applyRotation(model.rightArm, view, "right_arm");
        applyRotation(model.leftLeg, view, "left_leg");
        applyRotation(model.rightLeg, view, "right_leg");
    }

    public static double elapsedSeconds(int entityId) {
        AnimationState state = state(entityId);
        return state == null ? 0 : elapsedSeconds(state, System.nanoTime());
    }

    public static void tick() {
        long now = System.nanoTime();
        Iterator<AnimationState> iterator = STATES.values().iterator();
        while (iterator.hasNext()) if (elapsedSeconds(iterator.next(), now) >= DURATION_SECONDS) iterator.remove();
    }

    public static void reload() {
        STATES.clear();
        resources = null;
    }

    private static View view(BedrockModel template,
                             Map<String, com.xtdpotato.xero_delta.bedrock.animation.BedrockAnimation> animations,
                             String animationName) {
        BedrockModel model = template.instantiate();
        BedrockAnimationController controller = new BedrockAnimationController(animations);
        controller.play(animationName, BedrockAnimationController.State.CUSTOM, 0);
        return new View(model, controller);
    }

    private static Resources resources() {
        if (resources != null) return resources;
        Minecraft minecraft = Minecraft.getInstance();
        BedrockLoader loader = new BedrockLoader();
        try (Reader firstModel = reader(minecraft, FIRST_MODEL);
             Reader firstAnimation = reader(minecraft, FIRST_ANIMATION);
             Reader thirdModel = reader(minecraft, THIRD_MODEL);
             Reader thirdAnimation = reader(minecraft, THIRD_ANIMATION)) {
            resources = new Resources(loader.loadGeometry(firstModel), loader.loadAnimations(firstAnimation),
                loader.loadGeometry(thirdModel), loader.loadAnimations(thirdAnimation));
            return resources;
        } catch (Exception exception) {
            XeroDelta.LOGGER.error("Failed to load Bedrock inspection runtime", exception);
            return null;
        }
    }

    private static Reader reader(Minecraft minecraft, ResourceLocation location) throws IOException {
        return minecraft.getResourceManager().getResourceOrThrow(location).openAsReader();
    }

    private static AnimationState state(int entityId) {
        AnimationState state = STATES.get(entityId);
        if (state != null && elapsedSeconds(state, System.nanoTime()) >= DURATION_SECONDS) {
            STATES.remove(entityId);
            return null;
        }
        return state;
    }

    private static double elapsedSeconds(AnimationState state, long now) {
        return (now - state.startNanos()) / 1_000_000_000.0;
    }

    private static void applyRotation(ModelPart part, View view, String boneName) {
        BedrockBone bone = view.model().bones().get(boneName);
        if (bone == null) return;
        BedrockVec3 rotation = bone.bindRotation().add(bone.rotation());
        float x = (float)Math.toRadians(rotation.x());
        float y = (float)Math.toRadians(rotation.y());
        float z = (float)Math.toRadians(rotation.z());

        var current = view.controller().current();
        var channel = current == null ? null : current.bones().get(boneName);
        if (channel != null && channel.rotation() != null) {
            // Authored animation channels own the complete arm pose. Do not
            // blend vanilla running, attacking or item-use rotations into it.
            part.xRot = x;
            part.yRot = y;
            part.zRot = z;
        } else {
            part.xRot += x;
            part.yRot += y;
            part.zRot += z;
        }
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, path);
    }
}
