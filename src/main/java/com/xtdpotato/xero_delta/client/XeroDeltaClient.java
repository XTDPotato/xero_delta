package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.compat.BetterLootingClientPriority;
import com.xtdpotato.xero_delta.compat.TaczInteractionPriority;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.StaminaRules;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.grid.GridGeometry;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.ModItems;
import com.xtdpotato.xero_delta.item.TimedUseItem;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.network.CarriedRotationPacket;
import com.xtdpotato.xero_delta.network.CarryActionPacket;
import com.xtdpotato.xero_delta.network.DownedActionPacket;
import com.xtdpotato.xero_delta.network.GridActionPacket;
import com.xtdpotato.xero_delta.network.ItemGridConfigPacket;
import com.xtdpotato.xero_delta.network.InspectRequestPacket;
import com.xtdpotato.xero_delta.network.InspectCancelRequestPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.MailActionPacket;
import com.xtdpotato.xero_delta.network.MedicalUseActionPacket;
import com.xtdpotato.xero_delta.network.PlayerLayoutTogglePacket;
import com.xtdpotato.xero_delta.screen.ConfigScreen;
import com.xtdpotato.xero_delta.screen.SafetyBoxConfigScreen;
import com.xtdpotato.xero_delta.screen.SafetyBoxLayoutPack;
import com.xtdpotato.xero_delta.screen.SafetyBoxOverlay;
import com.xtdpotato.xero_delta.client.SafetyBoxOverlayRenderer;
import com.xtdpotato.xero_delta.screen.SafetyBoxScreen;
import com.xtdpotato.xero_delta.screen.TradingMarketScreen;
import com.xtdpotato.xero_delta.screen.RecyclingScreen;
import com.xtdpotato.xero_delta.screen.TradingOperatorScreen;
import com.xtdpotato.xero_delta.screen.PlayerStatusScreen;
import com.xtdpotato.xero_delta.screen.StatusEffectHudConfigScreen;
import com.xtdpotato.xero_delta.screen.CorpseScreen;
import com.xtdpotato.xero_delta.tag.ModTags;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import top.theillusivec4.curios.api.CuriosApi;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

@Mod(value = XeroDelta.MOD_ID, dist = Dist.CLIENT)
public class XeroDeltaClient {

