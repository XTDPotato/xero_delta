package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.TaczCompatibilityRules;
import com.xtdpotato.xero_delta.data.WarehouseCategory;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.grid.SafetyBoxGridInteraction;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.network.CurioSlotSwapPacket;
import com.xtdpotato.xero_delta.network.EquippedStorageActionPacket;
import com.xtdpotato.xero_delta.network.EquippedStorageShortcutPacket;
import com.xtdpotato.xero_delta.network.InventorySourceToMenuPacket;
import com.xtdpotato.xero_delta.network.InventorySourceToEquippedStoragePacket;
import com.xtdpotato.xero_delta.network.InventorySourceQuickMovePacket;
import com.xtdpotato.xero_delta.network.ItemDetailActionPacket;
import com.xtdpotato.xero_delta.network.CorpseStorageTransferPacket;
import com.xtdpotato.xero_delta.network.CarrierReplacePacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.PlayerEquipmentSlotClickPacket;
import com.xtdpotato.xero_delta.network.PlayerEquipmentSync;
import com.xtdpotato.xero_delta.network.WarehouseSourceTransferPacket;
import com.xtdpotato.xero_delta.screen.CardHolderPickerScreen;
import com.xtdpotato.xero_delta.screen.CorpseScreen;
import com.xtdpotato.xero_delta.screen.GroundPackScreen;
import com.xtdpotato.xero_delta.screen.KnifePickerScreen;
import com.xtdpotato.xero_delta.screen.PackRegionLayout;
import com.xtdpotato.xero_delta.screen.PersonalWarehouseScreen;
import com.xtdpotato.xero_delta.screen.PlayerStatusScreen;
import com.xtdpotato.xero_delta.screen.SafetyBoxPickerScreen;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;
import com.xtdpotato.xero_delta.trading.TradingInventorySources;
import com.xtdpotato.xero_delta.util.SlotFieldUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Adds the real Delta player inventory to the left of any normal container.
 * The native container remains intact on the right, including its background,
 * widgets, result slots and third-party controls.
 */
public final class DeltaContainerLayoutController {
    private static final Map<AbstractContainerScreen<?>, Placement> PLACEMENTS = new WeakHashMap<>();
    private static final Map<AbstractContainerScreen<?>, State> STATES = new WeakHashMap<>();
    private static final Map<AbstractContainerScreen<?>, Position> BASE_ORIGINS = new WeakHashMap<>();
    private static final Map<AbstractContainerScreen<?>, EditorState> EDITORS = new WeakHashMap<>();
    private static double warehousePlayerScroll;
    private static double warehousePlayerTargetScroll;
    private static boolean warehousePlayerScrollRemembered;

    // All hosts, including the E-key inventory, use this player surface.
    private static final int BASE_WIDTH = DeltaInventoryLayout.WIDTH;
    private static final int BASE_HEADER = DeltaInventoryLayout.HEADER;
    private static final int BASE_MODEL_X = 12;
    private static final int BASE_MODEL_WIDTH = 210;
    private static final int BASE_LOADOUT_X = 152;
    private static final int BASE_LOADOUT_WIDTH = 72;
    private static final int BASE_STORAGE_X = 226;
    private static final int BASE_STORAGE_WIDTH = 174;
    private static final int BASE_LARGE_SLOT = 36;
    private static int BASE_CELL = 18;
    private static final int BASE_SECTION_GAP = 6;
    // Deliberate breathing room between the Delta player surface and the
    // native container, matching the reference layout's center void.
    private static final int GAP = DeltaInventoryLayout.GAP;

    private static final float DELTA_PANEL_Z = 200.0F;
    private static final float ITEM_WEIGHT_BADGE_Z = 700.0F;
    private static final ResourceLocation GUI_ICONS = ResourceLocation.fromNamespaceAndPath(
        "xero_delta", "textures/gui/delta_icons.png");

    private DeltaContainerLayoutController() {
    }

    /** Called from init/render so the original screen itself is laid out on the right. */
    public static int[] position(AbstractContainerScreen<?> screen, int currentLeft, int currentTop) {
        if (!eligible(screen)) return new int[]{currentLeft, currentTop};
        Placement existing = PLACEMENTS.get(screen);
        float globalScale = StatusEffectHudState.inventoryLayoutScale();
        int layoutMargin = StatusEffectHudState.inventoryLayoutMargin();
        if (existing != null && existing.screenWidth == screen.width
            && existing.screenHeight == screen.height
            && existing.nativeContentWidth == nativeLayoutWidth(screen)
            && existing.positionRevision == ContainerUiPositionState.revision()
            && Float.compare(existing.layoutScale, globalScale) == 0
            && existing.layoutMargin == layoutMargin) {
            return new int[]{existing.nativeLeft, existing.nativeTop};
        }
        if (screen instanceof PlayerStatusScreen) {
            BASE_CELL = InventoryLayoutScale.cellSize(18, globalScale);
            int lootListWidth = net.neoforged.fml.ModList.get().isLoaded("better_looting") ? 140 : 0;
            float scale = DeltaInventoryLayout.fitScale(screen.width - lootListWidth - 8, screen.height - 8,
                InventoryLayoutScale.contentFactor(globalScale));
            int panelWidth = Math.max(1, Math.round(BASE_WIDTH * scale));
            int panelX = Math.max(4, (screen.width - panelWidth - lootListWidth) / 2);
            Placement placement = new Placement(screen.width, screen.height, panelX, 4,
                panelWidth, Math.max(1, screen.height - 8 - layoutMargin), scale, panelX, 4,
                panelX, 4, 0, 0, 1.0D, 1.0D, 1.0D, 1.0D,
                ContainerUiPositionState.revision(), globalScale, layoutMargin,
                nativeLayoutWidth(screen));
            PLACEMENTS.put(screen, placement);
            State previous = STATES.remove(screen);
            if (previous != null) restoreSlots(previous);
            return new int[]{panelX, 4};
        }
        Position baseOrigin = BASE_ORIGINS.computeIfAbsent(screen,
            ignored -> new Position(currentLeft, currentTop));
        ContainerUiPositionState.Offset offset = ContainerUiPositionState.get(positionKey(screen));
        // The global Delta scale belongs to the custom player panel on the left.
        // Native container screens on the right keep their own interface scale.
        BASE_CELL = InventoryLayoutScale.cellSize(18, globalScale);
        double contentFactor = InventoryLayoutScale.contentFactor(globalScale);
        double interfaceScaleX = offset.interfaceScaleX();
        double interfaceScaleY = offset.interfaceScaleY();
        if (isTravelersBackpackScreen(screen)) {
            int minimumPanelWidth = Math.max(1, Math.round(BASE_WIDTH * 0.22F));
            double widthFit = (screen.width - minimumPanelWidth - GAP - 12.0D)
                / Math.max(1.0D, nativeLayoutWidth(screen) * interfaceScaleX);
            double heightFit = (screen.height - 8.0D)
                / Math.max(1.0D, screen.getYSize() * interfaceScaleY);
            double automaticFit = clamp(Math.min(widthFit, heightFit), 0.22D, 1.0D);
            interfaceScaleX *= automaticFit;
            interfaceScaleY *= automaticFit;
        }
        int nativeWidth = Math.max(1, (int) Math.round(nativeLayoutWidth(screen) * interfaceScaleX));
        int nativeHeight = Math.max(1, (int) Math.round(screen.getYSize() * interfaceScaleY));
        float scale = DeltaInventoryLayout.fitScale(
            screen.width - nativeWidth - GAP - 12, screen.height - 8, contentFactor);
        int panelWidth = Math.round(BASE_WIDTH * scale);
        int totalWidth = panelWidth + GAP + nativeWidth;
        int centeredOrigin = Math.max(4, (screen.width - totalWidth) / 2);
        int rightAlignedNative = Math.max(4, screen.width - nativeWidth - 4);
        int baseNativeLeft = Math.min(centeredOrigin + panelWidth + GAP, rightAlignedNative);
        int nativeLeft = clampScreen(baseNativeLeft + offset.interfaceX(),
            screen.width, nativeWidth);
        int originX = Math.max(4, baseNativeLeft - panelWidth - GAP);
        int panelTop = 4;
        int panelHeight = Math.max(96, screen.height - 8
            - StatusEffectHudState.inventoryLayoutMargin());
        int preferredNativeTop = panelTop + Math.round((BASE_HEADER + 4) * scale);
        int baseNativeTop = Math.max(4, Math.min(preferredNativeTop,
            screen.height - nativeHeight - 4));
        int nativeTop = clampScreen(baseNativeTop + offset.interfaceY(),
            screen.height, nativeHeight);
        Placement placement = new Placement(screen.width, screen.height, originX, panelTop,
            panelWidth, panelHeight, scale, nativeLeft, nativeTop,
            baseOrigin.x, baseOrigin.y, offset.textureX(), offset.textureY(),
            interfaceScaleX, interfaceScaleY,
            offset.textureScaleX(), offset.textureScaleY(),
            ContainerUiPositionState.revision(), globalScale, layoutMargin,
            nativeLayoutWidth(screen));
        PLACEMENTS.put(screen, placement);
        State state = STATES.remove(screen);
        if (state != null) restoreSlots(state);
        return new int[]{nativeLeft, nativeTop};
    }

    private static int nativeLayoutWidth(AbstractContainerScreen<?> screen) {
        return screen instanceof CorpseScreen corpse
            ? corpse.embeddedNativeWidth() : screen.getXSize();
    }

    public static void prepare(AbstractContainerScreen<?> screen, int leftPos, int topPos) {
        if (!eligible(screen)) {
            restore(screen);
            return;
        }
        Placement placement = PLACEMENTS.get(screen);
        int currentRevision = ContainerUiPositionState.revision();
        float globalScale = StatusEffectHudState.inventoryLayoutScale();
        int layoutMargin = StatusEffectHudState.inventoryLayoutMargin();
        if (placement == null
            || placement.screenWidth != screen.width
            || placement.screenHeight != screen.height
            || placement.nativeContentWidth != nativeLayoutWidth(screen)
            || placement.positionRevision != currentRevision
            || Float.compare(placement.layoutScale, globalScale) != 0
            || placement.layoutMargin != layoutMargin) {
            position(screen, leftPos, topPos);
            placement = PLACEMENTS.get(screen);
        }
        if (placement == null) return;
        State state = STATES.get(screen);
        if (state == null || state.placement != placement
            || state.menuLeft != leftPos || state.menuTop != topPos) {
            if (state != null) restoreSlots(state);
            state = createState(screen, placement, leftPos, topPos);
            if (state == null) return;
            STATES.put(screen, state);
        }
        updateSmoothScroll(state);
        state.snapshot = snapshot(state);
        state.targetScroll = clamp(state.targetScroll, 0.0D, maxScroll(state));
        state.scroll = clamp(state.scroll, 0.0D, maxScroll(state));
        layoutPlayerSlots(state);
        if (screen instanceof PlayerStatusScreen) {
            // Crafting/result slots have no visible surface in the Delta layout.
            for (Slot slot : screen.getMenu().slots) {
                if (!state.playerSlots.contains(slot)) {
                    SlotFieldUtil.setX(slot, -1000);
                    SlotFieldUtil.setY(slot, -1000);
                }
            }
        }
    }

