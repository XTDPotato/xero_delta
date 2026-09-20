package com.xtdpotato.xero_delta.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.bedrock.render.BedrockRenderer;
import com.xtdpotato.xero_delta.bedrock.BedrockLoader;
import com.xtdpotato.xero_delta.bedrock.model.BedrockModel;
import com.xtdpotato.xero_delta.bedrock.model.BedrockVec3;
import com.xtdpotato.xero_delta.data.DownedRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Renders the lootable corpse as an adult prone player with its owner's skin and death equipment. */
public final class CorpseRenderer extends EntityRenderer<CorpseEntity> {
    private static final ResourceLocation LOOT_BOX_MODEL = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "models/loot_box/gti_loot_case.geo.json");
    private static final ResourceLocation LOOT_BOX_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "textures/uv/gti_loot_case.png");
    private static final BedrockRenderer LOOT_BOX_RENDERER = new BedrockRenderer();
    private static BedrockModel lootBoxModel;
    private static final Map<CorpseEntity, Float> lootBoxOpenStarts = new WeakHashMap<>();
    private final CorpseModelSet wideModels;
    private final CorpseModelSet slimModels;
    private final Map<CorpseEntity, CachedAppearance> appearances = new WeakHashMap<>();
    private final Map<CorpseEntity, CachedMob> mobs = new WeakHashMap<>();

    public CorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
        wideModels = new CorpseModelSet(context, false);
        slimModels = new CorpseModelSet(context, true);
        shadowRadius = 0.45F;
    }

    @Override
    public void render(CorpseEntity corpse, float yaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        CarryVisual carry = carryVisual(corpse);
        CarryWorldSmoother.apply(corpse, carry.carrier(), carry.phase(),
            carry.progress(), partialTick, pose);
        if (corpse.isLootBox()) {
            applyCarriedLootBoxPose(corpse, partialTick, carry.progress(), pose);
            renderLootBox(corpse, yaw, partialTick, pose, buffers, light);
            super.render(corpse, yaw, partialTick, pose, buffers, light);
            return;
        }

        float carriedProgress = carry.progress();
        if (carriedProgress > 0.0F) {
            pose.translate(0.0D, 1.32D * carriedProgress, 0.0D);
            pose.mulPose(Axis.ZP.rotationDegrees(-14.0F * carriedProgress));
        }

        if (!corpse.mobTypeId().isBlank()) {
            renderMobCorpse(corpse, minecraft.level, partialTick, pose, buffers, light);
            super.render(corpse, yaw, partialTick, pose, buffers, light);
            return;
        }

        PlayerSkin skin = resolveSkin(corpse);
        CorpseRenderPlayer player = renderPlayer(corpse, minecraft.level, skin);
        copyEquipment(corpse, player);

        CorpseModelSet models = skin.model() == PlayerSkin.Model.SLIM ? slimModels : wideModels;
        PlayerModel<CorpseRenderPlayer> model = models.playerModel;
        model.young = false;
        model.attackTime = 0.0F;
        model.riding = false;
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.setAllVisible(true);
        model.setupAnim(player, 0.0F, 0.0F, player.tickCount + partialTick, 0.0F, 0.0F);

        pose.pushPose();
        // Center the prone model on the entity AABB. The player model spans
        // -8..24 model pixels on its Y axis; after the vanilla 15/16 scale and
        // prone rotation its midpoint is offset by 0.46875 blocks.
        pose.translate(0.0D, 0.04D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - corpse.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.translate(0.0D, 0.93D, 0.0D);
        // Vanilla's adult player renderer uses the same 15/16 scale.
        pose.scale(-0.9375F, -0.9375F, 0.9375F);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture())),
            light, OverlayTexture.NO_OVERLAY);
        models.armorLayer.render(pose, buffers, light, player,
            0.0F, 0.0F, partialTick, player.tickCount + partialTick, 0.0F, 0.0F);
        renderCarrier(corpse, model, corpse.chestRigItem(), true, pose, buffers, light);
        renderCarrier(corpse, model, corpse.backpackItem(), false, pose, buffers, light);
        pose.popPose();

        super.render(corpse, yaw, partialTick, pose, buffers, light);
    }

    private static void applyCarriedLootBoxPose(CorpseEntity corpse, float partialTick,
                                                float progress, PoseStack pose) {
        if (progress <= 0.0F) return;
        float bob = (float) Math.sin((corpse.tickCount + partialTick) * 0.18F) * 0.025F;
        pose.translate(0.0D, (1.48D + bob) * progress, 0.0D);
        pose.mulPose(Axis.XP.rotationDegrees(-18.0F * progress));
        pose.mulPose(Axis.ZP.rotationDegrees(10.0F * progress));
    }

    private static CarryVisual carryVisual(CorpseEntity corpse) {
        net.minecraft.world.entity.player.Player carrier = corpse.carryingPlayer();
        if (carrier == null) {
            return CarryVisual.NONE;
        }
        Minecraft minecraft = Minecraft.getInstance();
        byte phase;
        float ticks;
        if (carrier == minecraft.player) {
            phase = DownedClientState.INSTANCE.carryPhase();
            ticks = DownedClientState.INSTANCE.estimatedCarryTicks();
        } else {
            var state = TeamStatusClientState.INSTANCE.members().stream()
                .filter(entry -> entry.playerId().equals(carrier.getUUID()))
                .findFirst().orElse(null);
            if (state == null) return new CarryVisual((byte) 2, 1.0F, carrier);
            phase = state.carryPhase();
            ticks = TeamStatusClientState.INSTANCE.estimatedCarryTicks(state);
        }
        int actionDuration = phase == 3
            ? DownedRules.CARRY_DROP_TICKS : DownedRules.CARRY_WINDUP_TICKS;
        return new CarryVisual(phase, CarryAnimationMath.progress(phase, ticks,
            actionDuration, DownedRules.CARRY_ANIMATION_TICKS), carrier);
    }

    private record CarryVisual(byte phase, float progress,
                               net.minecraft.world.entity.player.Player carrier) {
        private static final CarryVisual NONE = new CarryVisual((byte) 0, 0.0F, null);
    }

    private static void renderCarrier(CorpseEntity corpse,
                                      PlayerModel<CorpseRenderPlayer> model,
                                      ItemStack stack, boolean chest,
                                      PoseStack pose, MultiBufferSource buffers, int light) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        model.body.translateAndRotate(pose);
        pose.translate(0.0D, 0.37D, chest ? -0.19D : 0.19D);
        if (!chest) pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        float scale = chest ? 0.43F : 0.50F;
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(
            stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
            pose, buffers, corpse.level(), corpse.getId() + (chest ? 0 : 1));
        pose.popPose();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void renderMobCorpse(CorpseEntity corpse, ClientLevel level, float partialTick,
                                 PoseStack pose, MultiBufferSource buffers, int light) {
        Entity mob = mobAppearance(corpse, level);
        if (mob == null) return;
        mob.tickCount = corpse.tickCount;
        mob.setYRot(0.0F);
        mob.setXRot(0.0F);
        mob.setCustomNameVisible(false);
        if (mob instanceof LivingEntity living) {
            // The corpse renderer supplies the prone transform itself. Replaying
            // vanilla death/hurt animation here would rotate and offset it twice.
            living.deathTime = 0;
            living.hurtTime = 0;
            living.yBodyRot = 0.0F;
            living.yBodyRotO = 0.0F;
            living.yHeadRot = 0.0F;
            living.yHeadRotO = 0.0F;
            living.setItemSlot(EquipmentSlot.HEAD, corpse.equipment(EquipmentSlot.HEAD).copy());
            living.setItemSlot(EquipmentSlot.CHEST, corpse.equipment(EquipmentSlot.CHEST).copy());
            living.setItemSlot(EquipmentSlot.LEGS, corpse.equipment(EquipmentSlot.LEGS).copy());
            living.setItemSlot(EquipmentSlot.FEET, corpse.equipment(EquipmentSlot.FEET).copy());
        }

        float entityHeight = Math.max(0.5F, mob.getBbHeight());
        pose.pushPose();
        pose.translate(0.0D, 0.04D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - corpse.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.translate(0.0D, -entityHeight * 0.5D, 0.0D);
        EntityRenderer renderer = entityRenderDispatcher.getRenderer(mob);
        renderer.render(mob, 0.0F, partialTick, pose, buffers, light);
        renderMobCarrier(corpse, corpse.chestRigItem(), true, entityHeight, pose, buffers, light);
        renderMobCarrier(corpse, corpse.backpackItem(), false, entityHeight, pose, buffers, light);
        pose.popPose();
    }

    private static void renderMobCarrier(CorpseEntity corpse, ItemStack stack,
                                         boolean chest, float entityHeight,
                                         PoseStack pose, MultiBufferSource buffers, int light) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        pose.translate(0.0D, entityHeight * 0.42D, chest ? -0.24D : 0.24D);
        if (!chest) pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        float scale = chest ? 0.42F : 0.48F;
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(
            stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
            pose, buffers, corpse.level(), corpse.getId() + (chest ? 2 : 3));
        pose.popPose();
    }

    private Entity mobAppearance(CorpseEntity corpse, ClientLevel level) {
        String typeId = corpse.mobTypeId();
        var snapshot = corpse.mobData();
        CachedMob cached = mobs.get(corpse);
        if (cached != null && cached.level == level && cached.typeId.equals(typeId)
            && cached.snapshot.equals(snapshot)) return cached.entity;

        ResourceLocation parsed = ResourceLocation.tryParse(typeId);
        if (parsed == null) return null;
        Entity entity = BuiltInRegistries.ENTITY_TYPE.getOptional(parsed)
            .map(type -> type.create(level)).orElse(null);
        if (entity == null || entity instanceof CorpseEntity) return null;
        try {
            entity.load(snapshot.copy());
        } catch (RuntimeException error) {
            XeroDelta.LOGGER.warn("Unable to restore corpse appearance for {}", typeId, error);
        }
        entity.setPos(0.0D, 0.0D, 0.0D);
        CachedMob created = new CachedMob(level, typeId, snapshot.copy(), entity);
        mobs.put(corpse, created);
        return entity;
    }

    private static void renderLootBox(CorpseEntity corpse, float yaw, float partialTick, PoseStack pose,
                                      MultiBufferSource buffers, int light) {
        BedrockModel model = lootBoxModel();
        if (model == null) return;
        model.resetPose();
        float openProgress = 0.0F;
        if (corpse.wasOpened()) {
            float now = corpse.tickCount + partialTick;
            float start = lootBoxOpenStarts.computeIfAbsent(corpse, ignored -> now);
            openProgress = Mth.clamp((now - start) / 10.0F, 0.0F, 1.0F);
        } else {
            lootBoxOpenStarts.remove(corpse);
        }
        var lid = model.bones().get("lid");
        if (lid != null) {
            lid.setRotation(new BedrockVec3(-30.0D * openProgress, 0.0D, 0.0D));
        }
        pose.pushPose();
        // Bedrock geometry is authored with its GTI face on north (-Z).
        // Do not apply the corpse renderer's player-facing 180 degree flip:
        // that would expose the back of the case as the front.
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0.0D, 0.56D, 0.0D);
        pose.scale(1.28F, 1.28F, 1.28F);
        LOOT_BOX_RENDERER.renderSubtree(pose, model, "box",
            LOOT_BOX_TEXTURE, buffers, light, bone -> true);
        pose.popPose();
    }

    private static BedrockModel lootBoxModel() {
        if (lootBoxModel != null) return lootBoxModel;
        try (var reader = Minecraft.getInstance().getResourceManager()
            .getResourceOrThrow(LOOT_BOX_MODEL).openAsReader()) {
            lootBoxModel = new BedrockLoader().loadGeometry(reader);
        } catch (Exception error) {
            XeroDelta.LOGGER.error("Failed to load corpse loot-box model", error);
        }
        return lootBoxModel;
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity corpse) {
        return resolveSkin(corpse).texture();
    }

    private PlayerSkin resolveSkin(CorpseEntity corpse) {
        Minecraft minecraft = Minecraft.getInstance();
        UUID ownerId = corpse.ownerId();
        if (ownerId != null && minecraft.player != null
            && ownerId.equals(minecraft.player.getUUID())) {
            return minecraft.player.getSkin();
        }
        return appearance(corpse).skin.get();
    }

    private CorpseRenderPlayer renderPlayer(CorpseEntity corpse, ClientLevel level, PlayerSkin skin) {
        CachedAppearance appearance = appearance(corpse);
        CorpseRenderPlayer player = appearance.renderPlayer;
        if (player == null || player.clientLevel != level) {
            player = new CorpseRenderPlayer(level, appearance.profile, skin);
            appearance.renderPlayer = player;
        }
        player.setSkin(skin);
        return player;
    }

    private CachedAppearance appearance(CorpseEntity corpse) {
        CachedAppearance cached = appearances.get(corpse);
        if (cached != null && cached.matches(corpse)) return cached;

        UUID id = corpse.ownerId() == null ? new UUID(0L, 0L) : corpse.ownerId();
        GameProfile profile = new GameProfile(id, corpse.ownerName());
        if (!corpse.skinValue().isBlank()) {
            Property texture = corpse.skinSignature().isBlank()
                ? new Property("textures", corpse.skinValue())
                : new Property("textures", corpse.skinValue(), corpse.skinSignature());
            profile.getProperties().put("textures", texture);
        }
        Supplier<PlayerSkin> skin = Minecraft.getInstance().getSkinManager().lookupInsecure(profile);
        CachedAppearance created = new CachedAppearance(profile, corpse.skinValue(),
            corpse.skinSignature(), skin, null);
        appearances.put(corpse, created);
        return created;
    }

    private static void copyEquipment(CorpseEntity corpse, CorpseRenderPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, corpse.equipment(EquipmentSlot.HEAD).copy());
        player.setItemSlot(EquipmentSlot.CHEST, corpse.equipment(EquipmentSlot.CHEST).copy());
        player.setItemSlot(EquipmentSlot.LEGS, corpse.equipment(EquipmentSlot.LEGS).copy());
        player.setItemSlot(EquipmentSlot.FEET, corpse.equipment(EquipmentSlot.FEET).copy());
    }

    private static final class CorpseModelSet
        implements RenderLayerParent<CorpseRenderPlayer, PlayerModel<CorpseRenderPlayer>> {
        private final PlayerModel<CorpseRenderPlayer> playerModel;
        private final HumanoidArmorLayer<CorpseRenderPlayer, PlayerModel<CorpseRenderPlayer>,
            HumanoidArmorModel<CorpseRenderPlayer>> armorLayer;

        private CorpseModelSet(EntityRendererProvider.Context context, boolean slim) {
            playerModel = new PlayerModel<>(context.bakeLayer(
                slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
            armorLayer = new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(slim
                    ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(slim
                    ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager());
        }

        @Override
        public PlayerModel<CorpseRenderPlayer> getModel() {
            return playerModel;
        }

        @Override
        public ResourceLocation getTextureLocation(CorpseRenderPlayer player) {
            return player.getSkin().texture();
        }
    }

    private static final class CorpseRenderPlayer extends AbstractClientPlayer {
        private PlayerSkin skin;

        private CorpseRenderPlayer(ClientLevel level, GameProfile profile, PlayerSkin skin) {
            super(level, profile);
            this.skin = skin;
        }

        private void setSkin(PlayerSkin skin) {
            this.skin = skin;
        }

        @Override
        public PlayerSkin getSkin() {
            return skin;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }
    }

    private static final class CachedAppearance {
        private final GameProfile profile;
        private final String skinValue;
        private final String skinSignature;
        private final Supplier<PlayerSkin> skin;
        private CorpseRenderPlayer renderPlayer;

        private CachedAppearance(GameProfile profile, String skinValue, String skinSignature,
                                 Supplier<PlayerSkin> skin, CorpseRenderPlayer renderPlayer) {
            this.profile = profile;
            this.skinValue = skinValue;
            this.skinSignature = skinSignature;
            this.skin = skin;
            this.renderPlayer = renderPlayer;
        }

        private boolean matches(CorpseEntity corpse) {
            UUID id = corpse.ownerId() == null ? new UUID(0L, 0L) : corpse.ownerId();
            return profile.getId().equals(id)
                && skinValue.equals(corpse.skinValue())
                && skinSignature.equals(corpse.skinSignature());
        }
    }

    private record CachedMob(ClientLevel level, String typeId,
                             net.minecraft.nbt.CompoundTag snapshot, Entity entity) {
    }
}