    public static final KeyMapping TOGGLE_KEY = new KeyMapping(
        "key.xero_delta.toggle", GLFW.GLFW_KEY_GRAVE_ACCENT, "key.categories.xero_delta");
    public static final KeyMapping ROTATE_KEY = new KeyMapping(
        "key.xero_delta.rotate", GLFW.GLFW_KEY_T, "key.categories.xero_delta");
    public static final KeyMapping BULLET_DETAILS_KEY = new KeyMapping(
        "key.xero_delta.bullet_details", GLFW.GLFW_KEY_LEFT_CONTROL, "key.categories.xero_delta");
    public static final KeyMapping EFFECT_HUD_CONFIG_KEY = new KeyMapping(
        "key.xero_delta.effect_hud_config", GLFW.GLFW_KEY_APOSTROPHE,
        "key.categories.xero_delta");
    public static final KeyMapping CARRY_KEY = new KeyMapping(
        "key.xero_delta.carry", GLFW.GLFW_KEY_H, "key.categories.xero_delta");
    public static final KeyMapping RESCUE_KEY = new KeyMapping(
        "key.xero_delta.rescue", GLFW.GLFW_KEY_F, "key.categories.xero_delta");
    public static final KeyMapping MEDICAL_WHEEL_KEY = new KeyMapping(
        "key.xero_delta.medical_wheel", GLFW.GLFW_KEY_5, "key.categories.xero_delta");
    public static final KeyMapping COMMAND_WHEEL_KEY = new KeyMapping(
        "key.xero_delta.command_wheel", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.xero_delta");
    public static final KeyMapping SAFETY_BOX_CONFIG_KEY = new KeyMapping(
        "key.xero_delta.safety_box_config", GLFW.GLFW_KEY_BACKSLASH,
        "key.categories.xero_delta");
    public static final KeyMapping TOGGLE_LAYOUT_KEY = new KeyMapping(
        "key.xero_delta.toggle_layout", GLFW.GLFW_KEY_F8, "key.categories.xero_delta");

    public XeroDeltaClient(ModContainer modContainer) {
        SafetyBoxLayoutPack.refreshRuntimePack();
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
            (IConfigScreenFactory) (mod, parent) -> new ConfigScreen(parent));
        modContainer.getEventBus().addListener(this::registerKeys);
        modContainer.getEventBus().addListener(this::onRegisterScreens);
        modContainer.getEventBus().addListener(this::registerTooltipComponents);
        modContainer.getEventBus().addListener(this::registerRuntimePack);
        modContainer.getEventBus().addListener(this::addPlayerLayers);
        modContainer.getEventBus().addListener(this::registerInspectReload);
        modContainer.getEventBus().addListener(this::registerEntityRenderers);
        modContainer.getEventBus().addListener(this::registerTacticalArmorLayers);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(XeroDeltaClient.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(TooltipAutoOffset.class);
    }

    private void registerInspectReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
            manager -> {
                SafetyBoxInspectAnimation.reload();
                WeaponSlotTextureResolver.clear();
            });
    }

    private void registerRuntimePack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(consumer -> {
            PackLocationInfo location = new PackLocationInfo("xero_delta/delta_packs_default",
                Component.literal("Xero Delta Packs Default"), PackSource.BUILT_IN, java.util.Optional.empty());
            Pack pack = Pack.readMetaAndCreate(location,
                BuiltInPackSource.fromName(info ->
                    new SafetyBoxDefaultPackResources(info, SafetyBoxLayoutPack.getDefaultPackDir())),
                PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, true));
            if (pack != null) consumer.accept(pack);
        });
    }

    private void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new SafetyBoxInspectLayer(renderer));
                renderer.addLayer(new HealthInjuryRenderLayer(renderer));
            }
        }
    }

    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.xtdpotato.xero_delta.ModEntities.CORPSE.get(),
            CorpseRenderer::new);
        event.registerEntityRenderer(com.xtdpotato.xero_delta.ModEntities.LOOT_BOX.get(),
            CorpseRenderer::new);
    }

    private void registerTacticalArmorLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        TacticalArmorModel.registerLayers(event);
    }

    private void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(TOGGLE_KEY);
        e.register(ROTATE_KEY); e.register(BULLET_DETAILS_KEY);
        e.register(EFFECT_HUD_CONFIG_KEY);
        e.register(CARRY_KEY);
        e.register(RESCUE_KEY);
        e.register(MEDICAL_WHEEL_KEY);
        e.register(COMMAND_WHEEL_KEY);
        e.register(SAFETY_BOX_CONFIG_KEY);
        e.register(TOGGLE_LAYOUT_KEY);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void onRegisterScreens(RegisterMenuScreensEvent e) {
        e.register(com.xtdpotato.xero_delta.ModMenus.SAFETY_BOX.get(),
            SafetyBoxScreen::new);
        e.register(com.xtdpotato.xero_delta.ModMenus.TRADING_MARKET.get(),
            TradingMarketScreen::new);
        e.register(com.xtdpotato.xero_delta.ModMenus.RECYCLING_STATION.get(),
            RecyclingScreen::new);
        e.register(com.xtdpotato.xero_delta.ModMenus.TRADING_OPERATOR.get(),
            TradingOperatorScreen::new);
        e.register(com.xtdpotato.xero_delta.ModMenus.CORPSE.get(), CorpseScreen::new);
        e.register(com.xtdpotato.xero_delta.ModMenus.GROUND_PACK.get(),
            com.xtdpotato.xero_delta.screen.GroundPackScreen::new);
        e.register(com.xtdpotato.xero_delta.ModMenus.PERSONAL_WAREHOUSE.get(),
            com.xtdpotato.xero_delta.screen.PersonalWarehouseScreen::new);
    }

    private void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent e) {
        e.register(GridPreviewData.class, GridPreviewClientComponent::new);
        e.register(CoinPriceData.class, CoinPriceComponent::new);
        e.register(ItemSizeTooltipData.class, ItemSizeTooltipComponent::new);
        e.register(TooltipTitleData.class, TooltipTitleComponent::new);
    }

    // ==================== Overlay state ====================
    private static boolean overlayActive;
    private static boolean medicalLeftMouseBlocked;
    private static boolean medicalRightMouseBlocked;
    private static String lastMenuKey;
    private static ItemStack lastEquippedBox = ItemStack.EMPTY;
    private static GridBackingStore gridStore;
    private static GridBackingStore nativeGridStore;
    private static final Map<Integer, GridBackingStore> extraGridStores = new HashMap<>();
    private static final Map<Integer, int[]> extraGridBounds = new HashMap<>();
    private static int activeContainerIndex;
    private static int gridW, gridH;
    private static int cachedGridX, cachedGridY;
    private static float cachedGridScale;
    private static GridGeometry cachedGridGeom;
    private static int cachedAvoidanceX, cachedAvoidanceY;
    private static int overlayMinX, overlayMinY, overlayMaxX, overlayMaxY;
    private static int safePanelMinX, safePanelMinY, safePanelMaxX, safePanelMaxY;
    private static int inspectHeaderX, inspectHeaderY, inspectHeaderW, inspectHeaderH;
    private static PlayerStatusPanelRenderer.Layout statusPanelLayout;
    private static boolean statusPanelVisible;
    private static boolean returnOriginKnown;
    private static int returnOriginX, returnOriginY;
    private static int returnOriginContainerIndex;
    private static boolean returnOriginRotated;
    private static ItemStack returnOriginPickedStack = ItemStack.EMPTY;
    private static boolean returnOriginPickupObserved;
    private static final long GRID_DOUBLE_CLICK_WINDOW_MS = 350L;
    private static final double GRID_DOUBLE_CLICK_DISTANCE_SQ = 25.0;
    private static final int OVERLAY_BASE_Z = 300;
    private static long lastGridPickupTime;
    private static int lastGridPickupX = -1, lastGridPickupY = -1;
    private static double lastGridPickupMouseX, lastGridPickupMouseY;
    private static ItemStack lastGridPickupMatcher = ItemStack.EMPTY;
    private static long lastGridActionTime;
    private static int lastGridActionButton = -1, lastGridActionX = -1, lastGridActionY = -1;
    private static boolean gridDragArmed;
    private static boolean gridDragPickedUp;
    private static int gridDragSourceX, gridDragSourceY, gridDragSourceContainer;
    private static double gridDragStartX, gridDragStartY;
    private static ItemStack gridDragStack = ItemStack.EMPTY;
    private static Screen gridDragOwner;
    private static Screen safetyBoxSelectionOwner;
    private static int safetyBoxSelectionX, safetyBoxSelectionY;
    private static int safetyBoxSelectionWidth, safetyBoxSelectionHeight;

    private record ClientPlacement(int x, int y, boolean rotated, GridBackingStore.PlacementStatus status, Set<Integer> blockers) {
        boolean isAccepted() {
            return status != GridBackingStore.PlacementStatus.BLOCKED;
        }
    }

    // ==================== Overlay activate/deactivate ====================

    @SubscribeEvent
    public static void onStandaloneTradingHudLayer(RenderGuiLayerEvent.Pre event) {
        boolean deltaLayout = PlayerStatusClientState.INSTANCE.layoutEnabled();
        boolean downed = DownedClientState.INSTANCE.downed();
        if ((deltaLayout && isDeltaReplacedPlayerLayer(event.getName()))
            || (downed && isDownedHiddenLayer(event.getName()))) {
            event.setCanceled(true);
            return;
        }
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof TradingMarketScreen
            || screen instanceof TradingOperatorScreen
            || screen instanceof RecyclingScreen) {

        }
    }

    // Delta's player-status panel replaces these vanilla layers while its layout is enabled.
    private static boolean isDeltaReplacedPlayerLayer(net.minecraft.resources.ResourceLocation layer) {
        return VanillaGuiLayers.HOTBAR.equals(layer)
            || VanillaGuiLayers.JUMP_METER.equals(layer)
            || VanillaGuiLayers.EXPERIENCE_BAR.equals(layer)
            || VanillaGuiLayers.PLAYER_HEALTH.equals(layer)
            || VanillaGuiLayers.ARMOR_LEVEL.equals(layer)
            || VanillaGuiLayers.FOOD_LEVEL.equals(layer)
            || VanillaGuiLayers.VEHICLE_HEALTH.equals(layer)
            || VanillaGuiLayers.AIR_LEVEL.equals(layer)
            || VanillaGuiLayers.SELECTED_ITEM_NAME.equals(layer)
            || VanillaGuiLayers.EXPERIENCE_LEVEL.equals(layer)
            || VanillaGuiLayers.EFFECTS.equals(layer)
            || VanillaGuiLayers.SPECTATOR_TOOLTIP.equals(layer);
    }

    private static boolean isDownedHiddenLayer(net.minecraft.resources.ResourceLocation layer) {
        return VanillaGuiLayers.HOTBAR.equals(layer)
            || VanillaGuiLayers.ARMOR_LEVEL.equals(layer)
            || VanillaGuiLayers.AIR_LEVEL.equals(layer)
            || VanillaGuiLayers.EXPERIENCE_BAR.equals(layer);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderStatusEffectHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) return;
        renderDeltaHud(event.getGuiGraphics(), minecraft);
    }

    private static void renderDeltaHud(GuiGraphics graphics, Minecraft minecraft) {
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        if (StatusEffectHudState.adaptToScreen(screenWidth, screenHeight)) {
            StatusEffectHudState.save();
        }
        int mouseX = (int) (minecraft.mouseHandler.xpos() * screenWidth
            / minecraft.getWindow().getScreenWidth());
        int mouseY = (int) (minecraft.mouseHandler.ypos() * screenHeight
            / minecraft.getWindow().getScreenHeight());
        // Better Looting and ChatScreen can leave a strict scissor active.
        // Always restore an unclipped HUD state before and after Delta's layers.
        RenderSystem.disableScissor();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().pushPose();
        try {
            renderHealthVisionEffects(graphics, minecraft, screenWidth, screenHeight);
            boolean downed = DownedClientState.INSTANCE.downed();
            if (PlayerStatusClientState.INSTANCE.layoutEnabled()) {
                PlayerHudRenderer.render(graphics, minecraft);
                if (!downed) StaminaHudRenderer.render(graphics, minecraft);
            }
            if (downed) {
                DownedHudRenderer.render(graphics, minecraft);
            } else {
                StatusEffectHudRenderer.render(graphics, minecraft, mouseX, mouseY);
                TeamLocationHudRenderer.render(graphics, minecraft);
                DamageDirectionHudRenderer.render(graphics, minecraft);
            }
            RescueProgressHudRenderer.render(graphics, minecraft);
            CombatFeedHudRenderer.render(graphics, minecraft);
            ContextInteractionHudRenderer.render(graphics, minecraft);
            MedicalWheelClient.render(graphics, minecraft);
            CommandWheelClient.render(graphics, minecraft);
            XeroTitleOverlay.render(graphics, minecraft);
        } finally {
            graphics.pose().popPose();
            RenderSystem.disableScissor();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void renderHealthVisionEffects(GuiGraphics graphics, Minecraft minecraft,
                                                  int screenWidth, int screenHeight) {
        if (minecraft.player == null) return;
        float maximum = Math.max(1.0F, minecraft.player.getMaxHealth());
        float fraction = Math.max(0.0F, Math.min(1.0F,
            minecraft.player.getHealth() / maximum));
        float severity = fraction < 0.50F ? (0.50F - fraction) / 0.50F : 0.0F;
        if (DownedClientState.INSTANCE.downed()) severity = Math.max(severity, 0.85F);
        if (severity <= 0.0F) return;

        int depth = Math.max(10, Math.min(44, Math.min(screenWidth, screenHeight) / 8));
        int peakAlpha = Math.round(150.0F * severity);
        for (int inset = 0; inset < depth; inset++) {
            float progress = 1.0F - inset / (float) depth;
            int alpha = Math.max(1, Math.round(peakAlpha * progress * progress));
            int color = alpha << 24;
            int left = inset;
            int top = inset;
            int right = screenWidth - inset;
            int bottom = screenHeight - inset;
            if (right <= left || bottom <= top) break;
            graphics.fill(left, top, right, top + 1, color);
            graphics.fill(left, bottom - 1, right, bottom, color);
            graphics.fill(left, top + 1, left + 1, bottom - 1, color);
            graphics.fill(right - 1, top + 1, right, bottom - 1, color);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onXeroTitleScreenRenderPost(ScreenEvent.Render.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getScreen() instanceof ChatScreen && minecraft.player != null
            && !minecraft.options.hideGui) {
            renderDeltaHud(event.getGuiGraphics(), minecraft);
            return;
        }
        if (event.getScreen() instanceof PlayerStatusScreen statusScreen) {
            statusScreen.renderTopmostTooltips(event.getGuiGraphics(),
                event.getMouseX(), event.getMouseY());
        } else if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            DeltaContainerLayoutController.renderTooltipTopmost(containerScreen,
                event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
        var graphics = event.getGuiGraphics();
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 9500);
        XeroTitleOverlay.render(graphics, minecraft);
        graphics.flush();
        graphics.pose().popPose();
    }

    @SubscribeEvent
    public static void onNonContainerScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>) && overlayActive) {
            deactivate();
        }
    }

    @SubscribeEvent
    public static void onContainerRenderForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        GuiGraphics graphics = event.getGuiGraphics();
        var pose = graphics.pose();
        pose.pushPose();
        // ContainerScreenEvent.Foreground is posted while the vanilla pose is
        // translated by guiLeft/guiTop. XeroDelta overlay coordinates are
        // absolute, so cancel only that native translation. PlayerStatusScreen
        // additionally has an adaptive viewport; cancel that transform first.
        if (screen instanceof PlayerStatusScreen statusScreen) {
            pose.translate(-screen.getGuiLeft(), -screen.getGuiTop(), 0.0F);
            statusScreen.applyPhysicalOverlayTransform(graphics);
        } else {
            pose.translate(-screen.getGuiLeft(), -screen.getGuiTop(), 0.0F);
        }
        try {
            renderContainerOverlay(graphics, Minecraft.getInstance(), screen);
        } finally {
            pose.popPose();
        }
    }

    private static void renderContainerOverlay(GuiGraphics graphics, Minecraft mc,
                                               AbstractContainerScreen<?> cs) {
        statusPanelVisible = false;
        statusPanelLayout = null;
        if (cs instanceof TradingMarketScreen
            || cs instanceof TradingOperatorScreen
            || cs instanceof RecyclingScreen) {
            if (overlayActive) deactivate();
            return;
        }
        if (mc.player == null) return;
        ClientGridRotation.update(mc);

        // Container tabs only replace the embedded player surface. The native
        // container remains open on the right, while its health tab must not
        // leak the character tab's safety-box overlay.
        if (DeltaContainerLayoutController.isEmbedded(cs)
            && !DeltaContainerLayoutController.showsSafetyBox(cs)) {
            if (overlayActive) deactivate();
            renderIndependentMailButton(graphics, mc, cs);
            return;
        }

        // The Delta health tab embeds the same player inventory column as the
        // character tab, including its safety-box grid. Only the vanilla health
        // surface omits that embedded grid.
        if (cs instanceof PlayerStatusScreen statusScreen
            && statusScreen.isHealthTabOpen()
            && !statusScreen.hasEmbeddedDeltaInventory()) {
            if (overlayActive) deactivate();
            renderIndependentMailButton(graphics, mc, cs);
            return;
        }

        ItemStack boxStack = findEquipped(mc.player);
        String key = SafetyBoxOverlay.resolveMenuKey(cs.getMenu());

        boolean embeddedDeltaGrid = DeltaContainerLayoutController.isEmbedded(cs)
            || cs instanceof PlayerStatusScreen;
        if (boxStack.isEmpty() || !(boxStack.getItem() instanceof SafetyBoxItem sbi)
            || (!embeddedDeltaGrid && !SafetyBoxOverlay.shouldRender(cs.getMenu()))) {
            if (overlayActive) deactivate();
            renderIndependentMailButton(graphics, mc, cs);
            return;
        }

        int gw = sbi.getGridWidth(), gh = sbi.getGridHeight();
        if (overlayActive) {
            // A click on a custom grid temporarily switches the interaction context;
            // every render starts from the native safety-box grid again.
            gridStore = nativeGridStore;
            activeContainerIndex = 0;
            gridW = gw;
            gridH = gh;
        }

        // A grid sync replaces the equipped ItemStack instance. Preserve the
        // active lock pulse when that replacement is still the same box type.
        boolean boxTypeChanged = overlayActive
            && (lastEquippedBox.isEmpty() || !lastEquippedBox.is(boxStack.getItem()));
        if (!overlayActive || boxTypeChanged || !key.equals(lastMenuKey)) {
            if (overlayActive) deactivate();
            SafetyBoxLockAnimation.reset();
            gridStore = new GridBackingStore(boxStack, gw, gh);
            nativeGridStore = gridStore;
            extraGridStores.clear();
            extraGridBounds.clear();
            SafetyBoxLayoutPack.LayoutData layoutData = SafetyBoxLayoutPack.getLayoutForBox(
                boxStack.getItemHolder().getKey().location().toString());
            for (SafetyBoxLayoutPack.WidgetNode node : SafetyBoxLayoutPack.flattenWidgets(layoutData)) {
                if ("grid".equals(node.type) && node.containerIndex > 0 && !extraGridStores.containsKey(node.containerIndex)) {
                    var container = layoutData.containers.get(Math.min(node.containerIndex, layoutData.containers.size() - 1));
                    extraGridStores.put(node.containerIndex, new GridBackingStore(boxStack, container.columns, container.rows, node.containerIndex));
                }
            }
            gridW = gw; gridH = gh;
            overlayActive = true;
            lastEquippedBox = boxStack;
            lastMenuKey = key;
        } else if (lastEquippedBox != boxStack) {
            if (!SafetyBoxLockAnimation.hasActiveAnimations()) SafetyBoxLockAnimation.reset();
            lastEquippedBox = boxStack;
            if (nativeGridStore != null) nativeGridStore.reload(boxStack, gw, gh);
            for (GridBackingStore store : extraGridStores.values()) {
                store.reload(boxStack, store.getWidth(), store.getHeight());
            }
            gridStore = nativeGridStore;
        }

        // Compute layout coordinates
        SafetyBoxLayoutPack.LayoutData ld = SafetyBoxLayoutPack.getLayoutForBox(
            boxStack.getItemHolder().getKey().location().toString());
        boolean embeddedStatusGrid = cs instanceof PlayerStatusScreen
            && !DeltaContainerLayoutController.isEmbedded(cs);
        int[] off = embeddedDeltaGrid ? new int[]{0, 0} : SafetyBoxOverlay.getScreenOffset(key);
        int ox = off[0], oy = off[1];
        int centerX = ld.centerX, centerY = ld.centerY;
        int gox = ld.globalOffX, goy = ld.globalOffY;

        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int left = cs.getGuiLeft(), top = cs.getGuiTop();
        int imgW = cs.getXSize(), imgH = cs.getYSize();
        int anchorX;
        int anchorY;
        if (embeddedDeltaGrid) {
            if (embeddedStatusGrid) {
                PlayerStatusScreen statusScreen = (PlayerStatusScreen) cs;
                anchorX = statusScreen.safetyBoxGridX();
                anchorY = statusScreen.safetyBoxGridY();
            } else {
                anchorX = DeltaContainerLayoutController.safetyBoxGridX(cs);
                anchorY = DeltaContainerLayoutController.safetyBoxGridY(cs);
            }
        } else {
            anchorX = centerX >= 0 ? (int)(sw * centerX / 100.0) : left + imgW + ox + gox;
            anchorY = centerY >= 0 ? (int)(sh * centerY / 100.0) : top + oy + goy;
            anchorX += gox;
            anchorY += goy;
        }

        Config.Layout layout = Config.Layout.valueOf(ld.layout);
        int borderP = ld.borderPad, boffX = ld.borderOffX, boffY = ld.borderOffY;
        String title = (ld.customText != null && !ld.customText.isEmpty()) ? ld.customText : boxStack.getHoverName().getString();

        float embeddedScale = embeddedStatusGrid
            ? ((PlayerStatusScreen) cs).safetyBoxGridScale()
            : embeddedDeltaGrid
                ? DeltaContainerLayoutController.safetyBoxGridScale(cs) : 1.0F;
        float resolvedGridScale = embeddedDeltaGrid ? 1.0F : (float) ld.gridScale;
        GridGeometry baseGeom = new GridGeometry(gw, gh, 0, 0, embeddedScale, resolvedGridScale);
        var header = embeddedDeltaGrid ? null
            : SafetyBoxOverlayRenderer.computeHeaderSize(mc.font, baseGeom, ld, 1.0f, title);
        int headerW = header == null ? 0 : header.width();
        int headerH = header == null ? 0 : header.height();
        int scaledGW = baseGeom.pixelWidth(), scaledGH = baseGeom.pixelHeight();
        int gx, gy, hx, hy;
        if (embeddedDeltaGrid) {
            gx = anchorX;
            gy = anchorY;
            hx = gx;
            hy = gy;
        } else {
            switch (layout) {
                case LEFT -> { hx = anchorX + boffX; hy = anchorY + (scaledGH - headerH) / 2 + boffY; gx = anchorX + headerW + 4 + ld.gridOffX; gy = anchorY + ld.gridOffY; }
                case RIGHT -> { gx = anchorX + ld.gridOffX; gy = anchorY + ld.gridOffY; hx = anchorX + scaledGW + 4 + boffX; hy = anchorY + (scaledGH - headerH) / 2 + boffY; }
                case BOTTOM -> { gx = anchorX + ld.gridOffX; gy = anchorY + ld.gridOffY; hx = anchorX + (scaledGW - headerW) / 2 + boffX; hy = anchorY + scaledGH + 4 + boffY; }
                default -> { hx = anchorX + (scaledGW - headerW) / 2 + boffX; hy = anchorY + boffY; gx = anchorX + ld.gridOffX; gy = anchorY + headerH + 4 + ld.gridOffY; }
            }
        }

        int overlayX1 = Math.min(gx - Math.max(0, ld.panelPadLeft), hx);
        int overlayY1 = Math.min(gy - Math.max(0, ld.panelPadTop), hy);
        int overlayX2 = Math.max(gx + scaledGW + Math.max(0, ld.panelPadRight), hx + headerW);
        int overlayY2 = Math.max(gy + scaledGH + Math.max(0, ld.panelPadBottom), hy + headerH);
        if (embeddedDeltaGrid || SafetyBoxOverlay.hasManualPosition(key)) {
            cachedAvoidanceX = 0;
            cachedAvoidanceY = 0;
        } else {
            int[] avoidance = RecipeOverlayAvoidance.findShift(cs,
                new RecipeOverlayAvoidance.Bounds(overlayX1, overlayY1, overlayX2 - overlayX1, overlayY2 - overlayY1),
                new RecipeOverlayAvoidance.Bounds(left, top, imgW, imgH), sw, sh);
            cachedAvoidanceX = avoidance[0];
            cachedAvoidanceY = avoidance[1];
        }
        gx += cachedAvoidanceX;
        gy += cachedAvoidanceY;

        cachedGridX = gx; cachedGridY = gy; cachedGridScale = resolvedGridScale;
        GridGeometry geom = embeddedDeltaGrid
            ? GridGeometry.exactCellSize(gw, gh, gx, gy,
                embeddedStatusGrid
                    ? Math.max(1, Math.round(GridGeometry.CELL_SIZE * embeddedScale))
                    : DeltaContainerLayoutController.safetyBoxCellSize(cs))
            : new GridGeometry(gw, gh, gx, gy, embeddedScale, resolvedGridScale);
        cachedGridGeom = geom;

        boolean statusUsesDeltaClip = !(cs instanceof PlayerStatusScreen statusScreen)
            || statusScreen.hasEmbeddedDeltaInventory();
        boolean embeddedClipEnabled = embeddedDeltaGrid && statusUsesDeltaClip;
        if (embeddedClipEnabled) {
            if (embeddedStatusGrid) {
                PlayerStatusScreen statusScreen = (PlayerStatusScreen) cs;
                graphics.enableScissor(statusScreen.embeddedLayoutClipLeft(),
                    statusScreen.embeddedLayoutClipTop(), statusScreen.embeddedLayoutClipRight(),
                    statusScreen.embeddedLayoutClipBottom());
            } else {
                graphics.enableScissor(
                    DeltaContainerLayoutController.embeddedClipLeft(cs),
                    DeltaContainerLayoutController.embeddedClipTop(cs),
                    DeltaContainerLayoutController.embeddedClipRight(cs),
                    DeltaContainerLayoutController.embeddedClipBottom(cs));
            }
        }
        // The shared layout owns safety-box rendering for every Delta host.
        // Only the non-Delta status view still needs the foreground grid.
        if (embeddedDeltaGrid && !embeddedStatusGrid) {
            cachedGridGeom = geom;
            cachedGridX = gx;
            cachedGridY = gy;
            cachedGridScale = resolvedGridScale;
            if (embeddedStatusGrid) {
                PlayerStatusScreen statusScreen = (PlayerStatusScreen) cs;
                inspectHeaderX = statusScreen.safetyBoxInspectX();
                inspectHeaderY = statusScreen.safetyBoxInspectY();
                inspectHeaderW = statusScreen.safetyBoxInspectWidth();
                inspectHeaderH = statusScreen.safetyBoxInspectHeight();
            } else {
                inspectHeaderX = DeltaContainerLayoutController.safetyBoxInspectX(cs);
                inspectHeaderY = DeltaContainerLayoutController.safetyBoxInspectY(cs);
                inspectHeaderW = DeltaContainerLayoutController.safetyBoxInspectWidth(cs);
                inspectHeaderH = DeltaContainerLayoutController.safetyBoxInspectHeight(cs);
            }
            extraGridBounds.clear();
            if (embeddedClipEnabled) graphics.disableScissor();
            renderIndependentMailButton(graphics, mc, cs);
            return;
        }

        var overlayPose = graphics.pose();
        overlayPose.pushPose();
        // Embedded safety-box chrome belongs to the inventory surface. Keeping its
        // base Z low lets item models and tooltips remain above it.
        overlayPose.translate(0, 0,
            embeddedDeltaGrid ? OVERLAY_BASE_Z + 20 : OVERLAY_BASE_Z);
        try {
            if (embeddedDeltaGrid) renderEmbeddedGridPanel(graphics, geom);
            else renderGridPanel(graphics, geom, ld, hx, hy, headerW, headerH);
            renderGridSlots(graphics, geom);
            renderGridItems(graphics, mc, geom, cs,
                embeddedStatusGrid ? (PlayerStatusScreen) cs : null);
            renderSafetyBoxSelection(graphics, cs);
            if (geom.isSplit()) {
                int sepX = geom.separatorX();
                graphics.fill(sepX, geom.gridY(), sepX + 1, geom.gridY() + geom.pixelHeight(), 0xFF555555);
            }

            double mx = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
            int[] hoverCell = getCellAt(mx, my);
            if (hoverCell != null) {
                ItemStack carried = mc.player.containerMenu.getCarried();
                ItemStack hoveredItem = getStackAtIncludingFootprintClient(hoverCell[0], hoverCell[1]);
                if (!carried.isEmpty() && !embeddedDeltaGrid) {
                    renderPlacementPreview(graphics, geom, hoverCell[0], hoverCell[1], carried, mx, my);
                } else if (carried.isEmpty() && !gridDragPickedUp) {
                    renderGridHover(graphics, hoverCell[0], hoverCell[1]);
                    if (!hoveredItem.isEmpty() && !embeddedDeltaGrid
                        && !ItemDetailOverlay.isOpen(cs)) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(0.0F, 0.0F, 2800.0F);
                        graphics.renderTooltip(mc.font, hoveredItem, (int)mx, (int)my);
                        graphics.pose().popPose();
                    }
                }
            }

            if (embeddedDeltaGrid) {
                overlayMinX = safePanelMinX;
                overlayMinY = safePanelMinY;
                overlayMaxX = safePanelMaxX;
                overlayMaxY = safePanelMaxY;
                if (embeddedStatusGrid) {
                    PlayerStatusScreen statusScreen = (PlayerStatusScreen) cs;
                    inspectHeaderX = statusScreen.safetyBoxInspectX();
                    inspectHeaderY = statusScreen.safetyBoxInspectY();
                    inspectHeaderW = statusScreen.safetyBoxInspectWidth();
                    inspectHeaderH = statusScreen.safetyBoxInspectHeight();
                } else {
                    inspectHeaderX = DeltaContainerLayoutController.safetyBoxInspectX(cs);
                    inspectHeaderY = DeltaContainerLayoutController.safetyBoxInspectY(cs);
                    inspectHeaderW = DeltaContainerLayoutController.safetyBoxInspectWidth(cs);
                    inspectHeaderH = DeltaContainerLayoutController.safetyBoxInspectHeight(cs);
                }
                extraGridBounds.clear();
            } else {
                var hr = SafetyBoxOverlayRenderer.renderHeader(graphics, mc.font, geom, ld, 1.0f, boxStack, title);
                updateOverlayBounds(geom, hr);
                extraGridBounds.clear();
                SafetyBoxWidgetRenderer.Bounds widgetBounds = SafetyBoxWidgetRenderer.render(
                    graphics, mc.font, ld, overlayMinX, overlayMinY,
                    Math.max(1, overlayMaxX - overlayMinX), Math.max(1, overlayMaxY - overlayMinY), boxStack, title,
                    (cellGraphics, node, x, y, width, height) -> {
                        extraGridBounds.put(node.containerIndex, new int[]{x, y, width, height});
                        renderCustomGridItems(cellGraphics, mc, node, width, height);
                    });
                renderCustomGridPlacementPreview(graphics, mc);
                overlayMinX = Math.min(overlayMinX, widgetBounds.minX());
                overlayMinY = Math.min(overlayMinY, widgetBounds.minY());
                overlayMaxX = Math.max(overlayMaxX, widgetBounds.maxX());
                overlayMaxY = Math.max(overlayMaxY, widgetBounds.maxY());
                inspectHeaderX = hr.headerX();
                inspectHeaderY = hr.headerY();
                inspectHeaderW = hr.headerW();
                inspectHeaderH = hr.headerH();

                if (isInsideInspectHeader(mx, my)) {
                    var hintPose = graphics.pose();
                    hintPose.pushPose();
                    hintPose.translate(0, 0, 2800);
                    Component hint = boxStack.is(ModItems.SAFETY_BOX_3X3.get())
                        ? Component.translatable("overlay.xero_delta.drag_inspect_hint",
                            Component.translatable("key.mouse.right"))
                        : Component.translatable("overlay.xero_delta.drag_hint");
                    graphics.renderTooltip(mc.font, hint, (int)mx, (int)my);
                    hintPose.popPose();
                }
            }
        } finally {
            overlayPose.popPose();
            if (embeddedClipEnabled) graphics.disableScissor();
        }
        if (!embeddedDeltaGrid) {
            int detailMouseX = (int) (mc.mouseHandler.xpos()
                * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth());
            int detailMouseY = (int) (mc.mouseHandler.ypos()
                * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
            ItemDetailOverlay.render(cs, graphics, detailMouseX, detailMouseY);
            ItemDetailOverlay.renderModalTopmost(cs, graphics, detailMouseX, detailMouseY);
        }
        renderIndependentMailButton(graphics, mc, cs);
    }

    private static void renderIndependentMailButton(GuiGraphics graphics, Minecraft mc,
                                                    AbstractContainerScreen<?> screen) {
        CreativeItemRuleEditor.setPanelRendered(false);
        if (screen instanceof PlayerStatusScreen) return;
        if (PlayerStatusClientState.INSTANCE.layoutEnabled()
            && !(screen instanceof CreativeModeInventoryScreen)) {
            statusPanelLayout = null;
            statusPanelVisible = false;
            statusPanelDragging = false;
            return;
        }
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        boolean collapsed = PlayerStatusPanelState.isCollapsed();
        boolean creativeEditor = !collapsed && screen instanceof CreativeModeInventoryScreen
            && CreativeItemRuleEditor.isVisible(screen);
        int panelWidth = collapsed ? PlayerStatusPanelRenderer.COLLAPSED_WIDTH
            : creativeEditor ? PlayerStatusPanelRenderer.COMPACT_WIDTH : PlayerStatusPanelRenderer.WIDTH;
        int panelHeight = collapsed ? PlayerStatusPanelRenderer.COLLAPSED_HEIGHT
            : creativeEditor ? PlayerStatusPanelRenderer.COMPACT_HEIGHT : PlayerStatusPanelRenderer.HEIGHT;
        int creativeHeight = creativeEditor
            ? Math.max(CreativeRuleEditorLayout.MIN_HEIGHT, Math.min(CreativeItemRuleEditor.HEIGHT,
                screenHeight - panelHeight - 12)) : 0;
        if (creativeEditor) CreativeItemRuleEditor.setVisibleHeight(creativeHeight);
        int totalWidth = creativeEditor ? Math.max(panelWidth, CreativeItemRuleEditor.WIDTH) : panelWidth;
        int totalHeight = creativeEditor
            ? panelHeight + 4 + creativeHeight : panelHeight;
        int defaultX = screen.getGuiLeft() - totalWidth - 4;
        int defaultY = screen.getGuiTop();
        if (defaultX < 4) {
            defaultX = Math.max(4, Math.min(screenWidth - panelWidth - 4,
                screen.getGuiLeft()));
            defaultY = Math.max(4, screen.getGuiTop() - panelHeight - 4);
        }
        int[] position = PlayerStatusPanelState.position(defaultX, defaultY, screenWidth, screenHeight,
            totalWidth, totalHeight);
        double mouseX = mc.mouseHandler.xpos() * screenWidth / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * screenHeight / mc.getWindow().getScreenHeight();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, OVERLAY_BASE_Z + 500);
        statusPanelLayout = creativeEditor
            ? PlayerStatusPanelRenderer.renderCompact(graphics, mc.font,
                position[0], position[1], (int) mouseX, (int) mouseY)
            : PlayerStatusPanelRenderer.render(graphics, mc.font,
                position[0], position[1], (int) mouseX, (int) mouseY, collapsed);
        if (creativeEditor) {
            CreativeItemRuleEditor.render((CreativeModeInventoryScreen) screen, graphics,
                position[0], position[1] + panelHeight + 4,
                (int) mouseX, (int) mouseY);
        }
        pose.popPose();
        statusPanelVisible = true;
    }

    private static void renderGridSlots(GuiGraphics g, GridGeometry geom) {
        DeltaGridCellRenderer.renderGrid(g, geom);
    }

    private static void renderGridHover(GuiGraphics graphics, int column, int row) {
        if (cachedGridGeom == null || gridStore == null) return;
        int anchor = findAnchorIndexAtClient(column, row);
        int anchorX = anchor < 0 ? column : anchor % gridW;
        int anchorY = anchor < 0 ? row : anchor / gridW;
        int widthCells = 1;
        int heightCells = 1;
        if (anchor >= 0) {
            ItemStack stack = gridStore.getItemRaw(anchorX, anchorY);
            if (!stack.isEmpty()) {
                ItemSize size = clientOrientedSize(stack, GridBackingStore.isRotated(stack));
                widthCells = size.width();
                heightCells = size.height();
            }
        }
        int endColumn = Math.min(cachedGridGeom.cols() - 1,
            anchorX + widthCells - 1);
        int endRow = Math.min(cachedGridGeom.rows() - 1,
            anchorY + heightCells - 1);
        int x = cachedGridGeom.cellX(anchorX);
        int y = cachedGridGeom.cellY(anchorY);
        int width = cachedGridGeom.cellX(endColumn)
            + cachedGridGeom.cellSize() - x;
        int height = cachedGridGeom.cellY(endRow)
            + cachedGridGeom.cellSize() - y;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 260.0F);
        DeltaGridCellRenderer.renderHover(graphics, x, y, width, height);
        graphics.pose().popPose();
    }

    private static void renderEmbeddedGridPanel(GuiGraphics g, GridGeometry geom) {
        safePanelMinX = geom.gridX() - 2;
        safePanelMinY = geom.gridY() - 2;
        safePanelMaxX = geom.gridX() + geom.pixelWidth() + 2;
        safePanelMaxY = geom.gridY() + geom.pixelHeight() + 2;
        g.fill(safePanelMinX, safePanelMinY, safePanelMaxX, safePanelMaxY, 0xCC10181B);
        g.renderOutline(safePanelMinX, safePanelMinY,
            safePanelMaxX - safePanelMinX, safePanelMaxY - safePanelMinY, 0xFF52666C);
    }

    private static void renderGridPanel(GuiGraphics g, GridGeometry geom, SafetyBoxLayoutPack.LayoutData ld,
                                        int headerX, int headerY, int headerWidth, int headerHeight) {
        updateSafePanelBounds(geom, ld, headerX, headerY, headerWidth, headerHeight);
        int alpha = clamp(ld.panelAlpha, 0, 100) * 255 / 100;
        if (alpha <= 0) return;
        g.fill(safePanelMinX, safePanelMinY, safePanelMaxX, safePanelMaxY, argb(alpha, 0x10, 0x10, 0x10));
        int borderColor = argb(alpha, 0x37, 0x37, 0x37);
        g.fill(safePanelMinX, safePanelMinY, safePanelMaxX, safePanelMinY + 1, borderColor);
        g.fill(safePanelMinX, safePanelMaxY - 1, safePanelMaxX, safePanelMaxY, borderColor);
        g.fill(safePanelMinX, safePanelMinY, safePanelMinX + 1, safePanelMaxY, borderColor);
        g.fill(safePanelMaxX - 1, safePanelMinY, safePanelMaxX, safePanelMaxY, borderColor);
    }

    private static void renderGridItems(GuiGraphics g, Minecraft mc, GridGeometry geom,
                                        AbstractContainerScreen<?> screen,
                                        PlayerStatusScreen statusScreen) {
        if (gridStore == null) return;
        for (int cy = 0; cy < geom.rows(); cy++)
            for (int cx = 0; cx < geom.cols(); cx++) {
                ItemStack stack = gridStore.getItemRaw(cx, cy);
                if (stack.isEmpty()) continue;
                boolean rotated = GridBackingStore.isRotated(stack);
                ItemSize size = clientOrientedSize(stack, rotated);
                int x1 = geom.cellX(cx);
                int y1 = geom.cellY(cy);
                int endCx = Math.min(geom.cols() - 1, cx + size.width() - 1);
                int endCy = Math.min(geom.rows() - 1, cy + size.height() - 1);
                int x2 = geom.cellX(endCx) + geom.cellSize();
                int y2 = geom.cellY(endCy) + geom.cellSize();
                GridItemRenderer.renderSizedItem(g, mc.font, stack, x1, y1, x2 - x1, y2 - y1,
                    rotated, ClientDataCache.INSTANCE.shouldRotateTexture(stack),
                    ClientDataCache.INSTANCE.shouldStretchTexture(stack),
                    ClientDataCache.INSTANCE.proportionalTextureScale(stack));
                SafetyBoxLockAnimation.render(g, 0, cy * geom.cols() + cx,
                    x1, y1, x2 - x1, y2 - y1);
                if (statusScreen != null && statusScreen.showsItemWeightBadges()) {
                    statusScreen.renderExternalItemWeightBadge(g, stack,
                        x1, y1, x2 - x1, y2 - y1);
                } else if (DeltaContainerLayoutController.showsItemWeightBadges(screen)) {
                    DeltaContainerLayoutController.renderExternalItemWeightBadge(screen,
                        g, stack, x1, y1, x2 - x1, y2 - y1);
                }
            }
    }

    /** Keeps the safety-box selection above the global item-model pass. */
    private static void renderSafetyBoxSelection(GuiGraphics graphics, Screen screen) {
        if (screen != safetyBoxSelectionOwner
            || !ItemDetailOverlay.isSafetyBoxGridDetail(screen)
            || safetyBoxSelectionWidth <= 0 || safetyBoxSelectionHeight <= 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, ScreenLayerResolver.foreground());
        graphics.fill(safetyBoxSelectionX, safetyBoxSelectionY,
            safetyBoxSelectionX + safetyBoxSelectionWidth,
            safetyBoxSelectionY + safetyBoxSelectionHeight, 0x44000000);
        graphics.renderOutline(safetyBoxSelectionX, safetyBoxSelectionY,
            safetyBoxSelectionWidth, safetyBoxSelectionHeight, 0xFFFFFFFF);
        graphics.pose().popPose();
    }

    private static void renderCustomGridItems(GuiGraphics g, Minecraft mc, SafetyBoxLayoutPack.WidgetNode node,
                                              int width, int height) {
        GridBackingStore store = extraGridStores.get(node.containerIndex);
        if (store == null) return;
        int cellWidth = Math.max(1, width / Math.max(1, node.gridColumns));
        int cellHeight = Math.max(1, height / Math.max(1, node.gridRows));
        for (int cy = 0; cy < store.getHeight(); cy++) {
            for (int cx = 0; cx < store.getWidth(); cx++) {
                ItemStack stack = store.getItemRaw(cx, cy);
                if (stack.isEmpty()) continue;
                boolean rotated = GridBackingStore.isRotated(stack);
                ItemSize size = clientOrientedSize(stack, rotated);
                int x2 = Math.min(width, (cx + size.width()) * cellWidth);
                int y2 = Math.min(height, (cy + size.height()) * cellHeight);
                GridItemRenderer.renderSizedItem(g, mc.font, stack, cx * cellWidth, cy * cellHeight,
                    x2 - cx * cellWidth, y2 - cy * cellHeight, rotated,
                    ClientDataCache.INSTANCE.shouldRotateTexture(stack),
                    ClientDataCache.INSTANCE.shouldStretchTexture(stack),
                    ClientDataCache.INSTANCE.proportionalTextureScale(stack));
                SafetyBoxLockAnimation.render(g, node.containerIndex,
                    cy * store.getWidth() + cx, cx * cellWidth, cy * cellHeight,
                    x2 - cx * cellWidth, y2 - cy * cellHeight);
            }
        }
    }

    private static void renderCustomGridPlacementPreview(GuiGraphics g, Minecraft mc) {
        ItemStack carried = mc.player == null ? ItemStack.EMPTY : mc.player.containerMenu.getCarried();
        if (carried.isEmpty()) return;
        double mouseX = mc.mouseHandler.xpos() * (double)mc.getWindow().getGuiScaledWidth()
            / (double)mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * (double)mc.getWindow().getGuiScaledHeight()
            / (double)mc.getWindow().getScreenHeight();
        GridBackingStore previousStore = gridStore;
        GridGeometry previousGeometry = cachedGridGeom;
        int previousWidth = gridW, previousHeight = gridH, previousContainer = activeContainerIndex;
        try {
            for (Map.Entry<Integer, int[]> entry : extraGridBounds.entrySet()) {
                int[] bounds = entry.getValue();
                GridBackingStore store = extraGridStores.get(entry.getKey());
                if (store == null || mouseX < bounds[0] || mouseX >= bounds[0] + bounds[2]
                    || mouseY < bounds[1] || mouseY >= bounds[1] + bounds[3]) continue;
                gridStore = store;
                gridW = store.getWidth();
                gridH = store.getHeight();
                activeContainerIndex = entry.getKey();
                float cellScale = Math.max(0.1f, (bounds[2] / (float)store.getWidth()) / 18.0f);
                cachedGridGeom = new GridGeometry(store.getWidth(), store.getHeight(), bounds[0], bounds[1], cellScale, 1.0f);
                int cx = Math.max(0, Math.min(store.getWidth() - 1,
                    (int)((mouseX - bounds[0]) / Math.max(1, bounds[2] / store.getWidth()))));
                int cy = Math.max(0, Math.min(store.getHeight() - 1,
                    (int)((mouseY - bounds[1]) / Math.max(1, bounds[3] / store.getHeight()))));
                renderPlacementPreview(g, cachedGridGeom, cx, cy, carried, mouseX, mouseY);
            }
        } finally {
            gridStore = previousStore;
            cachedGridGeom = previousGeometry;
            gridW = previousWidth;
            gridH = previousHeight;
            activeContainerIndex = previousContainer;
        }
    }

    private static void updateOverlayBounds(GridGeometry geom, SafetyBoxOverlayRenderer.HeaderResult hr) {
        overlayMinX = Math.min(geom.gridX(), hr.headerX());
        overlayMinY = Math.min(geom.gridY(), hr.headerY());
        overlayMaxX = Math.max(geom.gridX() + geom.pixelWidth(), hr.headerX() + hr.headerW());
        overlayMaxY = Math.max(geom.gridY() + geom.pixelHeight(), hr.headerY() + hr.headerH());
        overlayMinX = Math.min(overlayMinX, safePanelMinX);
        overlayMinY = Math.min(overlayMinY, safePanelMinY);
        overlayMaxX = Math.max(overlayMaxX, safePanelMaxX);
        overlayMaxY = Math.max(overlayMaxY, safePanelMaxY);
    }

    private static void updateSafePanelBounds(GridGeometry geom, SafetyBoxLayoutPack.LayoutData ld,
                                              int headerX, int headerY, int headerWidth, int headerHeight) {
        int padLeft = clamp(ld.panelPadLeft, 0, 60);
        int padRight = clamp(ld.panelPadRight, 0, 60);
        int padTop = clamp(ld.panelPadTop, 0, 60);
        int padBottom = clamp(ld.panelPadBottom, 0, 80);
        // The panel is the full safety-box surface, not just the grid. In
        // particular, a BOTTOM header leaves an empty strip below the grid;
        // treating that strip as outside the screen causes vanilla to drop a
        // carried item when it is clicked.
        int contentMinX = Math.min(geom.gridX(), headerX);
        int contentMinY = Math.min(geom.gridY(), headerY);
        int contentMaxX = Math.max(geom.gridX() + geom.pixelWidth(), headerX + headerWidth);
        int contentMaxY = Math.max(geom.gridY() + geom.pixelHeight(), headerY + headerHeight);
        safePanelMinX = contentMinX - padLeft;
        safePanelMinY = contentMinY - padTop;
        safePanelMaxX = contentMaxX + padRight;
        safePanelMaxY = contentMaxY + padBottom;
    }

    private static int argb(int a, int r, int g, int b) {
        return (clamp(a, 0, 255) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean isInSafePanel(double mouseX, double mouseY) {
        return cachedGridGeom != null
            && mouseX >= safePanelMinX && mouseX < safePanelMaxX
            && mouseY >= safePanelMinY && mouseY < safePanelMaxY;
    }

    private static boolean isInOverlayGuard(double mouseX, double mouseY, boolean carryingItem) {
        // Only the header is an overlay control. Do not reserve a padded rectangle
        // around the whole panel: neighboring inventory slots must remain clickable
        // when the safety box is moved close to the inventory.
        return inspectHeaderW > 0 && inspectHeaderH > 0
            && mouseX >= inspectHeaderX && mouseX < inspectHeaderX + inspectHeaderW
            && mouseY >= inspectHeaderY && mouseY < inspectHeaderY + inspectHeaderH;
    }

    private static void deactivate() {
        overlayActive = false;
        gridStore = null;
        nativeGridStore = null;
        extraGridStores.clear();
        extraGridBounds.clear();
        lastEquippedBox = ItemStack.EMPTY;
        lastMenuKey = null;
        cachedGridGeom = null;
        cachedAvoidanceX = cachedAvoidanceY = 0;
        overlayMinX = overlayMinY = overlayMaxX = overlayMaxY = 0;
        safePanelMinX = safePanelMinY = safePanelMaxX = safePanelMaxY = 0;
        inspectHeaderX = inspectHeaderY = inspectHeaderW = inspectHeaderH = 0;
        statusPanelVisible = false;
        statusPanelLayout = null;
        SafetyBoxLockAnimation.reset();
        clearReturnOrigin();
        clearGridDoubleClick();
        clearGridActionGuard();
        clearGridDragGesture();
        safetyBoxSelectionOwner = null;
        safetyBoxSelectionX = safetyBoxSelectionY = 0;
        safetyBoxSelectionWidth = safetyBoxSelectionHeight = 0;
    }

    // ==================== Mouse click handling on grid ====================

    private static int[] getCellAt(double mouseX, double mouseY) {
        if (!overlayActive || gridStore == null || cachedGridGeom == null) return null;
        for (Map.Entry<Integer, int[]> entry : extraGridBounds.entrySet()) {
            int[] bounds = entry.getValue();
            if (mouseX >= bounds[0] && mouseX < bounds[0] + bounds[2]
                && mouseY >= bounds[1] && mouseY < bounds[1] + bounds[3]) {
                GridBackingStore store = extraGridStores.get(entry.getKey());
                if (store == null) continue;
                activeContainerIndex = entry.getKey();
                gridStore = store;
                gridW = store.getWidth();
                gridH = store.getHeight();
                int cellW = Math.max(1, bounds[2] / store.getWidth());
                int cellH = Math.max(1, bounds[3] / store.getHeight());
                cachedGridGeom = new GridGeometry(store.getWidth(), store.getHeight(), bounds[0], bounds[1],
                    Math.max(0.1f, cellW / 18.0f), 1.0f);
                return new int[]{(int)((mouseX - bounds[0]) / cellW), (int)((mouseY - bounds[1]) / cellH)};
            }
        }
        activeContainerIndex = 0;
        return cachedGridGeom.getCellAt(mouseX, mouseY);
    }

    private static void armGridDrag(Screen owner, int x, int y,
                                    double mouseX, double mouseY, ItemStack stack) {
        int anchor = findAnchorIndexAtClient(x, y);
        int anchorX = anchor < 0 ? x : anchor % gridW;
        int anchorY = anchor < 0 ? y : anchor / gridW;
        gridDragArmed = true;
        gridDragPickedUp = false;
        gridDragSourceX = anchorX;
        gridDragSourceY = anchorY;
        gridDragSourceContainer = activeContainerIndex;
        gridDragStartX = mouseX;
        gridDragStartY = mouseY;
        gridDragStack = stack.copy();
        recordGridPickup(x, y, mouseX, mouseY, stack);
        gridDragOwner = owner;
    }

    private static void selectGridContainer(int containerIndex) {
        activeContainerIndex = containerIndex;
        if (containerIndex <= 0) {
            gridStore = nativeGridStore;
        } else {
            gridStore = extraGridStores.get(containerIndex);
        }
        if (gridStore != null) {
            gridW = gridStore.getWidth();
            gridH = gridStore.getHeight();
        }
    }

    private static void clearGridDragGesture() {
        gridDragArmed = false;
        gridDragPickedUp = false;
        gridDragSourceX = gridDragSourceY = 0;
        gridDragSourceContainer = 0;
        gridDragStartX = gridDragStartY = 0.0D;
        gridDragStack = ItemStack.EMPTY;
        gridDragOwner = null;
    }

    private static boolean placeCarriedInGrid(int x, int y, double mouseX,
                                              double mouseY, ItemStack carried) {
        if (carried.isEmpty() || cachedGridGeom == null) return false;
        double fractionX = cellFractionX(cachedGridGeom, x, mouseX);
        double fractionY = cellFractionY(cachedGridGeom, y, mouseY);
        ClientPlacement placement = resolvePlacementClient(x, y, carried,
            rotatedForCarried(carried, x, y, fractionX, fractionY),
            false, fractionX, fractionY);
        if (!placement.isAccepted()) {
            tryReturnToOrigin(carried);
            return true;
        }
        updateReturnOriginAfterAcceptedPlacement(placement);
        ModNetwork.sendToServer(new GridActionPacket(GridActionPacket.PLACE,
            placement.x(), placement.y(), false, placement.rotated(), activeContainerIndex));
        return true;
    }

    private static void renderPlacementPreview(GuiGraphics g, GridGeometry geom, int x, int y, ItemStack carried,
                                               double mouseX, double mouseY) {
        double fractionX = cellFractionX(geom, x, mouseX);
        double fractionY = cellFractionY(geom, y, mouseY);
        ClientPlacement placement = resolvePlacementClient(x, y, carried,
            rotatedForCarried(carried, x, y, fractionX, fractionY), false,
            fractionX, fractionY);
        ItemSize size;
        if (placement.status() == GridBackingStore.PlacementStatus.CAN_STACK) {
            ItemStack target = gridStore.getItemRaw(placement.x(), placement.y());
            size = clientOrientedSize(target, GridBackingStore.isRotated(target));
        } else {
            size = clientOrientedSize(carried, placement.rotated());
        }
        int color = placementColor(placement);
        int cell = geom.cellSize();
        for (int yy = placement.y(); yy < placement.y() + size.height(); yy++) {
            for (int xx = placement.x(); xx < placement.x() + size.width(); xx++) {
                if (xx < 0 || yy < 0 || xx >= gridW || yy >= gridH) continue;
                int px = geom.cellX(xx);
                int py = geom.cellY(yy);
                g.fill(px, py, px + cell, py + cell, color);
            }
        }
    }

    private static int placementColor(ClientPlacement placement) {
        return switch (placement.status()) {
            case CAN_PLACE, CAN_STACK -> 0x9000CC00;
            case CAN_SWAP -> 0x90FFAA00;
            default -> 0x90CC2222;
        };
    }

    private static boolean rotatedForCarried(ItemStack stack, int x, int y, double fractionX, double fractionY) {
        boolean rotated = GridBackingStore.isRotated(stack);
        if (!ClientGridRotation.allowAutoRotate()) return rotated;
        ItemSize base = ClientDataCache.INSTANCE.getSize(stack);
        if (base.width() == base.height()) return rotated;
        ItemSize current = clientOrientedSize(stack, rotated);
        boolean shouldFlip;
        if (current.width() > current.height()) {
            shouldFlip = (fractionX < 0.5 && isBoundaryClient(x, y, -1, 0))
                || (fractionX >= 0.5 && isBoundaryClient(x, y, 1, 0));
        } else {
            shouldFlip = (fractionY < 0.5 && isBoundaryClient(x, y, 0, -1))
                || (fractionY >= 0.5 && isBoundaryClient(x, y, 0, 1));
        }
        if (shouldFlip) {
            ClientPlacement flipped = resolvePlacementClient(x, y, stack, !rotated, false, fractionX, fractionY);
            if (flipped.isAccepted()) return !rotated;
        }
        return rotated;
    }

    private static boolean isBoundaryClient(int x, int y, int dx, int dy) {
        int targetX = x + dx;
        int targetY = y + dy;
        if (targetX < 0 || targetY < 0 || targetX >= gridW || targetY >= gridH) return true;
        if (gridW == 4 && gridH == 2 && ((x == 2 && targetX == 3) || (x == 3 && targetX == 2))) return true;
        return findAnchorIndexAtClient(targetX, targetY) >= 0;
    }

    private static double cellFractionX(GridGeometry geom, int x, double mouseX) {
        return Math.max(0.0, Math.min(0.999, (mouseX - geom.cellX(x)) / geom.cellSize()));
    }

    private static double cellFractionY(GridGeometry geom, int y, double mouseY) {
        return Math.max(0.0, Math.min(0.999, (mouseY - geom.cellY(y)) / geom.cellSize()));
    }

    private static ItemSize clientOrientedSize(ItemStack stack, boolean rotated) {
        ItemSize size = ClientDataCache.INSTANCE.getSize(stack);
        return rotated ? size.rotated() : size;
    }

    private static boolean isBlockedClient(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.is(ModTags.SAFETY_BOX)) return true;
        return Config.INSTANCE.isBlacklisted(stack);
    }

    private static boolean canPlaceClient(int x, int y, ItemStack stack, boolean rotated, Set<Integer> ignored) {
        if (isBlockedClient(stack)) return false;
        ItemSize size = clientOrientedSize(stack, rotated);
        if (x < 0 || y < 0 || x + size.width() > gridW || y + size.height() > gridH) return false;
        if (!fitsPackRegionClient(x, y, size) || crossesSplitGapClient(x, size.width())) return false;
        for (int yy = y; yy < y + size.height(); yy++) {
            for (int xx = x; xx < x + size.width(); xx++) {
                int anchor = findAnchorIndexAtClient(xx, yy);
                if (anchor >= 0 && !ignored.contains(anchor)) return false;
            }
        }
        return true;
    }

    private static boolean canStackClient(int x, int y, ItemStack carried) {
        int anchor = findAnchorIndexAtClient(x, y);
        if (anchor < 0) return false;
        ItemStack target = gridStore.getItemRaw(anchor % gridW, anchor / gridW);
        return ItemStack.isSameItemSameComponents(target, carried)
            && target.getCount() < target.getMaxStackSize();
    }

    private static Set<Integer> blockersClient(int x, int y, ItemStack stack, boolean rotated) {
        Set<Integer> blockers = new HashSet<>();
        ItemSize size = clientOrientedSize(stack, rotated);
        if (!fitsPackRegionClient(x, y, size) || crossesSplitGapClient(x, size.width())) return blockers;
        for (int yy = y; yy < y + size.height(); yy++) {
            for (int xx = x; xx < x + size.width(); xx++) {
                int anchor = findAnchorIndexAtClient(xx, yy);
                if (anchor >= 0) blockers.add(anchor);
            }
        }
        return blockers;
    }

    private static ClientPlacement resolvePlacementClient(int hoverX, int hoverY, ItemStack stack, boolean preferredRotated,
                                                          boolean allowRotate, double fractionX, double fractionY) {
        if (isBlockedClient(stack)) {
            return new ClientPlacement(hoverX, hoverY, preferredRotated, GridBackingStore.PlacementStatus.BLOCKED, Set.of());
        }

        Set<Integer> ignoredAnchors = pendingReturnOriginAnchors(stack);
        int stackAnchor = findAnchorIndexAtClient(hoverX, hoverY);
        if (ignoredAnchors.contains(stackAnchor)) stackAnchor = -1;
        if (stackAnchor >= 0) {
            ItemStack target = gridStore.getItemRaw(stackAnchor % gridW, stackAnchor / gridW);
            if (ItemStack.isSameItemSameComponents(target, stack) && target.getCount() < target.getMaxStackSize()) {
                return new ClientPlacement(stackAnchor % gridW, stackAnchor / gridW, GridBackingStore.isRotated(target),
                    GridBackingStore.PlacementStatus.CAN_STACK, Set.of(stackAnchor));
            }
        }

        boolean[] rotations = clientRotationOrder(stack, preferredRotated, allowRotate);
        ClientPlacement swapCandidate = null;
        for (boolean rotated : rotations) {
            ItemSize size = clientOrientedSize(stack, rotated);
            for (int[] candidate : candidateAnchorsClient(
                hoverX, hoverY, size, fractionX, fractionY)) {
                int x = candidate[0];
                int y = candidate[1];
                if (canPlaceClient(x, y, stack, rotated, ignoredAnchors)) {
                    return new ClientPlacement(x, y, rotated, GridBackingStore.PlacementStatus.CAN_PLACE, Set.of());
                }
                Set<Integer> blockers = blockersClient(x, y, stack, rotated);
                blockers.removeAll(ignoredAnchors);
                if (blockers.size() == 1
                    && fitsInBoundsClient(x, y, size) && fitsPackRegionClient(x, y, size)
                    && !crossesSplitGapClient(x, size.width()) && swapCandidate == null) {
                    swapCandidate = new ClientPlacement(x, y, rotated, GridBackingStore.PlacementStatus.CAN_SWAP, Set.copyOf(blockers));
                }
            }
        }

        return swapCandidate != null ? swapCandidate
            : new ClientPlacement(hoverX, hoverY, preferredRotated, GridBackingStore.PlacementStatus.BLOCKED, Set.of());
    }

    private static boolean[] clientRotationOrder(ItemStack stack, boolean preferredRotated, boolean allowRotate) {
        if (!allowRotate) return new boolean[]{preferredRotated};
        ItemSize preferred = clientOrientedSize(stack, preferredRotated);
        ItemSize flipped = clientOrientedSize(stack, !preferredRotated);
        if (preferred.equals(flipped)) return new boolean[]{preferredRotated};
        return new boolean[]{preferredRotated, !preferredRotated};
    }

    private static List<int[]> candidateAnchorsClient(int hoverX, int hoverY, ItemSize size,
                                                      double fractionX, double fractionY) {
        List<int[]> candidates = new ArrayList<>();
        int preferredX = hoverX - ContainerGridHelper.anchorOffset(size.width(), fractionX);
        int preferredY = hoverY - ContainerGridHelper.anchorOffset(size.height(), fractionY);
        candidates.add(new int[]{preferredX, preferredY});
        for (int y = hoverY; y >= hoverY - size.height() + 1; y--) {
            for (int x = hoverX; x >= hoverX - size.width() + 1; x--) {
                if (x != preferredX || y != preferredY) {
                    candidates.add(new int[]{x, y});
                }
            }
        }
        return candidates;
    }

    private static Set<Integer> pendingReturnOriginAnchors(ItemStack carried) {
        if (!returnOriginKnown || returnOriginContainerIndex != activeContainerIndex
            || returnOriginPickedStack.isEmpty()
            || !isSameItemIgnoringRotation(carried, returnOriginPickedStack)
            || returnOriginX < 0 || returnOriginY < 0
            || returnOriginX >= gridW || returnOriginY >= gridH) {
            return Set.of();
        }
        return Set.of(returnOriginY * gridW + returnOriginX);
    }

    private static boolean fitsInBoundsClient(int x, int y, ItemSize size) {
        return x >= 0 && y >= 0 && x + size.width() <= gridW && y + size.height() <= gridH;
    }

    private static int findAnchorIndexAtClient(int x, int y) {
        if (gridStore == null || x < 0 || y < 0 || x >= gridW || y >= gridH) return -1;
        for (int i = 0; i < gridW * gridH; i++) {
            ItemStack stack = gridStore.getItemRaw(i % gridW, i / gridW);
            if (stack.isEmpty()) continue;
            ItemSize size = clientOrientedSize(stack, GridBackingStore.isRotated(stack));
            int ax = i % gridW;
            int ay = i / gridW;
            if (x >= ax && x < ax + size.width() && y >= ay && y < ay + size.height()) return i;
        }
        return -1;
    }

    private static ItemStack getStackAtIncludingFootprintClient(int x, int y) {
        int anchor = findAnchorIndexAtClient(x, y);
        return anchor >= 0 ? gridStore.getItemRaw(anchor % gridW, anchor / gridW) : ItemStack.EMPTY;
    }

    private static boolean fitsPackRegionClient(int x, int y, ItemSize size) {
        if (gridStore == null || !(gridStore.getBoxStack().getItem() instanceof DeltaPackItem pack)) {
            return true;
        }
        return pack.regions().stream().anyMatch(region ->
            region.contains(x, y, size.width(), size.height()));
    }

    private static boolean crossesSplitGapClient(int x, int footprintWidth) {
        return gridW == 4 && gridH == 2 && x < 3 && x + footprintWidth > 3;
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double mx = event.getMouseX(), my = event.getMouseY();
        double detailMouseX = event.getScreen() instanceof PlayerStatusScreen statusScreen
            ? statusScreen.itemDetailMouseX(event.getMouseX()) : event.getMouseX();
        double detailMouseY = event.getScreen() instanceof PlayerStatusScreen statusScreen
            ? statusScreen.itemDetailMouseY(event.getMouseY()) : event.getMouseY();
        if (ItemDetailOverlay.mouseClicked(
            event.getScreen(), detailMouseX, detailMouseY, event.getButton())) {
            event.setCanceled(true);
            return;
        }
        if (CreativeItemRuleEditor.mousePressed(
            event.getScreen(), mx, my, event.getButton())) {
            event.setCanceled(true);
            return;
        }
        if (event.getButton() == 0 && statusPanelVisible && statusPanelLayout != null
            && statusPanelLayout.contains(mx, my)) {
            event.setCanceled(true);
            if (statusPanelLayout.foldContains(mx, my)) {
                PlayerStatusPanelState.setCollapsed(true);
            } else if (statusPanelLayout.collapsed()) {
                beginStatusPanelDrag(mx, my);
            } else if (statusPanelLayout.mailContains(mx, my)) {
                MailClientState.INSTANCE.requestOpen();
                ModNetwork.sendToServer(MailActionPacket.open());
            } else if (statusPanelLayout.headerContains(mx, my)) {
                beginStatusPanelDrag(mx, my);
            } else {
                Screen parent = event.getScreen();
                if (parent instanceof CreativeModeInventoryScreen) {
                    // The creative picker is client-only; keep it available for returning.
                    mc.player.containerMenu = mc.player.inventoryMenu;
                } else if (mc.player.containerMenu != mc.player.inventoryMenu) {
                    mc.player.closeContainer();
                    parent = null;
                }
                PlayerStatusScreenState.enable();
                mc.setScreen(new PlayerStatusScreen(parent));
            }
            return;
        }
        if (!overlayActive) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            deactivate();
            return;
        }
        if (event.getButton() == 1
            && event.getScreen() instanceof AbstractContainerScreen<?> containerScreen
            && !(containerScreen instanceof PlayerStatusScreen)
            && !DeltaContainerLayoutController.isEmbedded(containerScreen)
            && lastEquippedBox.is(ModItems.SAFETY_BOX_3X3.get())
            && isInsideInspectHeader(mx, my)) {

            event.setCanceled(true);
            mc.setScreen(null);
            ModNetwork.sendToServer(new InspectRequestPacket());
            return;
        }
        // --- Free drag from header or panel padding ---
        if (event.getButton() == 0
            && event.getScreen() instanceof AbstractContainerScreen<?> containerScreen
            && !(event.getScreen() instanceof PlayerStatusScreen)
            && !DeltaContainerLayoutController.isEmbedded(containerScreen)) {
            SafetyBoxLayoutPack.LayoutData ld = SafetyBoxLayoutPack.getLayoutForBox(
                lastEquippedBox.getItemHolder().getKey().location().toString());
            int[] off = SafetyBoxOverlay.getScreenOffset(lastMenuKey);
            int centerX = ld.centerX, centerY = ld.centerY;
            int gox = ld.globalOffX, goy = ld.globalOffY;
            int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
            var cs = (AbstractContainerScreen<?>) event.getScreen();
            int left = cs.getGuiLeft(), top = cs.getGuiTop();
            int imgW = cs.getXSize(), imgH = cs.getYSize();
            int anchorX = centerX >= 0 ? (int)(sw * centerX / 100.0) : left + imgW + off[0] + gox;
            int anchorY = centerY >= 0 ? (int)(sh * centerY / 100.0) : top + off[1] + goy;
            anchorX += gox; anchorY += goy;

            var sbi = (SafetyBoxItem) lastEquippedBox.getItem();
            int gw = sbi.getGridWidth(), gh = sbi.getGridHeight();
            String title = (ld.customText != null && !ld.customText.isEmpty()) ? ld.customText : lastEquippedBox.getHoverName().getString();

            GridGeometry baseGeom = new GridGeometry(gw, gh, 0, 0, 1.0f, (float) ld.gridScale);
            var header = SafetyBoxOverlayRenderer.computeHeaderSize(mc.font, baseGeom, ld, 1.0f, title);
            int headerW = header.width();
            int headerH = header.height();
            int scaledGW = baseGeom.pixelWidth(), scaledGH = baseGeom.pixelHeight();
            int hx, hy;
            Config.Layout layout = Config.Layout.valueOf(ld.layout);
            switch (layout) {
                case LEFT -> { hx = anchorX; hy = anchorY + (scaledGH - headerH) / 2; }
                case RIGHT -> { hx = anchorX + scaledGW + 4; hy = anchorY + (scaledGH - headerH) / 2; }
                case BOTTOM -> { hx = anchorX + (scaledGW - headerW) / 2; hy = anchorY + scaledGH + 4; }
                default -> { hx = anchorX + (scaledGW - headerW) / 2; hy = anchorY; }
            }
            hx += ld.borderOffX + cachedAvoidanceX;
            hy += ld.borderOffY + cachedAvoidanceY;

            if (mx >= hx && mx < hx + headerW && my >= hy && my < hy + headerH) {
                beginOverlayDrag(mx, my);
                event.setCanceled(true);
                return;
            }
            if (isInSafePanel(mx, my) && getCellAt(mx, my) == null) {
                beginOverlayDrag(mx, my);
                event.setCanceled(true);
                return;
            }
        }

        // --- Grid click handling ---
        // Check if click is within grid bounds - consume all clicks to prevent item dropping
        ItemStack carriedNow = mc.player.containerMenu.getCarried();
        // In PlayerStatusScreen the embedded inspect-header bounds intentionally
        // describe the safety-box equipment card. That card belongs to the
        // screen itself (open picker / show permission notice), not to the grid
        // overlay. Let the screen receive the click before applying the normal
        // overlay guard used by external container screens.
        if (event.getScreen() instanceof PlayerStatusScreen
            || event.getScreen() instanceof AbstractContainerScreen<?> embedded
                && DeltaContainerLayoutController.isEmbedded(embedded)) {
            return;
        }
        if (isInSafePanel(mx, my) || isInOverlayGuard(mx, my, !carriedNow.isEmpty())) {
            event.setCanceled(true);
        }

        int[] cell = getCellAt(mx, my);
        if (cell == null) return;

        int cx = cell[0], cy = cell[1];
        int button = event.getButton();
        boolean shift = Screen.hasShiftDown();

        event.setCanceled(true);
        if (isDuplicateGridAction(button, cx, cy)) return;
        recordGridAction(button, cx, cy);

        if (button == 0) {
            ItemStack carried = mc.player.containerMenu.getCarried().copy();
            ItemStack inSlot = getStackAtIncludingFootprintClient(cx, cy);
            if (carried.isEmpty() && !inSlot.isEmpty()) {
                if (shift || isDoubleClickOnGrid(cx, cy, mx, my, inSlot)) {
                    ModNetwork.sendToServer(new GridActionPacket(
                        GridActionPacket.PICKUP, cx, cy, true, false, activeContainerIndex));
                    clearGridDoubleClick();
                    clearGridDragGesture();
                    clearReturnOrigin();
                    ItemDetailOverlay.close(event.getScreen());
                    return;
                }
                armGridDrag(event.getScreen(), cx, cy, mx, my, inSlot);
            } else if (!carried.isEmpty()) {
                clearGridDoubleClick();
                ItemDetailOverlay.close(event.getScreen());
                // Placement is committed by MouseButtonReleased so external
                // drags and safety-box-internal drags use the same gesture.
            } else {
                clearGridDoubleClick();
                ItemDetailOverlay.close(event.getScreen());
            }
        } else if (button == 1) {
            ItemStack carried = mc.player.containerMenu.getCarried().copy();
            ItemStack inSlot = getStackAtIncludingFootprintClient(cx, cy);
            clearGridDoubleClick();

            if (carried.isEmpty() && !inSlot.isEmpty() && inSlot.getCount() > 1) {
                ModNetwork.sendToServer(new GridActionPacket(GridActionPacket.SPLIT, cx, cy, false, false, activeContainerIndex));
            } else if (!carried.isEmpty() && inSlot.isEmpty()) {
                double fractionX = cellFractionX(cachedGridGeom, cx, mx);
                double fractionY = cellFractionY(cachedGridGeom, cy, my);
                ClientPlacement placement = resolvePlacementClient(cx, cy, carried,
                    rotatedForCarried(carried, cx, cy, fractionX, fractionY),
                    false, fractionX, fractionY);
                if (!placement.isAccepted()) {
                    tryReturnToOrigin(carried);
                    return;
                }
                if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE) {
                    ModNetwork.sendToServer(new GridActionPacket(GridActionPacket.RIGHT_CLICK,
                        placement.x(), placement.y(), false, placement.rotated(), activeContainerIndex));
                }
            }
        }
    }

    private static boolean isDoubleClickOnGrid(int x, int y, double mouseX, double mouseY, ItemStack matcher) {
        if (matcher.isEmpty() || lastGridPickupMatcher.isEmpty()) return false;
        long now = System.currentTimeMillis();
        double dx = mouseX - lastGridPickupMouseX;
        double dy = mouseY - lastGridPickupMouseY;
        return lastGridPickupX == x && lastGridPickupY == y
            && now - lastGridPickupTime <= GRID_DOUBLE_CLICK_WINDOW_MS
            && dx * dx + dy * dy <= GRID_DOUBLE_CLICK_DISTANCE_SQ
            && isSameItemIgnoringRotation(matcher, lastGridPickupMatcher);
    }

    private static void recordGridPickup(int x, int y, double mouseX, double mouseY, ItemStack matcher) {
        lastGridPickupX = x;
        lastGridPickupY = y;
        lastGridPickupMouseX = mouseX;
        lastGridPickupMouseY = mouseY;
        lastGridPickupTime = System.currentTimeMillis();
        lastGridPickupMatcher = matcher.copy();
    }

    private static void clearGridDoubleClick() {
        lastGridPickupX = -1;
        lastGridPickupY = -1;
        lastGridPickupMouseX = 0;
        lastGridPickupMouseY = 0;
        lastGridPickupTime = 0L;
        lastGridPickupMatcher = ItemStack.EMPTY;
    }

    private static boolean isDuplicateGridAction(int button, int x, int y) {
        return button == lastGridActionButton && x == lastGridActionX && y == lastGridActionY
            && System.nanoTime() - lastGridActionTime < 50_000_000L;
    }

    private static void recordGridAction(int button, int x, int y) {
        lastGridActionButton = button;
        lastGridActionX = x;
        lastGridActionY = y;
        lastGridActionTime = System.nanoTime();
    }

    private static void clearGridActionGuard() {
        lastGridActionButton = -1;
        lastGridActionX = -1;
        lastGridActionY = -1;
        lastGridActionTime = 0L;
    }

    private static boolean isSameItemIgnoringRotation(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) return false;
        ItemStack normalizedFirst = first.copy();
        ItemStack normalizedSecond = second.copy();
        GridBackingStore.setRotated(normalizedFirst, false);
        GridBackingStore.setRotated(normalizedSecond, false);
        return ItemStack.isSameItemSameComponents(normalizedFirst, normalizedSecond);
    }

    private static void recordReturnOrigin(int x, int y) {
        int anchor = findAnchorIndexAtClient(x, y);
        if (anchor < 0) {
            clearReturnOrigin();
            return;
        }
        ItemStack stack = gridStore.getItemRaw(anchor % gridW, anchor / gridW);
        returnOriginKnown = true;
        returnOriginX = anchor % gridW;
        returnOriginY = anchor / gridW;
        returnOriginContainerIndex = activeContainerIndex;
        returnOriginRotated = GridBackingStore.isRotated(stack);
        returnOriginPickedStack = stack.copy();
        returnOriginPickupObserved = false;
    }

    private static void updateReturnOriginAfterAcceptedPlacement(ClientPlacement placement) {
        if (placement.status() != GridBackingStore.PlacementStatus.CAN_SWAP) clearReturnOrigin();
    }

    private static void tryReturnToOrigin(ItemStack carried) {
        if (!returnOriginKnown || carried.isEmpty()) return;
        ModNetwork.sendToServer(new GridActionPacket(GridActionPacket.RETURN_ORIGIN,
            returnOriginX, returnOriginY, false, returnOriginRotated, returnOriginContainerIndex));
        clearReturnOrigin();
    }

    private static void clearReturnOrigin() {
        returnOriginKnown = false;
        returnOriginX = returnOriginY = 0;
        returnOriginContainerIndex = 0;
        returnOriginRotated = false;
        returnOriginPickedStack = ItemStack.EMPTY;
        returnOriginPickupObserved = false;
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        double detailMouseX = event.getScreen() instanceof PlayerStatusScreen statusScreen
            ? statusScreen.itemDetailMouseX(event.getMouseX()) : event.getMouseX();
        double detailMouseY = event.getScreen() instanceof PlayerStatusScreen statusScreen
            ? statusScreen.itemDetailMouseY(event.getMouseY()) : event.getMouseY();
        if (ItemDetailOverlay.mouseScrolled(event.getScreen(), detailMouseX,
            detailMouseY, event.getScrollDeltaY())) {
            event.setCanceled(true);
            return;
        }
        if (CreativeItemRuleEditor.mouseScrolled(event.getScreen(),
            event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
            event.setCanceled(true);
            return;
        }
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen
            && DeltaContainerLayoutController.mouseScrolled(screen,
                event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {

        }
    }

    // ==================== Header drag state ====================

    private static boolean dragging;
    private static int dragStartX, dragStartY, dragOrigOffX, dragOrigOffY;
    private static String dragMenuKey;
    private static boolean statusPanelDragging;
    private static boolean statusPanelDragMoved;
    private static boolean statusPanelDragWasCollapsed;
    private static int statusPanelDragStartX, statusPanelDragStartY;
    private static int statusPanelOriginX, statusPanelOriginY;

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        double detailMouseX = event.getScreen() instanceof PlayerStatusScreen statusScreen
            ? statusScreen.itemDetailMouseX(event.getMouseX()) : event.getMouseX();
        double detailMouseY = event.getScreen() instanceof PlayerStatusScreen statusScreen
            ? statusScreen.itemDetailMouseY(event.getMouseY()) : event.getMouseY();
        if (ItemDetailOverlay.mouseDragged(event.getScreen(), detailMouseX,
            detailMouseY, event.getMouseButton())) {
            event.setCanceled(true);
            return;
        }
        if (CreativeItemRuleEditor.mouseDragged(event.getScreen(),
            event.getMouseX(), event.getMouseY(), event.getMouseButton())) {
            event.setCanceled(true);
            return;
        }
        if (statusPanelDragging) {
            event.setCanceled(true);
            Minecraft minecraft = Minecraft.getInstance();
            int screenWidth = minecraft.getWindow().getGuiScaledWidth();
            int screenHeight = minecraft.getWindow().getGuiScaledHeight();
            int deltaX = (int) event.getMouseX() - statusPanelDragStartX;
            int deltaY = (int) event.getMouseY() - statusPanelDragStartY;
            if (deltaX * deltaX + deltaY * deltaY >= 9) statusPanelDragMoved = true;
            int nextX = statusPanelOriginX + deltaX;
            int nextY = statusPanelOriginY + deltaY;
            int panelWidth = statusPanelLayout == null ? PlayerStatusPanelRenderer.WIDTH : statusPanelLayout.width();
            int panelHeight = statusPanelLayout == null ? PlayerStatusPanelRenderer.HEIGHT : statusPanelLayout.height();
            if (statusPanelLayout != null && !statusPanelLayout.collapsed()
                && CreativeItemRuleEditor.isVisible(event.getScreen())) {
                panelHeight += 4 + CreativeItemRuleEditor.visibleHeight();
            }
            nextX = Math.max(4, Math.min(screenWidth - panelWidth - 4, nextX));
            nextY = Math.max(4, Math.min(screenHeight - panelHeight - 4, nextY));
            PlayerStatusPanelState.set(nextX, nextY);
            return;
        }
        if (gridDragArmed && event.getMouseButton() == 0) {
            event.setCanceled(true);
            double dx = event.getMouseX() - gridDragStartX;
            double dy = event.getMouseY() - gridDragStartY;
            if (!gridDragPickedUp && dx * dx + dy * dy >= 9.0D) {
                selectGridContainer(gridDragSourceContainer);
                if (gridStore != null) {
                    recordReturnOrigin(gridDragSourceX, gridDragSourceY);
                    ModNetwork.sendToServer(new GridActionPacket(
                        GridActionPacket.PICKUP, gridDragSourceX, gridDragSourceY,
                        false, false, gridDragSourceContainer));
                    gridDragPickedUp = true;
                    ItemDetailOverlay.close(event.getScreen());
                }
            }

            return;
        }
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen
            && DeltaContainerLayoutController.mouseDragged(screen,
                event.getMouseX(), event.getMouseY(), event.getMouseButton())) {

            return;
        }
        if (event.getScreen() instanceof PlayerStatusScreen) return;
        if (dragging) {

            int dx = (int)(event.getMouseX() - dragStartX);
            int dy = (int)(event.getMouseY() - dragStartY);
            SafetyBoxOverlay.setScreenOffset(dragMenuKey, dragOrigOffX + dx, dragOrigOffY + dy);
            return;
        }
        if (overlayActive && (isInSafePanel(event.getMouseX(), event.getMouseY())
            || isInOverlayGuard(event.getMouseX(), event.getMouseY(),
                Minecraft.getInstance().player != null && !Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        double detailMouseX = event.getScreen() instanceof PlayerStatusScreen statusScreen
            ? statusScreen.itemDetailMouseX(event.getMouseX()) : event.getMouseX();
        double detailMouseY = event.getScreen() instanceof PlayerStatusScreen statusScreen
            ? statusScreen.itemDetailMouseY(event.getMouseY()) : event.getMouseY();
        if (ItemDetailOverlay.mouseReleased(event.getScreen(), detailMouseX,
            detailMouseY, event.getButton())) {
            event.setCanceled(true);
            return;
        }
        if (CreativeItemRuleEditor.mouseReleased(event.getScreen(),
            event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
            return;
        }
        if (statusPanelDragging) {
            event.setCanceled(true);
            statusPanelDragging = false;
            if (statusPanelDragWasCollapsed && !statusPanelDragMoved) {
                PlayerStatusPanelState.setCollapsed(false);
            }
            PlayerStatusPanelState.save();
            return;
        }
        if (gridDragArmed && event.getButton() == 0) {
            event.setCanceled(true);
            boolean pickedUp = gridDragPickedUp;
            ItemStack dragged = gridDragStack.copy();
            Screen owner = gridDragOwner;
            int sourceX = gridDragSourceX;
            int sourceY = gridDragSourceY;
            int sourceContainer = gridDragSourceContainer;
            clearGridDragGesture();

            if (!pickedUp) {
                if (owner != null && !dragged.isEmpty() && cachedGridGeom != null) {
                    selectGridContainer(sourceContainer);
                    ItemSize size = clientOrientedSize(dragged, GridBackingStore.isRotated(dragged));
                    int anchorX = cachedGridGeom.cellX(sourceX);
                    int anchorY = cachedGridGeom.cellY(sourceY);
                    int anchorWidth = size.width() * cachedGridGeom.cellSize();
                    int anchorHeight = size.height() * cachedGridGeom.cellSize();
                    safetyBoxSelectionOwner = owner;
                    safetyBoxSelectionX = anchorX;
                    safetyBoxSelectionY = anchorY;
                    safetyBoxSelectionWidth = anchorWidth;
                    safetyBoxSelectionHeight = anchorHeight;
                    if (owner instanceof PlayerStatusScreen statusScreen) {
                        anchorX = statusScreen.itemDetailLogicalX(anchorX);
                        anchorY = statusScreen.itemDetailLogicalY(anchorY);
                        anchorWidth = statusScreen.itemDetailLogicalSize(anchorWidth);
                        anchorHeight = statusScreen.itemDetailLogicalSize(anchorHeight);
                    }
                    ItemDetailOverlay.openSafetyBoxGrid(owner, dragged,
                        anchorX, anchorY, anchorWidth, anchorHeight);
                }
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            ItemStack carried = minecraft.player == null
                ? ItemStack.EMPTY : minecraft.player.containerMenu.getCarried().copy();
            if (carried.isEmpty()) carried = dragged;
            int[] cell = getCellAt(event.getMouseX(), event.getMouseY());
            if (cell != null) {
                placeCarriedInGrid(cell[0], cell[1],
                    event.getMouseX(), event.getMouseY(), carried);
            } else if (event.getScreen() instanceof PlayerStatusScreen statusScreen
                && statusScreen.insideDiscardZonePhysical(
                    event.getMouseX(), event.getMouseY())
                && minecraft.player != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryMouseClick(
                    minecraft.player.containerMenu.containerId, -999, 0,
                    net.minecraft.world.inventory.ClickType.PICKUP, minecraft.player);
            }
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (overlayActive && event.getButton() == 0 && minecraft.player != null
            && !minecraft.player.containerMenu.getCarried().isEmpty()) {
            int[] cell = getCellAt(event.getMouseX(), event.getMouseY());
            if (cell != null && placeCarriedInGrid(cell[0], cell[1],
                event.getMouseX(), event.getMouseY(),
                minecraft.player.containerMenu.getCarried().copy())) {
                event.setCanceled(true);
                return;
            }
        }
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen
            && DeltaContainerLayoutController.mouseReleased(screen,
                event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
            return;
        }
        if (event.getScreen() instanceof PlayerStatusScreen) return;
        if (dragging) {
            event.setCanceled(true);
            dragging = false;
            SafetyBoxOverlay.saveConfig();
            return;
        }
        if (overlayActive && (isInSafePanel(event.getMouseX(), event.getMouseY())
            || isInOverlayGuard(event.getMouseX(), event.getMouseY(),
                Minecraft.getInstance().player != null && !Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()))) {

        }
    }

    private static void beginOverlayDrag(double mouseX, double mouseY) {
        dragging = true;
        dragStartX = (int) mouseX;
        dragStartY = (int) mouseY;
        dragMenuKey = lastMenuKey;
        int[] offset = SafetyBoxOverlay.getScreenOffset(dragMenuKey);
        dragOrigOffX = offset[0] + cachedAvoidanceX;
        dragOrigOffY = offset[1] + cachedAvoidanceY;
        SafetyBoxOverlay.setScreenOffset(dragMenuKey, dragOrigOffX, dragOrigOffY);
        cachedAvoidanceX = 0;
        cachedAvoidanceY = 0;
    }

    private static void beginStatusPanelDrag(double mouseX, double mouseY) {
        if (statusPanelLayout == null) return;
        statusPanelDragging = true;
        statusPanelDragMoved = false;
        statusPanelDragWasCollapsed = statusPanelLayout.collapsed();
        statusPanelDragStartX = (int) mouseX;
        statusPanelDragStartY = (int) mouseY;
        statusPanelOriginX = statusPanelLayout.x();
        statusPanelOriginY = statusPanelLayout.y();
    }

    // ==================== Key/Screen lifecycle ====================

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getKeyCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
            && ItemDetailOverlay.handleEscape(event.getScreen())) {
            event.setCanceled(true);
            return;
        }
        if (CreativeItemRuleEditor.keyPressed(
            event.getScreen(), event.getKeyCode(), event.getModifiers())) {
            event.setCanceled(true);
            return;
        }
        if (SAFETY_BOX_CONFIG_KEY.matches(event.getKeyCode(), event.getScanCode())
            && Screen.hasAltDown()
            && !(event.getScreen() instanceof SafetyBoxConfigScreen)) {

            while (SAFETY_BOX_CONFIG_KEY.consumeClick()) {
                // Drain the physical press before opening the configuration screen.
            }
            mc.setScreen(new SafetyBoxConfigScreen(event.getScreen()));
            return;
        }
        if (EFFECT_HUD_CONFIG_KEY.matches(event.getKeyCode(), event.getScanCode())
            && Screen.hasAltDown()
            && !(event.getScreen() instanceof StatusEffectHudConfigScreen)) {

            while (EFFECT_HUD_CONFIG_KEY.consumeClick()) {
                // Do not reopen the editor after this screen closes.
            }
            mc.setScreen(new StatusEffectHudConfigScreen(event.getScreen()));
            return;
        }
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> cs)) return;
        if (mc.player == null) return;
        if (ROTATE_KEY.matches(event.getKeyCode(), event.getScanCode())
            && !mc.player.containerMenu.getCarried().isEmpty()) {
            ItemStack carried = mc.player.containerMenu.getCarried();
            ItemSize size = ClientDataCache.INSTANCE.getSize(carried);
            if (size.width() > 1 || size.height() > 1) {
                boolean rotated = !GridBackingStore.isRotated(carried);
                GridBackingStore.setRotated(carried, rotated);
                mc.player.containerMenu.setCarried(carried);
                ClientGridRotation.activate(mc);
                ModNetwork.sendToServer(new CarriedRotationPacket(rotated, true));
    
                return;
            }
        }
        if (TOGGLE_KEY.matches(event.getKeyCode(), event.getScanCode())) {
            String mk = lastMenuKey != null ? lastMenuKey : SafetyBoxOverlay.resolveMenuKey(cs.getMenu());
            if (mk != null) {
                SafetyBoxOverlay.toggleMenu(mk);
            }
            boolean qm = !Config.INSTANCE.quickMoveEnabled.get(); Config.INSTANCE.quickMoveEnabled.set(qm);
            XeroTitleOverlay.show("top|center",
                "[icon:xero_delta:quality/lock] [translate:"
                    + (qm ? "overlay.xero_delta.quick_deposit_enabled"
                        : "overlay.xero_delta.quick_deposit_disabled") + "]", 60);
 return;
        }
    }

    @SubscribeEvent
    public static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (CreativeItemRuleEditor.charTyped(event.getScreen(), event.getCodePoint())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenClose(ScreenEvent.Closing event) {
        CreativeItemRuleEditor.close(event.getScreen());
        if (statusPanelDragging) {
            statusPanelDragging = false;
            PlayerStatusPanelState.save();
        }
        ClientGridRotation.clear(Minecraft.getInstance());
        deactivate();
    }

    @SubscribeEvent
    public static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getEntity() != minecraft.player) return;
        boolean staminaSprintLocked = StaminaClientState.INSTANCE.exhausted()
            || StaminaClientState.INSTANCE.fraction()
                <= StaminaRules.SPRINT_LOCK_FRACTION + 0.0001F;
        if (staminaSprintLocked) {
            minecraft.player.setSprinting(false);
        }
        if (DownedClientState.INSTANCE.downed()) return;
        byte carryPhase = DownedClientState.INSTANCE.carryPhase();
        if (carryPhase == 1 || carryPhase == 3) {
            event.getInput().leftImpulse = 0.0F;
            event.getInput().forwardImpulse = 0.0F;
        }
        if (MedicalUseClientState.INSTANCE.medicalActive()) {
            boolean prohibited = event.getInput().jumping || event.getInput().shiftKeyDown;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
            minecraft.player.setShiftKeyDown(false);
            if (prohibited) cancelMedicalUse(minecraft);
        }
        double factor = Math.max(0.20D,
            1.0D - PlayerStatusClientState.INSTANCE.speedPenalty());
        if (MedicalUseClientState.INSTANCE.active()) factor = Math.min(factor, 0.35D);
        if (factor >= 0.999D) return;
        event.getInput().leftImpulse *= (float) factor;
        event.getInput().forwardImpulse *= (float) factor;
        minecraft.player.setSprinting(false);
    }
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        MedicalUseClientState.INSTANCE.tick();
        MedicalWheelClient.tick(minecraft, MEDICAL_WHEEL_KEY.isDown());
        boolean taczOwnsInteraction = TaczInteractionPriority.ownsInteraction(minecraft,
            RESCUE_KEY);
        if (minecraft.player != null && minecraft.screen == null) {
            while (TOGGLE_LAYOUT_KEY.consumeClick()) {
                ModNetwork.sendToServer(new PlayerLayoutTogglePacket());
            }
            CommandWheelClient.tick(minecraft, COMMAND_WHEEL_KEY.isDown()
                && !DownedClientState.INSTANCE.interactionLocked());
            while (EFFECT_HUD_CONFIG_KEY.consumeClick()) {
                if (isAltDown(minecraft)) {
                    minecraft.setScreen(new StatusEffectHudConfigScreen(null));
                    break;
                }
            }
            while (SAFETY_BOX_CONFIG_KEY.consumeClick()) {
                if (isAltDown(minecraft)) {
                    minecraft.setScreen(new SafetyBoxConfigScreen(null));
                    break;
                }
            }
            while (CARRY_KEY.consumeClick()) {
                if (!DownedClientState.INSTANCE.downed()) {
                    ModNetwork.sendToServer(new CarryActionPacket(
                        ContextInteractionClient.carryTargetId(minecraft)));
                }
            }
            while (RESCUE_KEY.consumeClick()) {
                if (BetterLootingClientPriority.hasPickupTarget() || taczOwnsInteraction) continue;
                if (!DownedClientState.INSTANCE.interactionLocked()
                    && !RescueHoldClientState.INSTANCE.begin(minecraft)) {
                    ContextInteractionClient.activate(minecraft);
                }
            }
        }
        RescueHoldClientState.INSTANCE.tick(minecraft,
            RESCUE_KEY.isDown() && !taczOwnsInteraction);
        if (minecraft.player == null) return;
        if (MedicalUseClientState.INSTANCE.medicalActive()
            && (minecraft.options.keyJump.isDown() || minecraft.options.keyShift.isDown()
                || minecraft.player.isVisuallyCrawling())) {
            cancelMedicalUse(minecraft);
            minecraft.player.input.jumping = false;
            minecraft.player.input.shiftKeyDown = false;
            minecraft.player.setShiftKeyDown(false);
            suppressInspectionKey(minecraft.options.keyJump);
            suppressInspectionKey(minecraft.options.keyShift);
        }
        if (MedicalUseClientState.INSTANCE.active()) {
            minecraft.player.setSprinting(false);
            suppressInspectionKey(minecraft.options.keyAttack);
            suppressInspectionKey(minecraft.options.keyUse);
        }
        boolean staminaSprintLocked = StaminaClientState.INSTANCE.exhausted()
            || StaminaClientState.INSTANCE.fraction()
                <= StaminaRules.SPRINT_LOCK_FRACTION + 0.0001F;
        if (staminaSprintLocked) {
            minecraft.player.setSprinting(false);
        } else if (PlayerStatusClientState.INSTANCE.layoutEnabled()
            && minecraft.screen == null
            && !DownedClientState.INSTANCE.downed()
            && minecraft.options.keySprint.isDown()
            && minecraft.player.input.hasForwardImpulse()
            && !StaminaClientState.INSTANCE.exhausted()
            && StaminaClientState.INSTANCE.fraction()
                > StaminaRules.SPRINT_RESUME_FRACTION + 0.0001F
            && !minecraft.player.isSprinting()) {
            minecraft.player.setSprinting(true);
        }
        if (!DownedClientState.INSTANCE.redDown()) {
            DownedAbandonClientState.INSTANCE.cancel();
        }
        boolean rescueMovementLocked = RescueHoldClientState.INSTANCE.active()
            || DownedClientState.INSTANCE.rescuing()
            || DownedClientState.INSTANCE.beingRescued();
        if (rescueMovementLocked) {
            minecraft.player.setSprinting(false);
            minecraft.player.input.leftImpulse = 0.0F;
            minecraft.player.input.forwardImpulse = 0.0F;
            minecraft.player.input.jumping = false;
            minecraft.player.input.shiftKeyDown = false;
        }
        if (DownedClientState.INSTANCE.interactionLocked()
            || RescueHoldClientState.INSTANCE.active()) {
            suppressInspectionKey(minecraft.options.keyAttack);
            suppressInspectionKey(minecraft.options.keyUse);
            suppressInspectionKey(minecraft.options.keyPickItem);
            suppressInspectionKey(minecraft.options.keyDrop);
            suppressInspectionKey(minecraft.options.keySwapOffhand);
            suppressInspectionKey(minecraft.options.keyInventory);
            suppressInspectionKey(minecraft.options.keyJump);
            for (KeyMapping hotbarKey : minecraft.options.keyHotbarSlots) suppressInspectionKey(hotbarKey);
        }
        if (!SafetyBoxInspectAnimation.isLocalPlaying()) return;
        if (minecraft.player.getPose() == Pose.SWIMMING) {
            cancelLocalInspection(minecraft);
            return;
        }
        suppressInspectionKey(minecraft.options.keyAttack);
        suppressInspectionKey(minecraft.options.keyUse);
        suppressInspectionKey(minecraft.options.keyPickItem);
        suppressInspectionKey(minecraft.options.keyDrop);
        suppressInspectionKey(minecraft.options.keySwapOffhand);
        for (KeyMapping hotbarKey : minecraft.options.keyHotbarSlots) suppressInspectionKey(hotbarKey);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDownedMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() != GLFW.GLFW_PRESS
            || minecraft.screen != null
            || minecraft.player == null
            || !DownedClientState.INSTANCE.downed()) return;
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
            && DownedClientState.INSTANCE.yellowDown()) {
            if (minecraft.getConnection() != null) {
                ModNetwork.sendToServer(new DownedActionPacket(
                    DownedActionPacket.SWITCH_SPECTATOR));
            }

            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        RescueRequestClientState.INSTANCE.tryTriggerLocal(
            minecraft.player.getUUID(),
            DownedClientState.INSTANCE.yellowDown() ? (byte) 2 : (byte) 1);
        if (minecraft.getConnection() != null) {
            ModNetwork.sendToServer(new DownedActionPacket(DownedActionPacket.REQUEST_RESCUE));
        }
        event.setCanceled(true);
    }
    @SubscribeEvent
    public static void onInspectionKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (MEDICAL_WHEEL_KEY.matches(event.getKey(), event.getScanCode())
            && minecraft.player != null
            && minecraft.screen == null && !DownedClientState.INSTANCE.interactionLocked()
            && MedicalWheelClient.canOpen(minecraft.player)) {
            suppressInspectionKey(minecraft.options.keyHotbarSlots[4]);

            return;
        }
        if (minecraft.player != null && minecraft.screen == null
            && event.getAction() == GLFW.GLFW_PRESS
            && (minecraft.options.keyJump.matches(event.getKey(), event.getScanCode())
                || minecraft.options.keyShift.matches(event.getKey(), event.getScanCode()))
            && MedicalUseClientState.INSTANCE.medicalActive()) {
            cancelMedicalUse(minecraft);
            minecraft.player.input.jumping = false;
            minecraft.player.input.shiftKeyDown = false;
            minecraft.player.setShiftKeyDown(false);
            suppressInspectionKey(minecraft.options.keyJump);
            suppressInspectionKey(minecraft.options.keyShift);
            return;
        }
        if (minecraft.player != null && DownedClientState.INSTANCE.yellowDown()
            && minecraft.screen == null && event.getAction() == GLFW.GLFW_PRESS
            && event.getKey() == GLFW.GLFW_KEY_E) {
            minecraft.setScreen(createDownedAbandonScreen());
            return;
        }
        if (minecraft.player != null && DownedClientState.INSTANCE.redDown()
            && minecraft.options.keyJump.matches(event.getKey(), event.getScanCode())) {
            if (event.getAction() == GLFW.GLFW_PRESS
                && minecraft.screen == null
                && DownedAbandonClientState.INSTANCE.start()
                && minecraft.getConnection() != null) {
                ModNetwork.sendToServer(new DownedActionPacket(
                    DownedActionPacket.START_ABANDON_HOLD));
            } else if (event.getAction() == GLFW.GLFW_RELEASE
                && DownedAbandonClientState.INSTANCE.cancel()
                && minecraft.getConnection() != null) {
                ModNetwork.sendToServer(new DownedActionPacket(
                    DownedActionPacket.CANCEL_ABANDON_HOLD));
            }
            return;
        }

    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInspectionMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean left = event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        boolean right = event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        boolean blockedRelease = event.getAction() == GLFW.GLFW_RELEASE
            && (left && medicalLeftMouseBlocked || right && medicalRightMouseBlocked);
        if (blockedRelease) {
            if (left) medicalLeftMouseBlocked = false;
            if (right) medicalRightMouseBlocked = false;
            event.setCanceled(true);
            return;
        }
        if (event.getAction() == GLFW.GLFW_PRESS
            && !MedicalUseClientState.INSTANCE.medicalActive()) {
            if (left) medicalLeftMouseBlocked = false;
            if (right) medicalRightMouseBlocked = false;
        }
        if (event.getAction() == GLFW.GLFW_PRESS && (left || right)
            && minecraft.screen == null && minecraft.player != null
            && MedicalUseClientState.INSTANCE.medicalActive()) {
            if (left) medicalLeftMouseBlocked = true;
            if (right) medicalRightMouseBlocked = true;
            cancelMedicalUse(minecraft);
            suppressInspectionKey(minecraft.options.keyAttack);
            suppressInspectionKey(minecraft.options.keyUse);
            event.setCanceled(true);
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS
            || (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            || minecraft.screen != null
            || !SafetyBoxInspectAnimation.isLocalPlaying()) return;
        cancelLocalInspection(minecraft);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInspectionInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if ((event.isAttack() || event.isUseItem())
            && minecraft.player != null && minecraft.screen == null
            && MedicalUseClientState.INSTANCE.medicalActive()) {
            cancelMedicalUse(minecraft);
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (DownedClientState.INSTANCE.interactionLocked()) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (!SafetyBoxInspectAnimation.isLocalPlaying()) return;
        cancelLocalInspection(Minecraft.getInstance());
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLocalDownedPlayerSize(EntityEvent.Size event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getEntity() != minecraft.player
            || !DownedClientState.INSTANCE.redDown()) return;
        event.setNewSize(EntityDimensions.scalable(event.getNewSize().width(), 1.0F)
            .withEyeHeight(0.4F));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInspectionMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (CommandWheelClient.mouseScrolled(Minecraft.getInstance(), event.getScrollDeltaY())) {
            event.setCanceled(true);
            return;
        }
        if (SafetyBoxInspectAnimation.isLocalPlaying()
            || DownedClientState.INSTANCE.interactionLocked()) event.setCanceled(true);
    }

    private static void suppressInspectionKey(KeyMapping key) {
        key.setDown(false);
        while (key.consumeClick()) {
            // Drain queued clicks so Minecraft.handleKeybinds cannot perform
            // the action later in the same tick.
        }
    }

    private static boolean cancelMedicalUse(Minecraft minecraft) {
        if (!MedicalUseClientState.INSTANCE.cancelLocally()) return false;
        if (minecraft.getConnection() != null) {
            ModNetwork.sendToServer(new MedicalUseActionPacket(MedicalUseActionPacket.CANCEL));
        }
        return true;
    }

    private static boolean cancelLocalInspection(Minecraft minecraft) {
        if (!SafetyBoxInspectAnimation.cancelLocal()) return false;
        if (minecraft.getConnection() != null) {
            ModNetwork.sendToServer(new InspectCancelRequestPacket());
        }
        return true;
    }
    private static boolean isAltDown(Minecraft minecraft) {
        long window = minecraft.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private static ConfirmScreen createDownedAbandonScreen() {
        return new ConfirmScreen(confirmed -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (confirmed && minecraft.getConnection() != null
                && DownedClientState.INSTANCE.downed()) {
                ModNetwork.sendToServer(new DownedActionPacket(
                    DownedActionPacket.ABANDON_RESCUE));
            }
            minecraft.setScreen(null);
        }, Component.translatable("downed.xero_delta.abandon_title"),
            Component.translatable("downed.xero_delta.abandon_message"),
            Component.translatable("downed.xero_delta.abandon_confirm"),
            Component.translatable("downed.xero_delta.abandon_cancel"));
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getNewScreen() != null) {
            com.xtdpotato.xero_delta.screen.material.Material3Theme.refreshFromConfig();
        }
        if (event.getNewScreen() instanceof PauseScreen
            && DownedClientState.INSTANCE.yellowDown()) {
            event.setNewScreen(createDownedAbandonScreen());
            return;
        }
        if (event.getNewScreen() != null) cancelLocalInspection(minecraft);
        if (event.getNewScreen() instanceof AbstractContainerScreen<?>
            && DownedClientState.INSTANCE.interactionLocked()) {

            return;
        }
        if (!(event.getNewScreen() instanceof InventoryScreen)
            || event.getNewScreen() instanceof PlayerStatusScreen
            || minecraft.player == null
            || minecraft.player.isCreative()) return;
        if (shouldReplaceVanillaInventory()) {
            event.setNewScreen(new PlayerStatusScreen(null));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        SafetyBoxInspectAnimation.tick();
        if (mc.player == null) return;
        if (!mc.player.isCreative()
            && mc.screen instanceof InventoryScreen
            && !(mc.screen instanceof PlayerStatusScreen)
            && shouldReplaceVanillaInventory()) {
            mc.setScreen(new PlayerStatusScreen(null));
            return;
        }
        if (!(mc.screen instanceof InventoryScreen)) {
            PlayerStatusScreenState.endInventoryBypass();
        }
        completePendingSafetyBoxInventorySwap(mc);
    }

    /**
     * Keep the normal inventory as a Delta-owned screen even before the
     * server applies its restricted loadout rule. It still subclasses
     * InventoryScreen for Better Looting and other inventory integrations.
     */
    private static boolean shouldReplaceVanillaInventory() {
        return !PlayerStatusScreenState.isInventoryBypassed();
    }

    private static void completePendingSafetyBoxInventorySwap(Minecraft minecraft) {
        if (!returnOriginKnown || returnOriginPickedStack.isEmpty() || minecraft.player == null) return;
        ItemStack carried = minecraft.player.containerMenu.getCarried();
        if (!returnOriginPickupObserved) {
            if (!carried.isEmpty() && isSameItemIgnoringRotation(carried, returnOriginPickedStack)) {
                returnOriginPickupObserved = true;
            }
            return;
        }
        if (!carried.isEmpty() && isSameItemIgnoringRotation(carried, returnOriginPickedStack)) return;

        ModNetwork.sendToServer(new GridActionPacket(GridActionPacket.COMPLETE_INVENTORY_SWAP,
            returnOriginX, returnOriginY, false, false, returnOriginContainerIndex));
        clearReturnOrigin();
    }

    private static boolean isInspectOverlayTarget(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!overlayActive || screen != minecraft.screen || !lastEquippedBox.is(ModItems.SAFETY_BOX_3X3.get())) {
            return false;
        }
        double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth()
            / minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight()
            / minecraft.getWindow().getScreenHeight();
        return isInsideInspectHeader(mouseX, mouseY);
    }

    private static boolean isInsideInspectHeader(double mouseX, double mouseY) {
        return inspectHeaderW > 0 && inspectHeaderH > 0
            && mouseX >= inspectHeaderX && mouseX < inspectHeaderX + inspectHeaderW
            && mouseY >= inspectHeaderY && mouseY < inspectHeaderY + inspectHeaderH;
    }

    // ==================== Packet sync handler ====================

    /** Called from GridSyncPacket handler to update local grid store */
    public static void onGridSync(List<ItemStack> items, int gw, int gh, ItemStack carried) {
        onGridSync(items, gw, gh, carried, 0);
    }

    public static void onGridSync(List<ItemStack> items, int gw, int gh, ItemStack carried, int containerIndex) {
        GridBackingStore target = containerIndex == 0 ? gridStore : extraGridStores.get(containerIndex);
        if (target != null && gw == target.getWidth() && gh == target.getHeight()) {
            SafetyBoxLockAnimation.onGridSync(containerIndex, target, items);
            target.restoreAll(items);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.containerMenu.setCarried(carried.copy());
    }

    // ==================== Helpers ====================

    private static ItemStack findEquipped(net.minecraft.world.entity.player.Player p) {
        try { var o = CuriosApi.getCuriosInventory(p); if (o.isPresent()) { var r = o.get().findFirstCurio(
            s -> s.is(ModTags.SAFETY_BOX)); if (r.isPresent()) return r.get().stack(); } } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }
}