    private static State createState(AbstractContainerScreen<?> screen, Placement placement,
                                     int menuLeft, int menuTop) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;
        Map<Slot, Position> originals = new IdentityHashMap<>();
        List<Slot> playerSlots = new ArrayList<>();
        boolean external = false;
        for (Slot slot : screen.getMenu().slots) {
            originals.put(slot, new Position(slot.x, slot.y));
            if (slot.container == minecraft.player.getInventory()) playerSlots.add(slot);
            else external = true;
        }
        if (!external) return null;
        State state = new State(screen, placement, menuLeft, menuTop, originals, playerSlots);
        if (screen instanceof PersonalWarehouseScreen && warehousePlayerScrollRemembered) {
            state.scroll = warehousePlayerScroll;
            state.targetScroll = warehousePlayerTargetScroll;
        }
        return state;
    }

    /** Keeps the embedded player column stationary while a warehouse bin is replaced. */
    public static void rememberWarehouseScroll(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        if (state == null) return;
        warehousePlayerScroll = state.scroll;
        warehousePlayerTargetScroll = state.targetScroll;
        warehousePlayerScrollRemembered = true;
    }

    private static void layoutPlayerSlots(State state) {
        state.playerCards.clear();
        for (Slot slot : state.playerSlots) {
            int index = slot.getContainerSlot();
            Bounds absolute = playerCardBounds(state, index);
            int x = -1000;
            int y = -1000;
            if (absolute != null) {
                Bounds render = new Bounds(
                    (int) Math.round(nativeMouseX(state.screen, absolute.x)) - state.menuLeft,
                    (int) Math.round(nativeMouseY(state.screen, absolute.y)) - state.menuTop,
                    Math.max(1, (int) Math.round(absolute.width
                        / state.placement.interfaceScaleX)),
                    Math.max(1, (int) Math.round(absolute.height
                        / state.placement.interfaceScaleY)));
                state.playerCards.put(slot, new PlayerCard(render, absolute,
                    index == 0 || index == 1 ? String.valueOf(index + 1) : ""));
                x = render.x + Math.max(0, (render.width - 16) / 2);
                y = render.y + Math.max(0, (render.height - 16) / 2);
            }
            SlotFieldUtil.setX(slot, x);
            SlotFieldUtil.setY(slot, y);
        }
    }

    private static Bounds playerCardBounds(State state, int inventoryIndex) {
        if (inventoryIndex >= 4 && inventoryIndex <= 8 && state.snapshot != null) {
            Section pockets = state.snapshot.sections.get("pockets");
            if (pockets == null) return null;
            int cell = scaled(state.placement, BASE_CELL);
            int gap = scaled(state.placement, 2);
            int x = storageX(state.placement) + scaled(state.placement, 6)
                + (inventoryIndex - 4) * (cell + gap);
            int y = pockets.y + scaled(state.placement, 22);
            if (y + cell <= viewportTop(state.placement)
                || y >= scrollViewportBottom(state)) return null;
            return new Bounds(x, y, cell, cell);
        }
        if (state.tab != EmbeddedTab.CHARACTER) return null;
        // InventoryMenu uses container slots 39/38 for helmet/chest armor.
        if (inventoryIndex == 39) return loadoutBounds(state.placement, 0);
        if (inventoryIndex == 38) return loadoutBounds(state.placement, 1);
        if (inventoryIndex == 0) return loadoutBounds(state.placement, 4);
        if (inventoryIndex == 1) return loadoutBounds(state.placement, 5);
        if (inventoryIndex == 2) return loadoutBounds(state.placement, 2);
        if (inventoryIndex == 3) return loadoutBounds(state.placement, 3);
        return null;
    }

    public static void renderPanels(AbstractContainerScreen<?> screen, GuiGraphics graphics) {
        renderPanels(screen, graphics, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public static void renderPanels(AbstractContainerScreen<?> screen, GuiGraphics graphics,
                                    int mouseX, int mouseY) {
        State state = STATES.get(screen);
        if (state == null || state.snapshot == null) return;
        // Cache the exact result shown by the preview. A release can arrive
        // between render ticks with a slightly different fractional cursor
        // position, which is especially visible for 2x2 items.
        state.clearPreviewPlacement();
        pushFixedLayer(graphics, state.placement);
        graphics.pose().translate(0.0F, 0.0F, DELTA_PANEL_Z);
        mouseX = screenMouseX(state.placement, mouseX);
        mouseY = screenMouseY(state.placement, mouseY);
        try {
        state.snapshot = snapshot(state);
        state.targetScroll = clamp(state.targetScroll, 0.0D, maxScroll(state));
        state.scroll = clamp(state.scroll, 0.0D, maxScroll(state));
        layoutPlayerSlots(state);
        state.storageHelpTooltipKey = null;
        state.mouseX = mouseX;
        state.mouseY = mouseY;
        state.weightDetailsVisible = weightDetailsBounds(state).contains(mouseX, mouseY);
        Placement p = state.placement;
        graphics.fill(p.panelX, p.panelTop, p.panelX + p.panelWidth,
            p.panelTop + p.panelHeight, DeltaInventoryTheme.BACKGROUND);
        graphics.fill(storageX(p) - scaled(p, 4), p.panelTop + scaled(p, BASE_HEADER),
            storageX(p) + storageWidth(p) + scaled(p, 4), viewportBottom(p), DeltaInventoryTheme.STORAGE);
        drawHeader(graphics, state);
        drawPlayerModelAndWeight(graphics, state);
        if (state.tab == EmbeddedTab.HEALTH) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 1600);
            drawHealthBodyIcons(graphics, state);
            graphics.pose().popPose();
        }
        else drawLoadout(graphics, state);
        graphics.enableScissor(storageX(p), viewportTop(p),
            storageX(p) + storageWidth(p), scrollViewportBottom(state));
        drawSections(graphics, state);
        graphics.disableScissor();
        if (DeltaInventoryUiState.safetyBoxPinned()) {
            drawSafety(graphics, state, pinnedSafetySection(state));
        }
        drawScrollbar(graphics, state);
        if (state.tab == EmbeddedTab.HEALTH) {
            // Vanilla inventory entities occupy a depth around +1000, above ordinary GUI fills.
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 1600);
            state.medicalShortcuts.render(graphics,
                p.panelX + scaled(p, 52), p.panelTop + p.panelHeight - scaled(p, 94),
                scaled(p, 30), mouseX, mouseY);
            graphics.pose().popPose();
        }
        } finally {
            graphics.pose().popPose();
        }
    }

    /** Native and modded container backgrounds render intact on the right. */
    public static void beginNativeContainerLayer(AbstractContainerScreen<?> screen,
                                                  GuiGraphics graphics) {
        Placement placement = PLACEMENTS.get(screen);
        if (placement == null) return;
        BackgroundAnchor anchor = backgroundAnchor(screen);
        int anchorOffsetX = anchor.centeredX
            ? placement.nativeLeft - placement.originalLeft : 0;
        int anchorOffsetY = anchor.centeredY
            ? placement.nativeTop - placement.originalTop : 0;
        graphics.pose().pushPose();
        graphics.pose().translate(
            anchorOffsetX + placement.textureOffsetX,
            anchorOffsetY + placement.textureOffsetY,
            0.0F);
        int scaleAnchorX = anchor.centeredX ? placement.originalLeft : placement.nativeLeft;
        int scaleAnchorY = anchor.centeredY ? placement.originalTop : placement.nativeTop;
        graphics.pose().translate(scaleAnchorX, scaleAnchorY, 0.0F);
        graphics.pose().scale((float) placement.textureScaleX,
            (float) placement.textureScaleY, 1.0F);
        graphics.pose().translate(-scaleAnchorX, -scaleAnchorY, 0.0F);
    }

    /**
     * Sophisticated Core ignores leftPos/topPos for its main background and
     * recalculates a centered origin inside renderBg. Its slots and controls
     * still follow leftPos/topPos, so only that background needs the full
     * center-to-native translation. Normal container screens already draw
     * their texture at the moved leftPos/topPos and only need the editor's
     * explicit texture offset.
     */
    private static boolean isTravelersBackpackScreen(AbstractContainerScreen<?> screen) {
        Class<?> type = screen.getClass();
        while (type != null) {
            if (type.getName().startsWith(
                "com.tiviacz.travelersbackpack.client.screens.")) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    private static BackgroundAnchor backgroundAnchor(AbstractContainerScreen<?> screen) {
        boolean crafting = false;
        boolean centered = false;
        Class<?> type = screen.getClass();
        while (type != null) {
            String name = type.getName();
            if (name.startsWith("net.p3pp3rf1y.sophisticatedcore.client.gui.")
                || name.startsWith("com.tiviacz.travelersbackpack.client.screens.")) {
                centered = true;
            }
            String simpleName = type.getSimpleName();
            if ("CraftingScreen".equals(simpleName)) crafting = true;
            if (Set.of(
                "BeaconScreen", "BrewingStandScreen", "ContainerScreen",
                "ChestScreen", "CrafterScreen", "DispenserScreen", "EnchantmentScreen",
                "GrindstoneScreen", "HopperScreen", "HorseInventoryScreen",
                "MerchantScreen", "ShulkerBoxScreen"
            ).contains(simpleName)) centered = true;
            type = type.getSuperclass();
        }
        return crafting ? new BackgroundAnchor(false, true)
            : new BackgroundAnchor(centered, centered);
    }

    public static void endNativeContainerLayer(AbstractContainerScreen<?> screen,
                                                GuiGraphics graphics) {
        if (PLACEMENTS.containsKey(screen)) graphics.pose().popPose();
    }

    public static void beginRender(AbstractContainerScreen<?> screen, GuiGraphics graphics) {
        State state = STATES.get(screen);
        if (state == null || state.transitionPushed) return;
        state.transition.tick(Minecraft.getInstance());
        EditorState editor = EDITORS.get(screen);
        if (editor != null && editor.active) {
            graphics.pose().pushPose();
        } else {
            state.transition.push(graphics);
        }
        state.transitionPushed = true;
        graphics.pose().pushPose();
        graphics.pose().translate(state.placement.nativeLeft,
            state.placement.nativeTop, 0.0F);
        graphics.pose().scale((float) state.placement.interfaceScaleX,
            (float) state.placement.interfaceScaleY, 1.0F);
        graphics.pose().translate(-state.placement.nativeLeft,
            -state.placement.nativeTop, 0.0F);
        state.interfaceTransformPushed = true;
    }

    public static void endRender(AbstractContainerScreen<?> screen, GuiGraphics graphics) {
        State state = STATES.get(screen);
        if (state == null || !state.transitionPushed) return;
        if (state.interfaceTransformPushed) {
            graphics.pose().popPose();
            state.interfaceTransformPushed = false;
        }
        state.transition.pop(graphics);
        state.transition.drawFade(graphics, screen.width, screen.height);
        state.transitionPushed = false;
    }

    /** Public client API for compatibility layers that need to query the shared two-column layout. */
    /** Returns whether the player currently has a carrier equipped in the embedded layout. */
    public static boolean isCarrierEquipped(AbstractContainerScreen<?> screen, String identifier) {
        State state = STATES.get(screen);
        if (state == null || state.snapshot == null || identifier == null) return false;
        Carrier carrier = state.snapshot.carriers.get(identifier);
        return carrier != null && carrier.stack != null && !carrier.stack.isEmpty();
    }

    public static boolean isActive(AbstractContainerScreen<?> screen) {
        return screen != null && STATES.containsKey(screen);
    }

    /** Available during screen init, before the first render creates a State. */
    public static boolean isPositioned(AbstractContainerScreen<?> screen) {
        return screen != null && PLACEMENTS.containsKey(screen);
    }

    public static int nativeLeft(AbstractContainerScreen<?> screen, int fallback) {
        Placement placement = PLACEMENTS.get(screen);
        return placement == null ? fallback : placement.nativeLeft;
    }

    public static int nativeTop(AbstractContainerScreen<?> screen, int fallback) {
        Placement placement = PLACEMENTS.get(screen);
        return placement == null ? fallback : placement.nativeTop;
    }

    /** Maps screen coordinates into the scaled native container coordinate space. */
    public static double nativeMouseX(AbstractContainerScreen<?> screen, double mouseX) {
        Placement placement = PLACEMENTS.get(screen);
        if (placement == null) return mouseX;
        return placement.nativeLeft
            + (mouseX - placement.nativeLeft) / placement.interfaceScaleX;
    }

    /** Maps screen coordinates into the scaled native container coordinate space. */
    public static double nativeMouseY(AbstractContainerScreen<?> screen, double mouseY) {
        Placement placement = PLACEMENTS.get(screen);
        if (placement == null) return mouseY;
        return placement.nativeTop
            + (mouseY - placement.nativeTop) / placement.interfaceScaleY;
    }

    /** Maps an absolute native-container coordinate back to final screen space. */
    public static int nativeScreenX(AbstractContainerScreen<?> screen, int nativeX) {
        Placement placement = PLACEMENTS.get(screen);
        return placement == null ? nativeX : screenMouseX(placement, nativeX);
    }

    /** Maps an absolute native-container coordinate back to final screen space. */
    public static int nativeScreenY(AbstractContainerScreen<?> screen, int nativeY) {
        Placement placement = PLACEMENTS.get(screen);
        return placement == null ? nativeY : screenMouseY(placement, nativeY);
    }

    public static boolean renderPlayerSlot(AbstractContainerScreen<?> screen,
                                           GuiGraphics graphics, Slot slot) {
        State state = STATES.get(screen);
        if (state == null) return false;
        PlayerCard card = state.playerCards.get(slot);
        if (card == null) return screen instanceof PlayerStatusScreen || slot.container instanceof Inventory;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, DELTA_PANEL_Z);
        try {
            int containerSlot = slot.getContainerSlot();
            // The loadout surface draws armor itself so its durability bar and
            // overlays stay aligned; suppress the vanilla slot pass here.
            if (containerSlot == 39 || containerSlot == 38) return true;
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) renderSizedItem(graphics, stack, card.renderBounds.x,
                card.renderBounds.y, card.renderBounds.width, card.renderBounds.height);
            if (containerSlot >= 0 && containerSlot < 4) {
                LoadoutLabelRenderer.render(graphics, Minecraft.getInstance().font,
                    stack, card.label, card.renderBounds.x, card.renderBounds.y,
                    card.renderBounds.width, card.renderBounds.height);
            }
            return true;
        } finally {
            graphics.pose().popPose();
        }
    }

    /** Vanilla highlight coordinates are local to the native container pose. */
    public static boolean suppressesVanillaSlotHighlight(AbstractContainerScreen<?> screen,
                                                          int x, int y) {
        State state = STATES.get(screen);
        if (screen instanceof CorpseScreen corpse
            && corpse.suppressesVanillaSlotHighlight(x, y)) return true;
        if (state == null) return false;
        for (PlayerCard card : state.playerCards.values()) {
            if (card.renderBounds.x == x && card.renderBounds.y == y) return true;
        }
        return false;
    }

    public static Slot playerSlotAt(AbstractContainerScreen<?> screen,
                                    double localX, double localY) {
        State state = STATES.get(screen);
        if (state == null) return null;
        state.snapshot = snapshot(state);
        layoutPlayerSlots(state);
        for (Map.Entry<Slot, PlayerCard> entry : state.playerCards.entrySet()) {
            if (entry.getValue().renderBounds.contains(localX, localY)) return entry.getKey();
        }
        return null;
    }

    /** Treats the whole embedded Delta surface as part of the container GUI. */
    public static boolean isInsidePanel(AbstractContainerScreen<?> screen,
                                        double mouseX, double mouseY) {
        State state = STATES.get(screen);
        return state != null && panelBounds(state).contains(mouseX, mouseY);
    }
    /** Prevents vanilla's -999 outside click from dropping carried items over the left panel. */
    public static boolean blocksOutsideDrop(AbstractContainerScreen<?> screen,
                                            double mouseX, double mouseY) {
        State state = STATES.get(screen);
        if (state == null || screen.getMenu().getCarried().isEmpty()) return false;
        // Delta storage cells are not represented by vanilla Slot instances. Once
        // the controller has had a chance to handle a real player/storage target,
        // every remaining point inside the left panel must consume the click so
        // vanilla cannot turn it into slotId -999 (including gutters and empty
        // areas inside chest-rig/backpack sections).
        return panelBounds(state).contains(mouseX, mouseY);
    }
    public static boolean keyPressed(AbstractContainerScreen<?> screen,
                                     int keyCode, int scanCode, int modifiers,
                                     double mouseX, double mouseY) {
        State state = STATES.get(screen);
        Minecraft minecraft = Minecraft.getInstance();
        if (state == null || minecraft.player == null
            || !minecraft.options.keyDrop.matches(keyCode, scanCode)
            || !screen.getMenu().getCarried().isEmpty()) return false;
        state.snapshot = snapshot(state);
        ShortcutTarget target = shortcutTargetAt(state, mouseX, mouseY);
        if (target == null || target.stack().isEmpty()) return false;
        ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
            target.identifier(),
            Screen.hasControlDown()
                ? EquippedStorageShortcutPacket.DROP_STACK
                : EquippedStorageShortcutPacket.DROP_ONE,
            target.cell()));
        return true;
    }

    public static boolean mouseClicked(AbstractContainerScreen<?> screen,
                                       double mouseX, double mouseY, int button) {
        State earlyState = STATES.get(screen);
        boolean repeatedLayoutClick = button == 0 && earlyState != null
            && isRepeatedLayoutClick(earlyState, mouseX, mouseY);
        if (repeatedLayoutClick) {
            ItemDetailOverlay.close(screen);
        }
        boolean popupHit = ItemDetailOverlay.isPopupAt(screen, mouseX, mouseY);
        boolean playerLayoutHit = button == 0 && earlyState != null
            && earlyState.snapshot != null
            && panelBounds(earlyState).contains(mouseX, mouseY);
        boolean warehouseGridHit = button == 0
            && screen instanceof PersonalWarehouseScreen warehouse
            && warehouse.warehouseGridAt(mouseX, mouseY);
        if (popupHit && ItemDetailOverlay.mouseClicked(screen, mouseX, mouseY, button)) {
            if (earlyState != null) clearItemGesture(earlyState);
            return true;
        }
        State state = earlyState;
        if (state == null || state.snapshot == null) return false;
        state.snapshot = snapshot(state);
        state.targetScroll = clamp(state.targetScroll, 0.0D, maxScroll(state));
        state.scroll = clamp(state.scroll, 0.0D, maxScroll(state));
        layoutPlayerSlots(state);
        if (!popupHit && button == 0 && ItemDetailOverlay.isOpen(screen)) {
            ItemDetailOverlay.mouseClicked(screen, mouseX, mouseY, button);
            clearDetailSelection(state);
            if (screen instanceof PersonalWarehouseScreen warehouse) {
                warehouse.clearWarehouseDetailSelection();
            }
        }
        if (button == 2 && InventorySorterCompat.middleClickSortingEnabled()
            && screen.getMenu().getCarried().isEmpty()) {
            ShortcutTarget target = shortcutTargetAt(state, mouseX, mouseY);
            if (target != null) {
                ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
                    target.identifier(), EquippedStorageShortcutPacket.SORT, target.cell()));
                return true;
            }
        }
        EditorState editor = EDITORS.get(screen);
        if (editor != null && editor.active) {
            return editorMouseClicked(screen, state, editor, mouseX, mouseY, button);
        }
        if (state.tab == EmbeddedTab.HEALTH
            && state.medicalShortcuts.click(mouseX, mouseY, button)) {
            clearItemGesture(state);
            return true;
        }
        if (button == 0 && scrollbarHit(state, mouseX, mouseY)) {
            state.draggingScrollbar = true;
            updateScrollbar(state, mouseY);
            return true;
        }
        if (button == 0 && headerTabBounds(state, false).contains(mouseX, mouseY)) {
            setTab(state, EmbeddedTab.CHARACTER);
            return true;
        }
        if (button == 0 && headerTabBounds(state, true).contains(mouseX, mouseY)) {
            setTab(state, EmbeddedTab.HEALTH);
            return true;
        }

        if (state.tab != EmbeddedTab.HEALTH
            && loadoutBounds(state.placement, 3).contains(mouseX, mouseY)) {
            clearItemGesture(state);
            if (button == 0 && screen.getMenu().getCarried().isEmpty()
                && PlayerStatusClientState.INSTANCE.canChangeBc()) {
                Minecraft.getInstance().setScreen(new KnifePickerScreen(screen));
            }
            return true;
        }
        ShortcutTarget shortcut = shortcutTargetAt(state, mouseX, mouseY);
        if (button == 0 && shortcut != null && !shortcut.stack().isEmpty()
            && screen.getMenu().getCarried().isEmpty()) {
            if (Screen.hasShiftDown()) {
                quickMoveShortcut(screen, shortcut);
                clearItemGesture(state);
                return true;
            }
            if (PlayerStatusClientState.INSTANCE.layoutClick()) {
                long now = System.currentTimeMillis();
                String key = shortcut.identifier() + ":" + shortcut.cell();
                if (key.equals(state.lastItemClickKey)
                    && now - state.lastItemClickAt <= 280L) {
                    quickMoveShortcut(screen, shortcut);
                    clearItemGesture(state);
                } else {
                    state.lastItemClickKey = key;
                    state.lastItemClickAt = now;
                    state.armedItem = shortcut;
                    state.selectedItem = shortcut;
                    state.selectedPlayerSlot = null;
                    state.selectedCarrier = null;
                    state.itemDragStartX = mouseX;
                    state.itemDragStartY = mouseY;
                    state.itemDragPickedUp = false;
                }
                return true;
            }
        }

        Map.Entry<Slot, PlayerCard> playerCard = playerCardAt(state, mouseX, mouseY);
        if (playerCard != null) {
            ItemStack carried = screen.getMenu().getCarried();
            ItemStack selected = playerCard.getKey().getItem();
            if (button == 0 && carried.isEmpty() && !selected.isEmpty()
                && PlayerStatusClientState.INSTANCE.layoutClick()) {
                long now = System.currentTimeMillis();
                String key = "player:" + playerCard.getKey().index;
                if (key.equals(state.lastItemClickKey)
                    && now - state.lastItemClickAt <= 280L) {
                    quickMovePlayerCard(screen, playerCard.getKey());
                    clearItemGesture(state);
                } else {
                    state.lastItemClickKey = key;
                    state.lastItemClickAt = now;
                    state.armedPlayerSlot = playerCard.getKey();
                    state.armedPlayerStack = selected.copy();
                    state.selectedItem = null;
                    state.selectedPlayerSlot = playerCard.getKey();
                    state.selectedCarrier = null;
                    state.itemDragStartX = mouseX;
                    state.itemDragStartY = mouseY;
                    state.itemDragPickedUp = false;

                }
                return true;
            }
            if (!carried.isEmpty() && !canPlaceInPlayerCard(
                playerCard.getKey().getContainerSlot(), carried)) {
                return button == 0 || button == 1;
            }
        }
        Section safety = safetySection(state);
        if (button == 0 && safety != null
            && safetyPinBounds(state, safety).contains(mouseX, mouseY)) {
            DeltaInventoryUiState.setSafetyBoxPinned(
                !DeltaInventoryUiState.safetyBoxPinned());
            refreshCharacterLayout(state);
            return true;
        }
        ItemStack carriedToSafety = screen.getMenu().getCarried();
        SafetyGridTarget safetyDrop = safetyGridTargetAt(state, mouseX, mouseY);
        if ((button == 0 || button == 1) && safetyDrop != null) {
            if (carriedToSafety.isEmpty()) {
                ModNetwork.sendToServer(new EquippedStorageActionPacket(
                    "safety_box", safetyDrop.anchorCell(), button, false));
            } else {
                GridBackingStore.PlacementResult placement = resolveSafetyPlacement(
                    safetyDrop, carriedToSafety, Set.of());
                if (placement.isAccepted()) {
                    ModNetwork.sendToServer(new EquippedStorageActionPacket(
                        "safety_box",
                        placement.y() * safetyDrop.columns() + placement.x(),
                        button, placement.rotated()));
                }
            }
            return true;
        }
        if (state.tab == EmbeddedTab.CHARACTER) {
            Bounds helmet = loadoutBounds(state.placement, 0);
            Bounds chest = loadoutBounds(state.placement, 1);
            int equipmentSlot = helmet.contains(mouseX, mouseY) ? 0
                : chest.contains(mouseX, mouseY) ? 1 : -1;
            if (button == 0 && equipmentSlot >= 0) {
                Minecraft minecraft = Minecraft.getInstance();
                ItemStack carried = screen.getMenu().getCarried();
                ItemStack equipped = minecraft.player == null ? ItemStack.EMPTY
                    : equipmentSlot == 0
                        ? minecraft.player.getItemBySlot(EquipmentSlot.HEAD)
                        : minecraft.player.getItemBySlot(EquipmentSlot.CHEST);
                if (carried.isEmpty() && !equipped.isEmpty()
                    && PlayerStatusClientState.INSTANCE.layoutClick()) {
                    long now = System.currentTimeMillis();
                    String key = "equipment:" + equipmentSlot;
                    if (key.equals(state.lastItemClickKey)
                        && now - state.lastItemClickAt <= 280L) {
                        if (screen instanceof PersonalWarehouseScreen) {
                            ModNetwork.sendToServer(new WarehouseSourceTransferPacket(
                                "player|" + (equipmentSlot == 0 ? 39 : 38)));
                        } else {
                            ModNetwork.sendToServer(new PlayerEquipmentSlotClickPacket(
                                equipmentSlot + 2));
                        }
                        clearItemGesture(state);
                    } else {
                        state.lastItemClickKey = key;
                        state.lastItemClickAt = now;
                        state.armedEquipmentSlot = equipmentSlot;
                        state.armedEquipmentStack = equipped.copy();
                        state.selectedItem = null;
                        state.selectedPlayerSlot = null;
                        state.selectedCarrier = null;
                        state.itemDragStartX = mouseX;
                        state.itemDragStartY = mouseY;
                        state.itemDragPickedUp = false;
                    }
                } else {
                    ModNetwork.sendToServer(new PlayerEquipmentSlotClickPacket(equipmentSlot));
                }
                return true;
            }
            if ((button == 0 || button == 1)
                && loadoutBounds(state.placement, 3).contains(mouseX, mouseY)) {
                if (button == 0 && PlayerStatusClientState.INSTANCE.canChangeBc()) {
                    Minecraft.getInstance().setScreen(new KnifePickerScreen(screen));
                }
                return true;
            }
        }
        for (String identifier : List.of("chest_rig", "backpack", "card_holder")) {
            Section section = state.snapshot.sections.get(identifier);
            Carrier carrier = state.snapshot.carriers.get(identifier);
            if (section == null || carrier == null) continue;
            Bounds selector = carrierSelector(state, section);
            if (selector.contains(mouseX, mouseY)) {
                if (button == 0) {
                    ItemStack carried = screen.getMenu().getCarried();
                    if (carried.getCount() == 1
                        && carried.getItem() instanceof DeltaPackItem pack
                        && identifier.equals(pack.slotIdentifier())) {
                        ModNetwork.sendToServer(new CurioSlotSwapPacket(identifier, 0));
                    } else if (shouldInsertFromSelector(identifier, carried)) {
                        ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
                            identifier, EquippedStorageShortcutPacket.INSERT_CARRIED, -1));
                    } else if (carried.isEmpty() && !carrier.stack().isEmpty()
                        && ("chest_rig".equals(identifier) || "backpack".equals(identifier))) {
                        long now = System.currentTimeMillis();
                        String key = "carrier:" + identifier;
                        state.lastItemClickKey = key;
                        state.lastItemClickAt = now;
                        state.armedCarrier = identifier;
                        state.armedCarrierStack = carrier.stack().copy();
                        state.selectedItem = null;
                        state.selectedPlayerSlot = null;
                        state.selectedCarrier = identifier;
                        state.itemDragStartX = mouseX;
                        state.itemDragStartY = mouseY;
                        state.itemDragPickedUp = false;
                    } else if ("card_holder".equals(identifier)) {
                        if (PlayerStatusClientState.INSTANCE.canChangeBc()) {
                            Minecraft.getInstance().setScreen(new CardHolderPickerScreen(screen));
                        }
                    } else {
                        ModNetwork.sendToServer(new CurioSlotSwapPacket(identifier, 0));
                    }
                }
                return button == 0;
            }
            StorageTarget target = storageTargetAt(state, carrier, section, mouseX, mouseY);
            if (target != null && (button == 0 || button == 1)) {
                int targetCell = target.cell;
                boolean rotated = false;
                ItemStack carried = screen.getMenu().getCarried();
                if (carrier.store != null && !carried.isEmpty()) {
                    GridBackingStore.PlacementResult placement = carrier.store.resolveCursorPlacement(
                        target.column, target.row, carried, target.fractionX, target.fractionY,
                        ClientGridRotation.allowAutoRotate(), Set.of());
                    if (!placement.isAccepted()) return true;
                    targetCell = placement.y() * carrier.columns + placement.x();
                    rotated = placement.rotated();
                }
                ModNetwork.sendToServer(new EquippedStorageActionPacket(
                    identifier, targetCell, button, rotated));
                return true;
            }
        }
        CorpseScreen.CorpseStorageTarget corpseStorage = corpseStorageTargetAt(
            screen, mouseX, mouseY);
        if ((button == 0 || button == 1) && corpseStorage != null
            && !screen.getMenu().getCarried().isEmpty()) {
            sendSourceToCorpseStorage(screen, corpseStorage, mouseX, mouseY,
                screen.getMenu().getCarried().copy(), "cursor", null);
            return true;
        }
        if (button == 0 && corpseStorage != null && !corpseStorage.stack().isEmpty()
            && screen.getMenu().getCarried().isEmpty()) {
            String sourceId = corpseStorage.sourceId(
                ((CorpseScreen) screen).getMenu().corpseEntityId());
            if (Screen.hasShiftDown()) {
                ModNetwork.sendToServer(new InventorySourceQuickMovePacket(sourceId));
                clearItemGesture(state);
                return true;
            }
            long now = System.currentTimeMillis();
            String key = "corpse-storage:" + corpseStorage.carrierSlot()
                + ":" + corpseStorage.anchor();
            if (key.equals(state.lastItemClickKey)
                && now - state.lastItemClickAt <= 280L) {
                ModNetwork.sendToServer(new InventorySourceQuickMovePacket(sourceId));
                clearItemGesture(state);
            } else {
                state.lastItemClickKey = key;
                state.lastItemClickAt = now;
                state.armedCorpseStorage = corpseStorage;
                state.armedCorpseStorageStack = corpseStorage.stack().copy();
                state.itemDragStartX = mouseX;
                state.itemDragStartY = mouseY;
                state.itemDragPickedUp = false;
            }
            return true;
        }
        Slot externalSource = externalSlotAt(screen, state, mouseX, mouseY);
        if (button == 0 && externalSource != null
            && screen.getMenu().getCarried().isEmpty()
            && !externalSource.getItem().isEmpty()) {
            if (LootSearchOverlay.isHiddenSlot(screen, externalSource)) return true;
            if (Screen.hasShiftDown()) {
                quickMoveExternal(screen, externalSource);
                clearItemGesture(state);
                return true;
            }
            if (!PlayerStatusClientState.INSTANCE.layoutClick()) return false;
            ItemDetailOverlay.close(screen);
            clearDetailSelection(state);
            long now = System.currentTimeMillis();
            String key = "external:" + externalSource.index;
            if (key.equals(state.lastItemClickKey)
                && now - state.lastItemClickAt <= 280L) {
                quickMoveExternal(screen, externalSource);
                clearItemGesture(state);
            } else {
                state.lastItemClickKey = key;
                state.lastItemClickAt = now;
                state.armedExternalSlot = externalSource;
                state.armedExternalStack = externalSource.getItem().copy();
                state.itemDragStartX = mouseX;
                state.itemDragStartY = mouseY;
                state.itemDragPickedUp = false;
            }
            return true;
        }
        if (safety != null && safetySelector(state, safety).contains(mouseX, mouseY)
            && button == 0) {
            ItemStack carried = screen.getMenu().getCarried();
            if (!carried.isEmpty() && !(carried.getItem() instanceof SafetyBoxItem)) {
                ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
                    "safety_box", EquippedStorageShortcutPacket.INSERT_CARRIED, -1));
            } else if (PlayerStatusClientState.INSTANCE.canChangeBc()) {
                Minecraft.getInstance().setScreen(new SafetyBoxPickerScreen(screen));
            } else {
                Bounds selector = safetySelector(state, safety);
                state.selectedItem = null;
                state.selectedPlayerSlot = null;
                state.selectedCarrier = null;
                ItemStack stack = safetyBox();
                ItemDetailOverlay.open(screen, stack, true,
                    TradingInventorySources.sourceIdForCurioSlot("safety_box", 0, stack),
                    selector.x(), selector.y(), selector.width(), selector.height());
            }
            return true;
        }
        if (button == 0 && insideStorageViewport(state, mouseX, mouseY)
            && !playerCardHit(state, mouseX, mouseY)) {
            state.draggingContent = true;
            state.contentDragStartY = mouseY;
            state.contentDragStartScroll = state.scroll;
            return true;
        }
        if (panelBounds(state).contains(mouseX, mouseY)
            && !playerCardHit(state, mouseX, mouseY)) {
            return button == 0 || button == 1;
        }
        return false;
    }

    public static void clearDetailSelection(AbstractContainerScreen<?> screen) {
        clearDetailSelection(STATES.get(screen));
    }

    private static void clearDetailSelection(State state) {
        if (state == null) return;
        state.selectedItem = null;
        state.selectedPlayerSlot = null;
        state.selectedCarrier = null;
        state.selectedExternalSlot = null;
        state.selectedEquipmentSlot = -1;
        state.selectedCorpseStorage = null;
    }

    private static boolean tryMoveDetailSelection(AbstractContainerScreen<?> screen, State state,
                                                   double mouseX, double mouseY) {
        ItemStack selectedStack = selectedDetailStack(state);
        boolean deltaTarget = isConcreteDeltaTarget(state, mouseX, mouseY, selectedStack);
        boolean externalTarget = externalSlotAt(screen, state, mouseX, mouseY) != null;
        boolean corpseTarget = corpseStorageTargetAt(screen, mouseX, mouseY) != null;
        boolean warehouseTarget = screen instanceof PersonalWarehouseScreen warehouse
            && warehouse.warehouseGridAt(mouseX, mouseY);
        if (!deltaTarget && !externalTarget && !corpseTarget && !warehouseTarget) return false;

        clearItemGesture(state);
        if (state.selectedItem != null) {
            state.armedItem = state.selectedItem;
        } else if (state.selectedPlayerSlot != null) {
            state.armedPlayerSlot = state.selectedPlayerSlot;
            state.armedPlayerStack = state.selectedPlayerSlot.getItem().copy();
        } else if (state.selectedCarrier != null) {
            Carrier carrier = state.snapshot.carriers.get(state.selectedCarrier);
            if (carrier == null || carrier.stack().isEmpty()) return false;
            state.armedCarrier = state.selectedCarrier;
            state.armedCarrierStack = carrier.stack().copy();
        } else if (state.selectedEquipmentSlot >= 0) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return false;
            state.armedEquipmentSlot = state.selectedEquipmentSlot;
            state.armedEquipmentStack = state.selectedEquipmentSlot == 0
                ? minecraft.player.getItemBySlot(EquipmentSlot.HEAD).copy()
                : minecraft.player.getItemBySlot(EquipmentSlot.CHEST).copy();
        } else if (state.selectedExternalSlot != null) {
            state.armedExternalSlot = state.selectedExternalSlot;
            state.armedExternalStack = state.selectedExternalSlot.getItem().copy();
        } else if (state.selectedCorpseStorage != null) {
            state.armedCorpseStorage = state.selectedCorpseStorage;
            state.armedCorpseStorageStack = state.selectedCorpseStorage.stack().copy();
        } else {
            return false;
        }
        state.itemDragPickedUp = true;
        boolean handled = mouseReleased(screen, mouseX, mouseY, 0);
        if (handled) {
            ItemDetailOverlay.close(screen);
            clearDetailSelection(state);
        }
        return handled;
    }

    private static ItemStack selectedDetailStack(State state) {
        if (state.selectedItem != null) return state.selectedItem.stack().copy();
        if (state.selectedPlayerSlot != null) return state.selectedPlayerSlot.getItem().copy();
        if (state.selectedCarrier != null) {
            Carrier carrier = state.snapshot.carriers.get(state.selectedCarrier);
            return carrier == null ? ItemStack.EMPTY : carrier.stack().copy();
        }
        if (state.selectedEquipmentSlot >= 0) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return ItemStack.EMPTY;
            return minecraft.player.getItemBySlot(state.selectedEquipmentSlot == 0
                ? EquipmentSlot.HEAD : EquipmentSlot.CHEST).copy();
        }
        if (state.selectedExternalSlot != null) return state.selectedExternalSlot.getItem().copy();
        return state.selectedCorpseStorage == null ? ItemStack.EMPTY
            : state.selectedCorpseStorage.stack().copy();
    }

    private static boolean isConcreteDeltaTarget(State state, double mouseX,
                                                  double mouseY, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (equipmentTargetAt(state, mouseX, mouseY, stack) != null
            || canInsertAtStorageSelector(state, mouseX, mouseY, stack)
            || safetyGridTargetAt(state, mouseX, mouseY) != null
            || playerCardAt(state, mouseX, mouseY) != null) return true;
        for (String identifier : List.of("chest_rig", "backpack", "card_holder")) {
            Section section = state.snapshot.sections.get(identifier);
            Carrier carrier = state.snapshot.carriers.get(identifier);
            if (section != null && carrier != null
                && storageTargetAt(state, carrier, section, mouseX, mouseY) != null) return true;
        }
        return false;
    }

    private static boolean isRepeatedLayoutClick(State state, double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        if (now - state.lastItemClickAt > 280L) return false;
        ShortcutTarget shortcut = shortcutTargetAt(state, mouseX, mouseY);
        if (shortcut != null
            && matchesRepeatedKey(state.lastItemClickKey, shortcut.identifier(), shortcut.cell())) {
            return true;
        }
        for (String identifier : state.snapshot.carriers.keySet()) {
            Section section = state.snapshot.sections.get(identifier);
            Carrier carrier = state.snapshot.carriers.get(identifier);
            if (section != null && carrier != null
                && carrierSelector(state, section).contains(mouseX, mouseY)
                && matchesPrefixKey(state.lastItemClickKey,
                    new String(new char[]{'c','a','r','r','i','e','r', ':'}) + identifier)) {
                return true;
            }
        }
        Map.Entry<Slot, PlayerCard> playerCard = playerCardAt(state, mouseX, mouseY);
        if (playerCard != null
            && matchesPrefixKey(state.lastItemClickKey,
                new String(new char[]{'p','l','a','y','e','r', ':'})
                    + playerCard.getKey().index)) return true;
        Slot external = externalSlotAt(state.screen, state, mouseX, mouseY);
        return external != null && matchesPrefixKey(
            state.lastItemClickKey, "external:" + external.index);
    }

    private static boolean matchesRepeatedKey(String key, String identifier, int cell) {
        String suffix = Integer.toString(cell);
        return matchesPrefixKey(key, identifier)
            && key.length() == identifier.length() + 1 + suffix.length()
            && key.charAt(identifier.length()) == ':'
            && key.endsWith(suffix);
    }

    private static boolean matchesPrefixKey(String key, String prefix) {
        return key != null && key.startsWith(prefix);
    }

    public static boolean mouseScrolled(AbstractContainerScreen<?> screen,
        double mouseX, double mouseY, double scrollY) {
        State state = STATES.get(screen);
        if (state == null || !insideStorageViewport(state, mouseX, mouseY)) return false;
        if (scrollY != 0.0D) {
            state.targetScroll = clamp(state.targetScroll - scrollY * scaled(state.placement, 14),
                0.0D, maxScroll(state));
        }
        return true;
    }

    public static boolean mouseDragged(AbstractContainerScreen<?> screen, double mouseX,
                                       double mouseY, int button) {
        State state = STATES.get(screen);
        if (state != null && button == 0 && state.armedCorpseStorage != null) {
            double dx = mouseX - state.itemDragStartX;
            double dy = mouseY - state.itemDragStartY;
            if (!state.itemDragPickedUp && dx * dx + dy * dy >= 9.0D) {
                state.itemDragPickedUp = true;
                ItemDetailOverlay.close(screen);
            }
            return true;
        }
        if (state != null && button == 0 && state.armedExternalSlot != null) {
            if (LootSearchOverlay.isHiddenSlot(screen, state.armedExternalSlot)) {
                clearItemGesture(state);
                return true;
            }
            double dx = mouseX - state.itemDragStartX;
            double dy = mouseY - state.itemDragStartY;
            if (!state.itemDragPickedUp && dx * dx + dy * dy >= 9.0D) {
                state.itemDragPickedUp = true;
                ItemDetailOverlay.close(screen);
            }
            // Keep the source virtual until mouseReleased, where the final
            // destination is resolved atomically. This mirrors Delta-panel
            // and player-slot drags and prevents the first drag callback from
            // being mistaken for a detail click.
            return true;
        }
        if (state != null && button == 0
            && (state.armedPlayerSlot != null || state.armedCarrier != null
                || state.armedEquipmentSlot >= 0)) {
            double dx = mouseX - state.itemDragStartX;
            double dy = mouseY - state.itemDragStartY;
            if (!state.itemDragPickedUp && dx * dx + dy * dy >= 9.0D) {
                state.itemDragPickedUp = true;
                ItemDetailOverlay.close(screen);
            }
            if (state.itemDragPickedUp) return true;
        }
        if (state != null && button == 0 && state.armedItem != null) {
            double dx = mouseX - state.itemDragStartX;
            double dy = mouseY - state.itemDragStartY;
            if (!state.itemDragPickedUp && dx * dx + dy * dy >= 9.0D) {
                state.itemDragPickedUp = true;
                ItemDetailOverlay.close(screen);
            }
            if (state.itemDragPickedUp) return true;
        }
        EditorState editor = EDITORS.get(screen);
        if (state != null && editor != null && editor.active
            && editor.dragMode != DragMode.NONE
            && button == 0) {
            int dx = (int) Math.round(mouseX - editor.dragStartX);
            int dy = (int) Math.round(mouseY - editor.dragStartY);
            if (editor.dragMode == DragMode.MOVE) {
                if (isFixedSizeEditorTarget(editor.target)) {
                    ContainerUiPositionState.setInterface(
                        editorPositionKey(screen, editor.target),
                        editor.originX + dx, editor.originY + dy);
                } else if (editor.target == EditorTarget.INTERFACE) {
                    ContainerUiPositionState.setInterface(positionKey(screen),
                        editor.originX + dx, editor.originY + dy);
                } else {
                    ContainerUiPositionState.setTexture(positionKey(screen),
                        editor.originX + (int) Math.round(dx
                            / state.placement.interfaceScaleX),
                        editor.originY + (int) Math.round(dy
                            / state.placement.interfaceScaleY));
                }
            } else {
                double widthBase = screen.getXSize()
                    * (editor.target == EditorTarget.TEXTURE
                        ? state.placement.interfaceScaleX : 1.0D);
                double heightBase = screen.getYSize()
                    * (editor.target == EditorTarget.TEXTURE
                        ? state.placement.interfaceScaleY : 1.0D);
                double scaleX = editor.originScaleX;
                double scaleY = editor.originScaleY;
                if (editor.dragMode == DragMode.RESIZE_X
                    || editor.dragMode == DragMode.RESIZE_BOTH) {
                    scaleX = editor.originScaleX + dx / Math.max(1.0D, widthBase);
                }
                if (editor.dragMode == DragMode.RESIZE_Y
                    || editor.dragMode == DragMode.RESIZE_BOTH) {
                    scaleY = editor.originScaleY + dy / Math.max(1.0D, heightBase);
                }
                if (editor.target == EditorTarget.INTERFACE) {
                    ContainerUiPositionState.setInterfaceScale(positionKey(screen),
                        scaleX, scaleY);
                } else {
                    ContainerUiPositionState.setTextureScale(positionKey(screen),
                        scaleX, scaleY);
                }
            }
            return true;
        }
        if (state != null && button == 0 && state.draggingContent) {
            double delta = mouseY - state.contentDragStartY;
            state.scroll = clamp(state.contentDragStartScroll - delta, 0.0D, maxScroll(state));
            state.targetScroll = state.scroll;
            layoutPlayerSlots(state);
            return true;
        }
        if (state == null || button != 0 || !state.draggingScrollbar) return false;
        updateScrollbar(state, mouseY);
        return true;
    }

    public static boolean mouseReleased(AbstractContainerScreen<?> screen,
                                        double mouseX, double mouseY, int button) {
        State state = STATES.get(screen);
        if (ItemDetailOverlay.mouseReleased(screen, mouseX, mouseY, button)) {
            if (state != null) clearItemGesture(state);
            return true;
        }
        if (state != null && button == 0 && state.armedCorpseStorage != null) {
            CorpseScreen.CorpseStorageTarget source = state.armedCorpseStorage;
            ItemStack stack = state.armedCorpseStorageStack.copy();
            boolean dragged = state.itemDragPickedUp;
            String sourceId = source.sourceId(((CorpseScreen) screen)
                .getMenu().corpseEntityId());
            clearItemGesture(state);
            if (!dragged) {
                Bounds bounds = corpseStorageScreenBounds(state, source);
                state.selectedCorpseStorage = source;
                ItemDetailOverlay.open(screen, stack, false, sourceId,
                    bounds.x(), bounds.y(), bounds.width(), bounds.height());
                return true;
            }
            if (discardZoneAt(screen, mouseX, mouseY)) {
                ModNetwork.sendToServer(new ItemDetailActionPacket(
                    sourceId, ItemDetailActionPacket.DISCARD));
                return true;
            }
            if (sendSourceToDelta(screen, state, mouseX, mouseY, stack, sourceId)) return true;
            CorpseScreen.CorpseStorageTarget destination = corpseStorageTargetAt(
                screen, mouseX, mouseY);
            if (destination != null) {
                sendSourceToCorpseStorage(screen, destination, mouseX, mouseY,
                    stack, sourceId, source);
                return true;
            }
            Slot external = externalSlotAt(screen, state, mouseX, mouseY);
            if (external != null) {
                ExternalDropTarget drop = externalDropTargetAt(screen, state,
                    mouseX, mouseY, stack, Set.of());
                if (drop != null) {
                    ModNetwork.sendToServer(new InventorySourceToMenuPacket(
                        sourceId, screen.getMenu().containerId, drop.slot().index,
                        drop.rotated(stack)));
                } else showNoSpaceNotice();
                return true;
            }
            showNoSpaceNotice();
            return true;
        }
        if (state != null && button == 0 && state.armedExternalSlot != null) {
            Slot source = state.armedExternalSlot;
            if (LootSearchOverlay.isHiddenSlot(screen, source)) {
                clearItemGesture(state);
                return true;
            }
            ItemStack virtualStack = state.armedExternalStack.copy();
            boolean dragged = state.itemDragPickedUp;
            clearItemGesture(state);

            if (!dragged) {
                openExternalItemDetail(screen, state, source, virtualStack);
                return true;
            }
            String sourceId = "container|" + source.index;
            if (discardZoneAt(screen, mouseX, mouseY)) {
                ModNetwork.sendToServer(new ItemDetailActionPacket(
                    sourceId, ItemDetailActionPacket.DISCARD));
                return true;
            }
            if (sendSourceToDelta(screen, state, mouseX, mouseY, virtualStack,
                sourceId)) return true;
            CorpseScreen.CorpseStorageTarget corpseTarget = corpseStorageTargetAt(
                screen, mouseX, mouseY);
            if (corpseTarget != null) {
                sendSourceToCorpseStorage(screen, corpseTarget, mouseX, mouseY,
                    virtualStack, "container|" + source.index, null);
                return true;
            }
            Slot destination = externalSlotAt(screen, state, mouseX, mouseY);
            if (destination != null && destination != source) {
                ExternalDropTarget drop = externalDropTargetAt(screen, state,
                    mouseX, mouseY, virtualStack,
                    Set.of(externalGridAnchor(state, source)));
                if (drop == null) {
                    showNoSpaceNotice();
                    return true;
                }
                ModNetwork.sendToServer(new InventorySourceToMenuPacket(
                    sourceId, screen.getMenu().containerId,
                    drop.slot().index, drop.rotated(virtualStack)));
                return true;
            }
            if (destination != source) showNoSpaceNotice();
            return true;
        }
        if (state != null && button == 0
            && (state.armedPlayerSlot != null || state.armedCarrier != null
                || state.armedEquipmentSlot >= 0)) {
            Slot playerSource = state.armedPlayerSlot;
            String carrierSource = state.armedCarrier;
            int equipmentSource = state.armedEquipmentSlot;
            ItemStack virtualStack = playerSource != null
                ? state.armedPlayerStack.copy()
                : carrierSource != null ? state.armedCarrierStack.copy()
                    : state.armedEquipmentStack.copy();
            boolean dragged = state.itemDragPickedUp;
            clearItemGesture(state);
            String playerSourceId = playerSource != null
                ? "player|" + playerSource.getContainerSlot()
                : carrierSource != null
                    ? TradingInventorySources.sourceIdForCurioSlot(
                        carrierSource, 0, virtualStack)
                    : "player|" + (equipmentSource == 0 ? 39 : 38);
            if (!dragged) {
                openDeferredItemDetail(screen, state, playerSource,
                    carrierSource, equipmentSource, virtualStack);
                return true;
            }
            if (discardZoneAt(screen, mouseX, mouseY)) {
                ModNetwork.sendToServer(new ItemDetailActionPacket(
                    playerSourceId, ItemDetailActionPacket.DISCARD));
                return true;
            }
            if (screen instanceof PersonalWarehouseScreen warehouse
                && warehouse.warehouseGridAt(mouseX, mouseY)) {
                PersonalWarehouseScreen.WarehouseDropPlacement placement =
                    warehouse.warehouseDropPlacement(virtualStack, mouseX, mouseY);
                if (placement == null) return true;
                ModNetwork.sendToServer(new WarehouseSourceTransferPacket(
                    playerSourceId, warehouse.getMenu().category().id(),
                    placement.slot(), placement.rotated()));
                return true;
            }
            WarehouseCategory warehouseTarget = warehouseCategoryAt(
                screen, mouseX, mouseY);
            if (warehouseTarget != null) {
                ModNetwork.sendToServer(new WarehouseSourceTransferPacket(
                    playerSourceId, warehouseTarget.id()));
                return true;
            }
            if (sendSourceToDelta(screen, state, mouseX, mouseY, virtualStack, playerSourceId)) return true;
            CorpseScreen.CorpseStorageTarget corpseTarget = corpseStorageTargetAt(
                screen, mouseX, mouseY);
            if (corpseTarget != null) {
                sendSourceToCorpseStorage(screen, corpseTarget, mouseX, mouseY,
                    virtualStack, playerSourceId, null);
                return true;
            }
            Slot externalTarget = externalSlotAt(screen, state, mouseX, mouseY);
            if (externalTarget != null) {
                ExternalDropTarget drop = externalDropTargetAt(
                    screen, state, mouseX, mouseY, virtualStack, Set.of());
                if (drop == null) {
                    showNoSpaceNotice();
                    return true;
                }
                ModNetwork.sendToServer(new InventorySourceToMenuPacket(
                    playerSourceId, screen.getMenu().containerId, drop.slot().index,
                    drop.rotated(virtualStack)));
                return true;
            }
            return panelBounds(state).contains(mouseX, mouseY);
        }
        if (state != null && button == 0 && state.armedItem != null) {
            ShortcutTarget deferredSource = state.armedItem;
            boolean dragged = state.itemDragPickedUp;
            clearItemGesture(state);
            String sourceId = TradingInventorySources.sourceIdForCurio(
                deferredSource.identifier(), 0, deferredSource.cell(), deferredSource.stack());
            if (!dragged) {
                openItemDetail(screen, state, deferredSource);
                return true;
            }
            if (discardZoneAt(screen, mouseX, mouseY)) {
                ModNetwork.sendToServer(new ItemDetailActionPacket(
                    sourceId, ItemDetailActionPacket.DISCARD));
                return true;
            }
            if (screen instanceof PersonalWarehouseScreen warehouse
                && warehouse.warehouseGridAt(mouseX, mouseY)) {
                PersonalWarehouseScreen.WarehouseDropPlacement placement =
                    warehouse.warehouseDropPlacement(
                        deferredSource.stack(), mouseX, mouseY);
                if (placement == null) return true;
                ModNetwork.sendToServer(new WarehouseSourceTransferPacket(
                    TradingInventorySources.sourceIdForCurio(
                        deferredSource.identifier(), 0, deferredSource.cell(),
                        deferredSource.stack()), warehouse.getMenu().category().id(),
                    placement.slot(), placement.rotated()));
                return true;
            }
            WarehouseCategory warehouseTarget = warehouseCategoryAt(
                screen, mouseX, mouseY);
            if (warehouseTarget != null) {
                ModNetwork.sendToServer(new WarehouseSourceTransferPacket(
                    TradingInventorySources.sourceIdForCurio(
                        deferredSource.identifier(), 0, deferredSource.cell(),
                        deferredSource.stack()), warehouseTarget.id()));
                return true;
            }
            if (sendSourceToDelta(screen, state, mouseX, mouseY, deferredSource.stack(),
                sourceId, deferredSource)) {
                return true;
            }
            CorpseScreen.CorpseStorageTarget corpseTarget = corpseStorageTargetAt(
                screen, mouseX, mouseY);
            if (corpseTarget != null) {
                sendSourceToCorpseStorage(screen, corpseTarget, mouseX, mouseY,
                    deferredSource.stack(), sourceId, null);
                return true;
            }
            Slot external = externalSlotAt(screen, state, mouseX, mouseY);
            if (external != null) {
                ExternalDropTarget drop = externalDropTargetAt(
                    screen, state, mouseX, mouseY, deferredSource.stack(), Set.of());
                if (drop == null) {
                    showNoSpaceNotice();
                    return true;
                }
                ModNetwork.sendToServer(new InventorySourceToMenuPacket(
                    sourceId, screen.getMenu().containerId, drop.slot().index,
                    drop.rotated(deferredSource.stack())));
                return true;
            }
            return panelBounds(state).contains(mouseX, mouseY);
        }
        if (state != null && button == 0
            && !screen.getMenu().getCarried().isEmpty()
            && panelBounds(state).contains(mouseX, mouseY)) {
            ItemStack carried = screen.getMenu().getCarried().copy();
            if (!dropCarriedAt(screen, mouseX, mouseY, carried)) {
                showNoSpaceNotice();
            }
            return true;
        }
        EditorState editor = EDITORS.get(screen);
        if (editor != null && editor.dragMode != DragMode.NONE && button == 0) {
            editor.dragMode = DragMode.NONE;
            return true;
        }
        if (state != null && button == 0 && state.draggingContent) {
            state.draggingContent = false;
            return true;
        }
        if (state == null || button != 0 || !state.draggingScrollbar) return false;
        state.draggingScrollbar = false;
        return true;
    }

    /** Sends a source-to-Delta drop without exposing an intermediate server cursor. */
    private static boolean sendSourceToDelta(AbstractContainerScreen<?> screen, State state,
                                             double mouseX, double mouseY, ItemStack stack,
                                             String sourceId) {
        return sendSourceToDelta(screen, state, mouseX, mouseY, stack, sourceId, null);
    }

    private static boolean sendSourceToDelta(AbstractContainerScreen<?> screen, State state,
                                             double mouseX, double mouseY, ItemStack stack,
                                             String sourceId, ShortcutTarget storageSource) {
        if (state == null || stack == null || stack.isEmpty() || sourceId == null || sourceId.isBlank()) return false;
        EquipmentTarget equipment = equipmentTargetAt(state, mouseX, mouseY, stack);
        if (equipment != null) {
            if (equipment.curioIdentifier() != null && !equipment.curioIdentifier().isBlank()) {
                ModNetwork.sendToServer(new CarrierReplacePacket(sourceId));
                return true;
            }
            int inventoryIndex = equipment.equipmentIndex() == 0 ? 39 : 38;
            if (!("player|" + inventoryIndex).equals(sourceId)) {
                Slot target = playerInventorySlot(state, inventoryIndex);
                if (target != null) {
                    ModNetwork.sendToServer(new InventorySourceToMenuPacket(
                        sourceId, screen.getMenu().containerId, target.index, false));
                }
            }
            return true;
        }
        Section safety = safetySection(state);
        if (safety != null && safetySelector(state, safety).contains(mouseX, mouseY)) {
            ModNetwork.sendToServer(new InventorySourceToEquippedStoragePacket(
                sourceId, "safety_box", -1, false));
            return true;
        }
        SafetyGridTarget safetyTarget = safetyGridTargetAt(state, mouseX, mouseY);
        if (safetyTarget != null) {
            GridBackingStore.PlacementResult placement = resolveSafetyPlacement(
                safetyTarget, stack, ignoredStorageAnchors(storageSource, "safety_box"));
            placement = resolveCachedPreviewPlacement(state, "safety_box",
                safetyTarget.target().cell(), storageSource == null ? -1 : storageSource.cell(),
                stack, placement);
            if (!placement.isAccepted()) return true;
            ModNetwork.sendToServer(new InventorySourceToEquippedStoragePacket(
                sourceId, "safety_box",
                placement.y() * safetyTarget.columns() + placement.x(), placement.rotated()));
            return true;
        }
        Map.Entry<Slot, PlayerCard> pocketCard = playerCardAt(state, mouseX, mouseY);
        if (pocketCard != null) {
            int inventorySlot = pocketCard.getKey().getContainerSlot();
            ItemSize size = ClientDataCache.INSTANCE.getSize(stack);
            if (inventorySlot >= 4 && inventorySlot <= 8) {
                if (size.width() == 1 && size.height() == 1) {
                    ModNetwork.sendToServer(new InventorySourceToEquippedStoragePacket(
                        sourceId, "pockets", inventorySlot, false));
                }
                return true;
            }
        }
        for (String identifier : List.of("chest_rig", "backpack", "card_holder")) {
            Section section = state.snapshot.sections.get(identifier);
            Carrier carrier = state.snapshot.carriers.get(identifier);
            if (section == null || carrier == null) continue;
            if (carrierSelector(state, section).contains(mouseX, mouseY)) {
                if (stack.getItem() instanceof com.xtdpotato.xero_delta.item.DeltaPackItem pack
                    && identifier.equals(pack.slotIdentifier())) {
                    ModNetwork.sendToServer(new CarrierReplacePacket(sourceId));
                } else if (shouldInsertFromSelector(identifier, stack)) {
                    ModNetwork.sendToServer(new InventorySourceToEquippedStoragePacket(
                        sourceId, identifier, -1, false));
                }
                return true;
            }
            StorageTarget target = storageTargetAt(state, carrier, section, mouseX, mouseY);
            if (target == null) continue;
            int cell = target.cell();
            boolean rotated = GridBackingStore.isRotated(stack);
            if (carrier.store() != null) {
                Set<Integer> ignoredAnchors = ignoredStorageAnchors(
                    storageSource, identifier);
                var placement = carrier.store().resolveCursorPlacement(target.column(), target.row(),
                    stack, target.fractionX(), target.fractionY(),
                    ClientGridRotation.allowAutoRotate(), ignoredAnchors);
                placement = resolveCachedPreviewPlacement(state, identifier, target.cell(),
                    storageSource == null ? -1 : storageSource.cell(), stack, placement);
                if (!placement.isAccepted()) return true;
                cell = placement.y() * carrier.columns() + placement.x();
                rotated = placement.rotated();
            }
            ModNetwork.sendToServer(new InventorySourceToEquippedStoragePacket(
                sourceId, identifier, cell, rotated));
            return true;
        }
        Map.Entry<Slot, PlayerCard> playerCard = playerCardAt(state, mouseX, mouseY);
        if (playerCard != null) {
            ModNetwork.sendToServer(new InventorySourceToMenuPacket(
                sourceId, screen.getMenu().containerId, playerCard.getKey().index, false));
            return true;
        }
        return false;
    }

    private static Slot playerInventorySlot(State state, int inventoryIndex) {
        if (state == null) return null;
        for (Slot slot : state.playerSlots) {
            if (slot.getContainerSlot() == inventoryIndex) return slot;
        }
        return null;
    }

    private static Set<Integer> ignoredStorageAnchors(ShortcutTarget source,
                                                       String destinationIdentifier) {
        return source != null && destinationIdentifier != null
            && destinationIdentifier.equals(source.identifier())
            ? Set.of(source.cell()) : Set.of();
    }

    private static void openDeferredItemDetail(AbstractContainerScreen<?> screen, State state,
                                               Slot playerSource, String carrierSource,
                                               int equipmentSource, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (playerSource != null) {
            PlayerCard card = state.playerCards.get(playerSource);
            if (card == null) return;
            state.selectedItem = null;
            state.selectedPlayerSlot = playerSource;
            state.selectedCarrier = null;
            Bounds bounds = card.screenBounds();
            ItemDetailOverlay.open(screen, stack, false,
                "player|" + playerSource.getContainerSlot(),
                bounds.x(), bounds.y(), bounds.width(), bounds.height());
            return;
        }
        if (carrierSource != null) {
            Section section = state.snapshot.sections.get(carrierSource);
            if (section == null) return;
            Bounds bounds = carrierSelector(state, section);
            state.selectedItem = null;
            state.selectedPlayerSlot = null;
            state.selectedCarrier = carrierSource;
            ItemDetailOverlay.open(screen, stack, false,
                TradingInventorySources.sourceIdForCurioSlot(carrierSource, 0, stack),
                bounds.x(), bounds.y(), bounds.width(), bounds.height());
            return;
        }
        if (equipmentSource >= 0) {
            Bounds bounds = loadoutBounds(state.placement, equipmentSource);
            state.selectedItem = null;
            state.selectedPlayerSlot = null;
            state.selectedCarrier = null;
            state.selectedEquipmentSlot = equipmentSource;
            ItemDetailOverlay.open(screen, stack, false,
                "player|" + (equipmentSource == 0 ? 39 : 38),
                bounds.x(), bounds.y(), bounds.width(), bounds.height());
        }
    }

    private static void openExternalItemDetail(AbstractContainerScreen<?> screen, State state,
                                               Slot source, ItemStack stack) {
        if (source == null || stack == null || stack.isEmpty()) return;
        Bounds bounds = externalSlotBounds(state, source);
        clearDetailSelection(state);
        state.selectedExternalSlot = source;
        ItemDetailOverlay.open(screen, stack, false, "container|" + source.index,
            bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    private static void quickMoveExternal(AbstractContainerScreen<?> screen, Slot source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null || source == null) return;
        ModNetwork.sendToServer(new InventorySourceQuickMovePacket("container|" + source.index));
    }

    private static void showNoSpaceNotice() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(
                "storage.xero_delta.no_space_move"), true);
        }
    }

    private static void clearItemGesture(State state) {
        state.armedItem = null;
        state.armedPlayerSlot = null;
        state.armedPlayerStack = ItemStack.EMPTY;
        state.armedCarrier = null;
        state.armedCarrierStack = ItemStack.EMPTY;
        state.armedEquipmentSlot = -1;
        state.armedEquipmentStack = ItemStack.EMPTY;
        state.armedExternalSlot = null;
        state.armedExternalStack = ItemStack.EMPTY;
        state.armedCorpseStorage = null;
        state.armedCorpseStorageStack = ItemStack.EMPTY;
        state.itemDragPickedUp = false;
    }

    private static CorpseScreen.CorpseStorageTarget corpseStorageTargetAt(
        AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (!(screen instanceof CorpseScreen corpse)) return null;
        double nativeX = nativeMouseX(screen, mouseX) - screen.getGuiLeft();
        double nativeY = nativeMouseY(screen, mouseY) - screen.getGuiTop();
        return corpse.corpseStorageTargetAt(nativeX, nativeY);
    }

    private static Bounds corpseStorageScreenBounds(
        State state, CorpseScreen.CorpseStorageTarget target) {
        int x = screenMouseX(state.placement, target.x());
        int y = screenMouseY(state.placement, target.y());
        int right = screenMouseX(state.placement, target.x() + target.width());
        int bottom = screenMouseY(state.placement, target.y() + target.height());
        return new Bounds(x, y, Math.max(1, right - x), Math.max(1, bottom - y));
    }

    private static void sendSourceToCorpseStorage(
        AbstractContainerScreen<?> screen,
        CorpseScreen.CorpseStorageTarget target,
        double mouseX, double mouseY, ItemStack stack, String sourceId,
        CorpseScreen.CorpseStorageTarget source) {
        int column = target.cell() % target.store().getWidth();
        int row = target.cell() / target.store().getWidth();
        Set<Integer> ignored = source != null
            && source.carrierSlot() == target.carrierSlot()
            ? Set.of(source.anchor()) : Set.of();
        GridBackingStore.PlacementResult placement = target.store().resolveCursorPlacement(
            column, row, stack, target.fractionX(), target.fractionY(),
            ClientGridRotation.allowAutoRotate(), ignored);
        if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE
            && placement.status() != GridBackingStore.PlacementStatus.CAN_STACK) {
            showNoSpaceNotice();
            return;
        }
        CorpseScreen corpse = (CorpseScreen) screen;
        ModNetwork.sendToServer(new CorpseStorageTransferPacket(sourceId,
            corpse.getMenu().corpseEntityId(), target.carrierSlot(),
            placement.y() * target.store().getWidth() + placement.x(),
            placement.rotated()));
    }

    private static boolean dropDeferredCursorAt(AbstractContainerScreen<?> screen, State state,
                                                double mouseX, double mouseY,
                                                ItemStack virtualStack) {
        if (dropIntoEquipmentTarget(state, mouseX, mouseY, virtualStack)) return true;
        if (dropIntoStorageSelector(state, mouseX, mouseY, virtualStack)) return true;
        SafetyGridTarget safetyTarget = safetyGridTargetAt(state, mouseX, mouseY);
        if (safetyTarget != null) {
            GridBackingStore.PlacementResult placement = resolveSafetyPlacement(
                safetyTarget, virtualStack, Set.of());
            placement = resolveCachedPreviewPlacement(state, "safety_box",
                safetyTarget.target().cell(), -1, virtualStack, placement);
            if (placement.isAccepted()) {
                ModNetwork.sendToServer(new EquippedStorageActionPacket(
                    "safety_box",
                    placement.y() * safetyTarget.columns() + placement.x(),
                    0, placement.rotated()));
            }
            return true;
        }
        for (String identifier : List.of("chest_rig", "backpack", "card_holder")) {
            Section section = state.snapshot.sections.get(identifier);
            Carrier carrier = state.snapshot.carriers.get(identifier);
            if (section == null || carrier == null) continue;
            StorageTarget target = storageTargetAt(state, carrier, section, mouseX, mouseY);
            if (target == null) continue;
            int cell = target.cell();
            boolean rotated = GridBackingStore.isRotated(virtualStack);
            if (carrier.store() != null) {
                GridBackingStore.PlacementResult placement = carrier.store().resolveCursorPlacement(
                    target.column(), target.row(), virtualStack, target.fractionX(), target.fractionY(),
                    ClientGridRotation.allowAutoRotate(), Set.of());
                placement = resolveCachedPreviewPlacement(state, identifier, target.cell(),
                    -1, virtualStack, placement);
                if (!placement.isAccepted()) return true;
                cell = placement.y() * carrier.columns() + placement.x();
                rotated = placement.rotated();
            }
            ModNetwork.sendToServer(new EquippedStorageActionPacket(identifier, cell, 0, rotated));
            return true;
        }
        Map.Entry<Slot, PlayerCard> playerCard = playerCardAt(state, mouseX, mouseY);
        Minecraft minecraft = Minecraft.getInstance();
        if (playerCard != null && minecraft.player != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,
                playerCard.getKey().index, 0, net.minecraft.world.inventory.ClickType.PICKUP,
                minecraft.player);
            return true;
        }
        Slot external = externalSlotAt(screen, state, mouseX, mouseY);
        if (external != null) {
            ExternalDropTarget drop = externalDropTargetAt(
                screen, state, mouseX, mouseY, virtualStack, Set.of());
            if (drop == null) showNoSpaceNotice();
            else commitExternalDrop(screen, drop);
            return true;
        }
        return panelBounds(state).contains(mouseX, mouseY);
    }

    public static boolean canAcceptCarriedDrop(AbstractContainerScreen<?> screen,
                                               double mouseX, double mouseY,
                                               ItemStack virtualStack) {
        State state = STATES.get(screen);
        if (state == null || virtualStack == null || virtualStack.isEmpty()) return false;
        state.snapshot = snapshot(state);
        layoutPlayerSlots(state);
        if (equipmentTargetAt(state, mouseX, mouseY, virtualStack) != null) return true;
        if (canInsertAtStorageSelector(state, mouseX, mouseY, virtualStack)) return true;

        SafetyGridTarget safetyTarget = safetyGridTargetAt(state, mouseX, mouseY);
        if (safetyTarget != null && resolveSafetyPlacement(
            safetyTarget, virtualStack, Set.of()).isAccepted()) return true;

        for (String identifier : List.of("chest_rig", "backpack", "card_holder")) {
            Section section = state.snapshot.sections.get(identifier);
            Carrier carrier = state.snapshot.carriers.get(identifier);
            if (section == null || carrier == null) continue;
            StorageTarget target = storageTargetAt(state, carrier, section, mouseX, mouseY);
            if (target == null) continue;
            if (carrier.store() == null) return true;
            return carrier.store().resolveCursorPlacement(
                target.column(), target.row(), virtualStack,
                target.fractionX(), target.fractionY(),
                ClientGridRotation.allowAutoRotate(), Set.of()).isAccepted();
        }

        Map.Entry<Slot, PlayerCard> playerCard = playerCardAt(state, mouseX, mouseY);
        return playerCard != null && canPlaceInPlayerCard(
            playerCard.getKey().getContainerSlot(), virtualStack);
    }

    /** Resolves a warehouse drag over the equipped safety-box grid without
     * first moving the warehouse stack onto the vanilla menu cursor. */
    public static WarehouseSafetyBoxDrop warehouseSafetyBoxDrop(
        AbstractContainerScreen<?> screen, double mouseX, double mouseY,
        ItemStack virtualStack) {
        State state = STATES.get(screen);
        if (state == null || virtualStack == null || virtualStack.isEmpty()) return null;
        state.snapshot = snapshot(state);
        layoutPlayerSlots(state);
        Section safety = safetySection(state);
        if (safety != null && safetySelector(state, safety).contains(mouseX, mouseY)
            && !(virtualStack.getItem() instanceof SafetyBoxItem)) {
            return new WarehouseSafetyBoxDrop(-1, false, true);
        }
        SafetyGridTarget target = safetyGridTargetAt(state, mouseX, mouseY);
        if (target == null) return null;
        GridBackingStore.PlacementResult placement = resolveSafetyPlacement(
            target, virtualStack, Set.of());
        boolean accepted = placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE;
        if (placement.status() == GridBackingStore.PlacementStatus.CAN_STACK) {
            ItemStack existing = target.store().getItemRaw(placement.x(), placement.y());
            accepted = !existing.isEmpty()
                && virtualStack.getCount() <= existing.getMaxStackSize() - existing.getCount();
        }
        return new WarehouseSafetyBoxDrop(
            placement.y() * target.columns() + placement.x(),
            placement.rotated(), accepted);
    }

    public static boolean dropCarriedAt(AbstractContainerScreen<?> screen,
                                        double mouseX, double mouseY,
                                        ItemStack virtualStack) {
        State state = STATES.get(screen);
        if (state == null || !canAcceptCarriedDrop(
            screen, mouseX, mouseY, virtualStack)) return false;
        return dropDeferredCursorAt(screen, state, mouseX, mouseY, virtualStack);
    }

    /** Optional loot-list integration uses the same targets as an ordinary drag. */
    public static PlayerStatusScreen.BetterLootingDropTarget betterLootingDropTargetAt(
        AbstractContainerScreen<?> screen, double mouseX, double mouseY, ItemStack stack) {
        State state = STATES.get(screen);
        if (state == null || stack == null || stack.isEmpty()) return null;
        state.snapshot = snapshot(state);
        layoutPlayerSlots(state);
        EquipmentTarget equipment = equipmentTargetAt(state, mouseX, mouseY, stack);
        if (equipment != null) {
            return new PlayerStatusScreen.BetterLootingDropTarget(
                equipment.equipmentIndex() == 0 ? "helmet"
                    : equipment.equipmentIndex() == 1 ? "chest" : equipment.curioIdentifier(),
                -1, false);
        }
        Map.Entry<Slot, PlayerCard> card = playerCardAt(state, mouseX, mouseY);
        if (card != null) {
            int index = card.getKey().getContainerSlot();
            return index >= 4 && index <= 8 && canPlaceInPlayerCard(index, stack)
                ? new PlayerStatusScreen.BetterLootingDropTarget("pockets", index, false) : null;
        }
        SafetyGridTarget safety = safetyGridTargetAt(state, mouseX, mouseY);
        if (safety != null) {
            var placement = resolveSafetyPlacement(safety, stack, Set.of());
            return placement.isAccepted() ? new PlayerStatusScreen.BetterLootingDropTarget(
                "safety_box", placement.y() * safety.columns() + placement.x(),
                placement.rotated()) : null;
        }
        for (String identifier : state.snapshot.carriers.keySet()) {
            Carrier carrier = state.snapshot.carriers.get(identifier);
            Section section = state.snapshot.sections.get(identifier);
            StorageTarget target = storageTargetAt(state, carrier, section, mouseX, mouseY);
            if (target == null) continue;
            if (carrier.store() == null) {
                return new PlayerStatusScreen.BetterLootingDropTarget(identifier, target.cell(), false);
            }
            var placement = carrier.store().resolveCursorPlacement(target.column(), target.row(),
                stack, target.fractionX(), target.fractionY(), ClientGridRotation.allowAutoRotate(), Set.of());
            return placement.isAccepted() ? new PlayerStatusScreen.BetterLootingDropTarget(
                identifier, placement.y() * carrier.columns() + placement.x(), placement.rotated()) : null;
        }
        return null;
    }

    private static boolean dropIntoEquipmentTarget(State state, double mouseX,
                                                   double mouseY, ItemStack stack) {
        EquipmentTarget target = equipmentTargetAt(state, mouseX, mouseY, stack);
        if (target == null) return false;
        if (target.equipmentIndex() >= 0) {
            ModNetwork.sendToServer(new PlayerEquipmentSlotClickPacket(
                target.equipmentIndex()));
        } else {
            ModNetwork.sendToServer(new CurioSlotSwapPacket(target.curioIdentifier(), 0));
        }
        return true;
    }

    private static boolean canInsertAtStorageSelector(State state, double mouseX,
                                                       double mouseY, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Section safety = safetySection(state);
        if (safety != null && safetySelector(state, safety).contains(mouseX, mouseY)
            && !(stack.getItem() instanceof SafetyBoxItem)) {
            return true;
        }
        for (String identifier : state.snapshot.carriers.keySet()) {
            Section section = state.snapshot.sections.get(identifier);
            Carrier carrier = state.snapshot.carriers.get(identifier);
            if (section == null || carrier == null
                || !carrierSelector(state, section).contains(mouseX, mouseY)) continue;
            if (new String(new char[]{'c','a','r','d','_','h','o','l','d','e','r'})
                .equals(identifier)) return false;
            return shouldInsertFromSelector(identifier, stack);
        }
        return false;
    }

    private static boolean dropIntoStorageSelector(State state, double mouseX,
                                                    double mouseY, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Section safety = safetySection(state);
        if (safety != null && safetySelector(state, safety).contains(mouseX, mouseY)
            && !(stack.getItem() instanceof SafetyBoxItem)) {
            ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
                new String(new char[]{'s','a','f','e','t','y','_','b','o','x'}),
                EquippedStorageShortcutPacket.INSERT_CARRIED, -1));
            return true;
        }
        for (String identifier : state.snapshot.carriers.keySet()) {
            Section section = state.snapshot.sections.get(identifier);
            Carrier carrier = state.snapshot.carriers.get(identifier);
            if (section == null || carrier == null
                || !carrierSelector(state, section).contains(mouseX, mouseY)) continue;
            if (shouldInsertFromSelector(identifier, stack)) {
                ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
                    identifier, EquippedStorageShortcutPacket.INSERT_CARRIED, -1));
                return true;
            }
        }
        return false;
    }

    private static EquipmentTarget equipmentTargetAt(State state, double mouseX,
                                                      double mouseY, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || stack.isEmpty() || stack.getCount() != 1) return null;
        if (state.tab == EmbeddedTab.CHARACTER) {
            for (int index = 0; index < 2; index++) {
                EquipmentSlot slot = index == 0 ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
                if (loadoutBounds(state.placement, index).contains(mouseX, mouseY)
                    && PlayerEquipmentSync.canEquip(minecraft.player, stack, slot)) {
                    return new EquipmentTarget(index, "");
                }
            }
        }
        if (!(stack.getItem() instanceof DeltaPackItem pack)) return null;
        for (String identifier : List.of("chest_rig", "backpack")) {
            Section section = state.snapshot.sections.get(identifier);
            if (section != null && identifier.equals(pack.slotIdentifier())
                && carrierSelector(state, section).contains(mouseX, mouseY)) {
                return new EquipmentTarget(-1, identifier);
            }
        }
        return null;
    }
    private static Bounds externalSlotBounds(State state, Slot slot) {
        Placement placement = state.placement;
        // Corpse presentation cards already expose their complete visual bounds.
        // Do this before external grid-anchor resolution: treating a chest-rig or
        // backpack card as a normal grid item expands the detail anchor to the
        // item's configured footprint and visibly shifts the selection frame.
        if (state.screen instanceof CorpseScreen corpse) {
            int[] bounds = corpse.corpsePresentationBounds(slot);
            if (bounds != null) {
                int nativeX = corpse.getGuiLeft() + bounds[0];
                int nativeY = corpse.getGuiTop() + bounds[1];
                int right = nativeX + bounds[2];
                int bottom = nativeY + bounds[3];
                int x = screenMouseX(placement, nativeX);
                int y = screenMouseY(placement, nativeY);
                return new Bounds(x, y,
                    Math.max(1, screenMouseX(placement, right) - x),
                    Math.max(1, screenMouseY(placement, bottom) - y));
            }
        }
        Slot anchor = externalGridAnchor(state, slot);
        if (state.screen instanceof GroundPackScreen ground) {
            int[] bounds = ground.groundPackSlotBounds(anchor);
            if (bounds != null) {
                int x = (int) Math.round(placement.nativeLeft
                    + bounds[0] * placement.interfaceScaleX);
                int y = (int) Math.round(placement.nativeTop
                    + bounds[1] * placement.interfaceScaleY);
                int right = (int) Math.round(placement.nativeLeft
                    + (bounds[0] + bounds[2]) * placement.interfaceScaleX);
                int bottom = (int) Math.round(placement.nativeTop
                    + (bounds[1] + bounds[3]) * placement.interfaceScaleY);
                return new Bounds(x, y, Math.max(1, right - x),
                    Math.max(1, bottom - y));
            }
        }
        int minX = anchor.x;
        int minY = anchor.y;
        int maxRight = anchor.x + ContainerGridHelper.SLOT_FACE_SIZE;
        int maxBottom = anchor.y + ContainerGridHelper.SLOT_FACE_SIZE;

        ItemStack stack = anchor.getItem();
        if (!stack.isEmpty() && externalGridEnabled(state)) {
            ItemSize size = ContainerGridHelper.orientedSize(stack,
                GridBackingStore.isRotated(stack), ClientDataCache.INSTANCE::getSize);
            Set<Slot> cells = ContainerGridHelper.footprintCells(
                state.screen.getMenu(), anchor, size);
            if (cells.size() == size.width() * size.height()) {
                minX = cells.stream().mapToInt(cell -> cell.x).min().orElse(minX);
                minY = cells.stream().mapToInt(cell -> cell.y).min().orElse(minY);
                maxRight = cells.stream().mapToInt(cell -> cell.x).max().orElse(anchor.x)
                    + ContainerGridHelper.SLOT_FACE_SIZE;
                maxBottom = cells.stream().mapToInt(cell -> cell.y).max().orElse(anchor.y)
                    + ContainerGridHelper.SLOT_FACE_SIZE;
            }
        }

        int x = (int) Math.round(placement.nativeLeft
            + minX * placement.interfaceScaleX);
        int y = (int) Math.round(placement.nativeTop
            + minY * placement.interfaceScaleY);
        int right = (int) Math.round(placement.nativeLeft
            + maxRight * placement.interfaceScaleX);
        int bottom = (int) Math.round(placement.nativeTop
            + maxBottom * placement.interfaceScaleY);
        return new Bounds(x, y, Math.max(1, right - x), Math.max(1, bottom - y));
    }

    private static Slot externalSlotAt(AbstractContainerScreen<?> screen, State state,
                                       double mouseX, double mouseY) {
        double nativeX = nativeMouseX(screen, mouseX) - screen.getGuiLeft();
        double nativeY = nativeMouseY(screen, mouseY) - screen.getGuiTop();
        if (screen instanceof GroundPackScreen ground) {
            Slot carrier = ground.groundCarrierSlotAt(nativeX, nativeY);
            if (carrier != null) return carrier;
        }
        if (screen instanceof CorpseScreen corpse) {
            Slot corpseSlot = corpse.corpsePresentationSlotAt(nativeX, nativeY);
            return corpseSlot == null || LootSearchOverlay.isHiddenSlot(screen, corpseSlot)
                ? null : corpseSlot;
        }
        var menu = screen.getMenu();
        if (externalGridEnabled(state)) {
            ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
                ClientDataCache.INSTANCE.revision());
            Slot hit = ContainerGridHelper.hitSlot(menu, nativeX, nativeY);
            if (isExternalSlot(hit)) {
                Slot anchor = ContainerGridHelper.footprintAnchorFor(
                    menu, hit, ClientDataCache.INSTANCE::getSize);
                Slot resolved = anchor != null ? anchor : hit;
                return LootSearchOverlay.isHiddenSlot(screen, resolved) ? null : resolved;
            }
            if (hit != null) return null;

            // The last row/column ends at the visible 16 px face while inner
            // footprint gutters use the complete 18 px step. This fallback
            // mirrors the sized-item renderer exactly.
            for (Slot anchor : menu.slots) {
                if (!isExternalSlot(anchor) || anchor.getItem().isEmpty()
                    || !ContainerGridHelper.isGridSlotEnabled(menu, anchor)) continue;
                ItemSize size = ContainerGridHelper.orientedSize(anchor.getItem(),
                    ClientDataCache.INSTANCE::getSize);
                if (size.width() <= 1 && size.height() <= 1) continue;
                Set<Slot> cells = ContainerGridHelper.footprintCells(menu, anchor, size);
                if (cells.size() != size.width() * size.height()) continue;
                int maxX = cells.stream().mapToInt(cell -> cell.x).max().orElse(anchor.x);
                int maxY = cells.stream().mapToInt(cell -> cell.y).max().orElse(anchor.y);
                for (Slot cell : cells) {
                    int right = cell.x + (cell.x == maxX
                        ? ContainerGridHelper.SLOT_FACE_SIZE : ContainerGridHelper.SLOT_STEP);
                    int bottom = cell.y + (cell.y == maxY
                        ? ContainerGridHelper.SLOT_FACE_SIZE : ContainerGridHelper.SLOT_STEP);
                    if (nativeX >= cell.x && nativeX < right
                        && nativeY >= cell.y && nativeY < bottom) {
                        return LootSearchOverlay.isHiddenSlot(screen, anchor) ? null : anchor;
                    }
                }
            }
        }
        for (Slot slot : menu.slots) {
            if (!isExternalSlot(slot)) continue;
            if (nativeX >= slot.x && nativeX < slot.x + ContainerGridHelper.SLOT_FACE_SIZE
                && nativeY >= slot.y && nativeY < slot.y + ContainerGridHelper.SLOT_FACE_SIZE) {
                return LootSearchOverlay.isHiddenSlot(screen, slot) ? null : slot;
            }
        }
        return null;
    }

    private static boolean externalGridEnabled(State state) {
        return state != null && ClientDataCache.INSTANCE.isItemGridEnabled(
            state.screen.getMenu().getClass().getName());
    }

    private static boolean isExternalSlot(Slot slot) {
        return slot != null && slot.isActive()
            && !(slot.container instanceof net.minecraft.world.entity.player.Inventory);
    }

    private static Slot externalGridAnchor(State state, Slot slot) {
        if (!isExternalSlot(slot) || !externalGridEnabled(state)) return slot;
        var menu = state.screen.getMenu();
        ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
            ClientDataCache.INSTANCE.revision());
        Slot anchor = ContainerGridHelper.footprintAnchorFor(
            menu, slot, ClientDataCache.INSTANCE::getSize);
        return anchor != null ? anchor : slot;
    }

    private static ExternalDropTarget externalDropTargetAt(
        AbstractContainerScreen<?> screen, State state, double mouseX, double mouseY,
        ItemStack stack, Set<Slot> ignoredAnchors) {
        if (state == null || stack == null || stack.isEmpty()) return null;
        var menu = screen.getMenu();
        double nativeX = nativeMouseX(screen, mouseX) - screen.getGuiLeft();
        double nativeY = nativeMouseY(screen, mouseY) - screen.getGuiTop();
        Slot hovered = null;
        if (screen instanceof CorpseScreen corpse) {
            // Corpse presentation slots use the custom enlarged hitboxes; do not
            // run them through the native grid coordinate transform.
            hovered = corpse.corpsePresentationSlotAt(nativeX, nativeY);
            if (hovered == null || LootSearchOverlay.isHiddenSlot(screen, hovered)
                || !hovered.mayPlace(stack)) return null;
            return new ExternalDropTarget(hovered, null);
        }
        if (externalGridEnabled(state)) {
            ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
                ClientDataCache.INSTANCE.revision());
            hovered = ContainerGridHelper.hitSlot(menu, nativeX, nativeY);
        }
        if (!isExternalSlot(hovered)) {
            hovered = externalSlotAt(screen, state, mouseX, mouseY);
        }
        if (!isExternalSlot(hovered)
            || LootSearchOverlay.isHiddenSlot(screen, hovered)) return null;

        if (externalGridEnabled(state)
            && ContainerGridHelper.isGridSlotEnabled(menu, hovered)) {
            double fractionX = Math.max(0.0D, Math.min(0.999D,
                (nativeX - hovered.x) / ContainerGridHelper.SLOT_STEP));
            double fractionY = Math.max(0.0D, Math.min(0.999D,
                (nativeY - hovered.y) / ContainerGridHelper.SLOT_STEP));
            var placement = ContainerGridHelper.resolveCursorPlacement(
                menu, hovered, stack, fractionX, fractionY,
                ClientGridRotation.allowAutoRotate(),
                ignoredAnchors == null ? Set.of() : ignoredAnchors,
                ClientDataCache.INSTANCE::getSize);
            if (placement == null || !placement.isAccepted()
                || !isExternalSlot(placement.anchor())) return null;
            return new ExternalDropTarget(placement.anchor(), placement);
        }
        return hovered.mayPlace(stack)
            ? new ExternalDropTarget(hovered, null) : null;
    }

    private static void commitExternalDrop(AbstractContainerScreen<?> screen,
                                           ExternalDropTarget target) {
        if (target == null || target.slot() == null) return;
        if (target.placement() != null) {
            ContainerGridClickHandler.commitPlacement(
                screen.getMenu(), target.placement(), 0);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryMouseClick(
                screen.getMenu().containerId, target.slot().index, 0,
                net.minecraft.world.inventory.ClickType.PICKUP, minecraft.player);
        }
    }

    private record ExternalDropTarget(
        Slot slot, ContainerGridHelper.PlacementResult placement) {
        private boolean rotated(ItemStack stack) {
            return placement != null ? placement.rotated()
                : GridBackingStore.isRotated(stack);
        }
    }

    private static WarehouseCategory warehouseCategoryAt(
        AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        return screen instanceof PersonalWarehouseScreen warehouse
            ? warehouse.warehouseCategoryAt(mouseX, mouseY) : null;
    }

    public static void renderInteractionOverlay(AbstractContainerScreen<?> screen,
                                                GuiGraphics graphics, int mouseX, int mouseY) {
        State state = STATES.get(screen);
        if (state == null || state.snapshot == null) return;
        // The warehouse grid is rendered by PersonalWarehouseScreen in native screen
        // coordinates. Keep this preview outside the Delta layout transform; otherwise
        // the scaled panel coordinates are passed to the warehouse hit test and the
        // cross-panel footprint preview disappears as soon as the cursor enters the
        // warehouse side.
        ItemStack nativeCarried = screen.getMenu().getCarried();
        ItemStack nativeDeferred = state.armedPlayerSlot != null
            ? state.armedPlayerStack : state.armedCarrier != null
                ? state.armedCarrierStack : state.armedEquipmentSlot >= 0
                    ? state.armedEquipmentStack : state.armedExternalSlot != null
                        ? state.armedExternalStack : state.armedCorpseStorage != null
                            ? state.armedCorpseStorageStack : ItemStack.EMPTY;
        if (nativeCarried.isEmpty() && state.itemDragPickedUp && !nativeDeferred.isEmpty()) {
            nativeCarried = nativeDeferred;
        }
        if (nativeCarried.isEmpty() && state.itemDragPickedUp && state.armedItem != null) {
            nativeCarried = state.armedItem.stack();
        }
        if (screen instanceof PersonalWarehouseScreen warehouse
            && !nativeCarried.isEmpty() && state.itemDragPickedUp) {
            warehouse.renderPlayerDragPreview(graphics, mouseX, mouseY,
                nativeCarried);
        }
        if (screen instanceof CorpseScreen corpse && !nativeCarried.isEmpty()) {
            double nativeMouseX = nativeMouseX(screen, mouseX) - screen.getGuiLeft();
            double nativeMouseY = nativeMouseY(screen, mouseY) - screen.getGuiTop();
            corpse.renderCarriedPlacementPreview(graphics, nativeMouseX, nativeMouseY,
                nativeCarried, state.armedCorpseStorage);
        }
        // Discard edges are screen-space targets. Render them outside the
        // panel's inverse-scale transform so they remain flush with x=0 and
        // x=screen.width instead of leaving a scaled gap at the border.
        renderDiscardZones(screen, graphics, state, mouseX, mouseY);
        pushFixedLayer(graphics, state.placement);
        try {
        ItemStack carried = screen.getMenu().getCarried();
        ItemStack genericDeferred = state.armedPlayerSlot != null
            ? state.armedPlayerStack : state.armedCarrier != null
                ? state.armedCarrierStack : state.armedEquipmentSlot >= 0
                    ? state.armedEquipmentStack : state.armedExternalSlot != null
                        ? state.armedExternalStack : state.armedCorpseStorage != null
                            ? state.armedCorpseStorageStack : ItemStack.EMPTY;
        if (carried.isEmpty() && state.itemDragPickedUp && !genericDeferred.isEmpty()) {
            carried = genericDeferred;
        }
        ShortcutTarget deferredSource = null;
        if (carried.isEmpty() && state.armedItem != null && state.itemDragPickedUp) {
            carried = state.armedItem.stack();
            deferredSource = state.armedItem;
        }
        if (!carried.isEmpty()) {
            drawPlacementPreview(graphics, state, carried, mouseX, mouseY, deferredSource);
        }
        if (deferredSource != null) {
            drawDeferredStorageDragGhost(graphics, state, deferredSource, mouseX, mouseY);
        }
        if (deferredSource == null && state.itemDragPickedUp && !genericDeferred.isEmpty()) {
            drawDeferredDragGhost(graphics, state, genericDeferred, mouseX, mouseY);
        }
        if (state.weightDetailsVisible) renderWeightBadges(graphics, state);
        renderPositionEditor(screen, graphics, state, mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void renderDiscardZones(AbstractContainerScreen<?> screen,
                                           GuiGraphics graphics, State state,
                                           int mouseX, int mouseY) {
        boolean active = !screen.getMenu().getCarried().isEmpty()
            || state.itemDragPickedUp;
        if (!active) return;
        int width = discardVisualWidth(screen);
        int height = Math.max(1, screen.height);
        drawDiscardZone(graphics, 0, 0, width, height);
        int right = screen.width - width;
        drawDiscardZone(graphics, right, 0, width, height);
    }

    private static boolean discardZoneAt(AbstractContainerScreen<?> screen,
                                         double mouseX, double mouseY) {
        // The whole vertical screen edge is the target. It is intentionally
        // independent from the old red rectangular drop zone.
        int width = discardEdgeWidth(screen);
        return mouseY >= 0 && mouseY < screen.height
            && (mouseX >= 0 && mouseX < width
                || mouseX >= screen.width - width && mouseX < screen.width);
    }

    private static int discardVisualWidth(AbstractContainerScreen<?> screen) {
        return discardEdgeWidth(screen);
    }

    private static int discardEdgeWidth(AbstractContainerScreen<?> screen) {
        if (screen instanceof PlayerStatusScreen) return Math.min(30, Math.max(1, screen.width / 2));
        return Math.max(4, Math.min(8, screen.width / 160));
    }

    private static void drawDiscardZone(GuiGraphics graphics, int x, int y,
                                        int width, int height) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 680.0F);
        graphics.fill(x, y, x + width, y + height, 0x4DFF0000);
        graphics.pose().popPose();
    }

    /** Returns the stack owned by an in-progress Delta drag before it reaches
     * the vanilla menu cursor. Native container previews use this to render
     * the same footprint during cross-panel drags. */
    public static ItemStack virtualCarriedStack(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        if (state == null || !state.itemDragPickedUp
            || !screen.getMenu().getCarried().isEmpty()) return ItemStack.EMPTY;
        ItemStack stack = state.armedPlayerSlot != null
            ? state.armedPlayerStack : state.armedCarrier != null
                ? state.armedCarrierStack : state.armedEquipmentSlot >= 0
                    ? state.armedEquipmentStack : state.armedExternalSlot != null
                        ? state.armedExternalStack : state.armedCorpseStorage != null
                            ? state.armedCorpseStorageStack : state.armedItem != null
                                ? state.armedItem.stack() : ItemStack.EMPTY;
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    /** Source anchor to ignore while previewing an external-to-external move. */
    public static Slot virtualExternalSourceAnchor(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        if (state == null || !state.itemDragPickedUp || state.armedExternalSlot == null) {
            return null;
        }
        return externalGridAnchor(state, state.armedExternalSlot);
    }

    /**
     * Renders the embedded player-inventory destination for a drag gesture
     * owned by the native container screen. PersonalWarehouseScreen keeps its
     * source item virtual until release, so it cannot rely on menu.getCarried().
     */
    public static void renderCarriedDropPreview(AbstractContainerScreen<?> screen,
                                                GuiGraphics graphics, int mouseX, int mouseY,
                                                ItemStack virtualStack) {
        State state = STATES.get(screen);
        if (state == null || virtualStack == null || virtualStack.isEmpty()) return;
        state.snapshot = snapshot(state);
        state.targetScroll = clamp(state.targetScroll, 0.0D, maxScroll(state));
        state.scroll = clamp(state.scroll, 0.0D, maxScroll(state));
        layoutPlayerSlots(state);
        drawPlacementPreview(graphics, state, virtualStack, mouseX, mouseY, null);
    }

    public static boolean togglePositionEditor(AbstractContainerScreen<?> screen) {
        if (!isActive(screen)) return false;
        EditorState editor = EDITORS.computeIfAbsent(screen, ignored -> new EditorState());
        if (!editor.active) {
            editor.active = true;
            editor.original = ContainerUiPositionState.get(positionKey(screen));
            editor.originalRecipe = ContainerUiPositionState.get(recipeBookPositionKey(screen));
            editor.originalRecipeButton = ContainerUiPositionState.get(
                recipeBookButtonPositionKey(screen));
        } else {
            cancelEditor(screen, editor);
        }
        editor.dragMode = DragMode.NONE;
        editor.focused = null;
        editor.input = "";
        return true;
    }

    public static boolean isEmbedded(AbstractContainerScreen<?> screen) {
        return STATES.containsKey(screen);
    }

    public static void openHealthTab(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        if (state != null) setTab(state, EmbeddedTab.HEALTH);
    }

    public static boolean isHealthTabOpen(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        return state != null && state.tab == EmbeddedTab.HEALTH;
    }

    public static void closeWithTransition(AbstractContainerScreen<?> screen, Runnable close) {
        State state = STATES.get(screen);
        if (state == null) close.run();
        else state.transition.beginClose(close);
    }

    public static int playerPanelRight(AbstractContainerScreen<?> screen) {
        Placement placement = PLACEMENTS.get(screen);
        return placement == null ? screen.getGuiLeft() + screen.getXSize()
            : placement.panelX + placement.panelWidth;
    }

    public static boolean isDiscardEdge(AbstractContainerScreen<?> screen, double x, double y) {
        return isActive(screen) && discardZoneAt(screen, x, y);
    }

    public static int safetyBoxGridX(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        if (state == null || state.snapshot == null) return 0;
        Section section = safetySection(state);
        return section == null ? 0 : safetyGridBounds(state, section).x;
    }

    public static int safetyBoxGridY(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        if (state == null || state.snapshot == null) return 0;
        Section section = safetySection(state);
        return section == null ? 0 : safetyGridBounds(state, section).y;
    }

    public static int safetyBoxCellSize(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        return state == null ? BASE_CELL : scaled(state.placement, BASE_CELL);
    }
    public static float safetyBoxGridScale(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        return state == null ? 1.0F : state.placement.scale;
    }

    public static int safetyBoxInspectX(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        if (state == null || state.snapshot == null) return 0;
        Section section = safetySection(state);
        return section == null ? 0 : safetySelector(state, section).x;
    }

    public static int safetyBoxInspectY(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        if (state == null || state.snapshot == null) return 0;
        Section section = safetySection(state);
        return section == null ? 0 : safetySelector(state, section).y;
    }

    public static int safetyBoxInspectWidth(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        return state == null ? 0 : scaled(state.placement, 42);
    }

    public static int safetyBoxInspectHeight(AbstractContainerScreen<?> screen) {
        return safetyBoxInspectWidth(screen);
    }

    public static int embeddedClipLeft(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        return state == null ? 0 : storageX(state.placement);
    }

    public static int embeddedClipTop(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        return state == null ? 0 : viewportTop(state.placement);
    }

    public static int embeddedClipRight(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        return state == null ? screen.width : storageX(state.placement) + storageWidth(state.placement);
    }

    public static int embeddedClipBottom(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        return state == null ? screen.height : viewportBottom(state.placement);
    }

    public static boolean showsSafetyBox(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        return state != null;
    }

    public static String positionKey(AbstractContainerScreen<?> screen) {
        return screen == null ? null : screen.getClass().getName();
    }

    public static boolean hasMovableRecipeBook(AbstractContainerScreen<?> screen) {
        return screen instanceof CraftingScreen || screen instanceof AbstractFurnaceScreen<?>;
    }

    public static String recipeBookPositionKey(AbstractContainerScreen<?> screen) {
        String key = positionKey(screen);
        return key == null ? null : key + ":recipe_book";
    }

    public static String recipeBookButtonPositionKey(AbstractContainerScreen<?> screen) {
        String key = positionKey(screen);
        return key == null ? null : key + ":recipe_book_button";
    }

    public static int recipeBookButtonX(AbstractContainerScreen<?> screen) {
        int base;
        if (screen instanceof CraftingScreen) {
            int titleWidth = Minecraft.getInstance().font.width(screen.getTitle());
            base = nativeLeft(screen, 0) + 29 + (titleWidth - 20) / 2;
        } else {
            base = nativeLeft(screen, 0) + 20;
        }
        int offset = ContainerUiPositionState.get(
            recipeBookButtonPositionKey(screen)).interfaceX();
        return clampScreen(base + offset, screen.width, 20);
    }

    public static int recipeBookButtonY(AbstractContainerScreen<?> screen) {
        int base = screen instanceof CraftingScreen
            ? nativeTop(screen, 0) + 6 - 40
            : nativeTop(screen, 0) + 34;
        int offset = ContainerUiPositionState.get(
            recipeBookButtonPositionKey(screen)).interfaceY();
        return clampScreen(base + offset, screen.height, 18);
    }

    public static void positionRecipeBookButton(AbstractContainerScreen<?> screen) {
        if (!isPositioned(screen) || !hasMovableRecipeBook(screen)) return;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ImageButton button
                && button.getWidth() == 20 && button.getHeight() == 18) {
                button.setPosition(recipeBookButtonX(screen), recipeBookButtonY(screen));
                return;
            }
        }
    }

    public static int recipeBookX(AbstractContainerScreen<?> screen) {
        int base = screen instanceof CraftingScreen
            ? nativeLeft(screen, 0) + screen.getXSize() - RecipeBookComponent.IMAGE_WIDTH + 8
            : nativeLeft(screen, 0) - RecipeBookComponent.IMAGE_WIDTH - 6;
        int offset = ContainerUiPositionState.get(recipeBookPositionKey(screen)).interfaceX();
        return Math.max(4, Math.min(screen.width - RecipeBookComponent.IMAGE_WIDTH - 4,
            base + offset));
    }

    public static int recipeBookY(AbstractContainerScreen<?> screen) {
        int base = screen instanceof CraftingScreen
            ? nativeTop(screen, 0) + screen.getYSize() + 6
            : Math.max(4, (screen.height - RecipeBookComponent.IMAGE_HEIGHT) / 2);
        int offset = ContainerUiPositionState.get(recipeBookPositionKey(screen)).interfaceY();
        return Math.max(4, Math.min(screen.height - RecipeBookComponent.IMAGE_HEIGHT - 4,
            base + offset));
    }

    private static int clampScreen(int value, int screenSize, int elementSize) {
        return Math.max(4, Math.min(value, Math.max(4, screenSize - elementSize - 4)));
    }

    private static int screenMouseX(Placement placement, int nativeMouseX) {
        return (int) Math.round(placement.nativeLeft
            + (nativeMouseX - placement.nativeLeft) * placement.interfaceScaleX);
    }

    private static int screenMouseY(Placement placement, int nativeMouseY) {
        return (int) Math.round(placement.nativeTop
            + (nativeMouseY - placement.nativeTop) * placement.interfaceScaleY);
    }

    private static void pushFixedLayer(GuiGraphics graphics, Placement placement) {
        graphics.pose().pushPose();
        graphics.pose().translate(placement.nativeLeft, placement.nativeTop, 0.0F);
        graphics.pose().scale((float) (1.0D / placement.interfaceScaleX),
            (float) (1.0D / placement.interfaceScaleY), 1.0F);
        graphics.pose().translate(-placement.nativeLeft, -placement.nativeTop, 0.0F);
    }

    private static Bounds headerTabBounds(State state, boolean health) {
        Placement p = state.placement;
        int width = scaled(p, 78);
        return new Bounds(p.panelX + scaled(p, 8) + (health ? width : 0),
            p.panelTop + scaled(p, 4), width, scaled(p, 22));
    }

    private static Bounds panelBounds(State state) {
        Placement p = state.placement;
        return new Bounds(p.panelX, p.panelTop, p.panelWidth, p.panelHeight);
    }

    private static boolean playerCardHit(State state, double mouseX, double mouseY) {
        return playerCardAt(state, mouseX, mouseY) != null;
    }

    private static Map.Entry<Slot, PlayerCard> playerCardAt(State state,
                                                            double mouseX,
                                                            double mouseY) {
        for (Map.Entry<Slot, PlayerCard> entry : state.playerCards.entrySet()) {
            if (entry.getValue().screenBounds.contains(mouseX, mouseY)) return entry;
        }
        return null;
    }

    private static boolean canPlaceInPlayerCard(int inventoryIndex, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        if (inventoryIndex == 39 || inventoryIndex == 38) {
            Minecraft playerClient = Minecraft.getInstance();
            if (playerClient.player == null) return false;
            EquipmentSlot expected = inventoryIndex == 39 ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
            return PlayerEquipmentSync.canEquip(playerClient.player, stack, expected);
        }
        return switch (inventoryIndex) {
            case 0, 1 -> true;
            case 2 -> PlayerLayoutSlotRules.isTaczGun(stack)
                && PlayerLayoutSlotRules.isHandgun(stack);
            case 3 -> TaczCompatibilityRules.isLrTacticalMelee(stack);
            case 4, 5, 6, 7, 8 -> {
                ItemSize size = ClientDataCache.INSTANCE.getSize(stack);
                yield size.width() == 1 && size.height() == 1;
            }
            default -> false;
        };
    }

    private static void setTab(State state, EmbeddedTab tab) {
        if (state.tab == tab) return;
        ItemDetailOverlay.close(state.screen);
        clearDetailSelection(state);
        clearItemGesture(state);
        state.tab = tab;
        state.draggingScrollbar = false;
        layoutPlayerSlots(state);
    }

    private static void refreshCharacterLayout(State state) {
        state.snapshot = snapshot(state);
        state.targetScroll = clamp(state.targetScroll, 0.0D, maxScroll(state));
        state.scroll = clamp(state.scroll, 0.0D, maxScroll(state));
        layoutPlayerSlots(state);
    }

    /** Uses the rendered row's coordinates so every weight control has one hover target. */
    private static Bounds weightDetailsBounds(State state) {
        Placement p = state.placement;
        Minecraft minecraft = Minecraft.getInstance();
        int weightX = p.panelX + scaled(p, BASE_MODEL_X + 6);
        int weightY = p.panelTop + p.panelHeight - scaled(p, 52);
        int textX = weightX + scaled(p, 16);
        String current = formatWeight(PlayerStatusClientState.INSTANCE.weightKg());
        int menuX = textX + minecraft.font.width(current) + minecraft.font.width("/88KG")
            + scaled(p, 7);
        int left = weightX - scaled(p, 2);
        int right = menuX + scaled(p, 14);
        return new Bounds(left, weightY - scaled(p, 3),
            Math.max(1, right - left), scaled(p, 18));
    }

    private static void renderPositionEditor(AbstractContainerScreen<?> screen,
                                             GuiGraphics graphics, State state,
                                             int mouseX, int mouseY) {
        EditorState editor = EDITORS.get(screen);
        if (editor == null || !editor.active) return;
        Placement p = state.placement;
        Bounds interfaceBounds = editorTargetBounds(screen, p, EditorTarget.INTERFACE);
        Bounds textureBounds = editorTargetBounds(screen, p, EditorTarget.TEXTURE);
        Bounds recipeBounds = hasMovableRecipeBook(screen)
            ? editorTargetBounds(screen, p, EditorTarget.RECIPE_BOOK) : null;
        Bounds recipeButtonBounds = hasMovableRecipeBook(screen)
            ? editorTargetBounds(screen, p, EditorTarget.RECIPE_BOOK_BUTTON) : null;
        Bounds panel = editorPanelBounds(screen, p);
        Bounds interfaceButton = new Bounds(panel.x + 6, panel.y + 6, 52, 20);
        Bounds textureButton = new Bounds(panel.x + 60, panel.y + 6, 52, 20);
        Bounds recipePanelButton = new Bounds(panel.x + 114, panel.y + 6, 68, 20);
        Bounds recipeToggleButton = new Bounds(panel.x + 184, panel.y + 6, 86, 20);
        Bounds saveButton = new Bounds(panel.x + 320, panel.y + 6, 54, 20);
        Bounds resetButton = new Bounds(panel.x + 28, panel.y + 122, 176, 20);
        Bounds selected = switch (editor.target) {
            case INTERFACE -> interfaceBounds;
            case TEXTURE -> textureBounds;
            case RECIPE_BOOK -> recipeBounds == null ? interfaceBounds : recipeBounds;
            case RECIPE_BOOK_BUTTON ->
                recipeButtonBounds == null ? interfaceBounds : recipeButtonBounds;
        };

        graphics.pose().pushPose();
        // Keep the editor above the Delta panel, native container widgets and
        // item previews while the layout is being adjusted.
        graphics.pose().translate(0.0F, 0.0F, 2400.0F);
        graphics.renderOutline(interfaceBounds.x, interfaceBounds.y,
            interfaceBounds.width, interfaceBounds.height,
            editor.target == EditorTarget.INTERFACE ? 0xFF65D6AD : 0xAA65D6AD);
        graphics.renderOutline(textureBounds.x + 2, textureBounds.y + 2,
            Math.max(1, textureBounds.width - 4), Math.max(1, textureBounds.height - 4),
            editor.target == EditorTarget.TEXTURE ? 0xFFFFB85C : 0xAAFFB85C);
        if (recipeBounds != null) {
            graphics.renderOutline(recipeBounds.x, recipeBounds.y,
                recipeBounds.width, recipeBounds.height,
                editor.target == EditorTarget.RECIPE_BOOK ? 0xFF66B9FF : 0xAA66B9FF);
        }
        if (recipeButtonBounds != null) {
            graphics.renderOutline(recipeButtonBounds.x, recipeButtonBounds.y,
                recipeButtonBounds.width, recipeButtonBounds.height,
                editor.target == EditorTarget.RECIPE_BOOK_BUTTON
                    ? 0xFFFFD166 : 0xAAFFD166);
        }
        drawEditorButton(graphics, interfaceButton,
            Component.translatable("container_position.xero_delta.interface"),
            editor.target == EditorTarget.INTERFACE, interfaceButton.contains(mouseX, mouseY));
        drawEditorButton(graphics, textureButton,
            Component.translatable("container_position.xero_delta.texture"),
            editor.target == EditorTarget.TEXTURE, textureButton.contains(mouseX, mouseY));
        drawEditorButton(graphics, recipePanelButton,
            Component.translatable("container_position.xero_delta.recipe_book"),
            editor.target == EditorTarget.RECIPE_BOOK,
            recipePanelButton.contains(mouseX, mouseY));
        drawEditorButton(graphics, recipeToggleButton,
            Component.translatable("container_position.xero_delta.recipe_book_button"),
            editor.target == EditorTarget.RECIPE_BOOK_BUTTON,
            recipeToggleButton.contains(mouseX, mouseY));
        drawEditorButton(graphics, saveButton,
            Component.translatable("container_position.xero_delta.save"), false,
            saveButton.contains(mouseX, mouseY));
        graphics.fill(panel.x, panel.y + 30, panel.x + panel.width,
            panel.y + panel.height, 0xF2172124);
        graphics.renderOutline(panel.x, panel.y, panel.width, panel.height, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        ContainerUiPositionState.Offset offset = editorOffset(screen, editor.target);
        String[] labels = {"X", "Y", "W", "H"};
        String[] values = editorValues(offset, editor.target);
        for (int row = 0; row < 4; row++) {
            Bounds field = editorFieldBounds(panel, row);
            Bounds decrease = editorDecreaseBounds(panel, row);
            Bounds increase = editorIncreaseBounds(panel, row);
            boolean editable = row < 2 || !isFixedSizeEditorTarget(editor.target);
            graphics.drawString(Minecraft.getInstance().font, labels[row],
                panel.x + 10, field.y + 6, editable ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED : 0xFF596260, false);
            if (editable) {
                drawEditorButton(graphics, decrease,
                    Component.literal(row == 0 ? "<" : row == 1 ? "^" : "-"),
                    false, decrease.contains(mouseX, mouseY));
                drawEditorButton(graphics, increase,
                    Component.literal(row == 0 ? ">" : row == 1 ? "v" : "+"),
                    false, increase.contains(mouseX, mouseY));
            }
            boolean focused = editable && editor.focused == EditorField.values()[row];
            graphics.fill(field.x, field.y, field.x + field.width,
                field.y + field.height, focused ? 0xFF253B38
                    : editable ? 0xFF0D1517 : 0xFF111719);
            graphics.renderOutline(field.x, field.y, field.width, field.height,
                focused ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : editable ? com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT : 0xFF38413F);
            String value = focused ? editor.input : values[row];
            graphics.drawCenteredString(Minecraft.getInstance().font,
                value + (focused && (System.currentTimeMillis() / 450L) % 2L == 0L
                    ? "|" : ""),
                field.x + field.width / 2, field.y + 6, editable ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
        }
        if (!isFixedSizeEditorTarget(editor.target)) {
            drawResizeHandles(graphics, selected, editor.target, mouseX, mouseY);
        }
        drawEditorButton(graphics, resetButton,
            Component.translatable("container_position.xero_delta.reset"), false,
            resetButton.contains(mouseX, mouseY));
        graphics.drawString(Minecraft.getInstance().font,
            Component.translatable("container_position.xero_delta.hint"),
            panel.x + 6, panel.y + panel.height + 4, 0xFFE8ECEA, true);
        graphics.pose().popPose();
    }
    private static void drawEditorButton(GuiGraphics graphics, Bounds bounds,
                                         Component label, boolean selected, boolean hovered) {
        int background = selected ? 0xEE31584E : hovered ? 0xEE334043 : 0xEE172124;
        graphics.fill(bounds.x, bounds.y, bounds.x + bounds.width,
            bounds.y + bounds.height, background);
        graphics.renderOutline(bounds.x, bounds.y, bounds.width, bounds.height,
            selected ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawCenteredString(Minecraft.getInstance().font, label,
            bounds.x + bounds.width / 2, bounds.y + 6, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT);
    }

    private static boolean editorMouseClicked(AbstractContainerScreen<?> screen,
                                              State state, EditorState editor,
                                              double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        Placement p = state.placement;
        Bounds panel = editorPanelBounds(screen, p);
        if (new Bounds(panel.x + 6, panel.y + 6, 52, 20).contains(mouseX, mouseY)) {
            applyEditorInput(screen, editor);
            editor.target = EditorTarget.INTERFACE;
            return true;
        }
        if (new Bounds(panel.x + 60, panel.y + 6, 52, 20).contains(mouseX, mouseY)) {
            applyEditorInput(screen, editor);
            editor.target = EditorTarget.TEXTURE;
            return true;
        }
        if (new Bounds(panel.x + 114, panel.y + 6, 68, 20).contains(mouseX, mouseY)) {
            applyEditorInput(screen, editor);
            if (hasMovableRecipeBook(screen)) editor.target = EditorTarget.RECIPE_BOOK;
            return true;
        }
        if (new Bounds(panel.x + 184, panel.y + 6, 86, 20).contains(mouseX, mouseY)) {
            applyEditorInput(screen, editor);
            if (hasMovableRecipeBook(screen)) {
                editor.target = EditorTarget.RECIPE_BOOK_BUTTON;
            }
            return true;
        }
        if (new Bounds(panel.x + 28, panel.y + 122, 176, 20).contains(mouseX, mouseY)) {
            ContainerUiPositionState.set(editorPositionKey(screen, editor.target),
                ContainerUiPositionState.Offset.ZERO);
            editor.focused = null;
            return true;
        }
        if (new Bounds(panel.x + 320, panel.y + 6, 54, 20).contains(mouseX, mouseY)) {
            applyEditorInput(screen, editor);
            ContainerUiPositionState.save();
            editor.active = false;
            editor.focused = null;
            editor.dragMode = DragMode.NONE;
            Minecraft minecraft = Minecraft.getInstance();
            screen.resize(minecraft, screen.width, screen.height);
            return true;
        }
        ContainerUiPositionState.Offset offset = editorOffset(screen, editor.target);
        for (int row = 0; row < 4; row++) {
            if (row >= 2 && isFixedSizeEditorTarget(editor.target)) continue;
            if (editorFieldBounds(panel, row).contains(mouseX, mouseY)) {
                applyEditorInput(screen, editor);
                editor.focused = EditorField.values()[row];
                editor.input = editorValues(offset, editor.target)[row];
                return true;
            }
            if (editorDecreaseBounds(panel, row).contains(mouseX, mouseY)) {
                applyEditorStep(screen, editor.target, row, false);
                editor.focused = null;
                return true;
            }
            if (editorIncreaseBounds(panel, row).contains(mouseX, mouseY)) {
                applyEditorStep(screen, editor.target, row, true);
                editor.focused = null;
                return true;
            }
        }
        applyEditorInput(screen, editor);
        Bounds target = editorTargetBounds(screen, p, editor.target);
        DragMode dragMode = isFixedSizeEditorTarget(editor.target)
            ? DragMode.NONE : resizeModeAt(target, mouseX, mouseY);
        if (dragMode == DragMode.NONE && !target.contains(mouseX, mouseY)) return true;
        editor.dragMode = dragMode == DragMode.NONE ? DragMode.MOVE : dragMode;
        editor.dragStartX = mouseX;
        editor.dragStartY = mouseY;
        boolean texture = editor.target == EditorTarget.TEXTURE;
        editor.originX = texture ? offset.textureX() : offset.interfaceX();
        editor.originY = texture ? offset.textureY() : offset.interfaceY();
        editor.originScaleX = texture
            ? offset.textureScaleX() : offset.interfaceScaleX();
        editor.originScaleY = texture
            ? offset.textureScaleY() : offset.interfaceScaleY();
        return true;
    }
    private static Bounds editorPanelBounds(AbstractContainerScreen<?> screen, Placement p) {
        int width = 380;
        int height = 148;
        int preferredX = p.nativeLeft - width - 8;
        if (preferredX < 4) preferredX = p.nativeLeft + Math.max(1,
            (int) Math.round(screen.getXSize() * p.interfaceScaleX)) + 8;
        int x = Math.max(4, Math.min(screen.width - width - 4, preferredX));
        int y = Math.max(4, Math.min(screen.height - height - 18, p.nativeTop));
        return new Bounds(x, y, width, height);
    }

    private static Bounds editorFieldBounds(Bounds panel, int row) {
        return new Bounds(panel.x + 28, panel.y + 34 + row * 21, 92, 18);
    }

    private static Bounds editorDecreaseBounds(Bounds panel, int row) {
        return new Bounds(panel.x + 124, panel.y + 34 + row * 21, 38, 18);
    }

    private static Bounds editorIncreaseBounds(Bounds panel, int row) {
        return new Bounds(panel.x + 166, panel.y + 34 + row * 21, 38, 18);
    }

    private static Bounds editorTargetBounds(AbstractContainerScreen<?> screen,
                                             Placement p, EditorTarget target) {
        if (target == EditorTarget.INTERFACE) {
            return new Bounds(p.nativeLeft, p.nativeTop,
                Math.max(1, (int) Math.round(screen.getXSize() * p.interfaceScaleX)),
                Math.max(1, (int) Math.round(screen.getYSize() * p.interfaceScaleY)));
        }
        if (target == EditorTarget.RECIPE_BOOK) {
            return new Bounds(recipeBookX(screen), recipeBookY(screen),
                RecipeBookComponent.IMAGE_WIDTH, RecipeBookComponent.IMAGE_HEIGHT);
        }
        if (target == EditorTarget.RECIPE_BOOK_BUTTON) {
            return new Bounds(recipeBookButtonX(screen), recipeBookButtonY(screen), 20, 18);
        }
        return new Bounds(
            p.nativeLeft + (int) Math.round(p.textureOffsetX * p.interfaceScaleX),
            p.nativeTop + (int) Math.round(p.textureOffsetY * p.interfaceScaleY),
            Math.max(1, (int) Math.round(screen.getXSize()
                * p.interfaceScaleX * p.textureScaleX)),
            Math.max(1, (int) Math.round(screen.getYSize()
                * p.interfaceScaleY * p.textureScaleY)));
    }

    private static boolean isFixedSizeEditorTarget(EditorTarget target) {
        return target == EditorTarget.RECIPE_BOOK
            || target == EditorTarget.RECIPE_BOOK_BUTTON;
    }

    private static String editorPositionKey(AbstractContainerScreen<?> screen,
                                            EditorTarget target) {
        return switch (target) {
            case RECIPE_BOOK -> recipeBookPositionKey(screen);
            case RECIPE_BOOK_BUTTON -> recipeBookButtonPositionKey(screen);
            default -> positionKey(screen);
        };
    }

    private static ContainerUiPositionState.Offset editorOffset(
        AbstractContainerScreen<?> screen, EditorTarget target) {
        return ContainerUiPositionState.get(editorPositionKey(screen, target));
    }

    private static String[] editorValues(ContainerUiPositionState.Offset offset,
                                         EditorTarget target) {
        boolean texture = target == EditorTarget.TEXTURE;
        int x = texture ? offset.textureX() : offset.interfaceX();
        int y = texture ? offset.textureY() : offset.interfaceY();
        if (target == EditorTarget.RECIPE_BOOK) {
            return new String[]{Integer.toString(x), Integer.toString(y),
                Integer.toString(RecipeBookComponent.IMAGE_WIDTH),
                Integer.toString(RecipeBookComponent.IMAGE_HEIGHT)};
        }
        if (target == EditorTarget.RECIPE_BOOK_BUTTON) {
            return new String[]{Integer.toString(x), Integer.toString(y), "20", "18"};
        }
        double width = texture ? offset.textureScaleX() : offset.interfaceScaleX();
        double height = texture ? offset.textureScaleY() : offset.interfaceScaleY();
        return new String[]{Integer.toString(x), Integer.toString(y),
            formatEditorDecimal(width), formatEditorDecimal(height)};
    }
    private static String formatEditorDecimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static void applyEditorStep(AbstractContainerScreen<?> screen,
                                        EditorTarget target, int row, boolean increase) {
        String key = editorPositionKey(screen, target);
        ContainerUiPositionState.Offset offset = ContainerUiPositionState.get(key);
        double direction = increase ? 1.0D : -1.0D;
        int positionStep = Screen.hasShiftDown() ? 10 : 1;
        double scaleStep = Screen.hasShiftDown() ? 0.1D : 0.01D;
        boolean texture = target == EditorTarget.TEXTURE;
        if (row == 0 || row == 1) {
            int x = texture ? offset.textureX() : offset.interfaceX();
            int y = texture ? offset.textureY() : offset.interfaceY();
            if (row == 0) x += (int) direction * positionStep;
            else y += (int) direction * positionStep;
            if (texture) ContainerUiPositionState.setTexture(key, x, y);
            else ContainerUiPositionState.setInterface(key, x, y);
        } else if (!isFixedSizeEditorTarget(target)) {
            double x = texture ? offset.textureScaleX() : offset.interfaceScaleX();
            double y = texture ? offset.textureScaleY() : offset.interfaceScaleY();
            if (row == 2) x += direction * scaleStep;
            else y += direction * scaleStep;
            if (texture) ContainerUiPositionState.setTextureScale(key, x, y);
            else ContainerUiPositionState.setInterfaceScale(key, x, y);
        }
    }

    private static void applyEditorInput(AbstractContainerScreen<?> screen,
                                         EditorState editor) {
        if (editor.focused == null || editor.input.isBlank()
            || "-".equals(editor.input) || ".".equals(editor.input)
            || "-.".equals(editor.input)) return;
        try {
            String key = editorPositionKey(screen, editor.target);
            ContainerUiPositionState.Offset offset = ContainerUiPositionState.get(key);
            int row = editor.focused.ordinal();
            boolean texture = editor.target == EditorTarget.TEXTURE;
            if (row == 0 || row == 1) {
                int value = new BigDecimal(editor.input).intValue();
                int x = texture ? offset.textureX() : offset.interfaceX();
                int y = texture ? offset.textureY() : offset.interfaceY();
                if (row == 0) x = value;
                else y = value;
                if (texture) ContainerUiPositionState.setTexture(key, x, y);
                else ContainerUiPositionState.setInterface(key, x, y);
            } else if (!isFixedSizeEditorTarget(editor.target)) {
                double value = Double.parseDouble(editor.input);
                double x = texture ? offset.textureScaleX() : offset.interfaceScaleX();
                double y = texture ? offset.textureScaleY() : offset.interfaceScaleY();
                if (row == 2) x = value;
                else y = value;
                if (texture) ContainerUiPositionState.setTextureScale(key, x, y);
                else ContainerUiPositionState.setInterfaceScale(key, x, y);
            }
        } catch (NumberFormatException ignored) {
        }
        editor.input = "";
    }
    private static DragMode resizeModeAt(Bounds target, double mouseX, double mouseY) {
        boolean right = mouseX >= target.x + target.width - 5
            && mouseX < target.x + target.width + 5
            && mouseY >= target.y - 2 && mouseY < target.y + target.height + 5;
        boolean bottom = mouseY >= target.y + target.height - 5
            && mouseY < target.y + target.height + 5
            && mouseX >= target.x - 2 && mouseX < target.x + target.width + 5;
        if (right && bottom) return DragMode.RESIZE_BOTH;
        if (right) return DragMode.RESIZE_X;
        if (bottom) return DragMode.RESIZE_Y;
        return DragMode.NONE;
    }

    private static void drawResizeHandles(GuiGraphics graphics, Bounds target,
                                          EditorTarget editorTarget,
                                          int mouseX, int mouseY) {
        int color = editorTarget == EditorTarget.INTERFACE ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : 0xFFFFB85C;
        Bounds right = new Bounds(target.x + target.width - 3,
            target.y + target.height / 2 - 8, 7, 16);
        Bounds bottom = new Bounds(target.x + target.width / 2 - 8,
            target.y + target.height - 3, 16, 7);
        Bounds corner = new Bounds(target.x + target.width - 5,
            target.y + target.height - 5, 10, 10);
        for (Bounds handle : List.of(right, bottom, corner)) {
            graphics.fill(handle.x, handle.y, handle.x + handle.width,
                handle.y + handle.height,
                handle.contains(mouseX, mouseY) ? 0xFFFFFFFF : color);
        }
    }

    public static boolean editorKeyPressed(AbstractContainerScreen<?> screen,
                                           int keyCode, int modifiers) {
        EditorState editor = EDITORS.get(screen);
        if (editor == null || !editor.active || editor.focused == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applyEditorInput(screen, editor);
            editor.focused = null;
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            editor.focused = null;
            editor.input = "";
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !editor.input.isEmpty()) {
            editor.input = editor.input.substring(0, editor.input.length() - 1);
        } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
            editor.input = "";
        }
        return true;
    }

    public static boolean editorCharTyped(AbstractContainerScreen<?> screen, char character) {
        EditorState editor = EDITORS.get(screen);
        if (editor == null || !editor.active || editor.focused == null) return false;
        if ((character >= '0' && character <= '9') || character == '-'
            || character == '.' || character == ',') {
            editor.input += character == ',' ? '.' : character;
        }
        return true;
    }

    private static void cancelEditor(AbstractContainerScreen<?> screen, EditorState editor) {
        if (editor.original != null) {
            ContainerUiPositionState.set(positionKey(screen), editor.original);
        }
        if (editor.originalRecipe != null) {
            ContainerUiPositionState.set(recipeBookPositionKey(screen), editor.originalRecipe);
        }
        if (editor.originalRecipeButton != null) {
            ContainerUiPositionState.set(recipeBookButtonPositionKey(screen),
                editor.originalRecipeButton);
        }
        editor.active = false;
        editor.dragMode = DragMode.NONE;
        editor.focused = null;
        editor.input = "";
    }
    public static void restore(AbstractContainerScreen<?> screen) {
        State state = STATES.remove(screen);
        if (state != null) restoreSlots(state);
        PLACEMENTS.remove(screen);
        BASE_ORIGINS.remove(screen);
        EditorState editor = EDITORS.remove(screen);
        if (editor != null && editor.active) cancelEditor(screen, editor);
    }

    private static void restoreSlots(State state) {
        state.originals.forEach((slot, position) -> {
            SlotFieldUtil.setX(slot, position.x);
            SlotFieldUtil.setY(slot, position.y);
        });
    }

    private static boolean eligible(AbstractContainerScreen<?> screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isCreative()
            || !PlayerStatusClientState.INSTANCE.layoutEnabled()) return false;
        if (screen instanceof PlayerStatusScreen status) return status.usesSharedDeltaLayout();
        if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) return false;
        String name = screen.getClass().getName().toLowerCase(Locale.ROOT);
        if (name.contains("creativemode") || name.contains("safetyboxpicker")
            || name.contains("cardholderpicker") || name.contains("knifepicker")
            || name.contains("tradingscreen") || name.contains("recyclingscreen")
            || name.contains("configscreen") || name.contains("settingsscreen")
            || name.contains("layoutscreen") || name.contains("mailscreen")) return false;
        boolean externalSlot = false;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != minecraft.player.getInventory()) externalSlot = true;
        }
        return externalSlot;
    }

    private static Snapshot snapshot(State state) {
        Placement p = state.placement;
        int y = viewportTop(p) - (int) Math.round(state.scroll);
        Map<String, Section> sections = new LinkedHashMap<>();
        Map<String, Carrier> carriers = new LinkedHashMap<>();
        List<String> identifiers = List.of(
            "chest_rig", "pockets", "backpack", "card_holder", "safety_box");
        for (String identifier : identifiers) {
            if ("safety_box".equals(identifier)
                && DeltaInventoryUiState.safetyBoxPinned()) continue;
            int height;
            if ("pockets".equals(identifier)) {
            height = scaled(p, 28 + BASE_CELL);
            } else if ("safety_box".equals(identifier)) {
                height = safetyHeight(state);
            } else {
                Carrier carrier = carrier(identifier, p);
                carriers.put(identifier, carrier);
                height = carrierHeight(carrier, p);
            }
            sections.put(identifier, new Section(identifier, y, height));
            y += height + scaled(p, BASE_SECTION_GAP);
        }
        int total = y - (viewportTop(p) - (int) Math.round(state.scroll));
        return new Snapshot(sections, carriers, total);
    }

    private static Carrier carrier(String identifier, Placement p) {
        ItemStack stack = accessory(identifier);
        if (stack.isEmpty()) return new Carrier(identifier, stack, null, null, null, 0, 0);
        if (stack.getItem() instanceof DeltaPackItem pack
            && identifier.equals(pack.slotIdentifier())) {
            GridBackingStore store = new GridBackingStore(stack, pack.gridWidth(), pack.gridHeight(), 0,
                item -> GridBackingStore.isBlockedInEquippedStorage(identifier, item));
            return new Carrier(identifier, stack, pack, store, null,
                pack.gridWidth(), pack.gridHeight());
        }
        if ("backpack".equals(identifier)) {
            try {
                IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
                if (handler != null && handler.getSlots() > 0) {
                    int columns = Math.min(5, handler.getSlots());
                    int rows = (handler.getSlots() + columns - 1) / columns;
                    return new Carrier(identifier, stack, null, null, handler, columns, rows);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return new Carrier(identifier, stack, null, null, null, 0, 0);
    }

    private static ItemStack accessory(String identifier) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return ItemStack.EMPTY;
        return CuriosApi.getCuriosInventory(minecraft.player)
            .flatMap(handler -> handler.getStacksHandler(identifier))
            .filter(handler -> handler.getSlots() > 0)
            .map(handler -> handler.getStacks().getStackInSlot(0))
            .orElse(ItemStack.EMPTY);
    }

    private static ItemStack safetyBox() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return ItemStack.EMPTY;
        try {
            return CuriosApi.getCuriosInventory(minecraft.player)
                .flatMap(handler -> handler.findFirstCurio(stack ->
                    stack.getItem() instanceof SafetyBoxItem))
                .map(result -> result.stack()).orElse(ItemStack.EMPTY);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static int carrierHeight(Carrier carrier, Placement p) {
        if (carrier.stack.isEmpty()) return scaled(p, 64);
        int gridHeight = 0;
        if (carrier.pack != null) gridHeight = new PackRegionLayout(carrier.pack,
            scaled(p, BASE_CELL), Math.max(1, scaled(p, 2))).height();
        else if (carrier.handler != null) gridHeight = carrier.rows * scaled(p, BASE_CELL);
        return scaled(p, 22) + Math.max(scaled(p, BASE_LARGE_SLOT), gridHeight)
            + scaled(p, 6);
    }

    private static int safetyHeight(State state) {
        ItemStack stack = safetyBox();
        int gridHeight = stack.getItem() instanceof SafetyBoxItem box
            ? box.getGridHeight() * scaled(state.placement, BASE_CELL) : 0;
        return scaled(state.placement, 22)
            + Math.max(scaled(state.placement, 42), gridHeight) + scaled(state.placement, 6);
    }

    private static void drawHeader(GuiGraphics graphics, State state) {
        Placement p = state.placement;
        Minecraft minecraft = Minecraft.getInstance();
        int tabWidth = scaled(p, 78);
        int tabX = p.panelX + scaled(p, 8);
        int tabY = p.panelTop + scaled(p, 4);
        int tabHeight = scaled(p, 22);
        boolean characterHovered = headerTabBounds(state, false).contains(state.mouseX, state.mouseY);
        boolean healthHovered = headerTabBounds(state, true).contains(state.mouseX, state.mouseY);
        boolean characterSelected = state.tab == EmbeddedTab.CHARACTER;
        graphics.fill(tabX, tabY, tabX + tabWidth, tabY + tabHeight,
            characterSelected ? 0xAA29483E : characterHovered ? 0x99313E3C : 0x00000000);
        graphics.fill(tabX + tabWidth, tabY, tabX + tabWidth * 2,
            tabY + tabHeight, !characterSelected ? 0xAA29483E
                : healthHovered ? 0x99313E3C : 0x00000000);
        int selectedX = characterSelected ? tabX : tabX + tabWidth;
        graphics.fill(selectedX, tabY + tabHeight - scaled(p, 2),
            selectedX + tabWidth, tabY + tabHeight, DeltaInventoryTheme.ACCENT);
        graphics.drawCenteredString(minecraft.font,
            Component.translatable("status.xero_delta.character"),
            tabX + tabWidth / 2, tabY + scaled(p, 7), characterSelected ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
        graphics.drawCenteredString(minecraft.font,
            Component.translatable("status.xero_delta.health"),
            tabX + tabWidth + tabWidth / 2, tabY + scaled(p, 7),
            characterSelected ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT);
        graphics.fill(p.panelX, p.panelTop + scaled(p, BASE_HEADER) - 1,
            p.panelX + p.panelWidth, p.panelTop + scaled(p, BASE_HEADER), 0xFF263231);
    }

    private static void drawPlayerModelAndWeight(GuiGraphics graphics, State state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        Placement p = state.placement;
        int left = p.panelX + scaled(p, BASE_MODEL_X);
        int top = p.panelTop + scaled(p, BASE_HEADER + 4);
        int right = p.panelX + scaled(p, state.tab == EmbeddedTab.HEALTH
            ? BASE_STORAGE_X - 12 : BASE_LOADOUT_X - 4);
        int bottom = p.panelTop + p.panelHeight - scaled(p,
            state.tab == EmbeddedTab.HEALTH ? 112 : 64);
        if (right > left + 12 && bottom > top + 24) {
            InventoryScreen.renderEntityInInventoryFollowsAngle(graphics,
                left, top, right, bottom, Math.max(12, Math.min(scaled(p, 108), (bottom - top) / 2)),
                0.0625F, 0.0F, 0.0F, minecraft.player);
        }

        int weightX = left + scaled(p, 6);
        int weightY = p.panelTop + p.panelHeight - scaled(p, 52);
        PlayerStatusClientState playerState = PlayerStatusClientState.INSTANCE;
        int color = playerState.overloaded() ? 0xFFFF665C
            : playerState.encumbered() ? 0xFFFFC857 : 0xFFB8D7CB;
        drawWeightIcon(graphics, weightX, weightY - scaled(p, 1), color, p);
        String current = formatWeight(playerState.weightKg());
        int textX = weightX + scaled(p, 16);
        graphics.drawString(minecraft.font, current, textX,
            weightY + scaled(p, 2), color, false);
        int suffix = (0xB3 << 24) | (color & 0x00FFFFFF);
        graphics.drawString(minecraft.font, "/88KG", textX + minecraft.font.width(current),
            weightY + scaled(p, 2), suffix, false);
        int menuX = textX + minecraft.font.width(current) + minecraft.font.width("/88KG")
            + scaled(p, 7);
        if (state.weightDetailsVisible) {
            graphics.fill(menuX - scaled(p, 3), weightY - scaled(p, 3),
                menuX + scaled(p, 14), weightY + scaled(p, 15), 0x884B5E59);
        }
        for (int row = 0; row < 3; row++) {
            graphics.fill(menuX, weightY + scaled(p, 1 + row * 4),
                menuX + scaled(p, 11), weightY + scaled(p, 2 + row * 4), 0xFF9EAAA7);
        }
        if (state.weightDetailsVisible) drawWeightDetails(graphics, state, weightX, weightY);
        int healthY = p.panelTop + p.panelHeight - scaled(p, 30);
        int healthWidth = scaled(p, 124);
        float maximum = Math.max(1.0F, minecraft.player.getMaxHealth());
        float health = minecraft.player.getHealth();
        graphics.drawString(minecraft.font, Math.round(health) + "/" + Math.round(maximum),
            weightX, healthY - 9, DeltaInventoryTheme.TEXT, false);
        graphics.fill(weightX, healthY + 2, weightX + healthWidth, healthY + 5, DeltaInventoryTheme.BORDER);
        graphics.fill(weightX, healthY + 2, weightX + Math.round(healthWidth * Math.clamp(health / maximum, 0, 1)),
            healthY + 5, DeltaInventoryTheme.TEXT);
        graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(
            minecraft.player.getDisplayName().getString(), healthWidth), weightX, healthY + 8,
            DeltaInventoryTheme.MUTED, false);
    }

    private static void drawHealthBodyIcons(GuiGraphics graphics, State state) {
        PlayerStatusClientState status = PlayerStatusClientState.INSTANCE;
        for (PlayerStatusUi.Part part : PlayerStatusUi.Part.values()) {
            Bounds bounds = bodyIconBounds(state, part);
            PlayerStatusUi.drawIcon(graphics, bounds.x, bounds.y, bounds.width, part,
                PlayerStatusUi.value(status, part), bounds.contains(state.mouseX, state.mouseY));
            graphics.drawString(Minecraft.getInstance().font, part.label(), bounds.x,
                bounds.y - 10, DeltaInventoryTheme.MUTED, false);
        }
    }

    private static Bounds bodyIconBounds(State state, PlayerStatusUi.Part part) {
        Placement p = state.placement;
        int x = p.panelX + scaled(p, BASE_MODEL_X);
        int y = p.panelTop + scaled(p, BASE_HEADER + 4);
        int size = scaled(p, 18);
        int offsetX = switch (part) {
            case HEAD -> 148;
            case CHEST -> 164;
            case LEFT_ARM -> 12;
            case RIGHT_ARM -> 174;
            case ABDOMEN -> 40;
            case LEFT_LEG -> 24;
            case RIGHT_LEG -> 156;
        };
        int offsetY = switch (part) {
            case HEAD -> 26;
            case CHEST -> 60;
            case LEFT_ARM, RIGHT_ARM -> 94;
            case ABDOMEN -> 124;
            case LEFT_LEG, RIGHT_LEG -> 178;
        };
        int rightLimit = p.panelX + scaled(p, BASE_STORAGE_X - 8) - size;
        return new Bounds(Math.min(x + scaled(p, offsetX), rightLimit),
            y + Math.round(offsetY / 220.0F * Math.max(1,
                p.panelHeight - scaled(p, BASE_HEADER + 116))), size, size);
    }

    private static void drawWeightDetails(GuiGraphics graphics, State state, int x, int y) {
        Placement p = state.placement;
        Minecraft minecraft = Minecraft.getInstance();
        int width = scaled(p, 142);
        int height = Math.max(66, scaled(p, 64));
        int normalTop = y - height - scaled(p, 5);
        // The medical selector expands upward from the bottom edge of the health tab.
        // Keep this tooltip above that popup so both surfaces remain usable.
        int medicalPopupTop = p.panelTop + p.panelHeight - scaled(p, 94)
            - scaled(p, 30) - scaled(p, 28);
        int top = Math.max(p.panelTop + scaled(p, BASE_HEADER + 2),
            Math.min(normalTop, medicalPopupTop - height - scaled(p, 8)));
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 1600.0F);
        graphics.fill(x, top, x + width, top + height, DeltaInventoryTheme.STORAGE);
        graphics.renderOutline(x, top, width, height, DeltaInventoryTheme.BORDER);
        graphics.drawString(minecraft.font,
            Component.translatable("status.xero_delta.weight_current",
                formatWeight(PlayerStatusClientState.INSTANCE.weightKg()), "88KG"),
            x + scaled(p, 6), top + scaled(p, 7), com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        PlayerStatusClientState status = PlayerStatusClientState.INSTANCE;
        graphics.drawString(minecraft.font, Component.translatable("status.xero_delta.weight_state."
            + (status.overloaded() ? "overloaded" : status.encumbered() ? "encumbered" : "normal")),
            x + 6, top + 20, DeltaInventoryTheme.TEXT, false);
        int barWidth = width - 12;
        graphics.fill(x + 6, top + 36, x + width - 6, top + 39, DeltaInventoryTheme.BORDER);
        graphics.fill(x + 6, top + 36, x + 6 + (int)(barWidth * Math.clamp(status.weightKg() / 88, 0, 1)),
            top + 39, status.overloaded() ? 0xFFE6453D : DeltaInventoryTheme.TEXT);
        int threshold = x + 6 + Math.round(barWidth * 50.0F / 88.0F);
        graphics.fill(threshold, top + 33, threshold + 1, top + 42, DeltaInventoryTheme.MUTED);
        graphics.drawString(minecraft.font, "50", threshold - 6, top + 45, DeltaInventoryTheme.MUTED, false);
        graphics.drawString(minecraft.font, "88KG", x + width - 32, top + 45, DeltaInventoryTheme.MUTED, false);
        graphics.pose().popPose();
    }

    private static void drawWeightIcon(GuiGraphics graphics, int x, int y,
                                       int color, Placement p) {
        int one = scaled(p, 1);
        graphics.fill(x + scaled(p, 3), y, x + scaled(p, 10), y + scaled(p, 2), color);
        graphics.fill(x + one, y + scaled(p, 2), x + scaled(p, 12), y + scaled(p, 4), color);
        graphics.fill(x, y + scaled(p, 4), x + scaled(p, 13), y + scaled(p, 13), color);
        graphics.fill(x + scaled(p, 3), y + scaled(p, 6), x + scaled(p, 10),
            y + scaled(p, 11), 0xD00B1113);
    }

    private static String formatWeight(double value) {
        return BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString() + "KG";
    }

    private static void drawLoadout(GuiGraphics graphics, State state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        String[] labels = {
            "", "",
            "3", "4", "1", "2"
        };
        ItemStack[] stacks = {
            minecraft.player.getItemBySlot(EquipmentSlot.HEAD),
            minecraft.player.getItemBySlot(EquipmentSlot.CHEST),
            minecraft.player.getInventory().getItem(2),
            minecraft.player.getInventory().getItem(3),
            minecraft.player.getInventory().getItem(0),
            minecraft.player.getInventory().getItem(1)
        };
        for (int index = 0; index < labels.length; index++) {
            Bounds bounds = loadoutBounds(state.placement, index);
            graphics.fill(bounds.x, bounds.y, bounds.x + bounds.width,
                bounds.y + bounds.height, DeltaInventoryTheme.CELL);
            if (stacks[index].isEmpty()) {
                LoadoutSlotBackgroundRenderer.Kind kind = switch (index) {
                    case 0 -> LoadoutSlotBackgroundRenderer.Kind.HELMET;
                    case 1 -> LoadoutSlotBackgroundRenderer.Kind.CHESTPLATE;
                    case 2 -> LoadoutSlotBackgroundRenderer.Kind.SIDEARM;
                    case 4, 5 -> LoadoutSlotBackgroundRenderer.Kind.PRIMARY;
                    default -> null;
                };
                if (kind != null) LoadoutSlotBackgroundRenderer.render(graphics,
                    bounds.x, bounds.y, bounds.width, bounds.height, kind);
            }
            if (index == 3) drawKnifeStripes(graphics, bounds);
            graphics.renderOutline(bounds.x, bounds.y, bounds.width, bounds.height, DeltaInventoryTheme.BORDER);
            if (!stacks[index].isEmpty() && index < 2) {
                renderSizedItemAbsolute(graphics, stacks[index], bounds);
                drawDurabilityBar(graphics, stacks[index], bounds);
                if (state.weightDetailsVisible) drawItemWeightBadge(graphics, stacks[index],
                    bounds.x, bounds.y, bounds.width, bounds.height);
            }
            if (index < 2) {
                LoadoutLabelRenderer.render(graphics, minecraft.font,
                    stacks[index], "", bounds.x, bounds.y, bounds.width,
                    bounds.height);
            }
            if (index == 3) drawLockIcon(graphics, bounds.x + 3,
                bounds.y + bounds.height - scaled(state.placement, 11));
        }
    }

    private static void drawDurabilityBar(GuiGraphics graphics, ItemStack stack, Bounds bounds) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()
            || stack.getMaxDamage() <= 0) return;
        float ratio = Math.max(0.0F, Math.min(1.0F,
            (stack.getMaxDamage() - stack.getDamageValue())
                / (float) stack.getMaxDamage()));
        int barHeight = Math.max(2, Math.min(3, bounds.height / 12));
        int y = bounds.y + bounds.height - barHeight - 2;
        int fill = Math.round((bounds.width - 4) * ratio);
        int color = ratio <= 0.20F ? 0xFFE6453D : ratio <= 0.50F ? 0xFFF2B544 : 0xFF55D68B;
        graphics.fill(bounds.x + 2, y, bounds.x + bounds.width - 2, y + barHeight, 0xB51A2224);
        if (fill > 0) graphics.fill(bounds.x + 2, y, bounds.x + 2 + fill, y + barHeight, color);
    }
    private static void drawSections(GuiGraphics graphics, State state) {
        List<String> identifiers = List.of(
            "chest_rig", "pockets", "backpack", "card_holder", "safety_box");
        for (String identifier : identifiers) {
            if ("safety_box".equals(identifier)
                && DeltaInventoryUiState.safetyBoxPinned()) continue;
            Section section = state.snapshot.sections.get(identifier);
            if (section == null) continue;
            if ("pockets".equals(identifier)) drawPockets(graphics, state, section);
            else if ("safety_box".equals(identifier)) drawSafety(graphics, state, section);
            else drawCarrier(graphics, state, state.snapshot.carriers.get(identifier), section);
        }
    }

    private static void captureStorageHelp(State state, String helpKey) {
        if (helpKey != null) state.storageHelpTooltipKey = helpKey;
    }

    private static void drawSectionFrame(GuiGraphics graphics, State state,
                                         Section section, Component title) {
        int x = storageX(state.placement);
        int width = storageWidth(state.placement);
        graphics.fill(x, section.y, x + width, section.y + section.height, DeltaInventoryTheme.STORAGE);
        int headerHeight = Math.max(14, scaled(state.placement, 16));
        graphics.fill(x, section.y, x + width, section.y + headerHeight, DeltaInventoryTheme.CELL);
        graphics.renderOutline(x, section.y, width, headerHeight, DeltaInventoryTheme.BORDER);
        graphics.drawString(Minecraft.getInstance().font, title,
            x + scaled(state.placement, 4), section.y + scaled(state.placement, 5), com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
    }

    private static void drawPockets(GuiGraphics graphics, State state, Section section) {
        drawSectionFrame(graphics, state, section, Component.empty());
        int used = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            for (int index = 4; index <= 8; index++) {
                if (!minecraft.player.getInventory().getItem(index).isEmpty()) used++;
            }
        }
        captureStorageHelp(state, StorageSectionHeaderRenderer.render(
            graphics, minecraft.font,
            Component.translatable("status.xero_delta.pockets"),
            used, 5, "storage_help.xero_delta.pockets",
            storageX(state.placement) + scaled(state.placement, 4),
            section.y + scaled(state.placement, 5), 1.0F,
            state.mouseX, state.mouseY));
        int cell = scaled(state.placement, BASE_CELL);
        int gap = scaled(state.placement, 2);
        int startX = storageX(state.placement) + scaled(state.placement, 6);
        int y = section.y + scaled(state.placement, 22);
        for (int index = 0; index < 5; index++) {
            drawCell(graphics, startX + index * (cell + gap), y, cell);
        }
    }

    private static void drawCarrier(GuiGraphics graphics, State state,
                                    Carrier carrier, Section section) {
        if (carrier == null) return;
        Component title = Component.translatable("status.xero_delta." + carrier.identifier);
        if ("chest_rig".equals(carrier.identifier)
            || "backpack".equals(carrier.identifier)) {
            drawSectionFrame(graphics, state, section, Component.empty());
            int used = carrier.store != null
                ? StorageSectionHeaderRenderer.usedCells(carrier.store)
                : StorageSectionHeaderRenderer.usedSlots(carrier.handler);
            int total = carrier.store != null
                ? carrier.columns * carrier.rows
                : carrier.handler == null ? 0 : carrier.handler.getSlots();
            captureStorageHelp(state, StorageSectionHeaderRenderer.render(
                graphics, Minecraft.getInstance().font, title, used, total,
                "storage_help.xero_delta." + carrier.identifier,
                storageX(state.placement) + scaled(state.placement, 4),
                section.y + scaled(state.placement, 5), 1.0F,
                state.mouseX, state.mouseY));
        } else if ("card_holder".equals(carrier.identifier)) {
            drawSectionFrame(graphics, state, section, Component.empty());
            captureStorageHelp(state, StorageSectionHeaderRenderer.renderTitleWithHelp(
                graphics, Minecraft.getInstance().font, title,
                "storage_help.xero_delta.card_holder",
                storageX(state.placement) + scaled(state.placement, 4),
                section.y + scaled(state.placement, 5), 1.0F,
                state.mouseX, state.mouseY));
        } else {
            drawSectionFrame(graphics, state, section, title);
        }
        Bounds selector = carrierSelector(state, section);
        graphics.fill(selector.x, selector.y, selector.x + selector.width,
            selector.y + selector.height, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        if (!carrier.stack.isEmpty()) {
            renderSizedItemAbsolute(graphics, carrier.stack, selector);
            if (state.weightDetailsVisible) drawItemWeightBadge(graphics, carrier.stack,
                selector.x, selector.y, selector.width, selector.height);
        }
        graphics.renderOutline(selector.x, selector.y, selector.width, selector.height, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        if (("chest_rig".equals(carrier.identifier)
            || "backpack".equals(carrier.identifier)) && !carrier.stack.isEmpty()) {
            LoadoutLabelRenderer.render(graphics, Minecraft.getInstance().font,
                carrier.stack, "", selector.x, selector.y, selector.width, selector.height);
        }
        if ("card_holder".equals(carrier.identifier)) {
            drawLockIcon(graphics, selector.x + 3,
                selector.y + selector.height - scaled(state.placement, 11));
            if (PlayerStatusClientState.INSTANCE.canChangeBc()) {
                drawSwapIcon(graphics, selector.x + selector.width - scaled(state.placement, 11),
                    selector.y + 3);
            }
        }
        drawStorageGrid(graphics, state, carrier, section);
    }

    private static void drawStorageGrid(GuiGraphics graphics, State state,
                                        Carrier carrier, Section section) {
        if (carrier.stack.isEmpty() || (carrier.store == null && carrier.handler == null)) return;
        Bounds grid = storageGridBounds(state, carrier, section);
        int cell = scaled(state.placement, BASE_CELL);
        PackRegionLayout layout = carrier.pack == null ? null
            : new PackRegionLayout(carrier.pack, cell, Math.max(1, scaled(state.placement, 2)));
        if (layout != null) {
            for (PackRegionLayout.Rect region : layout.regionBounds()) {
                for (int y = 0; y < region.height(); y += cell) {
                    for (int x = 0; x < region.width(); x += cell) {
                        drawCell(graphics, grid.x + region.x() + x, grid.y + region.y() + y, cell);
                    }
                }
                graphics.renderOutline(grid.x + region.x(), grid.y + region.y(),
                    region.width(), region.height(), com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
            }
            for (int index = 0; index < carrier.store.getSize(); index++) {
                ItemStack stack = carrier.store.getItemRaw(index % carrier.columns,
                    index / carrier.columns);
                if (stack.isEmpty()) continue;
                var size = GridBackingStore.sizeOfStored(stack);
                PackRegionLayout.Rect rect = layout.footprintBounds(index % carrier.columns,
                    index / carrier.columns, size.width(), size.height());
                if (rect != null) {
                    Bounds itemBounds = new Bounds(grid.x + rect.x(), grid.y + rect.y(),
                        rect.width(), rect.height());
                    renderSizedItemAbsolute(graphics, stack, itemBounds);
                    if (state.weightDetailsVisible) drawItemWeightBadge(graphics, stack,
                        itemBounds.x, itemBounds.y, itemBounds.width, itemBounds.height);
                }
            }
        } else {
            for (int row = 0; row < carrier.rows; row++) {
                for (int column = 0; column < carrier.columns; column++) {
                    int x = grid.x + column * cell;
                    int y = grid.y + row * cell;
                    drawCell(graphics, x, y, cell);
                    int slot = row * carrier.columns + column;
                    if (slot < carrier.handler.getSlots()) {
                        ItemStack stack = carrier.handler.getStackInSlot(slot);
                        if (!stack.isEmpty()) {
                            graphics.renderItem(stack, x + 1, y + 1);
                            if (state.weightDetailsVisible) drawItemWeightBadge(graphics, stack,
                                x, y, cell, cell);
                        }
                    }
                }
            }
        }
    }

    private static void drawSafety(GuiGraphics graphics, State state, Section section) {
        drawSectionFrame(graphics, state, section, Component.empty());
        ItemStack stack = safetyBox();
        int used = 0;
        int total = 0;
        if (stack.getItem() instanceof SafetyBoxItem box) {
            GridBackingStore store = new GridBackingStore(
                stack, box.getGridWidth(), box.getGridHeight());
            used = StorageSectionHeaderRenderer.usedCells(store);
            total = box.getGridWidth() * box.getGridHeight();
        }
        captureStorageHelp(state, StorageSectionHeaderRenderer.render(
            graphics, Minecraft.getInstance().font,
            Component.translatable("status.xero_delta.safety_box"),
            used, total, "storage_help.xero_delta.safety_box",
            storageX(state.placement) + scaled(state.placement, 4),
            section.y + scaled(state.placement, 5), 1.0F,
            state.mouseX, state.mouseY));
        Bounds selector = safetySelector(state, section);
        graphics.fill(selector.x, selector.y, selector.x + selector.width,
            selector.y + selector.height, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        if (!stack.isEmpty()) {
            renderSizedItemAbsolute(graphics, stack, selector);
            if (state.weightDetailsVisible) drawItemWeightBadge(graphics, stack,
                selector.x, selector.y, selector.width, selector.height);
        }
        graphics.renderOutline(selector.x, selector.y, selector.width, selector.height, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        drawLockIcon(graphics, selector.x + 3,
            selector.y + selector.height - scaled(state.placement, 11));
        if (PlayerStatusClientState.INSTANCE.canChangeBc()) {
            drawSwapIcon(graphics, selector.x + selector.width - scaled(state.placement, 11),
                selector.y + 3);
        }
        drawSafetyGrid(graphics, state, section, stack);
        Bounds pin = safetyPinBounds(state, section);
        drawPinIcon(graphics, pin.x, pin.y, DeltaInventoryUiState.safetyBoxPinned(),
            pin.contains(state.mouseX, state.mouseY));
    }

    private static void drawSafetyGrid(GuiGraphics graphics, State state,
                                       Section section, ItemStack boxStack) {
        if (!(boxStack.getItem() instanceof SafetyBoxItem box)) return;
        Bounds grid = safetyGridBounds(state, section);
        int cell = scaled(state.placement, BASE_CELL);
        GridBackingStore store = new GridBackingStore(boxStack,
            box.getGridWidth(), box.getGridHeight());
        graphics.pose().pushPose();
        // Keep safety-box contents above the section chrome and the lower
        // panel fill. The item renderer adds its own local z offsets.
        graphics.pose().translate(0.0F, 0.0F, 64.0F);
        for (int row = 0; row < box.getGridHeight(); row++) {
            for (int column = 0; column < box.getGridWidth(); column++) {
                drawCell(graphics, grid.x() + column * cell,
                    grid.y() + row * cell, cell);
            }
        }
        for (int index = 0; index < store.getSize(); index++) {
            ItemStack item = store.getItemRaw(index % box.getGridWidth(), index / box.getGridWidth());
            if (item.isEmpty() || store.findAnchorIndexAt(index % box.getGridWidth(),
                index / box.getGridWidth()) != index) continue;
            ItemSize size = GridBackingStore.sizeOfStored(item);
            Bounds itemBounds = new Bounds(grid.x() + (index % box.getGridWidth()) * cell,
                grid.y() + (index / box.getGridWidth()) * cell,
                size.width() * cell, size.height() * cell);
            renderSizedItemAbsolute(graphics, item, itemBounds);
        }
        if (state.screen.getMenu().getCarried().isEmpty() && !state.itemDragPickedUp
            && grid.contains(state.mouseX, state.mouseY)) {
            int column = (state.mouseX - grid.x()) / cell;
            int row = (state.mouseY - grid.y()) / cell;
            int anchor = store.findAnchorIndexAt(column, row);
            int anchorColumn = anchor < 0 ? column : anchor % box.getGridWidth();
            int anchorRow = anchor < 0 ? row : anchor / box.getGridWidth();
            ItemStack hovered = anchor < 0 ? ItemStack.EMPTY
                : store.getItemRaw(anchorColumn, anchorRow);
            ItemSize size = hovered.isEmpty()
                ? new ItemSize(1, 1) : GridBackingStore.sizeOfStored(hovered);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 260.0F);
            DeltaGridCellRenderer.renderHover(graphics,
                grid.x() + anchorColumn * cell,
                grid.y() + anchorRow * cell,
                Math.min(size.width(), box.getGridWidth() - anchorColumn) * cell,
                Math.min(size.height(), box.getGridHeight() - anchorRow) * cell);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }

    private static void drawScrollbar(GuiGraphics graphics, State state) {
        double maximum = maxScroll(state);
        if (maximum <= 0.0D) return;
        Placement p = state.placement;
        int x = storageX(p) + storageWidth(p) + 3;
        int top = viewportTop(p);
        int height = scrollViewportBottom(state) - top;
        graphics.fill(x, top, x + 3, top + height, 0xFF1B2527);
        int thumb = thumbHeight(state);
        int y = top + (int) Math.round((height - thumb) * state.scroll / maximum);
        graphics.fill(x, y, x + 3, y + thumb, DeltaInventoryTheme.MUTED);
    }

    private static StorageTarget storageTargetAt(State state, Carrier carrier, Section section,
                                                 double mouseX, double mouseY) {
        if (carrier.store == null && carrier.handler == null) return null;
        Bounds grid = storageGridBounds(state, carrier, section);
        if (!grid.contains(mouseX, mouseY)) return null;
        int cell = scaled(state.placement, BASE_CELL);
        int column;
        int row;
        double fractionX;
        double fractionY;
        if (carrier.pack != null) {
            PackRegionLayout layout = new PackRegionLayout(carrier.pack, cell,
                Math.max(1, scaled(state.placement, 2)));
            PackRegionLayout.Cell mapped = layout.cellAt(mouseX - grid.x, mouseY - grid.y);
            if (mapped == null) return null;
            column = mapped.column();
            row = mapped.row();
            PackRegionLayout.Rect bounds = layout.cellBounds(column, row);
            fractionX = bounds == null ? 0.5D : clamp(
                (mouseX - grid.x - bounds.x()) / Math.max(1.0D, bounds.width()), 0.0D, 0.999D);
            fractionY = bounds == null ? 0.5D : clamp(
                (mouseY - grid.y - bounds.y()) / Math.max(1.0D, bounds.height()), 0.0D, 0.999D);
        } else {
            column = (int) ((mouseX - grid.x) / cell);
            row = (int) ((mouseY - grid.y) / cell);
            fractionX = clamp((mouseX - grid.x - column * cell) / Math.max(1.0D, cell),
                0.0D, 0.999D);
            fractionY = clamp((mouseY - grid.y - row * cell) / Math.max(1.0D, cell),
                0.0D, 0.999D);
        }
        int index = row * carrier.columns + column;
        int size = carrier.store != null ? carrier.store.getSize() : carrier.handler.getSlots();
        return index >= 0 && index < size
            ? new StorageTarget(index, column, row, fractionX, fractionY) : null;
    }

    private static ShortcutTarget shortcutTargetAt(State state, double mouseX, double mouseY) {
        if (state.snapshot == null) return null;
        SafetyGridTarget safetyTarget = safetyGridTargetAt(state, mouseX, mouseY);
        if (safetyTarget != null) {
            return new ShortcutTarget("safety_box", safetyTarget.anchorCell(),
                safetyTarget.stack());
        }
        for (Map.Entry<String, Carrier> entry : state.snapshot.carriers.entrySet()) {
            Section section = state.snapshot.sections.get(entry.getKey());
            Carrier carrier = entry.getValue();
            if (section == null || carrier == null) continue;
            StorageTarget target = storageTargetAt(state, carrier, section, mouseX, mouseY);
            if (target == null) continue;
            int cell = target.cell();
            ItemStack stack;
            if (carrier.store != null) {
                int anchor = carrier.store.findAnchorIndexAt(target.column(), target.row());
                if (anchor >= 0) {
                    cell = anchor;
                    stack = carrier.store.getItemRaw(
                        anchor % carrier.columns(), anchor / carrier.columns()).copy();
                } else {
                    stack = ItemStack.EMPTY;
                }
            } else {
                stack = carrier.handler != null && cell < carrier.handler.getSlots()
                    ? carrier.handler.getStackInSlot(cell).copy() : ItemStack.EMPTY;
            }
            return new ShortcutTarget(entry.getKey(), cell, stack);
        }

        return null;
    }

    private static SafetyGridTarget safetyGridTargetAt(State state,
                                                        double mouseX, double mouseY) {
        Section safety = safetySection(state);
        ItemStack boxStack = safetyBox();
        if (safety == null || !(boxStack.getItem() instanceof SafetyBoxItem box)) return null;
        Bounds grid = safetyGridBounds(state, safety);
        SafetyBoxGridInteraction.Target target = SafetyBoxGridInteraction.targetAt(
            mouseX, mouseY, grid.x(), grid.y(),
            scaled(state.placement, BASE_CELL), box.getGridWidth(), box.getGridHeight());
        if (target == null) return null;
        GridBackingStore store = new GridBackingStore(
            boxStack, box.getGridWidth(), box.getGridHeight());
        int anchor = store.findAnchorIndexAt(target.column(), target.row());
        ItemStack stack = anchor >= 0
            ? store.getItemRaw(anchor % box.getGridWidth(),
                anchor / box.getGridWidth()).copy()
            : ItemStack.EMPTY;
        return new SafetyGridTarget(target, store, box.getGridWidth(),
            anchor >= 0 ? anchor : target.cell(), stack);
    }

    private static GridBackingStore.PlacementResult resolveSafetyPlacement(
        SafetyGridTarget target, ItemStack carried, Set<Integer> ignoredAnchors) {
        return target.store().resolveCursorPlacement(
            target.target().column(), target.target().row(), carried,
            target.target().fractionX(), target.target().fractionY(),
            ClientGridRotation.allowAutoRotate(), ignoredAnchors);
    }

    private static GridBackingStore.PlacementResult resolveCachedPreviewPlacement(
        State state, String identifier, int hoverCell, int sourceCell,
        ItemStack stack, GridBackingStore.PlacementResult fallback) {
        if (state == null || state.previewPlacement == null
            || !Objects.equals(identifier, state.previewIdentifier)
            || hoverCell != state.previewHoverCell
            || sourceCell != state.previewSourceCell
            || stack == null || stack.isEmpty()
            || !ItemStack.isSameItemSameComponents(stack, state.previewStack)) {
            return fallback;
        }
        return state.previewPlacement;
    }

    private static void openItemDetail(AbstractContainerScreen<?> screen, State state,
                                       ShortcutTarget shortcut) {
        Bounds bounds = shortcutDetailBounds(state, shortcut);
        ItemDetailOverlay.open(screen, shortcut.stack(), false,
            TradingInventorySources.sourceIdForCurio(
                shortcut.identifier(), 0, shortcut.cell(), shortcut.stack()),
            bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    private static void quickMoveShortcut(AbstractContainerScreen<?> screen,
                                          ShortcutTarget shortcut) {
        if (screen instanceof PersonalWarehouseScreen) {
            ModNetwork.sendToServer(new WarehouseSourceTransferPacket(
                TradingInventorySources.sourceIdForCurio(
                    shortcut.identifier(), 0, shortcut.cell(), shortcut.stack())));
            return;
        }
        ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
            shortcut.identifier(), EquippedStorageShortcutPacket.QUICK_MOVE,
            shortcut.cell()));
    }

    private static void quickMovePlayerCard(AbstractContainerScreen<?> screen, Slot slot) {
        if (screen instanceof PersonalWarehouseScreen) {
            ModNetwork.sendToServer(new WarehouseSourceTransferPacket(
                "player|" + slot.getContainerSlot()));
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryMouseClick(
                screen.getMenu().containerId, slot.index, 0,
                net.minecraft.world.inventory.ClickType.QUICK_MOVE, minecraft.player);
        }
    }

    private static Bounds shortcutDetailBounds(State state, ShortcutTarget shortcut) {
        int cell = scaled(state.placement, BASE_CELL);
        if ("safety_box".equals(shortcut.identifier())) {
            Section safety = safetySection(state);
            ItemStack boxStack = safetyBox();
            if (safety != null && boxStack.getItem() instanceof SafetyBoxItem box) {
                Bounds grid = safetyGridBounds(state, safety);
                GridBackingStore store = new GridBackingStore(
                    boxStack, box.getGridWidth(), box.getGridHeight());
                int column = shortcut.cell() % box.getGridWidth();
                int row = shortcut.cell() / box.getGridWidth();
                int anchor = store.findAnchorIndexAt(column, row);
                if (anchor >= 0) {
                    column = anchor % box.getGridWidth();
                    row = anchor / box.getGridWidth();
                }
                ItemSize size = GridBackingStore.sizeOfStored(shortcut.stack());
                return new Bounds(grid.x() + column * cell, grid.y() + row * cell,
                    size.width() * cell, size.height() * cell);
            }
        }
        Carrier carrier = state.snapshot.carriers.get(shortcut.identifier());
        Section section = state.snapshot.sections.get(shortcut.identifier());
        if (carrier == null || section == null || carrier.columns() <= 0) {
            return new Bounds(state.placement.panelX(), state.placement.panelTop(), cell, cell);
        }
        Bounds grid = storageGridBounds(state, carrier, section);
        int column = shortcut.cell() % carrier.columns();
        int row = shortcut.cell() / carrier.columns();
        if (carrier.store() != null) {
            int anchor = carrier.store().findAnchorIndexAt(column, row);
            if (anchor >= 0) {
                column = anchor % carrier.columns();
                row = anchor / carrier.columns();
            }
        }
        ItemSize size = GridBackingStore.sizeOfStored(shortcut.stack());
        if (carrier.pack() != null) {
            PackRegionLayout layout = new PackRegionLayout(carrier.pack(), cell,
                Math.max(1, scaled(state.placement, 2)));
            PackRegionLayout.Rect first = layout.cellBounds(column, row);
            PackRegionLayout.Rect last = layout.cellBounds(
                column + size.width() - 1, row + size.height() - 1);
            if (first != null && last != null) {
                return new Bounds(grid.x() + first.x(), grid.y() + first.y(),
                    last.x() + last.width() - first.x(),
                    last.y() + last.height() - first.y());
            }
        }
        return new Bounds(grid.x() + column * cell, grid.y() + row * cell,
            size.width() * cell, size.height() * cell);
    }

    private static boolean shouldInsertFromSelector(String identifier, ItemStack carried) {
        if (carried == null || carried.isEmpty()
            || (!"chest_rig".equals(identifier) && !"backpack".equals(identifier))) {
            return false;
        }
        return !(carried.getItem() instanceof DeltaPackItem pack
            && identifier.equals(pack.slotIdentifier()));
    }
    private static void drawPlacementPreview(GuiGraphics graphics, State state,
                                             ItemStack carried, int mouseX, int mouseY,
                                             ShortcutTarget deferredSource) {
        if (drawStorageSelectorPlacementPreview(graphics, state, carried, mouseX, mouseY)) {
            return;
        }
        if (drawPlayerCardPlacementPreview(graphics, state, carried, mouseX, mouseY)) return;
        SafetyGridTarget safetyTarget = safetyGridTargetAt(state, mouseX, mouseY);
        if (safetyTarget != null) {
            Set<Integer> ignoredAnchors = ignoredStorageAnchors(
                deferredSource, "safety_box");
            GridBackingStore.PlacementResult result = resolveSafetyPlacement(
                safetyTarget, carried, ignoredAnchors);
            state.rememberPreviewPlacement("safety_box", safetyTarget.target().cell(),
                deferredSource == null ? -1 : deferredSource.cell(), carried, result);
            ItemSize size = GridBackingStore.orientedSize(carried, result.rotated());
            Section safety = safetySection(state);
            Bounds grid = safetyGridBounds(state, safety);
            int cell = scaled(state.placement, BASE_CELL);
            int previewX = grid.x() + result.x() * cell;
            int previewY = grid.y() + result.y() * cell;
            int previewWidth = size.width() * cell;
            int previewHeight = size.height() * cell;
            int color = result.isAccepted() ? 0x8849D79A : 0x88E05252;
            int border = result.isAccepted() ? 0xFF6FE8B2 : 0xFFFF6767;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 520);
            graphics.fill(previewX + 1, previewY + 1,
                previewX + previewWidth - 1, previewY + previewHeight - 1, color);
            graphics.renderOutline(previewX, previewY,
                previewWidth, previewHeight, border);
            graphics.pose().popPose();
            return;
        }
        for (Map.Entry<String, Carrier> entry : state.snapshot.carriers.entrySet()) {
            Carrier carrier = entry.getValue();
            Section section = state.snapshot.sections.get(entry.getKey());
            if (carrier.store == null || carrier.pack == null || section == null) continue;
            StorageTarget target = storageTargetAt(state, carrier, section, mouseX, mouseY);
            if (target == null) continue;
            Set<Integer> ignoredAnchors = ignoredStorageAnchors(
                deferredSource, entry.getKey());
            GridBackingStore.PlacementResult result = carrier.store.resolveCursorPlacement(
                target.column, target.row, carried, target.fractionX, target.fractionY,
                ClientGridRotation.allowAutoRotate(), ignoredAnchors);
            state.rememberPreviewPlacement(entry.getKey(), target.cell,
                deferredSource == null ? -1 : deferredSource.cell(), carried, result);
            var size = GridBackingStore.orientedSize(carried, result.rotated());
            PackRegionLayout layout = new PackRegionLayout(carrier.pack,
                scaled(state.placement, BASE_CELL), Math.max(1, scaled(state.placement, 2)));
            PackRegionLayout.Rect rect = layout.footprintBounds(result.x(), result.y(),
                size.width(), size.height());
            if (rect == null) return;
            Bounds grid = storageGridBounds(state, carrier, section);
            int color = result.isAccepted() ? 0x8849D79A : 0x88E05252;
            int border = result.isAccepted() ? 0xFF6FE8B2 : 0xFFFF6767;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 520);
            graphics.fill(grid.x + rect.x() + 1, grid.y + rect.y() + 1,
                grid.x + rect.x() + rect.width() - 1,
                grid.y + rect.y() + rect.height() - 1, color);
            graphics.renderOutline(grid.x + rect.x(), grid.y + rect.y(),
                rect.width(), rect.height(), border);
            graphics.pose().popPose();
            return;
        }
    }

    private static boolean drawStorageSelectorPlacementPreview(GuiGraphics graphics,
                                                                State state,
                                                                ItemStack carried,
                                                                int mouseX,
                                                                int mouseY) {
        if (carried == null || carried.isEmpty()) return false;
        Section safety = safetySection(state);
        if (safety != null) {
            Bounds selector = safetySelector(state, safety);
            if (selector.contains(mouseX, mouseY)) {
                boolean accepted = !(carried.getItem() instanceof SafetyBoxItem);
                drawDropPreviewBounds(graphics, selector, accepted);
                return true;
            }
        }
        for (String identifier : List.of("chest_rig", "backpack", "card_holder")) {
            Section section = state.snapshot.sections.get(identifier);
            Carrier carrier = state.snapshot.carriers.get(identifier);
            if (section == null || carrier == null) continue;
            Bounds selector = carrierSelector(state, section);
            if (!selector.contains(mouseX, mouseY)) continue;
            drawDropPreviewBounds(graphics, selector,
                selectorPreviewState(identifier, carrier.stack(), carried));
            return true;
        }
        return false;
    }

    private static DropPreviewState selectorPreviewState(String identifier,
                                                         ItemStack equipped,
                                                         ItemStack carried) {
        if (carried != null && carried.getCount() == 1
            && carried.getItem() instanceof DeltaPackItem pack
            && identifier.equals(pack.slotIdentifier())) {
            return equipped == null || equipped.isEmpty()
                ? DropPreviewState.ACCEPT : DropPreviewState.SWAP;
        }
        return shouldInsertFromSelector(identifier, carried)
            ? DropPreviewState.ACCEPT : DropPreviewState.REJECT;
    }

    private static void drawDropPreviewBounds(GuiGraphics graphics, Bounds bounds,
                                              boolean accepted) {
        drawDropPreviewBounds(graphics, bounds,
            accepted ? DropPreviewState.ACCEPT : DropPreviewState.REJECT);
    }

    private static void drawDropPreviewBounds(GuiGraphics graphics, Bounds bounds,
                                              DropPreviewState state) {
        int fill = switch (state) {
            case ACCEPT -> 0x8849D79A;
            case SWAP -> 0x88D8A83E;
            case REJECT -> 0x88E05252;
        };
        int border = switch (state) {
            case ACCEPT -> 0xFF6FE8B2;
            case SWAP -> 0xFFFFD166;
            case REJECT -> 0xFFFF6767;
        };
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 520.0F);
        graphics.fill(bounds.x + 1, bounds.y + 1,
            bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, fill);
        graphics.renderOutline(bounds.x, bounds.y, bounds.width, bounds.height, border);
        graphics.pose().popPose();
    }

    private static void drawDeferredDragGhost(GuiGraphics graphics, State state,
                                              ItemStack stack, int mouseX, int mouseY) {
        if (stack.isEmpty()) return;
        ItemSize size = ClientDataCache.INSTANCE.getSize(stack);
        if (GridBackingStore.isRotated(stack)) size = size.rotated();
        int cell = scaled(state.placement, BASE_CELL);
        int gap = Math.max(1, scaled(state.placement, 2));
        int width = size.width() * cell + Math.max(0, size.width() - 1) * gap;
        int height = size.height() * cell + Math.max(0, size.height() - 1) * gap;
        Bounds bounds = new Bounds(mouseX - width / 2, mouseY - height / 2, width, height);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 720.0F);
        renderSizedItemAbsolute(graphics, stack, bounds);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(),
            bounds.y() + bounds.height(), 0x55070B0C);
        graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
            0xCCFFFFFF);
        graphics.pose().popPose();
    }
    private static void drawDeferredStorageDragGhost(GuiGraphics graphics, State state,
                                                     ShortcutTarget source,
                                                     int mouseX, int mouseY) {
        ItemStack stack = source.stack();
        if (stack.isEmpty()) return;
        ItemSize size = ClientDataCache.INSTANCE.getSize(stack);
        if (GridBackingStore.isRotated(stack)) size = size.rotated();
        int cell = scaled(state.placement, BASE_CELL);
        int gap = Math.max(1, scaled(state.placement, 2));
        int width = size.width() * cell + Math.max(0, size.width() - 1) * gap;
        int height = size.height() * cell + Math.max(0, size.height() - 1) * gap;
        Bounds bounds = new Bounds(mouseX - width / 2, mouseY - height / 2,
            width, height);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 720.0F);
        renderSizedItemAbsolute(graphics, stack, bounds);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(),
            bounds.y() + bounds.height(), 0x55070B0C);
        graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
            0xCCFFFFFF);
        graphics.pose().popPose();
    }

    private static boolean drawPlayerCardPlacementPreview(GuiGraphics graphics, State state,
                                                           ItemStack carried,
                                                           int mouseX, int mouseY) {
        Map.Entry<Slot, PlayerCard> hit = playerCardAt(state, mouseX, mouseY);
        if (hit == null) return false;
        Bounds bounds = hit.getValue().screenBounds;
        boolean accepted = canPlaceInPlayerCard(hit.getKey().getContainerSlot(), carried);
        int fill = accepted ? 0x8849D79A : 0x88E05252;
        int border = accepted ? 0xFF6FE8B2 : 0xFFFF6767;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 720.0F);
        graphics.fill(bounds.x + 1, bounds.y + 1,
            bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, fill);
        graphics.renderOutline(bounds.x, bounds.y, bounds.width, bounds.height, border);
        graphics.pose().popPose();
        return true;
    }

    public static void renderTooltip(AbstractContainerScreen<?> screen, GuiGraphics graphics,
                                     int mouseX, int mouseY) {
        State state = STATES.get(screen);
        if (state == null || state.snapshot == null) return;
        if (!ItemDetailOverlay.isOpen(screen) && !state.itemDragPickedUp
            && screen.getMenu().getCarried().isEmpty()) {
            Bounds hover = null;
            ShortcutTarget shortcut = shortcutTargetAt(state, mouseX, mouseY);
            if (shortcut != null && !"safety_box".equals(shortcut.identifier())) {
                hover = shortcutDetailBounds(state, shortcut);
            } else if (shortcut == null) {
                var playerCard = playerCardAt(state, mouseX, mouseY);
                if (playerCard != null) hover = playerCard.getValue().screenBounds();
                var corpseTarget = corpseStorageTargetAt(screen, mouseX, mouseY);
                if (corpseTarget != null) hover = corpseStorageScreenBounds(state, corpseTarget);
                if (hover == null) {
                    Slot external = externalSlotAt(screen, state, mouseX, mouseY);
                    if (external != null) hover = externalSlotBounds(state, external);
                }
            }
            if (hover != null) DeltaGridCellRenderer.renderHover(graphics,
                hover.x(), hover.y(), hover.width(), hover.height());
        }
        if (state.storageHelpTooltipKey != null) {
            StorageSectionHeaderRenderer.renderTooltip(graphics,
                Minecraft.getInstance().font, state.storageHelpTooltipKey,
                mouseX, mouseY);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, ScreenLayerResolver.tooltip());
        renderCustomTooltip(graphics, state, mouseX, mouseY);
        graphics.pose().popPose();
        if (state.selectedItem != null) {
            Bounds selected = shortcutDetailBounds(state, state.selectedItem);
            boolean visible = selected.y() + selected.height() > viewportTop(state.placement)
                && selected.y() < scrollViewportBottom(state);
            ItemDetailOverlay.updateAnchor(screen, selected.x(), selected.y(),
                selected.width(), selected.height(), visible);
        } else if (state.selectedPlayerSlot != null) {
            PlayerCard card = state.playerCards.get(state.selectedPlayerSlot);
            if (card != null) {
                Bounds selected = card.screenBounds();
                ItemDetailOverlay.updateAnchor(screen, selected.x(), selected.y(),
                    selected.width(), selected.height(), true);
            }
        } else if (state.selectedCarrier != null) {
            Section section = state.snapshot.sections.get(state.selectedCarrier);
            Carrier carrier = state.snapshot.carriers.get(state.selectedCarrier);
            if (section != null && carrier != null) {
                Bounds selected = carrierSelector(state, section);
                boolean visible = selected.y() + selected.height() > viewportTop(state.placement)
                    && selected.y() < scrollViewportBottom(state);
                ItemDetailOverlay.updateAnchor(screen, selected.x(), selected.y(),
                    selected.width(), selected.height(), visible);
            }
        } else if (state.selectedExternalSlot != null
            && !state.selectedExternalSlot.getItem().isEmpty()) {
            Bounds selected = externalSlotBounds(state, state.selectedExternalSlot);
            ItemDetailOverlay.updateAnchor(screen, selected.x(), selected.y(),
                selected.width(), selected.height(), true);
        } else if (state.selectedCorpseStorage != null
            && !state.selectedCorpseStorage.stack().isEmpty()) {
            Bounds selected = corpseStorageScreenBounds(state, state.selectedCorpseStorage);
            ItemDetailOverlay.updateAnchor(screen, selected.x(), selected.y(),
                selected.width(), selected.height(), true);
        }
        ItemDetailOverlay.render(screen, graphics, mouseX, mouseY);
    }

    /** Draw all Delta tooltip surfaces after every container and compatibility layer. */
    public static void renderTooltipTopmost(AbstractContainerScreen<?> screen,
                                            GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isActive(screen)) return;
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, ScreenLayerResolver.finalPass());
        try {
            State state = STATES.get(screen);
            Placement p = state.placement;
            boolean effectHovered = InventoryStatusEffectRenderer.render(screen, graphics, mouseX, mouseY,
                p.panelX + scaled(p, BASE_MODEL_X),
                p.panelTop + p.panelHeight - scaled(p, 50), p.scale);
            if (!effectHovered) renderTooltip(screen, graphics, mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        }
        ItemDetailOverlay.renderModalTopmost(screen, graphics, mouseX, mouseY);
    }

    private static void renderWeightBadges(GuiGraphics graphics, State state) {
        for (Map.Entry<Slot, PlayerCard> entry : state.playerCards.entrySet()) {
            int containerSlot = entry.getKey().getContainerSlot();
            // Helmet and chest armor are rendered by drawLoadout, including
            // their weight badges. The generic player-card pass owns the rest.
            if (containerSlot == 39 || containerSlot == 38) continue;
            ItemStack stack = entry.getKey().getItem();
            if (stack.isEmpty()) continue;
            Bounds card = entry.getValue().screenBounds;
            drawItemWeightBadge(graphics, stack,
                card.x, card.y, card.width, card.height);
        }
    }

    public static boolean showsItemWeightBadges(AbstractContainerScreen<?> screen) {
        State state = STATES.get(screen);
        return state != null && state.weightDetailsVisible;
    }

    public static void renderExternalItemWeightBadge(AbstractContainerScreen<?> screen,
                                                     GuiGraphics graphics, ItemStack stack,
                                                     int x, int y, int width, int height) {
        if (showsItemWeightBadges(screen)) {
            drawItemWeightBadge(graphics, stack, x, y, width, height);
        }
    }

    private static void drawItemWeightBadge(GuiGraphics graphics, ItemStack stack,
                                            int x, int y, int width, int height) {
        if (!com.xtdpotato.xero_delta.data.ItemWeightDisplayPolicy.shouldShow(stack)) return;
        double kilograms = ClientDataCache.INSTANCE.getWeight(stack)
            * Math.max(1, stack.getCount());
        String text = formatBadgeWeight(kilograms);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, ITEM_WEIGHT_BADGE_Z);
        graphics.fill(x + 1, y + Math.max(1, height - 10), x + width - 1,
            y + height - 1, 0x66080D0F);
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        int logicalWidth = width * 2;
        int logicalHeight = height * 2;
        int contentWidth = Minecraft.getInstance().font.width(text) + 10;
        int startX = Math.max(2, logicalWidth - contentWidth - 2);
        int baseline = logicalHeight - 11;
        drawMiniWeightIcon(graphics, startX, baseline - 1);
        graphics.drawString(Minecraft.getInstance().font, text, startX + 9, baseline,
            0xFFF1F4F2, false);
        graphics.pose().popPose();
    }

    private static void drawMiniWeightIcon(GuiGraphics graphics, int x, int y) {
        int color = 0xFFE8ECEA;
        graphics.fill(x + 2, y, x + 6, y + 1, color);
        graphics.fill(x + 1, y + 1, x + 7, y + 3, color);
        graphics.fill(x, y + 3, x + 8, y + 9, color);
        graphics.fill(x + 2, y + 5, x + 6, y + 8, 0xD0080D0F);
    }

    private static String formatBadgeWeight(double value) {
        BigDecimal decimal = BigDecimal.valueOf(Math.max(0.0D, value));
        int scale = value < 10.0D ? 1 : 0;
        return decimal.setScale(scale, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString();
    }

    private static void renderItemTooltipWithDurability(GuiGraphics graphics,
                                                         Minecraft minecraft,
                                                         ItemStack stack,
                                                         int mouseX, int mouseY) {
        if (stack == null || stack.isEmpty()) return;
        List<Component> lines = new ArrayList<>();
        try {
            lines.addAll(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level),
                minecraft.player, TooltipFlag.NORMAL));
        } catch (RuntimeException ignored) {
            lines.add(stack.getHoverName());
        }
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            int current = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            lines.add(Component.literal("耐久度: " + current + "/" + stack.getMaxDamage()));
        }
        graphics.renderTooltip(minecraft.font, lines, Optional.empty(), mouseX, mouseY);
    }
    private static void renderCustomTooltip(GuiGraphics graphics, State state,
                                            int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (state.tab == EmbeddedTab.HEALTH) {
            for (PlayerStatusUi.Part part : PlayerStatusUi.Part.values()) {
                Bounds bounds = bodyIconBounds(state, part);
                if (!bounds.contains(mouseX, mouseY)) continue;
                PlayerStatusUi.renderTooltip(graphics, minecraft.font, part,
                    PlayerStatusUi.value(PlayerStatusClientState.INSTANCE, part), mouseX, mouseY);
                return;
            }
        }
        Section safety = safetySection(state);
        if (safety != null && safetyPinBounds(state, safety).contains(mouseX, mouseY)) {
            graphics.renderTooltip(minecraft.font, Component.translatable(
                DeltaInventoryUiState.safetyBoxPinned()
                    ? "status.xero_delta.unpin_safety_box"
                    : "status.xero_delta.pin_safety_box"), mouseX, mouseY);
            return;
        }
    }

    private static Bounds loadoutBounds(Placement p, int visualIndex) {
        int x = p.panelX + scaled(p, BASE_LOADOUT_X);
        int y = p.panelTop + scaled(p, BASE_HEADER + 4 + visualIndex * 40);
        int width = scaled(p, BASE_LOADOUT_WIDTH);
        int large = scaled(p, BASE_LARGE_SLOT);
        if (visualIndex < 4) return new Bounds(x + width - large, y, large, large);
        return new Bounds(x, y, width, large);
    }

    private static Bounds carrierSelector(State state, Section section) {
        int large = scaled(state.placement, BASE_LARGE_SLOT);
        return new Bounds(storageX(state.placement) + scaled(state.placement, 6),
            section.y + scaled(state.placement, 22), large, large);
    }

    private static Bounds safetySelector(State state, Section section) {
        int size = scaled(state.placement, 42);
        return new Bounds(storageX(state.placement) + scaled(state.placement, 6),
            section.y + scaled(state.placement, 22), size, size);
    }

    private static Bounds storageGridBounds(State state, Carrier carrier, Section section) {
        int x = storageX(state.placement) + scaled(state.placement, 6 + BASE_LARGE_SLOT + 10);
        int y = section.y + scaled(state.placement, 22);
        int cell = scaled(state.placement, BASE_CELL);
        if (carrier.pack != null) {
            PackRegionLayout layout = new PackRegionLayout(carrier.pack, cell,
                Math.max(1, scaled(state.placement, 2)));
            return new Bounds(x, y, layout.width(), layout.height());
        }
        return new Bounds(x, y, carrier.columns * cell, carrier.rows * cell);
    }

    private static Bounds safetyGridBounds(State state, Section section) {
        ItemStack stack = safetyBox();
        int width = stack.getItem() instanceof SafetyBoxItem box
            ? box.getGridWidth() * scaled(state.placement, BASE_CELL) : 0;
        int height = stack.getItem() instanceof SafetyBoxItem box
            ? box.getGridHeight() * scaled(state.placement, BASE_CELL) : 0;
        return new Bounds(storageX(state.placement) + scaled(state.placement, 58),
            section.y + scaled(state.placement, 22), width, height);
    }

    private static Section safetySection(State state) {
        if (state.snapshot == null) return null;
        return DeltaInventoryUiState.safetyBoxPinned()
            ? pinnedSafetySection(state) : state.snapshot.sections.get("safety_box");
    }

    private static Section pinnedSafetySection(State state) {
        int height = safetyHeight(state);
        return new Section("safety_box",
            viewportBottom(state.placement) - height, height);
    }

    private static Bounds safetyPinBounds(State state, Section section) {
        Placement p = state.placement;
        int size = scaled(p, 15);
        return new Bounds(storageX(p) + storageWidth(p) - scaled(p, 19),
            section.y + scaled(p, 3), size, size);
    }

    private static void renderSizedItemAbsolute(GuiGraphics graphics, ItemStack stack, Bounds bounds) {
        GridItemRenderer.renderSizedItem(graphics, Minecraft.getInstance().font,
            stack, bounds.x, bounds.y, bounds.width, bounds.height,
            GridBackingStore.isRotated(stack),
            ClientDataCache.INSTANCE.shouldRotateTexture(stack),
            ClientDataCache.INSTANCE.shouldStretchTexture(stack),
            ClientDataCache.INSTANCE.proportionalTextureScale(stack));
    }

    private static void renderSizedItem(GuiGraphics graphics, ItemStack stack,
                                        int x, int y, int width, int height) {
        GridItemRenderer.renderSizedItem(graphics, Minecraft.getInstance().font,
            stack, x, y, width, height, GridBackingStore.isRotated(stack),
            ClientDataCache.INSTANCE.shouldRotateTexture(stack),
            ClientDataCache.INSTANCE.shouldStretchTexture(stack),
            ClientDataCache.INSTANCE.proportionalTextureScale(stack));
    }

    private static void drawCell(GuiGraphics graphics, int x, int y, int cell) {
        DeltaGridCellRenderer.render(graphics, x, y, cell);
    }

    private static void drawKnifeStripes(GuiGraphics graphics, Bounds bounds) {
        for (int offset = -bounds.height; offset < bounds.width; offset += 7) {
            for (int row = 0; row < bounds.height; row++) {
                int x = bounds.x + offset + row;
                if (x >= bounds.x + 1 && x < bounds.x + bounds.width - 1) {
                    graphics.fill(x, bounds.y + row, x + 1, bounds.y + row + 1, 0x2AFFFFFF);
                }
            }
        }
    }

    private static void drawLockIcon(GuiGraphics graphics, int x, int y) {
        graphics.blit(GUI_ICONS, x, y, 0, 0, 8, 8, 40, 8);
    }

    private static void drawSwapIcon(GuiGraphics graphics, int x, int y) {
        graphics.blit(GUI_ICONS, x, y, 8, 0, 8, 8, 40, 8);
    }

    private static void drawPinIcon(GuiGraphics graphics, int x, int y,
                                    boolean pinned, boolean hovered) {
        if (hovered) {
            graphics.fill(x - 1, y - 1, x + 15, y + 14, 0x663B514B);
            graphics.renderOutline(x - 1, y - 1, 16, 15, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
        }
        graphics.blit(GUI_ICONS, x + 3, y + 2, pinned ? 16 : 24, 0,
            8, 8, 40, 8);
    }

    private static int scaled(Placement placement, int value) {
        return Math.max(1, Math.round(value * placement.scale));
    }

    private static int storageX(Placement p) {
        return p.panelX + scaled(p, BASE_STORAGE_X);
    }

    private static int storageWidth(Placement p) {
        return scaled(p, BASE_STORAGE_WIDTH);
    }

    private static int viewportTop(Placement p) {
        return p.panelTop + scaled(p, BASE_HEADER + 4);
    }

    private static int viewportBottom(Placement p) {
        return p.panelTop + p.panelHeight - scaled(p, 6);
    }

    private static boolean insideStorageViewport(State state, double x, double y) {
        Placement p = state.placement;
        return x >= storageX(p) && x < storageX(p) + storageWidth(p)
            && y >= viewportTop(p) && y < scrollViewportBottom(state);
    }

    private static int scrollViewportBottom(State state) {
        if (!DeltaInventoryUiState.safetyBoxPinned()) return viewportBottom(state.placement);
        return Math.max(viewportTop(state.placement) + scaled(state.placement, 24),
            pinnedSafetySection(state).y - scaled(state.placement, BASE_SECTION_GAP));
    }

    private static double maxScroll(State state) {
        if (state.snapshot == null) return 0.0D;
        return Math.max(0.0D, state.snapshot.totalHeight
            - (scrollViewportBottom(state) - viewportTop(state.placement)));
    }

    private static void updateSmoothScroll(State state) {
        long now = System.nanoTime();
        if (state.lastFrameNanos == 0L) {
            state.lastFrameNanos = now;
            return;
        }
        double elapsed = Math.min(0.10D, Math.max(0.0D,
            (now - state.lastFrameNanos) / 1_000_000_000.0D));
        state.lastFrameNanos = now;
        if (state.draggingScrollbar) return;
        double blend = 1.0D - Math.exp(-elapsed * 18.0D);
        state.scroll += (state.targetScroll - state.scroll) * blend;
        if (Math.abs(state.targetScroll - state.scroll) < 0.02D) state.scroll = state.targetScroll;
    }

    private static boolean scrollbarHit(State state, double mouseX, double mouseY) {
        if (maxScroll(state) <= 0.0D) return false;
        int x = storageX(state.placement) + storageWidth(state.placement);
        return mouseX >= x && mouseX < x + 9
            && mouseY >= viewportTop(state.placement)
            && mouseY < scrollViewportBottom(state);
    }

    private static int thumbHeight(State state) {
        int viewport = scrollViewportBottom(state) - viewportTop(state.placement);
        return Math.max(scaled(state.placement, 24),
            (int) Math.round(viewport * viewport
                / (double) Math.max(1, state.snapshot.totalHeight)));
    }

    private static void updateScrollbar(State state, double mouseY) {
        int top = viewportTop(state.placement);
        int height = scrollViewportBottom(state) - top;
        int thumb = thumbHeight(state);
        double relative = mouseY - top - thumb / 2.0D;
        double progress = clamp(relative / Math.max(1.0D, height - thumb), 0.0D, 1.0D);
        state.scroll = progress * maxScroll(state);
        state.targetScroll = state.scroll;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Placement(int screenWidth, int screenHeight, int panelX, int panelTop,
                             int panelWidth, int panelHeight, float scale,
                             int nativeLeft, int nativeTop,
                             int originalLeft, int originalTop,
                             int textureOffsetX, int textureOffsetY,
                             double interfaceScaleX, double interfaceScaleY,
                             double textureScaleX, double textureScaleY,
                             int positionRevision, float layoutScale, int layoutMargin,
                             int nativeContentWidth) {
    }

    private record Position(int x, int y) {
    }
    private record BackgroundAnchor(boolean centeredX, boolean centeredY) {
    }


    private record Bounds(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
        }
    }

    private enum DropPreviewState {
        ACCEPT,
        SWAP,
        REJECT
    }

    private record PlayerCard(Bounds renderBounds, Bounds screenBounds, String label) {
    }

    private record Section(String identifier, int y, int height) {
    }

    private record Carrier(String identifier, ItemStack stack, DeltaPackItem pack,
                           GridBackingStore store, IItemHandler handler,
                           int columns, int rows) {
    }

    private record Snapshot(Map<String, Section> sections, Map<String, Carrier> carriers,
                            int totalHeight) {
    }

    private record StorageTarget(int cell, int column, int row,
                                 double fractionX, double fractionY) {
    }

    private record SafetyGridTarget(SafetyBoxGridInteraction.Target target,
                                    GridBackingStore store, int columns,
                                    int anchorCell, ItemStack stack) {
    }

    public record WarehouseSafetyBoxDrop(int targetCell, boolean rotated,
                                         boolean accepted) {
    }

    private record ShortcutTarget(String identifier, int cell, ItemStack stack) {
    }

    private record EquipmentTarget(int equipmentIndex, String curioIdentifier) {
    }

    private enum EmbeddedTab { CHARACTER, HEALTH }

    private enum EditorTarget {
        INTERFACE, TEXTURE, RECIPE_BOOK, RECIPE_BOOK_BUTTON
    }

    private enum EditorField { X, Y, WIDTH, HEIGHT }

    private enum DragMode { NONE, MOVE, RESIZE_X, RESIZE_Y, RESIZE_BOTH }

    private static final class EditorState {
        private boolean active;
        private EditorTarget target = EditorTarget.INTERFACE;
        private EditorField focused;
        private String input = "";
        private DragMode dragMode = DragMode.NONE;
        private double dragStartX;
        private double dragStartY;
        private int originX;
        private int originY;
        private double originScaleX;
        private double originScaleY;
        private ContainerUiPositionState.Offset original;
        private ContainerUiPositionState.Offset originalRecipe;
        private ContainerUiPositionState.Offset originalRecipeButton;
    }

    private static final class State {
        private final AbstractContainerScreen<?> screen;
        private final Placement placement;
        private final int menuLeft;
        private final int menuTop;
        private final Map<Slot, Position> originals;
        private final List<Slot> playerSlots;
        private final Map<Slot, PlayerCard> playerCards = new IdentityHashMap<>();
        private Snapshot snapshot;
        private double scroll;
        private double targetScroll;
        private boolean draggingScrollbar;
        private boolean draggingContent;
        private double contentDragStartY;
        private double contentDragStartScroll;
        private long lastFrameNanos;
        private final ScreenTransition transition;
        private boolean transitionPushed;
        private boolean interfaceTransformPushed;
        private int mouseX = Integer.MIN_VALUE;
        private int mouseY = Integer.MIN_VALUE;
        private boolean weightDetailsVisible;
        private final InventoryMedicalShortcuts medicalShortcuts = new InventoryMedicalShortcuts();
        private String storageHelpTooltipKey;
        private EmbeddedTab tab = EmbeddedTab.CHARACTER;
        private ShortcutTarget armedItem;
        private Slot armedPlayerSlot;
        private ItemStack armedPlayerStack = ItemStack.EMPTY;
        private String armedCarrier;
        private ItemStack armedCarrierStack = ItemStack.EMPTY;
        private int armedEquipmentSlot = -1;
        private ItemStack armedEquipmentStack = ItemStack.EMPTY;
        private Slot armedExternalSlot;
        private ItemStack armedExternalStack = ItemStack.EMPTY;
        private CorpseScreen.CorpseStorageTarget armedCorpseStorage;
        private ItemStack armedCorpseStorageStack = ItemStack.EMPTY;
        private ShortcutTarget selectedItem;
        private Slot selectedPlayerSlot;
        private String selectedCarrier;
        private Slot selectedExternalSlot;
        private int selectedEquipmentSlot = -1;
        private CorpseScreen.CorpseStorageTarget selectedCorpseStorage;
        private double itemDragStartX;
        private double itemDragStartY;
        private boolean itemDragPickedUp;
        private String lastItemClickKey = "";
        private long lastItemClickAt;
        private String previewIdentifier;
        private int previewHoverCell = -1;
        private int previewSourceCell = -1;
        private ItemStack previewStack = ItemStack.EMPTY;
        private GridBackingStore.PlacementResult previewPlacement;

        private void clearPreviewPlacement() {
            previewIdentifier = null;
            previewHoverCell = -1;
            previewSourceCell = -1;
            previewStack = ItemStack.EMPTY;
            previewPlacement = null;
        }

        private void rememberPreviewPlacement(String identifier, int hoverCell,
                                               int sourceCell, ItemStack stack,
                                               GridBackingStore.PlacementResult placement) {
            previewIdentifier = identifier;
            previewHoverCell = hoverCell;
            previewSourceCell = sourceCell;
            previewStack = stack == null ? ItemStack.EMPTY : stack.copy();
            previewPlacement = placement;
        }

        private State(AbstractContainerScreen<?> screen, Placement placement,
                      int menuLeft, int menuTop, Map<Slot, Position> originals,
                      List<Slot> playerSlots) {
            this.screen = screen;
            this.placement = placement;
            this.menuLeft = menuLeft;
            this.menuTop = menuTop;
            this.originals = originals;
            this.playerSlots = playerSlots;
            this.transition = new ScreenTransition(
                !(screen instanceof PersonalWarehouseScreen));
        }
    }
}
