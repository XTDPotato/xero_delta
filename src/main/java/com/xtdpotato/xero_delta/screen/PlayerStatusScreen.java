package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;
import com.xtdpotato.xero_delta.Config;

import com.xtdpotato.xero_delta.client.PlayerStatusClientState;
import com.xtdpotato.xero_delta.client.DeltaContainerLayoutController;
import com.xtdpotato.xero_delta.client.DeltaInventoryUiState;
import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.ClientGridRotation;
import com.xtdpotato.xero_delta.client.GridItemRenderer;
import com.xtdpotato.xero_delta.client.DeltaGridCellRenderer;
import com.xtdpotato.xero_delta.client.InventorySorterCompat;
import com.xtdpotato.xero_delta.client.InventoryLayoutScale;
import com.xtdpotato.xero_delta.client.ItemDetailOverlay;
import com.xtdpotato.xero_delta.client.PlayerStatusUi;
import com.xtdpotato.xero_delta.client.PlayerStatusScreenState;
import com.xtdpotato.xero_delta.client.MailClientState;
import com.xtdpotato.xero_delta.client.MailOverlayRenderer;
import com.xtdpotato.xero_delta.client.LoadoutLabelRenderer;
import com.xtdpotato.xero_delta.client.LoadoutSlotBackgroundRenderer;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import com.xtdpotato.xero_delta.client.StatusEffectHudRenderer;
import com.xtdpotato.xero_delta.client.StatusEffectHudState;
import com.xtdpotato.xero_delta.client.StorageSectionHeaderRenderer;
import com.xtdpotato.xero_delta.client.TradingUi;
import com.xtdpotato.xero_delta.client.TradingUiScale;
import com.xtdpotato.xero_delta.client.XeroTitleOverlay;
import com.xtdpotato.xero_delta.network.CurioSlotSwapPacket;
import com.xtdpotato.xero_delta.network.CarrierReplacePacket;
import com.xtdpotato.xero_delta.network.EquippedStorageActionPacket;
import com.xtdpotato.xero_delta.network.EquippedStorageShortcutPacket;
import com.xtdpotato.xero_delta.network.MailActionPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.PlayerEquipmentSlotClickPacket;
import com.xtdpotato.xero_delta.network.PlayerLayoutSlotClickPacket;
import com.xtdpotato.xero_delta.network.InventorySourceToEquippedStoragePacket;
import com.xtdpotato.xero_delta.network.InventorySourceToMenuPacket;
import com.xtdpotato.xero_delta.network.ItemDetailActionPacket;
import com.xtdpotato.xero_delta.network.PlayerEquipmentSync;
import com.xtdpotato.xero_delta.trading.TradingInventorySources;
import com.xtdpotato.xero_delta.util.SlotFieldUtil;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.grid.GridGeometry;
import com.xtdpotato.xero_delta.grid.SafetyBoxGridInteraction;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import top.theillusivec4.curios.api.CuriosApi;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;

/** Delta-style character layout backed by the player's real InventoryMenu. */
public final class PlayerStatusScreen extends InventoryScreen {
    private enum Tab { CHARACTER, HEALTH }

    private static final int CANVAS_WIDTH = 704;
    private static final int CANVAS_HEIGHT = 356;
    private static final int HEADER_HEIGHT = 30;
    private static final int MODEL_X = 12;
    private static final int MODEL_WIDTH = 234;
    private static final int EQUIPMENT_X = 254;
    private static final int EQUIPMENT_WIDTH = 174;
    private static final int STORAGE_X = 436;
    private static final int STORAGE_WIDTH = 256;

    private static final int SLOT_HOVER = 0x338FD9B8;
    private static final int EFFECT_CELL = 24;
    private static final int EFFECT_GAP = 2;
    private static final int EFFECT_COLUMNS = 6;
    private static final int EFFECT_LIMIT = 12;
    private static int STORAGE_CELL = 18;
    private static final int STORAGE_REGION_GAP = 2;
    private static final int POCKET_GAP = 2;
    private static final int BACKPACK_COLUMNS = 5;
    private static final int BACKPACK_VISIBLE_ROWS = 9;
    private static final int CARRIER_PANEL_HEIGHT = 226;
    private static final int PRIMARY_CARD_WIDTH = 72;
    private static final int LARGE_SLOT = 36;
    private static final int HEALTH_ACCESSORY_ROW = 24;

    private static final int POCKET_SECTION_HEIGHT = 50;
    private static final int EMPTY_CARRIER_SECTION_HEIGHT = 64;
    private static final int SECTION_GAP = 6;
    private static final int DISCARD_ZONE_WIDTH = 42;
    private static final int DISCARD_EDGE_PHYSICAL_WIDTH = 30;
    private static final float ITEM_WEIGHT_BADGE_Z = 460.0F;
    private static final ResourceLocation GUI_ICONS = ResourceLocation.fromNamespaceAndPath(
        "xero_delta", "textures/gui/delta_icons.png");

    private final Screen parent;
    private final ScreenTransition transition = new ScreenTransition();
    private TradingUiScale.Viewport viewport = TradingUiScale.viewport(1, 1, 0);
    private Tab tab = Tab.CHARACTER;
    private int canvasX;
    private int panelTop;
    private int canvasWidth = CANVAS_WIDTH;
    private int canvasHeight = CANVAS_HEIGHT;
    private boolean restrictedLayout;
    private boolean sharedDeltaLayout;

    public boolean usesSharedDeltaLayout() {
        return sharedDeltaLayout;
    }
    private final int[] originalSlotX;
    private final int[] originalSlotY;
    private double backpackScrollPixels;
    private double backpackTargetScrollPixels;
    private boolean backpackScrollbarDragging;
    private boolean backpackContentDragArmed;
    private boolean backpackContentDragged;
    private double backpackContentLastY;
    private double backpackContentTravel;
    private StorageCell pendingBackpackCell;
    private double layoutScrollPixels;
    private double layoutTargetScrollPixels;
    private boolean layoutScrollbarDragging;
    private boolean layoutContentDragging;
    private double layoutContentLastY;
    private boolean weightDetailsVisible;
    private boolean modelDragging;
    private float modelYaw;
    private long lastScrollFrameNanos;
    private StorageCell armedItem;
    private int armedPocketSlot = -1;
    private StorageCell selectedStorageItem;
    private int selectedPocketSlot = -1;
    private int selectedEquipmentSlot = -1;
    private int selectedLargeInventorySlot = -1;
    private String selectedCurioIdentifier;
    private double itemDragStartX;
    private double itemDragStartY;
    private boolean itemDragPickedUp;
    private ItemStack armedDragStack = ItemStack.EMPTY;
    private int armedEquipmentSlot = -1;
    private int armedLargeInventorySlot = -1;
    private String armedCurioIdentifier;
    private String lastItemClickKey = "";
    private long lastItemClickAt;
    private String storageHelpTooltipKey;
    private float appliedInventoryLayoutScale = -1.0F;
    private double healthAccessoryScroll;

    public PlayerStatusScreen(Screen parent) {
        this(parent, Objects.requireNonNull(Minecraft.getInstance().player));
    }

    public PlayerStatusScreen(Screen parent, boolean openHealthTab) {
        this(parent, Objects.requireNonNull(Minecraft.getInstance().player));
        tab = openHealthTab ? Tab.HEALTH : Tab.CHARACTER;
    }

    private PlayerStatusScreen(Screen parent, LocalPlayer player) {
        // Extending InventoryScreen is intentional: Better Looting registers its
        // inventory-side loot list and drag interactions only for InventoryScreen.
        super(player);
        this.parent = parent;
        imageWidth = CANVAS_WIDTH;
        imageHeight = CANVAS_HEIGHT;
        originalSlotX = new int[menu.slots.size()];
        originalSlotY = new int[menu.slots.size()];
        for (int index = 0; index < menu.slots.size(); index++) {
            originalSlotX[index] = menu.slots.get(index).x;
            originalSlotY[index] = menu.slots.get(index).y;
        }
    }

    @Override
    protected void init() {
        DeltaContainerLayoutController.restore(this);
        sharedDeltaLayout = PlayerStatusClientState.INSTANCE.layoutEnabled()
            && minecraft.player != null && !minecraft.player.isCreative();
        if (sharedDeltaLayout) {
            restoreSlotLayout();
            restrictedLayout = false;
            getRecipeBookComponent().init(width, height, minecraft, false, menu);
            if (getRecipeBookComponent().isVisible()) getRecipeBookComponent().toggleVisibility();
            imageWidth = com.xtdpotato.xero_delta.client.DeltaInventoryLayout.WIDTH;
            imageHeight = com.xtdpotato.xero_delta.client.DeltaInventoryLayout.HEIGHT;
            int[] position = DeltaContainerLayoutController.position(this, leftPos, topPos);
            leftPos = position[0];
            topPos = position[1];
            titleLabelX = -1000;
            inventoryLabelX = -1000;
            DeltaContainerLayoutController.prepare(this, leftPos, topPos);
            if (tab == Tab.HEALTH) DeltaContainerLayoutController.openHealthTab(this);
            ModNetwork.sendToServer(new com.xtdpotato.xero_delta.network.OpenNearbyWarehousePacket());
            return;
        }
        STORAGE_CELL = InventoryLayoutScale.cellSize(
            18, StatusEffectHudState.inventoryLayoutScale());
        viewport = adaptiveViewport(width, height);
        width = viewport.logicalWidth();
        height = viewport.logicalHeight();
        // Do not call InventoryScreen#init: it would add the vanilla recipe
        // book button and replace this screen with the creative inventory.
        // The component is initialized but kept hidden because InventoryScreen
        // still references it from its render/input methods.
        getRecipeBookComponent().init(width, height, minecraft, false, menu);
        if (getRecipeBookComponent().isVisible()) {
            getRecipeBookComponent().toggleVisibility();
        }
        canvasWidth = Math.max(CANVAS_WIDTH, width - 16);
        canvasHeight = Math.max(CANVAS_HEIGHT, height - 16);
        panelTop = Math.max(4, (height - canvasHeight) / 2);
        canvasX = Math.max(4, (width - canvasWidth) / 2);
        imageWidth = canvasWidth;
        imageHeight = canvasHeight;
        leftPos = canvasX;
        topPos = panelTop;
        applyDeltaSlotLayout();
        titleLabelX = -1000;
        inventoryLabelX = -1000;
        if (restrictedLayout) {
            ModNetwork.sendToServer(new com.xtdpotato.xero_delta.network.OpenNearbyWarehousePacket());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (sharedDeltaLayout) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        refreshInventoryLayoutScale();
        updateSmoothScroll();
        storageHelpTooltipKey = null;
        int logicalMouseX = viewport.mouseX(mouseX);
        int logicalMouseY = viewport.mouseY(mouseY);
        weightDetailsVisible = weightDetailsBounds().contains(logicalMouseX, logicalMouseY);
        graphics.pose().pushPose();
        viewport.apply(graphics);
        transition.push(graphics);
        try {
            super.render(graphics, logicalMouseX, logicalMouseY, partialTick);
            if (restrictedLayout) {
                renderPocketHoverHighlight(graphics, logicalMouseX, logicalMouseY);
                renderCarriedStoragePreview(graphics, logicalMouseX, logicalMouseY);
                drawDiscardZones(graphics, logicalMouseX, logicalMouseY);
            }
            if (weightDetailsVisible) renderVanillaSlotWeightBadges(graphics);
            if (!restrictedLayout) renderTooltip(graphics, logicalMouseX, logicalMouseY);
        } finally {
            transition.pop(graphics);
            transition.drawFade(graphics, width, height);
            graphics.pose().popPose();
        }
    }

    /** Final screen layer, after the global safety-box foreground overlay. */
    public void renderTopmostTooltips(GuiGraphics graphics, int physicalMouseX,
                                      int physicalMouseY) {
        if (sharedDeltaLayout) {
            DeltaContainerLayoutController.renderTooltipTopmost(this, graphics,
                physicalMouseX, physicalMouseY);
            return;
        }
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        int mouseX = viewport.mouseX(physicalMouseX);
        int mouseY = viewport.mouseY(physicalMouseY);
        graphics.pose().pushPose();
        viewport.apply(graphics);
        transition.pushContent(graphics);
        graphics.pose().translate(0.0F, 0.0F,
            com.xtdpotato.xero_delta.client.ScreenLayerResolver.finalPass());
        try {
            drawExtraTooltips(graphics, mouseX, mouseY);
            if (selectedStorageItem != null) {
                int[] selected = storageDetailBounds(selectedStorageItem);
                boolean visible = storageSelectionVisible(
                    selectedStorageItem.identifier(), selected);
                ItemDetailOverlay.updateAnchor(this, selected[0], selected[1],
                    selected[2], selected[3], visible);
            } else if (selectedPocketSlot >= 4) {
                int[] selected = pocketBounds(selectedPocketSlot);
                boolean visible = scrollableSelectionVisible(selected);
                ItemDetailOverlay.updateAnchor(this, selected[0], selected[1],
                    selected[2], selected[3], visible);
            } else if (selectedCurioIdentifier != null) {
                int[] selected = carrierSlotBounds(selectedCurioIdentifier);
                ItemDetailOverlay.updateAnchor(this, selected[0], selected[1],
                    selected[2], selected[3], scrollableSelectionVisible(selected));
            }
            ItemDetailOverlay.render(this, graphics, mouseX, mouseY);
            StorageSectionHeaderRenderer.renderTooltip(graphics, font,
                storageHelpTooltipKey, mouseX, mouseY);
        } finally {
            transition.pop(graphics);
            graphics.pose().popPose();
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        }
        // Source/uses is a screen-space dialog. It must not inherit the Delta
        // inventory viewport scale used by the underlying item-detail card.
        ItemDetailOverlay.renderModalTopmost(this, graphics, physicalMouseX, physicalMouseY);
    }

    private boolean storageSelectionVisible(String identifier, int[] bounds) {
        if ("safety_box".equals(identifier) && DeltaInventoryUiState.safetyBoxPinned()) {
            return bounds[1] + bounds[3] > panelTop + HEADER_HEIGHT
                && bounds[1] < panelTop + canvasHeight;
        }
        return scrollableSelectionVisible(bounds);
    }

    private boolean scrollableSelectionVisible(int[] bounds) {
        return bounds[0] + bounds[2] > restrictedColumnX()
            && bounds[0] < restrictedColumnX() + restrictedColumnWidth()
            && bounds[1] + bounds[3] > layoutViewportTop()
            && bounds[1] < layoutViewportBottom();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (sharedDeltaLayout) return;
        graphics.fill(0, 0, width, height, 0xB8050909);
        graphics.fill(canvasX, panelTop, canvasX + canvasWidth,
            panelTop + canvasHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.BACKGROUND);
        drawHeader(graphics, mouseX, mouseY);
        drawInventoryPanel(graphics, mouseX, mouseY);
        drawStatusPanel(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }


    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        int right = canvasX + canvasWidth;
        graphics.fill(canvasX, panelTop, right, panelTop + HEADER_HEIGHT, 0xF00C1313);
        graphics.fill(canvasX, panelTop + HEADER_HEIGHT - 1, right,
            panelTop + HEADER_HEIGHT, 0xFF263231);
        int tabWidth = 78;
        drawTab(graphics, canvasX + 8, panelTop + 4, tabWidth,
            Component.translatable("status.xero_delta.character"), tab == Tab.CHARACTER, mouseX, mouseY);
        drawTab(graphics, canvasX + 8 + tabWidth, panelTop + 4, tabWidth,
            Component.translatable("status.xero_delta.health"), tab == Tab.HEALTH, mouseX, mouseY);
        PlayerStatusClientState status = PlayerStatusClientState.INSTANCE;
        int closeX = right - 26;
        int mailX = closeX - 24;
        int balanceWidth = TradingUi.balanceWidth(font, status.balance());
        TradingUi.drawBalance(graphics, font, status.balance(),
            Math.max(canvasX + 190, mailX - balanceWidth - 8), panelTop + 10, 0xFFF0D477);
        MailOverlayRenderer.render(graphics, font, mailX, panelTop + 4,
            MailClientState.INSTANCE.unreadCount(), mouseX, mouseY);
        drawControl(graphics, closeX, panelTop + 4, "x", mouseX, mouseY);
    }

    private void drawTab(GuiGraphics g, int x, int y, int width, Component label,
                         boolean selected, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, 22);
        g.fill(x, y, x + width, y + 22, selected ? 0xAA29483E : hovered ? 0x99313E3C : 0x00000000);
        if (selected) g.fill(x, y + 20, x + width, y + 22, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
        g.drawCenteredString(font, label, x + width / 2, y + 7,
            selected ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
    }

    private void drawControl(GuiGraphics g, int x, int y, String text, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 20, 22);
        g.fill(x, y, x + 20, y + 22, hovered ? 0xAA29483E : 0x440C1313);
        g.renderOutline(x, y, 20, 22, hovered ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        g.drawCenteredString(font, text, x + 10, y + 7, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT);
    }

    private void drawStatusPanel(GuiGraphics g, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) return;
        int modelLeft = canvasX + MODEL_X;
        int modelTop = panelTop + HEADER_HEIGHT + 4;
        int modelRight = modelLeft + MODEL_WIDTH;
        int modelBottom = panelTop + canvasHeight - 10;
        g.fill(modelLeft, modelTop, modelRight, modelBottom, 0x22050A0A);
        int entityRight = restrictedLayout ? modelLoadoutX() - 4 : modelRight;
        if (Config.INSTANCE.inventoryShowEquipment.get()) {
            InventoryScreen.renderEntityInInventoryFollowsAngle(g, modelLeft, modelTop, entityRight, modelBottom,
                82, 0.0625F, 0.0F, 0.0F, minecraft.player);
        } else {
            ItemStack[] armor = new ItemStack[4];
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < slots.length; i++) {
                armor[i] = minecraft.player.getItemBySlot(slots[i]).copy();
                minecraft.player.setItemSlot(slots[i], ItemStack.EMPTY);
            }
            try {
                InventoryScreen.renderEntityInInventoryFollowsAngle(g, modelLeft, modelTop, entityRight, modelBottom,
                    82, 0.0625F, 0.0F, 0.0F, minecraft.player);
            } finally {
                for (int i = 0; i < slots.length; i++) minecraft.player.setItemSlot(slots[i], armor[i]);
            }
        }
        if (restrictedLayout && tab == Tab.CHARACTER) {
            drawRestrictedLoadoutFrames(g, mouseX, mouseY);
        } else if (tab == Tab.HEALTH) {
            drawHealthBodyIcons(g, mouseX, mouseY, modelLeft, modelTop);
        }
        drawWeight(g, modelLeft + 6, weightRowY(), MODEL_WIDTH - 12);
        if (!restrictedLayout) {
            drawSafetyBoxSelector(g, safetySelectorX(), safetySelectorY(),
                LARGE_SLOT + 6, LARGE_SLOT + 6, mouseX, mouseY);
            drawActiveEffects(g, mouseX, mouseY);
        }
    }

    private void drawHealthBodyIcons(GuiGraphics graphics, int mouseX, int mouseY,
                                     int modelLeft, int modelTop) {
        PlayerStatusClientState state = PlayerStatusClientState.INSTANCE;
        for (PlayerStatusUi.Part part : PlayerStatusUi.Part.values()) {
            int[] bounds = healthBodyPartBounds(part, modelLeft, modelTop);
            PlayerStatusUi.drawIcon(graphics, bounds[0], bounds[1], bounds[2], part,
                PlayerStatusUi.value(state, part), inside(mouseX, mouseY,
                    bounds[0], bounds[1], bounds[2], bounds[2]));
        }
    }

    private int[] healthBodyPartBounds(PlayerStatusUi.Part part, int modelLeft, int modelTop) {
        int[] offset = switch (part) {
            case HEAD -> new int[]{180, 18};
            case CHEST -> new int[]{190, 66};
            case LEFT_ARM -> new int[]{12, 94};
            case RIGHT_ARM -> new int[]{195, 112};
            case ABDOMEN -> new int[]{188, 158};
            case LEFT_LEG -> new int[]{24, 220};
            case RIGHT_LEG -> new int[]{184, 220};
        };
        return new int[]{modelLeft + offset[0], modelTop + offset[1], 27};
    }

    private void switchTab(Tab next) {
        if (tab == next) return;
        ItemDetailOverlay.close(this);
        clearDetailSelection();
        clearItemGesture();
        tab = next;
        applyDeltaSlotLayout();
    }

    private void drawActiveEffects(GuiGraphics g, int mouseX, int mouseY) {
        List<MobEffectInstance> effects = visibleStatusEffects();
        float scale = StatusEffectHudState.inventoryEffectScale();
        float opacity = StatusEffectHudState.inventoryEffectOpacity();
        int cell = Math.max(12, Math.round(EFFECT_CELL * scale));
        int gap = Math.max(1, Math.round(EFFECT_GAP * scale));
        int[] origin = inventoryEffectOrigin(cell, gap);
        int startX = origin[0];
        int startY = origin[1];
        for (int index = 0; index < Math.min(EFFECT_LIMIT, effects.size()); index++) {
            MobEffectInstance effect = effects.get(index);
            int x = startX + index % EFFECT_COLUMNS * (cell + gap);
            int y = startY + index / EFFECT_COLUMNS * (cell + gap);
            boolean hovered = inside(mouseX, mouseY, x, y, cell, cell);
            int border = effect.getEffect().value().isBeneficial() ? 0xFF67D99F : 0xFFE56A62;
            g.fill(x, y, x + cell, y + cell,
                withOpacity(hovered ? 0xE02D3B40 : 0xD0141D20, opacity));
            g.renderOutline(x, y, cell, cell,
                withOpacity(hovered ? 0xFFF1F5F3 : border, opacity));
            g.setColor(1.0F, 1.0F, 1.0F, opacity);
            StatusEffectHudRenderer.drawEffectIconScaled(g, minecraft, effect,
                x, y, cell, opacity);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (effect.getAmplifier() > 0) {
                String level = String.valueOf(effect.getAmplifier() + 1);
                g.drawCenteredString(font, level, x + cell / 2,
                    y + cell - font.lineHeight, withOpacity(0xFFFFFFFF, opacity));
            }
        }
    }

    private int[] inventoryEffectOrigin(int cell, int gap) {
        int effectWidth = EFFECT_COLUMNS * cell + (EFFECT_COLUMNS - 1) * gap;
        int effectHeight = 2 * cell + gap;
        return StatusEffectHudState.inventoryEffectPosition(
            restrictedColumnX() + 8,
            panelTop + canvasHeight - StatusEffectHudState.inventoryLayoutMargin()
                - effectHeight,
            width, height, effectWidth, effectHeight);
    }

    private static int withOpacity(int color, float opacity) {
        int alpha = Math.max(0, Math.min(255,
            Math.round(((color >>> 24) & 0xFF) * opacity)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private List<MobEffectInstance> visibleStatusEffects() {
        if (minecraft == null || minecraft.player == null) return List.of();
        List<MobEffectInstance> effects = new ArrayList<>(minecraft.player.getActiveEffects());
        effects.removeIf(effect -> !effect.showIcon());
        effects.sort(Comparator
            .comparing((MobEffectInstance value) -> value.getEffect().value().isBeneficial()).reversed()
            .thenComparing(value -> value.getEffect().value().getDisplayName().getString()));
        return effects;
    }

    private boolean renderActiveEffectTooltip(GuiGraphics g, int mouseX, int mouseY) {
        List<MobEffectInstance> effects = visibleStatusEffects();
        float scale = StatusEffectHudState.inventoryEffectScale();
        int cell = Math.max(12, Math.round(EFFECT_CELL * scale));
        int gap = Math.max(1, Math.round(EFFECT_GAP * scale));
        int[] origin = inventoryEffectOrigin(cell, gap);
        int startX = origin[0];
        int startY = origin[1];
        for (int index = 0; index < Math.min(EFFECT_LIMIT, effects.size()); index++) {
            int x = startX + index % EFFECT_COLUMNS * (cell + gap);
            int y = startY + index / EFFECT_COLUMNS * (cell + gap);
            if (!inside(mouseX, mouseY, x, y, cell, cell)) continue;
            drawActiveEffectTooltip(g, effects.get(index), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void drawActiveEffectTooltip(GuiGraphics g, MobEffectInstance effect,
                                         int mouseX, int mouseY) {
        String level = Component.translatable("potion.potency." + effect.getAmplifier()).getString();
        String title = effect.getEffect().value().getDisplayName().getString() + " " + level;
        String duration = StatusEffectHudRenderer.formatDuration(effect.getDuration());
        int panelWidth = Math.max(92, Math.max(font.width(title), font.width(duration)) + 32);
        int panelHeight = 32;
        int x = Math.min(width - panelWidth - 4, mouseX + 12);
        int y = Math.min(height - panelHeight - 4, mouseY + 12);
        x = Math.max(4, x);
        y = Math.max(4, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(x, y, x + panelWidth, y + panelHeight, 0xF0101719);
        g.renderOutline(x, y, panelWidth, panelHeight,
            effect.getEffect().value().isBeneficial() ? 0xFF67D99F : 0xFFE56A62);
        StatusEffectHudRenderer.drawEffectIcon(g, minecraft, effect, x - 1, y + 2, 1.0F);
        g.drawString(font, title, x + 28, y + 5, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        g.drawString(font, duration, x + 28, y + 18, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        g.pose().popPose();
    }
    private void drawSafetyBoxSelector(GuiGraphics g, int x, int y, int width, int height,
        int mouseX, int mouseY) {
        boolean canChange = PlayerStatusClientState.INSTANCE.canChangeBc();
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        ItemStack equipped = equippedSafetyBox();
        if (equipped.isEmpty()) {
            g.fill(x, y, x + width, y + height, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        } else {
            renderSizedGridItem(g, equipped, x, y, width, height);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 160);
        g.renderOutline(x, y, width, height, hovered ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        g.pose().popPose();
        if (weightDetailsVisible && !equipped.isEmpty()) {
            drawItemWeightBadge(g, equipped, x, y, width, height);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        drawLockIcon(g, x + 3, y + height - 11);
        if (canChange) drawSwapIcon(g, x + width - 10, y + 3);
        g.pose().popPose();
    }

    private int safetySectionY() {
        if (restrictedLayout) {
            return DeltaInventoryUiState.safetyBoxPinned()
                ? pinnedSafetySectionY() : restrictedSectionY("safety_box");
        }
        return panelTop + 180;
    }

    private int pinnedSafetySectionY() {
        return panelTop + canvasHeight - 10 - safetyBoxSectionHeight();
    }

    private int safetyBoxBaseSectionHeight() {
        int gridHeight = 0;
        ItemStack equipped = equippedSafetyBox();
        if (equipped.getItem() instanceof com.xtdpotato.xero_delta.item.SafetyBoxItem box) {
            gridHeight = box.getGridHeight() * STORAGE_CELL;
        }
        return 22 + Math.max(LARGE_SLOT + 6, gridHeight) + 8;
    }

    private int safetyBoxSectionHeight() {
        int base = safetyBoxBaseSectionHeight();
        if (!restrictedLayout) return base;
        int effectCount = Math.min(EFFECT_LIMIT, visibleStatusEffects().size());
        if (effectCount <= 0) return base;
        float scale = StatusEffectHudState.inventoryEffectScale();
        int cell = Math.max(12, Math.round(EFFECT_CELL * scale));
        int gap = Math.max(1, Math.round(EFFECT_GAP * scale));
        int rows = (effectCount + EFFECT_COLUMNS - 1) / EFFECT_COLUMNS;
        return base + 6 + rows * cell + Math.max(0, rows - 1) * gap;
    }

    private int safetyPinX() {
        return restrictedColumnX() + restrictedColumnWidth() - 19;
    }

    private int safetySelectorX() {
        return (restrictedLayout ? restrictedColumnX() : canvasX + STORAGE_X) + 8;
    }

    private int safetySelectorY() {
        return safetySectionY() + 22;
    }

    private void openSafetyBoxPickerOrNotice() {
        if (minecraft == null) return;
        if (PlayerStatusClientState.INSTANCE.canChangeBc()) {
            minecraft.setScreen(new SafetyBoxPickerScreen(this));
            return;
        }
        ItemStack stack = equippedSafetyBox();
        ItemDetailOverlay.open(this, stack, true,
            TradingInventorySources.sourceIdForCurioSlot("safety_box", 0, stack),
            safetySelectorX(), safetySelectorY(), LARGE_SLOT + 6, LARGE_SLOT + 6);
    }
    /** Physical coordinates used by global mouse input and foreground overlays. */
    public int safetyBoxGridX() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.safetyBoxGridX(this);
        return viewport.offsetX() + (int) Math.round(
            (safetyBoxGridLogicalX() + transition.horizontalOffset()) * viewport.scale());
    }

    public int safetyBoxGridY() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.safetyBoxGridY(this);
        return viewport.offsetY() + Math.round(safetyBoxGridLogicalY() * viewport.scale());
    }

    public int safetyBoxGridLogicalX() {
        if (sharedDeltaLayout) return safetyBoxGridX();
        return (restrictedLayout ? restrictedColumnX() : canvasX + STORAGE_X) + 58;
    }

    public int safetyBoxGridLogicalY() {
        if (sharedDeltaLayout) return safetyBoxGridY();
        return safetySelectorY();
    }

    public int safetyBoxInspectX() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.safetyBoxInspectX(this);
        return viewport.offsetX() + (int) Math.round(
            (safetySelectorX() + transition.horizontalOffset()) * viewport.scale());
    }

    public int safetyBoxInspectY() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.safetyBoxInspectY(this);
        return viewport.offsetY() + Math.round(safetySelectorY() * viewport.scale());
    }

    public int safetyBoxInspectWidth() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.safetyBoxInspectWidth(this);
        return Math.round((LARGE_SLOT + 6) * viewport.scale());
    }

    public int safetyBoxInspectHeight() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.safetyBoxInspectHeight(this);
        return Math.round((LARGE_SLOT + 6) * viewport.scale());
    }

    /** Cancel the screen's adaptive viewport while drawing physical overlays. */
    public void applyPhysicalOverlayTransform(GuiGraphics graphics) {
        if (sharedDeltaLayout) return;
        graphics.pose().translate((float) -transition.horizontalOffset(), 0.0F, 0.0F);
float scale = viewport.scale();
        graphics.pose().scale(1.0F / scale, 1.0F / scale, 1.0F);
        graphics.pose().translate(-viewport.offsetX(), -viewport.offsetY(), 0.0F);
    }
    public float safetyBoxGridScale() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.safetyBoxCellSize(this)
            / (float) GridGeometry.CELL_SIZE;
        return InventoryLayoutScale.embeddedGridScale(
            viewport.scale(), STORAGE_CELL, GridGeometry.CELL_SIZE);
    }

    public boolean showsItemWeightBadges() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.showsItemWeightBadges(this);
        return weightDetailsVisible;
    }

    public boolean suppressesVanillaSlotHighlight(int x, int y) {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.suppressesVanillaSlotHighlight(this, x, y);
        if (!restrictedLayout) return false;
        for (Slot slot : menu.slots) {
            int index = slot.getContainerSlot();
            if ((index >= 4 && index <= 8 || index == 38 || index == 39)
                && slot.x == x && slot.y == y) return true;
        }
        return false;
    }

    public boolean suppressesHoveredItemTooltip() {
        if (!(sharedDeltaLayout || restrictedLayout) || hoveredSlot == null
            || minecraft == null || minecraft.player == null
            || hoveredSlot.container != minecraft.player.getInventory()) return false;
        int inventorySlot = hoveredSlot.getContainerSlot();
        return inventorySlot >= 0 && inventorySlot <= 8;
    }

    public void renderExternalItemWeightBadge(GuiGraphics graphics, ItemStack stack,
                                              int x, int y, int width, int height) {
        if (sharedDeltaLayout) {
            DeltaContainerLayoutController.renderExternalItemWeightBadge(this, graphics,
                stack, x, y, width, height);
            return;
        }
        if (weightDetailsVisible) {
            drawItemWeightBadge(graphics, stack, x, y, width, height);
        }
    }

    public int embeddedLayoutClipLeft() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.embeddedClipLeft(this);
        return viewport.offsetX() + (int) Math.round((restrictedColumnX()
            + transition.horizontalOffset()) * viewport.scale());
    }

    public int embeddedLayoutClipTop() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.embeddedClipTop(this);
        return viewport.offsetY() + Math.round(layoutViewportTop() * viewport.scale());
    }

    public int embeddedLayoutClipRight() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.embeddedClipRight(this);
        return viewport.offsetX() + (int) Math.round((restrictedColumnX()
            + restrictedColumnWidth() + transition.horizontalOffset()) * viewport.scale());
    }

    public int embeddedLayoutClipBottom() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.embeddedClipBottom(this);
        int bottom = DeltaInventoryUiState.safetyBoxPinned()
            ? panelTop + canvasHeight - 10 : layoutViewportBottom();
        return viewport.offsetY() + Math.round(bottom * viewport.scale());
    }

    /**
     * Better Looting computes panelX as leftPos - 2 - 30 - constant.
     * Returning a dynamic constant keeps its renderer and hit-test caches in
     * the intentionally empty right-hand region of the Delta canvas.
     */
    public int betterLootingPanelOffsetConstant() {
        if (sharedDeltaLayout) return leftPos - 32
            - (DeltaContainerLayoutController.playerPanelRight(this) + 12);
        int desiredPanelX = viewport.offsetX()
            + (int) Math.round((canvasX + STORAGE_X + 8
                + transition.horizontalOffset()) * viewport.scale());
        return leftPos - 32 - desiredPanelX;
    }

    /** Physical Y coordinate aligned with the chest-rig title row. */
    public int betterLootingPanelTop() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.embeddedClipTop(this);
        // Better Looting is a separate, fixed right-hand surface. Only the player
        // layout scrolls, so its title stays anchored to the viewport.
        return viewport.offsetY() + Math.round(layoutViewportTop() * viewport.scale());
    }

    /**
     * Resolves a Better Looting drag release against the custom Delta storage
     * surfaces. Input coordinates are physical GUI coordinates because Better
     * Looting receives them before this screen applies its adaptive viewport.
     */
    public BetterLootingDropTarget betterLootingDropTargetAt(double mouseX, double mouseY, ItemStack dragged) {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.betterLootingDropTargetAt(
            this, mouseX, mouseY, dragged);
        if (!restrictedLayout) return null;
        double logicalX = viewport.mouseXDouble(mouseX);
        double logicalY = viewport.mouseYDouble(mouseY);
        int[] helmet = loadoutBounds(0);
        if (inside(logicalX, logicalY, helmet[0], helmet[1], helmet[2], helmet[3])) {
            return minecraft != null && minecraft.player != null
                && PlayerEquipmentSync.canEquip(minecraft.player, dragged, EquipmentSlot.HEAD)
                    ? new BetterLootingDropTarget("helmet", -1, false) : null;
        }
        int[] chest = loadoutBounds(1);
        if (inside(logicalX, logicalY, chest[0], chest[1], chest[2], chest[3])) {
            return minecraft != null && minecraft.player != null
                && PlayerEquipmentSync.canEquip(minecraft.player, dragged, EquipmentSlot.CHEST)
                    ? new BetterLootingDropTarget("chest", -1, false) : null;
        }

        int pocketSlot = pocketInventorySlotAt(logicalX, logicalY);
        if (pocketSlot >= 4) {
            return new BetterLootingDropTarget("pockets", pocketSlot, false);
        }

        for (String identifier : List.of("chest_rig", "backpack")) {
            int[] bounds = carrierSlotBounds(identifier);
            if (inside(logicalX, logicalY, bounds[0], bounds[1], bounds[2], bounds[3])
                && dragged.getCount() == 1
                && dragged.getItem() instanceof DeltaPackItem pack
                && identifier.equals(pack.slotIdentifier())) {
                return new BetterLootingDropTarget(identifier, -1, false);
            }
        }

        StorageCell storage = shortcutStorageCellAt(logicalX, logicalY);
        if (storage != null) {
            GridBackingStore.PlacementResult placement = resolveStoragePlacement(
                storage, dragged, logicalX, logicalY);
            if (placement != null && !placement.isAccepted()) return null;
            return placement == null
                ? new BetterLootingDropTarget(storage.identifier(), storage.cell(),
                    GridBackingStore.isRotated(dragged))
                : new BetterLootingDropTarget(storage.identifier(),
                    placement.y() * storageColumns(storage.identifier()) + placement.x(),
                    placement.rotated());
        }

        return null;
    }

    public record BetterLootingDropTarget(String identifier, int cell, boolean rotated) {}

    private ItemStack equippedSafetyBox() {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        try {
            var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(minecraft.player);
            if (curios.isPresent()) {
                var found = curios.get().findFirstCurio(stack ->
                    stack.is(com.xtdpotato.xero_delta.tag.ModTags.SAFETY_BOX));
                if (found.isPresent()) return found.get().stack();
            }
        } catch (RuntimeException ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static int qualityColor(ItemStack stack) {
        if (!(stack.getItem() instanceof com.xtdpotato.xero_delta.item.SafetyBoxItem box)) return 0xAA222B2E;
        int cells = box.getGridWidth() * box.getGridHeight();
        if (cells >= 9) return 0xB08B6B24;
        if (cells >= 6) return 0xB06C3D83;
        if (cells >= 4) return 0xB02F5E8C;
        return 0xB0356548;
    }

    private static void drawLockIcon(GuiGraphics g, int x, int y) {
        g.blit(GUI_ICONS, x, y, 0, 0, 8, 8, 40, 8);
    }

    private static void drawSwapIcon(GuiGraphics g, int x, int y) {
        g.blit(GUI_ICONS, x, y, 8, 0, 8, 8, 40, 8);
    }

    private static void drawPinIcon(GuiGraphics g, int x, int y,
                                    boolean pinned, boolean hovered) {
        if (hovered) {
            g.fill(x - 2, y - 2, x + 14, y + 13, 0x663B514B);
            g.renderOutline(x - 2, y - 2, 16, 15, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
        }
        g.blit(GUI_ICONS, x + 2, y + 1, pinned ? 16 : 24, 0, 8, 8, 40, 8);
    }

    private void drawDiscardZones(GuiGraphics g, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null
            || (minecraft.player.containerMenu.getCarried().isEmpty()
                && (!itemDragPickedUp || armedDragStack.isEmpty()))) return;
        int top = panelTop + HEADER_HEIGHT;
        int zoneHeight = Math.max(1, canvasHeight - HEADER_HEIGHT);
        drawDiscardZone(g, canvasX, top, DISCARD_ZONE_WIDTH, zoneHeight);
        int rightX = canvasX + canvasWidth - DISCARD_ZONE_WIDTH;
        drawDiscardZone(g, rightX, top, DISCARD_ZONE_WIDTH, zoneHeight);
    }

    private void drawDiscardZone(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0x4DFF0000);
    }

    private boolean insideDiscardZone(double mouseX, double mouseY) {
        int top = panelTop + HEADER_HEIGHT;
        int height = Math.max(1, canvasHeight - HEADER_HEIGHT);
        return inside(mouseX, mouseY, canvasX, top, DISCARD_ZONE_WIDTH, height)
            || inside(mouseX, mouseY, canvasX + canvasWidth - DISCARD_ZONE_WIDTH,
                top, DISCARD_ZONE_WIDTH, height);
    }

    public boolean insideDiscardZonePhysical(double mouseX, double mouseY) {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.isDiscardEdge(this, mouseX, mouseY);
        int physicalWidth = viewport.physicalWidth() > 0 ? viewport.physicalWidth() : width;
        int physicalHeight = viewport.physicalHeight() > 0 ? viewport.physicalHeight() : height;
        int edgeWidth = Math.min(DISCARD_EDGE_PHYSICAL_WIDTH,
            Math.max(1, physicalWidth / 2));
        return mouseY >= 0.0D && mouseY < physicalHeight
            && (mouseX >= 0.0D && mouseX < edgeWidth
                || mouseX >= physicalWidth - edgeWidth && mouseX < physicalWidth);
    }

    private void drawBodyIcons(GuiGraphics g, int x, int y, int mouseX, int mouseY,
                               boolean aroundModel) {
        PlayerStatusClientState state = PlayerStatusClientState.INSTANCE;
        PlayerStatusUi.Part[] parts = PlayerStatusUi.Part.values();
        int size = 27;
        for (int index = 0; index < parts.length; index++) {
            int[] position = bodyIconPosition(parts[index], index, x, y, aroundModel);
            int iconX = position[0];
            int iconY = position[1];
            float value = PlayerStatusUi.value(state, parts[index]);
            boolean hovered = inside(mouseX, mouseY, iconX, iconY, size, size);
            PlayerStatusUi.drawIcon(g, iconX, iconY, size, parts[index], value, hovered);
        }
    }

    private static int[] bodyIconPosition(PlayerStatusUi.Part part, int index,
                                          int x, int y, boolean aroundModel) {
        if (!aroundModel) return new int[]{x + index % 3 * 30, y + index / 3 * 31};
        return switch (part) {
            case HEAD -> new int[]{x + 180, y + 18};
            case CHEST -> new int[]{x + 190, y + 66};
            case LEFT_ARM -> new int[]{x + 12, y + 94};
            case RIGHT_ARM -> new int[]{x + 195, y + 112};
            case ABDOMEN -> new int[]{x + 188, y + 158};
            case LEFT_LEG -> new int[]{x + 24, y + 220};
            case RIGHT_LEG -> new int[]{x + 184, y + 220};
        };
    }

    private int weightRowY() {
        return panelTop + canvasHeight - 22;
    }

    private int weightMenuX() {
        PlayerStatusClientState state = PlayerStatusClientState.INSTANCE;
        String current = formatWeight(state.weightKg());
        String maximum = "/88KG";
        return Math.min(canvasX + MODEL_X + MODEL_WIDTH - 19,
            canvasX + MODEL_X + 6 + 16 + font.width(current)
                + font.width(maximum) + 7);
    }

    /** Covers the KG icon, value, and details affordance as one hover target. */
    private WeightBounds weightDetailsBounds() {
        int weightX = canvasX + MODEL_X + 6;
        int weightY = weightRowY();
        int menuX = weightMenuX();
        int left = weightX - 2;
        int right = menuX + 17;
        return new WeightBounds(left, weightY - 3, Math.max(1, right - left), 18);
    }

    private void drawWeight(GuiGraphics g, int x, int y, int width) {
        PlayerStatusClientState state = PlayerStatusClientState.INSTANCE;
        int color = 0xFFFFFFFF;
        g.blit(ResourceLocation.fromNamespaceAndPath("xero_delta", "textures/gui/weight_kg.png"),
            x, y - 2, 16, 16, 0, 0, 16, 16, 16, 16);
        String current = formatWeight(state.weightKg());
        String maximum = "/88KG";
        int valueX = x + 16;
        g.drawString(font, current, valueX, y + 2, color, false);
        g.drawString(font, maximum, valueX + font.width(current), y + 2, color, false);

        int menuX = weightMenuX();
        boolean hovered = weightDetailsVisible;
        g.fill(menuX - 2, y - 2, menuX + 17, y + 13,
            hovered ? 0xAA2A3937 : 0x550B1212);
        for (int row = 0; row < 3; row++) {
            g.fill(menuX + 2, y + 1 + row * 4, menuX + 13, y + 2 + row * 4,
                hovered ? 0xFFF2F5F3 : 0xFF9EAAA7);
        }
        if (hovered) drawWeightDetails(g, x, y, width, state, color);
    }

    private void drawWeightDetails(GuiGraphics g, int x, int y, int width,
                                   PlayerStatusClientState state, int color) {
        int panelHeight = 76;
        int top = y - panelHeight - 5;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 420.0F);
        g.fill(x, top, x + width, y - 3, 0xF00B1113);
        g.renderOutline(x, top, width, panelHeight + 2, color);
        Component title = state.overloaded()
            ? Component.translatable("status.xero_delta.weight_state.overloaded")
            : state.encumbered()
                ? Component.translatable("status.xero_delta.weight_state.encumbered")
                : Component.translatable("status.xero_delta.weight_state.normal");
        g.drawString(font, title, x + 8, top + 7, color, false);
        g.drawString(font, Component.translatable("status.xero_delta.weight_current",
            formatWeight(state.weightKg()), "88KG"), x + 8, top + 21, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        g.drawString(font, Component.translatable("status.xero_delta.weight_threshold",
            "50KG", "88KG"), x + 8, top + 35, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        g.drawString(font, Component.translatable("status.xero_delta.speed_penalty",
            Math.round(state.speedPenalty() * 100.0D)), x + 8, top + 49,
            state.speedPenalty() > 0.0D ? color : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        g.drawString(font, Component.translatable("status.xero_delta.weight_badge_hint"),
            x + 8, top + 62, 0xFF7F8D89, false);
        g.pose().popPose();
    }

    private static void drawWeightIcon(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y, x + 10, y + 2, color);
        g.fill(x + 1, y + 2, x + 12, y + 4, color);
        g.fill(x, y + 4, x + 13, y + 13, color);
        g.fill(x + 3, y + 6, x + 10, y + 11, 0xD00B1113);
        g.fill(x + 5, y + 7, x + 6, y + 10, color);
        g.fill(x + 7, y + 7, x + 9, y + 8, color);
        g.fill(x + 7, y + 9, x + 9, y + 10, color);
    }

    private record WeightBounds(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
        }
    }

    private void drawInventoryPanel(GuiGraphics g, int mouseX, int mouseY) {
        int contentTop = panelTop + HEADER_HEIGHT + 8;
        if (tab == Tab.HEALTH) {
            drawHealthAccessories(g, mouseX, mouseY);
            if (restrictedLayout) drawRestrictedInventoryColumn(g, mouseX, mouseY);
            return;
        }
        if (restrictedLayout) {
            drawRestrictedInventoryColumn(g, mouseX, mouseY);
            return;
        }

        drawSection(g, canvasX + EQUIPMENT_X, contentTop, EQUIPMENT_WIDTH,
            canvasHeight - HEADER_HEIGHT - 18, Component.translatable("status.xero_delta.equipment"));
        drawSection(g, canvasX + STORAGE_X, contentTop, STORAGE_WIDTH, 90,
            Component.translatable("status.xero_delta.inventory_title"));
        drawSection(g, canvasX + STORAGE_X, panelTop + 130, STORAGE_WIDTH, 48,
            Component.translatable("status.xero_delta.hotbar"));
        drawSection(g, canvasX + STORAGE_X, panelTop + 180, STORAGE_WIDTH, 166,
            Component.translatable("status.xero_delta.safety_box"));
        g.drawString(font, Component.translatable("container.crafting"),
            canvasX + EQUIPMENT_X + 72, panelTop + 43, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        for (Slot slot : menu.slots) {
            if (slot.x < 0 || slot.y < 0) continue;
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
            g.renderOutline(x, y, 18, 18, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        }
    }

    private void drawRestrictedInventoryColumn(GuiGraphics g, int mouseX, int mouseY) {
        drawLayoutScrollbar(g);
        int transitionX = (int) Math.round(transition.horizontalOffset());
        viewport.enableScissor(g, restrictedColumnX() + transitionX,
            layoutViewportTop(), restrictedColumnX() + restrictedColumnWidth() + transitionX,
            layoutViewportBottom());
        drawScrollableCarrierSections(g, mouseX, mouseY);
        g.disableScissor();
        if (DeltaInventoryUiState.safetyBoxPinned()) {
            drawSafetyBoxSection(g, pinnedSafetySectionY(), mouseX, mouseY);
        }
    }

    private void drawLayoutScrollbar(GuiGraphics g) {
        int top = layoutViewportTop();
        int height = layoutViewportHeight();
        int contentHeight = restrictedContentHeight();
        if (contentHeight <= height) return;
        int x = restrictedColumnX() + restrictedColumnWidth() + 3;
        g.fill(x, top, x + 3, top + height, 0xFF1B2527);
        double maximum = Math.max(1.0D, layoutMaxScroll());
        int thumbHeight = Math.max(24,
            (int) Math.round(height * height / (double) contentHeight));
        int thumbY = top + (int) Math.round((height - thumbHeight)
            * layoutScrollPixels / maximum);
        g.fill(x, thumbY, x + 3, thumbY + thumbHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
    }

    private int modelLoadoutX() {
        return canvasX + MODEL_X + MODEL_WIDTH - PRIMARY_CARD_WIDTH - 6;
    }

    private int modelLoadoutTop() {
        return panelTop + HEADER_HEIGHT + 8;
    }

    private int[] loadoutBounds(int visualIndex) {
        int x = modelLoadoutX();
        int y = modelLoadoutTop();
        if (visualIndex < 4) {
            return new int[]{x + PRIMARY_CARD_WIDTH - LARGE_SLOT,
                y + visualIndex * 40, LARGE_SLOT, LARGE_SLOT};
        }
        return new int[]{x, y + visualIndex * 40, PRIMARY_CARD_WIDTH, LARGE_SLOT};
    }

    private void drawRestrictedLoadoutFrames(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack[] stacks = minecraft == null || minecraft.player == null
            ? new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY}
            : new ItemStack[]{
                minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD),
                minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
                minecraft.player.getInventory().getItem(2),
                minecraft.player.getInventory().getItem(0),
                minecraft.player.getInventory().getItem(1)
            };
        for (int visualIndex = 0; visualIndex < 6; visualIndex++) {
            int[] bounds = loadoutBounds(visualIndex);
            drawLoadoutFrame(g, bounds[0], bounds[1], bounds[2], bounds[3],
                visualIndex == 3, mouseX, mouseY);
            int stackIndex = visualIndex < 3 ? visualIndex : visualIndex - 1;
            if (visualIndex != 3 && stacks[stackIndex].isEmpty()) {
                LoadoutSlotBackgroundRenderer.Kind kind = switch (visualIndex) {
                    case 0 -> LoadoutSlotBackgroundRenderer.Kind.HELMET;
                    case 1 -> LoadoutSlotBackgroundRenderer.Kind.CHESTPLATE;
                    case 2 -> LoadoutSlotBackgroundRenderer.Kind.SIDEARM;
                    default -> LoadoutSlotBackgroundRenderer.Kind.PRIMARY;
                };
                LoadoutSlotBackgroundRenderer.render(g, bounds[0], bounds[1],
                    bounds[2], bounds[3], kind);
            }
        }
        if (minecraft == null || minecraft.player == null) return;
        ItemStack pistol = minecraft.player.getInventory().getItem(2);
        ItemStack primaryOne = minecraft.player.getInventory().getItem(0);
        ItemStack primaryTwo = minecraft.player.getInventory().getItem(1);
        renderLargeLoadoutItem(g, pistol, loadoutBounds(2));
        renderLargeLoadoutItem(g, primaryOne, loadoutBounds(4));
        renderLargeLoadoutItem(g, primaryTwo, loadoutBounds(5));
        renderLoadoutLabel(g, pistol, "", loadoutBounds(2));
        renderLoadoutLabel(g, primaryOne, "1", loadoutBounds(4));
        renderLoadoutLabel(g, primaryTwo, "2", loadoutBounds(5));
    }

    private void drawDurabilityBar(GuiGraphics g, ItemStack stack,
                                   int x, int y, int width, int height) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()
            || stack.getMaxDamage() <= 0) return;
        float ratio = Math.max(0.0F, Math.min(1.0F,
            (stack.getMaxDamage() - stack.getDamageValue())
                / (float) stack.getMaxDamage()));
        int barHeight = Math.max(2, Math.min(3, height / 12));
        int barY = y + height - barHeight - 2;
        int fill = Math.round((width - 4) * ratio);
        int color = ratio <= 0.20F ? 0xFFE6453D : ratio <= 0.50F ? 0xFFF2B544 : 0xFF55D68B;
        g.fill(x + 2, barY, x + width - 2, barY + barHeight, 0xB51A2224);
        if (fill > 0) g.fill(x + 2, barY, x + 2 + fill, barY + barHeight, color);
    }
    private void drawLoadoutFrame(GuiGraphics g, int x, int y, int width, int height,
                                  boolean knife, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        g.fill(x, y, x + width, y + height, hovered ? 0xD022302D : 0xC00B1212);
        if (knife) {
            for (int offset = -height; offset < width; offset += 7) {
                for (int row = 0; row < height; row++) {
                    int stripeX = x + offset + row;
                    if (stripeX >= x + 1 && stripeX < x + width - 1) {
                        g.fill(stripeX, y + row, stripeX + 1, y + row + 1, 0x2AFFFFFF);
                    }
                }
            }
        }
        g.renderOutline(x, y, width, height, hovered ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        if (knife) {
            drawLockIcon(g, x + 3, y + height - 13);
            if (PlayerStatusClientState.INSTANCE.canChangeBc()) {
                drawSwapIcon(g, x + width - 13, y + 2);
            }
        }
    }

    private void renderLargeLoadoutItem(GuiGraphics g, ItemStack stack, int[] bounds) {
        if (stack.isEmpty()) return;
        GridItemRenderer.renderSizedItem(g, font, stack, bounds[0], bounds[1],
            bounds[2], bounds[3], GridBackingStore.isRotated(stack),
            ClientDataCache.INSTANCE.shouldRotateTexture(stack),
            ClientDataCache.INSTANCE.shouldStretchTexture(stack));
        drawDurabilityBar(g, stack, bounds[0], bounds[1], bounds[2], bounds[3]);
        if (weightDetailsVisible) {
            drawItemWeightBadge(g, stack, bounds[0], bounds[1], bounds[2], bounds[3]);
        }
    }

    private void renderLoadoutLabel(GuiGraphics graphics, ItemStack stack,
                                    String keyLabel, int[] bounds) {
        LoadoutLabelRenderer.render(graphics, font, stack, keyLabel,
            bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (sharedDeltaLayout) {
            super.renderSlot(graphics, slot);
            return;
        }
        if (restrictedLayout) {
            if (tab == Tab.CHARACTER) {
                int visualIndex = switch (slot.index) {
                    case 5 -> 0;
                    case 6 -> 1;
                    case 39 -> 3;
                    default -> -1;
                };
                if (visualIndex >= 0) {
                    ItemStack stack = slot.getItem();
                    int[] bounds = loadoutBounds(visualIndex);
                    if (!stack.isEmpty()) {
                        GridItemRenderer.renderSizedItem(graphics, font, stack,
                            bounds[0] - leftPos, bounds[1] - topPos,
                            bounds[2], bounds[3], GridBackingStore.isRotated(stack),
                            ClientDataCache.INSTANCE.shouldRotateTexture(stack),
                            ClientDataCache.INSTANCE.shouldStretchTexture(stack));
                        drawDurabilityBar(graphics, stack,
                            bounds[0] - leftPos, bounds[1] - topPos, bounds[2], bounds[3]);
                        if (weightDetailsVisible) {
                            drawItemWeightBadge(graphics, stack,
                                bounds[0] - leftPos, bounds[1] - topPos,
                                bounds[2], bounds[3]);
                        }
                    }
                    LoadoutLabelRenderer.render(graphics, font, stack, "",
                        bounds[0] - leftPos, bounds[1] - topPos, bounds[2], bounds[3]);
                    return;
                }
            }
            if (slot.index >= 40 && slot.index <= 44) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    int[] bounds = pocketBounds(slot.index - 36);
                    GridItemRenderer.renderSizedItem(graphics, font, stack,
                        bounds[0] - leftPos, bounds[1] - topPos,
                        bounds[2], bounds[3], GridBackingStore.isRotated(stack),
                        ClientDataCache.INSTANCE.shouldRotateTexture(stack),
                        ClientDataCache.INSTANCE.shouldStretchTexture(stack),
                        ClientDataCache.INSTANCE.proportionalTextureScale(stack));
                    if (weightDetailsVisible) {
                        drawItemWeightBadge(graphics, stack,
                            bounds[0] - leftPos, bounds[1] - topPos,
                            bounds[2], bounds[3]);
                    }
                }
                return;
            }
        }
        super.renderSlot(graphics, slot);
    }

    private int pocketInventorySlotAt(double mouseX, double mouseY) {
        if (!restrictedLayout) return -1;
        if (!inside(mouseX, mouseY, restrictedColumnX(), layoutViewportTop(),
            restrictedColumnWidth(), layoutViewportHeight())) return -1;
        for (int index = 0; index < PocketSlotLayout.COUNT; index++) {
            PocketSlotLayout.Bounds bounds = pocketCellBounds(index);
            if (bounds.contains(mouseX, mouseY)) return index + 4;
        }
        return -1;
    }

    private void renderPocketHoverHighlight(GuiGraphics graphics, int mouseX, int mouseY) {
        int inventorySlot = pocketInventorySlotAt(mouseX, mouseY);
        if (inventorySlot < 4) return;
        int[] bounds = pocketBounds(inventorySlot);
        int transitionX = (int) Math.round(transition.horizontalOffset());
        viewport.enableScissor(graphics, restrictedColumnX() + transitionX,
            layoutViewportTop(), restrictedColumnX() + restrictedColumnWidth() + transitionX,
            layoutViewportBottom());
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.fill(bounds[0], bounds[1], bounds[0] + bounds[2],
            bounds[1] + bounds[3], SLOT_HOVER);
        graphics.renderOutline(bounds[0], bounds[1], bounds[2], bounds[3], com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    private int[] pocketBounds(int inventorySlot) {
        PocketSlotLayout.Bounds bounds = pocketCellBounds(inventorySlot - 4);
        return new int[]{bounds.x(), bounds.y(), bounds.width(), bounds.height()};
    }

    private PocketSlotLayout.Bounds pocketCellBounds(int index) {
        return PocketSlotLayout.bounds(restrictedColumnX(), restrictedSectionY("pockets"),
            STORAGE_CELL, POCKET_GAP, index);
    }
    private int restrictedLargeInventorySlotAt(double mouseX, double mouseY) {
        if (tab == Tab.HEALTH) return -1;
        int[] pistol = loadoutBounds(2);
        if (inside(mouseX, mouseY, pistol[0], pistol[1], pistol[2], pistol[3])) return 2;
        int[] primaryOne = loadoutBounds(4);
        if (inside(mouseX, mouseY, primaryOne[0], primaryOne[1], primaryOne[2], primaryOne[3])) return 0;
        int[] primaryTwo = loadoutBounds(5);
        if (inside(mouseX, mouseY, primaryTwo[0], primaryTwo[1], primaryTwo[2], primaryTwo[3])) return 1;
        return -1;
    }

    private int restrictedEquipmentSlotAt(double mouseX, double mouseY) {
        if (!restrictedLayout || tab != Tab.CHARACTER) return -1;
        int[] helmet = loadoutBounds(0);
        if (inside(mouseX, mouseY, helmet[0], helmet[1], helmet[2], helmet[3])) return 0;
        int[] chest = loadoutBounds(1);
        if (inside(mouseX, mouseY, chest[0], chest[1], chest[2], chest[3])) return 1;
        return -1;
    }

    private void drawScrollableCarrierSections(GuiGraphics g, int mouseX, int mouseY) {
        int x = restrictedColumnX();
        drawCarrierColumnIfEquipped(g, "chest_rig", x,
            Component.translatable("status.xero_delta.chest_rig"), mouseX, mouseY);
        drawPocketSection(g, x, restrictedSectionY("pockets"), mouseX, mouseY);
        drawCarrierColumnIfEquipped(g, "backpack", x,
            Component.translatable("status.xero_delta.backpack"), mouseX, mouseY);
        drawCarrierColumnIfEquipped(g, "card_holder", x,
            Component.translatable("status.xero_delta.card_holder"), mouseX, mouseY);
        if (!DeltaInventoryUiState.safetyBoxPinned()) {
            drawSafetyBoxSection(g, restrictedSectionY("safety_box"), mouseX, mouseY);
        }
    }

    private void drawSafetyBoxSection(GuiGraphics g, int safetyY, int mouseX, int mouseY) {
        int x = restrictedColumnX();
        drawSection(g, x, safetyY, restrictedColumnWidth(), safetyBoxSectionHeight(),
            Component.empty());
        ItemStack safetyStack = equippedSafetyBox();
        int safetyUsed = 0;
        int safetyTotal = 0;
        if (safetyStack.getItem() instanceof SafetyBoxItem box) {
            GridBackingStore safetyStore = new GridBackingStore(
                safetyStack, box.getGridWidth(), box.getGridHeight());
            safetyUsed = StorageSectionHeaderRenderer.usedCells(safetyStore);
            safetyTotal = box.getGridWidth() * box.getGridHeight();
        }
        captureStorageHelp(StorageSectionHeaderRenderer.render(g, font,
            Component.translatable("status.xero_delta.safety_box"),
            safetyUsed, safetyTotal, "storage_help.xero_delta.safety_box",
            x + 4, safetyY + 5, 1.0F, mouseX, mouseY));
        drawSafetyBoxSelector(g, safetySelectorX(), safetySelectorY(),
            LARGE_SLOT + 6, LARGE_SLOT + 6, mouseX, mouseY);
        int pinX = safetyPinX();
        boolean pinHovered = inside(mouseX, mouseY, pinX, safetyY + 2, 16, 15);
        drawPinIcon(g, pinX, safetyY + 3,
            DeltaInventoryUiState.safetyBoxPinned(), pinHovered);
        drawActiveEffects(g, mouseX, mouseY);
    }

    private int restrictedSectionY(String identifier) {
        int y = layoutPageTop();
        if ("chest_rig".equals(identifier)) return y;
        y += carrierSectionSpan("chest_rig");
        if ("pockets".equals(identifier)) return y;
        y += POCKET_SECTION_HEIGHT + SECTION_GAP;
        if ("backpack".equals(identifier)) return y;
        y += carrierSectionSpan("backpack");
        if ("card_holder".equals(identifier)) return y;
        return y + carrierSectionSpan("card_holder");
    }

    private int carrierSectionHeight(String identifier) {
        if (accessorySlot(identifier).stack().isEmpty()) {
            return EMPTY_CARRIER_SECTION_HEIGHT;
        }
        StorageInfo info = storageInfo(identifier);
        if (info == null) return EMPTY_CARRIER_SECTION_HEIGHT;
        int storageHeight = info.deltaStore() != null
            ? packRegionLayout(info).height()
            : info.visibleRows() * STORAGE_CELL;
        return 22 + Math.max(LARGE_SLOT, storageHeight) + 6;
    }

    private int carrierSectionSpan(String identifier) {
        int height = carrierSectionHeight(identifier);
        return height <= 0 ? 0 : height + SECTION_GAP;
    }

    private int restrictedContentHeight() {
        return carrierSectionSpan("chest_rig")
            + POCKET_SECTION_HEIGHT + SECTION_GAP
            + carrierSectionSpan("backpack")
            + carrierSectionSpan("card_holder")
            + (DeltaInventoryUiState.safetyBoxPinned()
                ? 0 : safetyBoxSectionHeight());
    }

    private void drawCarrierColumnIfEquipped(GuiGraphics g, String identifier,
                                             int x, Component title,
                                             int mouseX, int mouseY) {
        int height = carrierSectionHeight(identifier);
        if (height <= 0) return;
        drawCarrierColumn(g, identifier, x, restrictedSectionY(identifier),
            restrictedColumnWidth(), height, title, mouseX, mouseY);
    }

    private void drawPocketSection(GuiGraphics g, int x, int y,
                                   int mouseX, int mouseY) {
        g.fill(x, y, x + restrictedColumnWidth(), y + POCKET_SECTION_HEIGHT, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_LOW);
        g.renderOutline(x, y, restrictedColumnWidth(), POCKET_SECTION_HEIGHT, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        int used = 0;
        if (minecraft != null && minecraft.player != null) {
            for (int index = 4; index <= 8; index++) {
                if (!minecraft.player.getInventory().getItem(index).isEmpty()) used++;
            }
        }
        captureStorageHelp(StorageSectionHeaderRenderer.render(g, font,
            Component.translatable("status.xero_delta.pockets"),
            used, 5, "storage_help.xero_delta.pockets",
            x + 4, y + 5, 1.0F, mouseX, mouseY));
        int slotY = y + 24;
        for (int index = 0; index < PocketSlotLayout.COUNT; index++) {
            PocketSlotLayout.Bounds bounds = PocketSlotLayout.bounds(
                x, y, STORAGE_CELL, POCKET_GAP, index);
            DeltaGridCellRenderer.render(g, bounds.x(), bounds.y(),
                bounds.width(), bounds.height());
        }
    }

    private void drawCarrierColumn(GuiGraphics g, String identifier, int x, int y,
                                   int width, int height, Component title,
                                   int mouseX, int mouseY) {
        g.fill(x, y, x + width, y + height, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_LOW);
        g.renderOutline(x, y, width, height, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        g.fill(x, y + 17, x + width, y + 18, 0xFF283332);
        AccessorySlot accessory = accessorySlot(identifier);
        boolean occupied = !accessory.stack().isEmpty();
        StorageInfo info = occupied ? storageInfo(identifier) : null;
        if ("chest_rig".equals(identifier) || "backpack".equals(identifier)) {
            int used = info == null ? 0 : info.deltaStore() != null
                ? StorageSectionHeaderRenderer.usedCells(info.deltaStore())
                : StorageSectionHeaderRenderer.usedSlots(info.handler());
            int total = info == null ? 0 : info.deltaStore() != null
                ? info.columns() * info.rows()
                : info.handler() == null ? 0 : info.handler().getSlots();
            captureStorageHelp(StorageSectionHeaderRenderer.render(g, font, title,
                used, total, "storage_help.xero_delta." + identifier,
                x + 4, y + 5, 1.0F, mouseX, mouseY));
        } else if ("card_holder".equals(identifier)) {
            captureStorageHelp(StorageSectionHeaderRenderer.renderTitleWithHelp(
                g, font, title, "storage_help.xero_delta.card_holder",
                x + 4, y + 5, 1.0F, mouseX, mouseY));
        } else {
            String label = title.getString();
            int labelWidth = Math.max(8, width - 30);
            if (font.width(label) > labelWidth) {
                label = font.plainSubstrByWidth(label,
                    Math.max(1, labelWidth - font.width("..."))) + "...";
            }
            g.drawString(font, label, x + 4, y + 5, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        }
        int carrierSlotSize = LARGE_SLOT;
        int slotX = x + 6;
        int slotY = y + 22;
        if (occupied) {
            renderSizedGridItem(g, accessory.stack(), slotX, slotY,
                carrierSlotSize, carrierSlotSize);
        } else {
            g.fill(slotX, slotY, slotX + carrierSlotSize,
                slotY + carrierSlotSize, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        }
        boolean slotHovered = inside(mouseX, mouseY, slotX, slotY,
            carrierSlotSize, carrierSlotSize);
        g.pose().pushPose();
        g.pose().translate(0, 0, 160);
        g.renderOutline(slotX, slotY, carrierSlotSize, carrierSlotSize,
            slotHovered ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        g.pose().popPose();
        if (occupied && weightDetailsVisible) {
            drawItemWeightBadge(g, accessory.stack(), slotX, slotY,
                carrierSlotSize, carrierSlotSize);
        }
        if ("card_holder".equals(identifier)) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            drawLockIcon(g, slotX + 3, slotY + carrierSlotSize - 13);
            if (PlayerStatusClientState.INSTANCE.canChangeBc()) {
                drawSwapIcon(g, slotX + carrierSlotSize - 13, slotY + 2);
            }
            g.pose().popPose();
        }
        if (!occupied) return;
        if (info == null) return;
        int gridX = storageGridX(identifier, info);
        int gridY = storageGridY(identifier);
        renderStorageGrid(g, info, gridX, gridY, mouseX, mouseY);
    }

    private void renderStorageGrid(GuiGraphics g, StorageInfo info, int gridX, int gridY,
                                   int mouseX, int mouseY) {
        int visibleRows = info.visibleRows();
        PackRegionLayout packLayout = info.deltaStore() == null
            ? null : packRegionLayout(info);
        if (packLayout != null) {
            for (PackRegionLayout.Rect region : packLayout.regionBounds()) {
                for (int localY = 0; localY < region.height(); localY += STORAGE_CELL) {
                    for (int localX = 0; localX < region.width(); localX += STORAGE_CELL) {
                        int x = gridX + region.x() + localX;
                        int y = gridY + region.y() + localY;
                        DeltaGridCellRenderer.render(g, x, y, STORAGE_CELL);
                    }
                }
                g.renderOutline(gridX + region.x(), gridY + region.y(),
                    region.width(), region.height(), 0xFF6A8079);
            }
        } else {
            for (int row = 0; row < visibleRows; row++) {
                for (int column = 0; column < info.columns(); column++) {
                    int x = gridX + column * STORAGE_CELL;
                    int y = gridY + row * STORAGE_CELL;
                    DeltaGridCellRenderer.render(g, x, y, STORAGE_CELL);
                }
            }
        }

        if (info.deltaStore() != null) {
            for (int index = 0; index < info.deltaStore().getSize(); index++) {
                ItemStack stack = info.deltaStore().getItemRaw(
                    index % info.columns(), index / info.columns());
                if (stack.isEmpty()) continue;
                var size = GridBackingStore.sizeOfStored(stack);
                PackRegionLayout.Rect itemBounds = packLayout.footprintBounds(
                    index % info.columns(), index / info.columns(),
                    size.width(), size.height());
                if (itemBounds == null) continue;
                int itemX = gridX + itemBounds.x();
                int itemY = gridY + itemBounds.y();
                int itemWidth = itemBounds.width();
                int itemHeight = itemBounds.height();
                renderSizedGridItem(g, stack, itemX, itemY, itemWidth, itemHeight);
                g.pose().pushPose();
                g.pose().translate(0, 0, 160);
                g.renderOutline(itemX, itemY, itemWidth, itemHeight, 0xFF72807E);
                g.pose().popPose();
                if (weightDetailsVisible) {
                    drawItemWeightBadge(g, stack, itemX, itemY, itemWidth, itemHeight);
                }
            }
        } else if (info.handler() != null) {
            int firstRow = (int) Math.floor(info.scrollPixels() / STORAGE_CELL);
            int rowOffset = (int) Math.round(
                info.scrollPixels() - firstRow * STORAGE_CELL);
            for (int row = 0; row <= visibleRows; row++) {
                int sourceRow = firstRow + row;
                for (int column = 0; column < info.columns(); column++) {
                    int slot = sourceRow * info.columns() + column;
                    if (slot >= info.handler().getSlots()) continue;
                    int itemX = gridX + column * STORAGE_CELL;
                    int itemY = gridY + row * STORAGE_CELL - rowOffset;
                    if (itemY + 17 <= gridY || itemY >= gridY + visibleRows * STORAGE_CELL) continue;
                    ItemStack stack = info.handler().getStackInSlot(slot);
                    if (stack.isEmpty()) continue;
                    g.renderItem(stack, itemX + 1, itemY + 1);
                    g.renderItemDecorations(font, stack, itemX + 1, itemY + 1);
                    if (weightDetailsVisible) {
                        drawItemWeightBadge(g, stack, itemX, itemY,
                            STORAGE_CELL, STORAGE_CELL);
                    }
                }
            }
            drawBackpackScrollbar(g, info, gridX, gridY);
        }

        int gridWidth = packLayout == null
            ? info.columns() * STORAGE_CELL : packLayout.width();
        int gridHeight = packLayout == null
            ? visibleRows * STORAGE_CELL : packLayout.height();
        if (inside(mouseX, mouseY, gridX, gridY, gridWidth, gridHeight)) {
            PackRegionLayout.Cell mapped = packLayout == null ? null
                : packLayout.cellAt(mouseX - gridX, mouseY - gridY);
            if (packLayout != null && mapped == null) return;
            int column = mapped == null ? (mouseX - gridX) / STORAGE_CELL : mapped.column();
            int row = mapped == null ? (mouseY - gridY) / STORAGE_CELL : mapped.row();
            ItemStack carried = minecraft == null || minecraft.player == null
                ? ItemStack.EMPTY : minecraft.player.containerMenu.getCarried();
            if (packLayout != null && info.deltaStore() != null && !carried.isEmpty()) return;
            PackRegionLayout.Rect cell = packLayout == null ? null
                : packLayout.cellBounds(column, row);
            int x = packLayout == null ? gridX + column * STORAGE_CELL : gridX + cell.x();
            int y = packLayout == null ? gridY + row * STORAGE_CELL : gridY + cell.y();
            g.pose().pushPose();
            g.pose().translate(0, 0, 260);
            g.fill(x + 1, y + 1, x + STORAGE_CELL - 1,
                y + STORAGE_CELL - 1, 0x55FFFFFF);
            g.pose().popPose();
        }
    }

    /**
     * Draw placement footprints after the vanilla slot/item pass. Rendering the
     * preview from renderBg allowed the later container pass to cover it.
     */
    private void renderCarriedStoragePreview(GuiGraphics g, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) return;
        ItemStack carried = minecraft.player.containerMenu.getCarried();
        StorageCell deferredSource = null;
        boolean virtualDrag = carried.isEmpty() && itemDragPickedUp
            && !armedDragStack.isEmpty();
        if (virtualDrag) {
            carried = armedDragStack;
            if (armedItem != null) deferredSource = armedItem;
        }
        if (!renderCarrierSelectorPlacementPreview(g, carried, mouseX, mouseY)) {
            renderStoragePlacementPreview(g, carried, mouseX, mouseY, deferredSource);
        }
        if (virtualDrag) {
            renderDeferredStorageDragGhost(g, carried, mouseX, mouseY);
        }
    }

    private boolean renderCarrierSelectorPlacementPreview(GuiGraphics graphics,
                                                           ItemStack carried,
                                                           int mouseX,
                                                           int mouseY) {
        if (carried == null || carried.isEmpty()) return false;
        if (inside(mouseX, mouseY, safetySelectorX(), safetySelectorY(),
            LARGE_SLOT + 6, LARGE_SLOT + 6)) {
            int[] bounds = {safetySelectorX(), safetySelectorY(),
                LARGE_SLOT + 6, LARGE_SLOT + 6};
            drawDropPreviewBounds(graphics, bounds,
                carried.getItem() instanceof SafetyBoxItem
                    ? DropPreviewState.REJECT : DropPreviewState.ACCEPT);
            return true;
        }
        for (String identifier : List.of("chest_rig", "backpack")) {
            int[] bounds = carrierSlotBounds(identifier);
            if (!inside(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3])) {
                continue;
            }
            DropPreviewState state = carrierSelectorPreviewState(
                identifier, carried, accessorySlot(identifier).stack());
            drawDropPreviewBounds(graphics, bounds, state);
            return true;
        }
        return false;
    }

    private void renderDeferredStorageDragGhost(GuiGraphics graphics, ItemStack stack,
                                                 int mouseX, int mouseY) {
        if (stack.isEmpty()) return;
        ItemSize size = ClientDataCache.INSTANCE.getSize(stack);
        if (GridBackingStore.isRotated(stack)) size = size.rotated();
        int width = size.width() * STORAGE_CELL
            + Math.max(0, size.width() - 1) * STORAGE_REGION_GAP;
        int height = size.height() * STORAGE_CELL
            + Math.max(0, size.height() - 1) * STORAGE_REGION_GAP;
        int x = mouseX - width / 2;
        int y = mouseY - height / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 720.0F);
        renderSizedGridItem(graphics, stack, x, y, width, height);
        graphics.fill(x, y, x + width, y + height, 0x55070B0C);
        graphics.renderOutline(x, y, width, height, 0xCCFFFFFF);
        graphics.pose().popPose();
    }

    /** Called by the optional Better Looting integration after its drag list renders. */
    public void renderBetterLootingPlacementPreview(GuiGraphics graphics, ItemStack stack,
                                                     int physicalMouseX, int physicalMouseY) {
        if (sharedDeltaLayout) {
            DeltaContainerLayoutController.renderCarriedDropPreview(this, graphics,
                physicalMouseX, physicalMouseY, stack);
            return;
        }
        if (stack == null || stack.isEmpty() || !restrictedLayout) return;
        graphics.pose().pushPose();
        viewport.apply(graphics);
        graphics.pose().translate((float) transition.horizontalOffset(), 0.0F, 0.0F);
        int logicalMouseX = viewport.mouseX(physicalMouseX);
        int logicalMouseY = viewport.mouseY(physicalMouseY);
        for (int visualIndex = 0; visualIndex < 2; visualIndex++) {
            int[] bounds = loadoutBounds(visualIndex);
            if (!inside(logicalMouseX, logicalMouseY,
                bounds[0], bounds[1], bounds[2], bounds[3])) continue;
            EquipmentSlot expected = visualIndex == 0 ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
            boolean accepted = minecraft != null && minecraft.player != null
                && PlayerEquipmentSync.canEquip(minecraft.player, stack, expected);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 520);
            graphics.fill(bounds[0] + 1, bounds[1] + 1,
                bounds[0] + bounds[2] - 1, bounds[1] + bounds[3] - 1,
                accepted ? 0x8849D79A : 0x88E05252);
            graphics.renderOutline(bounds[0], bounds[1], bounds[2], bounds[3],
                accepted ? 0xFF6FE8B2 : 0xFFFF6767);
            graphics.pose().popPose();
            graphics.pose().popPose();
            return;
        }
        int pocketSlot = pocketInventorySlotAt(logicalMouseX, logicalMouseY);
        if (pocketSlot >= 4) {
            int[] bounds = pocketBounds(pocketSlot);
            ItemStack current = minecraft.player.getInventory().getItem(pocketSlot);
            com.xtdpotato.xero_delta.data.ItemSize size = ClientDataCache.INSTANCE.getSize(stack);
            boolean accepted = size.width() == 1 && size.height() == 1
                && (current.isEmpty() || (ItemStack.isSameItemSameComponents(current, stack)
                    && current.getCount() < current.getMaxStackSize()));
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 520);
            graphics.fill(bounds[0] + 1, bounds[1] + 1,
                bounds[0] + bounds[2] - 1, bounds[1] + bounds[3] - 1,
                accepted ? 0x8849D79A : 0x88E05252);
            graphics.renderOutline(bounds[0], bounds[1], bounds[2], bounds[3],
                accepted ? 0xFF6FE8B2 : 0xFFFF6767);
            graphics.pose().popPose();
            graphics.pose().popPose();
            return;
        }
        for (String identifier : List.of("chest_rig", "backpack")) {
            int[] bounds = carrierSlotBounds(identifier);
            if (!inside(logicalMouseX, logicalMouseY, bounds[0], bounds[1], bounds[2], bounds[3])) continue;
            ItemStack current = accessorySlot(identifier).stack();
            drawDropPreviewBounds(graphics, bounds,
                carrierEquipmentPreviewState(identifier, stack, current));
            graphics.pose().popPose();
            return;
        }
        renderStoragePlacementPreview(graphics, stack, logicalMouseX, logicalMouseY);
        graphics.pose().popPose();
    }

    private void renderStoragePlacementPreview(GuiGraphics g, ItemStack carried,
                                               int mouseX, int mouseY) {
        renderStoragePlacementPreview(g, carried, mouseX, mouseY, null);
    }

    private void renderStoragePlacementPreview(GuiGraphics g, ItemStack carried,
                                               int mouseX, int mouseY,
                                               StorageCell deferredSource) {
        if (carried == null || carried.isEmpty()) return;
        StorageCell safety = safetyBoxStorageCellAt(mouseX, mouseY);
        if (safety != null) {
            Set<Integer> ignoredAnchors = deferredSource != null
                && "safety_box".equals(deferredSource.identifier())
                ? Set.of(deferredSource.cell()) : Set.of();
            GridBackingStore.PlacementResult placement = resolveStoragePlacement(
                safety, carried, mouseX, mouseY, ignoredAnchors);
            if (placement == null) return;
            ItemSize size = GridBackingStore.orientedSize(
                carried, placement.rotated());
            int previewX = safetyBoxGridLogicalX() + placement.x() * STORAGE_CELL;
            int previewY = safetyBoxGridLogicalY() + placement.y() * STORAGE_CELL;
            int previewWidth = size.width() * STORAGE_CELL;
            int previewHeight = size.height() * STORAGE_CELL;
            g.pose().pushPose();
            g.pose().translate(0, 0, 520);
            g.fill(previewX + 1, previewY + 1,
                previewX + previewWidth - 1, previewY + previewHeight - 1,
                placement.isAccepted() ? 0x8849D79A : 0x88E05252);
            g.renderOutline(previewX, previewY, previewWidth, previewHeight,
                placement.isAccepted() ? 0xFF6FE8B2 : 0xFFFF6767);
            g.pose().popPose();
            return;
        }
        for (String identifier : List.of("chest_rig", "backpack", "card_holder")) {
            StorageInfo info = storageInfo(identifier);
            if (info == null || info.deltaStore() == null) continue;
            PackRegionLayout layout = packRegionLayout(info);
            int gridX = storageGridX(identifier, info);
            int gridY = storageGridY(identifier);
            if (!inside(mouseX, mouseY, gridX, gridY, layout.width(), layout.height())) continue;
            PackRegionLayout.Cell mapped = layout.cellAt(mouseX - gridX, mouseY - gridY);
            if (mapped == null) return;
            PackRegionLayout.Rect hovered = layout.cellBounds(mapped.column(), mapped.row());
            double fractionX = hovered == null ? 0.5D : clampCellFraction(
                mouseX - gridX - hovered.x(), hovered.width());
            double fractionY = hovered == null ? 0.5D : clampCellFraction(
                mouseY - gridY - hovered.y(), hovered.height());
            Set<Integer> ignoredAnchors = deferredSource != null
                && identifier.equals(deferredSource.identifier())
                ? Set.of(deferredSource.cell()) : Set.of();
            GridBackingStore.PlacementResult placement = info.deltaStore().resolveCursorPlacement(
                mapped.column(), mapped.row(), carried, fractionX, fractionY,
                ClientGridRotation.allowAutoRotate(), ignoredAnchors);
            var size = GridBackingStore.orientedSize(carried, placement.rotated());
            PackRegionLayout.Rect preview = layout.footprintBounds(
                placement.x(), placement.y(), size.width(), size.height());
            boolean accepted = placement.isAccepted() && preview != null;
            if (preview == null) {
                hovered = layout.cellBounds(
                    mapped.column(), mapped.row());
                if (hovered != null) {
                    preview = new PackRegionLayout.Rect(hovered.x(), hovered.y(),
                        size.width() * STORAGE_CELL, size.height() * STORAGE_CELL);
                }
            }
            if (preview == null) return;
            int previewX = gridX + preview.x();
            int previewY = gridY + preview.y();
            g.pose().pushPose();
            g.pose().translate(0, 0, 520);
            g.fill(previewX + 1, previewY + 1,
                previewX + preview.width() - 1, previewY + preview.height() - 1,
                accepted ? 0x8849D79A : 0x88E05252);
            g.renderOutline(previewX, previewY, preview.width(), preview.height(),
                accepted ? 0xFF6FE8B2 : 0xFFFF6767);
            g.pose().popPose();
            return;
        }

    }
    private static boolean betterLootingPlacementAccepted(
        GridBackingStore.PlacementResult placement) {
        return placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE
            || placement.status() == GridBackingStore.PlacementStatus.CAN_STACK;
    }

    private void renderSizedGridItem(GuiGraphics g, ItemStack stack, int x, int y,
                                     int width, int height) {
        GridItemRenderer.renderSizedItem(g, font, stack, x, y, width, height,
            GridBackingStore.isRotated(stack),
            ClientDataCache.INSTANCE.shouldRotateTexture(stack),
            ClientDataCache.INSTANCE.shouldStretchTexture(stack),
            ClientDataCache.INSTANCE.proportionalTextureScale(stack));
    }

    private void drawBackpackScrollbar(GuiGraphics g, StorageInfo info, int gridX, int gridY) {
        if (!"backpack".equals(info.identifier()) || info.rows() <= info.visibleRows()) return;
        int trackX = gridX + info.columns() * STORAGE_CELL + 2;
        int trackHeight = info.visibleRows() * STORAGE_CELL;
        g.fill(trackX, gridY, trackX + 3, gridY + trackHeight, 0xFF1B2527);
        int thumbHeight = Math.max(12,
            Math.round(trackHeight * info.visibleRows() / (float) info.rows()));
        double maxScroll = Math.max(1.0D,
            (info.rows() - info.visibleRows()) * STORAGE_CELL);
        int thumbY = gridY + (int) Math.round((trackHeight - thumbHeight)
            * info.scrollPixels() / maxScroll);
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
    }

    private StorageInfo storageInfo(String identifier) {
        AccessorySlot accessory = accessorySlot(identifier);
        ItemStack carrier = accessory.stack();
        if (carrier.isEmpty()) return null;
        if (carrier.getItem() instanceof DeltaPackItem pack
            && identifier.equals(pack.slotIdentifier())) {
            GridBackingStore store = new GridBackingStore(
                carrier, pack.gridWidth(), pack.gridHeight(), 0,
                stack -> GridBackingStore.isBlockedInEquippedStorage(identifier, stack));
            return new StorageInfo(identifier, carrier, pack, store, null,
                pack.gridWidth(), pack.gridHeight(), pack.gridHeight(), 0);
        }
        if (!"backpack".equals(identifier)) return null;
        try {
            IItemHandler handler = carrier.getCapability(Capabilities.ItemHandler.ITEM);
            if (handler == null || handler.getSlots() <= 0) return null;
            int columns = Math.min(BACKPACK_COLUMNS, Math.max(1, handler.getSlots()));
            int rows = (handler.getSlots() + columns - 1) / columns;
            int visibleRows = Math.min(BACKPACK_VISIBLE_ROWS, rows);
            double maxScrollPixels = Math.max(0,
                (rows - visibleRows) * STORAGE_CELL);
            backpackTargetScrollPixels = Math.max(0.0D,
                Math.min(maxScrollPixels, backpackTargetScrollPixels));
            backpackScrollPixels = Math.max(0.0D,
                Math.min(maxScrollPixels, backpackScrollPixels));
            return new StorageInfo(identifier, carrier, null, null, handler,
                columns, rows, visibleRows, backpackScrollPixels);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int storageGridX(String identifier, StorageInfo info) {
        int columnX = carrierColumnX(identifier);
        return columnX + LARGE_SLOT + 16;
    }

    private int storageGridY(String identifier) {
        return restrictedSectionY(identifier) + 22;
    }

    private int carrierColumnX(String identifier) {
        return restrictedColumnX();
    }

    private int carrierColumnWidth(String identifier) {
        return restrictedColumnWidth();
    }

    private int[] carrierSlotBounds(String identifier) {
        int x = carrierColumnX(identifier);
        int width = carrierColumnWidth(identifier);
        int y = restrictedSectionY(identifier);
        return new int[]{x + 6, y + 22, LARGE_SLOT, LARGE_SLOT};
    }

    private GridBackingStore.PlacementResult resolveStoragePlacement(
        StorageCell storage, ItemStack stack, double mouseX, double mouseY) {
        return resolveStoragePlacement(storage, stack, mouseX, mouseY, Set.of());
    }

    private GridBackingStore.PlacementResult resolveStoragePlacement(
        StorageCell storage, ItemStack stack, double mouseX, double mouseY,
        Set<Integer> ignoredAnchors) {
        if (storage == null || stack == null || stack.isEmpty()) return null;
        if ("safety_box".equals(storage.identifier())) {
            ItemStack boxStack = equippedSafetyBox();
            if (!(boxStack.getItem() instanceof SafetyBoxItem box)) return null;
            SafetyBoxGridInteraction.Target target = SafetyBoxGridInteraction.targetAt(
                mouseX, mouseY, safetyBoxGridLogicalX(), safetyBoxGridLogicalY(),
                STORAGE_CELL, box.getGridWidth(), box.getGridHeight());
            if (target == null) return null;
            GridBackingStore store = new GridBackingStore(
                boxStack, box.getGridWidth(), box.getGridHeight());
            return store.resolveCursorPlacement(
                target.column(), target.row(), stack,
                target.fractionX(), target.fractionY(),
                ClientGridRotation.allowAutoRotate(), ignoredAnchors);
        }
        StorageInfo info = storageInfo(storage.identifier());
        if (info == null || info.deltaStore() == null || info.columns() <= 0) return null;
        int column = storage.cell() % info.columns();
        int row = storage.cell() / info.columns();
        PackRegionLayout layout = packRegionLayout(info);
        PackRegionLayout.Rect cell = layout.cellBounds(column, row);
        int gridX = storageGridX(storage.identifier(), info);
        int gridY = storageGridY(storage.identifier());
        double fractionX = cell == null ? 0.5D
            : clampCellFraction(mouseX - gridX - cell.x(), cell.width());
        double fractionY = cell == null ? 0.5D
            : clampCellFraction(mouseY - gridY - cell.y(), cell.height());
        return info.deltaStore().resolveCursorPlacement(column, row, stack,
            fractionX, fractionY, ClientGridRotation.allowAutoRotate(), ignoredAnchors);
    }

    private static double clampCellFraction(double value, int span) {
        return Math.max(0.0D, Math.min(0.999D, value / Math.max(1, span)));
    }

    private boolean sendStorageClick(StorageCell storage, int button,
                                     double mouseX, double mouseY) {
        if (storage == null) return false;
        ItemStack carried = menu.getCarried();
        int cell = storage.cell();
        boolean rotated = GridBackingStore.isRotated(carried);
        if (!carried.isEmpty()) {
            GridBackingStore.PlacementResult placement = resolveStoragePlacement(
                storage, carried, mouseX, mouseY);
            if (placement != null) {
                if (!placement.isAccepted()) return true;
                cell = placement.y() * storageColumns(storage.identifier())
                    + placement.x();
                rotated = placement.rotated();
            }
        }
        ModNetwork.sendToServer(new EquippedStorageActionPacket(
            storage.identifier(), cell, button, rotated));
        return true;
    }

    private int storageColumns(String identifier) {
        if ("safety_box".equals(identifier)) {
            ItemStack stack = equippedSafetyBox();
            return stack.getItem() instanceof SafetyBoxItem box
                ? box.getGridWidth() : 0;
        }
        StorageInfo info = storageInfo(identifier);
        return info == null ? 0 : info.columns();
    }
    private StorageCell storageCellAt(double mouseX, double mouseY) {
        if (restrictedLayout && !inside(mouseX, mouseY,
            restrictedColumnX(), layoutViewportTop(),
            restrictedColumnWidth(), layoutViewportHeight())) return null;
        for (String identifier : List.of("chest_rig", "backpack", "card_holder")) {
            StorageInfo info = storageInfo(identifier);
            if (info == null) continue;
            int gridX = storageGridX(identifier, info);
            int gridY = storageGridY(identifier);
            PackRegionLayout layout = info.deltaStore() == null
                ? null : packRegionLayout(info);
            int width = layout == null ? info.columns() * STORAGE_CELL : layout.width();
            int height = layout == null ? info.visibleRows() * STORAGE_CELL : layout.height();
            if (!inside(mouseX, mouseY, gridX, gridY, width, height)) continue;
            PackRegionLayout.Cell mapped = layout == null ? null
                : layout.cellAt(mouseX - gridX, mouseY - gridY);
            if (layout != null && mapped == null) continue;
            int column = mapped == null
                ? (int) ((mouseX - gridX) / STORAGE_CELL) : mapped.column();
            int row = mapped == null
                ? (int) ((mouseY - gridY) / STORAGE_CELL) : mapped.row();
            int cell = info.deltaStore() != null
                ? row * info.columns() + column
                                : (int) Math.floor((mouseY - gridY + info.scrollPixels())
                    / STORAGE_CELL) * info.columns() + column;
            ItemStack stack = ItemStack.EMPTY;
            if (info.deltaStore() != null) {
                int anchor = info.deltaStore().findAnchorIndexAt(column, row);
                if (anchor >= 0) {
                    cell = anchor;
                    stack = info.deltaStore().getItemRaw(
                        anchor % info.columns(), anchor / info.columns()).copy();
                }
            } else if (info.handler() != null && cell < info.handler().getSlots()) {
                stack = info.handler().getStackInSlot(cell).copy();
            }
            return new StorageCell(identifier, cell, stack);
        }
        return null;
    }

    private StorageCell shortcutStorageCellAt(double mouseX, double mouseY) {
        StorageCell safety = safetyBoxStorageCellAt(mouseX, mouseY);
        return safety != null ? safety : storageCellAt(mouseX, mouseY);
    }

    private StorageCell safetyBoxStorageCellAt(double mouseX, double mouseY) {
        ItemStack boxStack = equippedSafetyBox();
        if (!(boxStack.getItem() instanceof
            com.xtdpotato.xero_delta.item.SafetyBoxItem box)) return null;
        int gridX = safetyBoxGridLogicalX();
        int gridY = safetyBoxGridLogicalY();
        SafetyBoxGridInteraction.Target target = SafetyBoxGridInteraction.targetAt(
            mouseX, mouseY, gridX, gridY, STORAGE_CELL,
            box.getGridWidth(), box.getGridHeight());
        if (target == null) return null;
        GridBackingStore store = new GridBackingStore(
            boxStack, box.getGridWidth(), box.getGridHeight());
        int anchor = store.findAnchorIndexAt(target.column(), target.row());
        int cell = anchor >= 0 ? anchor : target.cell();
        ItemStack stack = anchor >= 0
            ? store.getItemRaw(anchor % box.getGridWidth(),
                anchor / box.getGridWidth()).copy()
            : ItemStack.EMPTY;
        return new StorageCell("safety_box", cell, stack);
    }

    private void openItemDetail(StorageCell storage) {
        selectedStorageItem = storage;
        selectedPocketSlot = -1;
        selectedEquipmentSlot = -1;
        selectedLargeInventorySlot = -1;
        selectedCurioIdentifier = null;
        int[] bounds = storageDetailBounds(storage);
        ItemDetailOverlay.open(this, storage.stack(), false,
            TradingInventorySources.sourceIdForCurio(
                storage.identifier(), 0, storage.cell(), storage.stack()),
            bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    private int[] storageDetailBounds(StorageCell storage) {
        if ("safety_box".equals(storage.identifier())) {
            ItemStack boxStack = equippedSafetyBox();
            if (boxStack.getItem() instanceof SafetyBoxItem box) {
                GridBackingStore store = new GridBackingStore(
                    boxStack, box.getGridWidth(), box.getGridHeight());
                int column = storage.cell() % box.getGridWidth();
                int row = storage.cell() / box.getGridWidth();
                int anchor = store.findAnchorIndexAt(column, row);
                if (anchor >= 0) {
                    column = anchor % box.getGridWidth();
                    row = anchor / box.getGridWidth();
                }
                ItemSize size = ClientDataCache.INSTANCE.getSize(storage.stack());
                if (GridBackingStore.isRotated(storage.stack())) size = size.rotated();
                return new int[]{safetyBoxGridLogicalX() + column * STORAGE_CELL,
                    safetyBoxGridLogicalY() + row * STORAGE_CELL,
                    size.width() * STORAGE_CELL, size.height() * STORAGE_CELL};
            }
        }
        StorageInfo info = storageInfo(storage.identifier());
        if (info == null || info.columns() <= 0) {
            return new int[]{(int) itemDragStartX - 8, (int) itemDragStartY - 8,
                STORAGE_CELL, STORAGE_CELL};
        }
        int column = storage.cell() % info.columns();
        int row = storage.cell() / info.columns();
        if (info.deltaStore() != null) {
            int anchor = info.deltaStore().findAnchorIndexAt(column, row);
            if (anchor >= 0) {
                column = anchor % info.columns();
                row = anchor / info.columns();
            }
        }
        ItemSize size = ClientDataCache.INSTANCE.getSize(storage.stack());
        if (GridBackingStore.isRotated(storage.stack())) size = size.rotated();
        int gridX = storageGridX(storage.identifier(), info);
        int gridY = storageGridY(storage.identifier());
        PackRegionLayout layout = info.deltaStore() == null ? null : packRegionLayout(info);
        if (layout != null) {
            PackRegionLayout.Rect first = layout.cellBounds(column, row);
            PackRegionLayout.Rect last = layout.cellBounds(
                column + size.width() - 1, row + size.height() - 1);
            if (first != null && last != null) {
                return new int[]{gridX + first.x(), gridY + first.y(),
                    last.x() + last.width() - first.x(),
                    last.y() + last.height() - first.y()};
            }
        }
        int visibleY = gridY + row * STORAGE_CELL
            - (int) Math.floor(info.scrollPixels());
        return new int[]{gridX + column * STORAGE_CELL, visibleY,
            size.width() * STORAGE_CELL, size.height() * STORAGE_CELL};
    }

    private static boolean shouldInsertFromCarrierSelector(
        String identifier, ItemStack carried) {
        if (carried == null || carried.isEmpty()
            || (!"chest_rig".equals(identifier) && !"backpack".equals(identifier))) {
            return false;
        }
        return !(carried.getItem() instanceof DeltaPackItem pack
            && identifier.equals(pack.slotIdentifier()));
    }

    private static DropPreviewState carrierSelectorPreviewState(String identifier,
                                                                ItemStack carried,
                                                                ItemStack equipped) {
        DropPreviewState equipment = carrierEquipmentPreviewState(
            identifier, carried, equipped);
        if (equipment != DropPreviewState.REJECT) return equipment;
        return shouldInsertFromCarrierSelector(identifier, carried)
            ? DropPreviewState.ACCEPT : DropPreviewState.REJECT;
    }

    private static DropPreviewState carrierEquipmentPreviewState(String identifier,
                                                                 ItemStack carried,
                                                                 ItemStack equipped) {
        if (carried == null || carried.getCount() != 1
            || !(carried.getItem() instanceof DeltaPackItem pack)
            || !identifier.equals(pack.slotIdentifier())) {
            return DropPreviewState.REJECT;
        }
        return equipped == null || equipped.isEmpty()
            ? DropPreviewState.ACCEPT : DropPreviewState.SWAP;
    }

    private static void drawDropPreviewBounds(GuiGraphics graphics, int[] bounds,
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
        graphics.pose().translate(0, 0, 520);
        graphics.fill(bounds[0] + 1, bounds[1] + 1,
            bounds[0] + bounds[2] - 1, bounds[1] + bounds[3] - 1, fill);
        graphics.renderOutline(bounds[0], bounds[1], bounds[2], bounds[3], border);
        graphics.pose().popPose();
    }
    private boolean renderStorageTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (!menu.getCarried().isEmpty()) return false;
        StorageCell cell = shortcutStorageCellAt(mouseX, mouseY);
        if (cell == null || cell.stack().isEmpty()) return false;
        g.renderTooltip(font, cell.stack(), mouseX, mouseY);
        return true;
    }

    private boolean beginBackpackContentDrag(double mouseX, double mouseY) {
        StorageInfo info = storageInfo("backpack");
        if (info == null || info.handler() == null) return false;
        int gridX = storageGridX("backpack", info);
        int gridY = storageGridY("backpack");
        if (!inside(mouseX, mouseY, gridX, gridY,
            info.columns() * STORAGE_CELL, info.visibleRows() * STORAGE_CELL)) return false;
        backpackContentDragArmed = true;
        backpackContentDragged = false;
        backpackContentLastY = mouseY;
        backpackContentTravel = 0.0D;
        pendingBackpackCell = storageCellAt(mouseX, mouseY);
        return true;
    }

    private void dragBackpackContent(double mouseY) {
        StorageInfo info = storageInfo("backpack");
        if (info == null || info.handler() == null) return;
        double delta = mouseY - backpackContentLastY;
        backpackContentLastY = mouseY;
        backpackContentTravel += Math.abs(delta);
        if (backpackContentTravel >= 3.0D) backpackContentDragged = true;
        if (!backpackContentDragged) return;
        double maximum = Math.max(0.0D,
            (info.rows() - info.visibleRows()) * STORAGE_CELL);
        double value = Math.max(0.0D, Math.min(maximum,
            backpackScrollPixels - delta));
        backpackScrollPixels = value;
        backpackTargetScrollPixels = value;
    }

    private boolean beginBackpackScrollbarDrag(double mouseX, double mouseY) {
        StorageInfo info = storageInfo("backpack");
        if (info == null || info.handler() == null || info.rows() <= info.visibleRows()) return false;
        int gridX = storageGridX("backpack", info);
        int gridY = storageGridY("backpack");
        int trackX = gridX + info.columns() * STORAGE_CELL + 1;
        if (!inside(mouseX, mouseY, trackX, gridY, 5,
            info.visibleRows() * STORAGE_CELL)) return false;
        backpackScrollbarDragging = true;
        updateBackpackScrollbar(mouseY);
        return true;
    }

    private void updateBackpackScrollbar(double mouseY) {
        StorageInfo info = storageInfo("backpack");
        if (info == null || info.handler() == null || info.rows() <= info.visibleRows()) {
            backpackScrollbarDragging = false;
            return;
        }
        int gridY = storageGridY("backpack");
        int trackHeight = info.visibleRows() * STORAGE_CELL;
        int thumbHeight = Math.max(12,
            Math.round(trackHeight * info.visibleRows() / (float) info.rows()));
        double travel = Math.max(1, trackHeight - thumbHeight);
        double relative = mouseY - gridY - thumbHeight / 2.0D;
        double progress = Math.max(0.0D, Math.min(1.0D, relative / travel));
        double value = progress * (info.rows() - info.visibleRows()) * STORAGE_CELL;
        backpackScrollPixels = value;
        backpackTargetScrollPixels = value;
    }
    private boolean scrollBackpackGrid(double mouseX, double mouseY, double scrollY) {
        StorageInfo info = storageInfo("backpack");
        if (info == null || info.handler() == null || info.rows() <= info.visibleRows()) return false;
        int gridX = storageGridX("backpack", info);
        int gridY = storageGridY("backpack");
        if (!inside(mouseX, mouseY, gridX, gridY,
            info.columns() * STORAGE_CELL + 6, info.visibleRows() * STORAGE_CELL)) return false;
        if (scrollY == 0.0D) return true;
        double maximum = (info.rows() - info.visibleRows()) * STORAGE_CELL;
        backpackTargetScrollPixels = Math.max(0.0D, Math.min(maximum,
            backpackTargetScrollPixels - scrollY * 12.0D));
        return true;
    }

    private PackRegionLayout packRegionLayout(StorageInfo info) {
        return new PackRegionLayout(info.pack(), STORAGE_CELL, STORAGE_REGION_GAP);
    }

    private int restrictedColumnX() {
        return canvasX + (tab == Tab.HEALTH ? STORAGE_X : EQUIPMENT_X);
    }

    private int restrictedColumnRelativeX() {
        return tab == Tab.HEALTH ? STORAGE_X : EQUIPMENT_X;
    }

    private int restrictedColumnWidth() {
        return tab == Tab.HEALTH ? STORAGE_WIDTH : EQUIPMENT_WIDTH;
    }

    private int layoutViewportTop() {
        return panelTop + HEADER_HEIGHT + 8;
    }

    private int layoutViewportBottom() {
        if (DeltaInventoryUiState.safetyBoxPinned()) {
            return pinnedSafetySectionY() - SECTION_GAP;
        }
        return panelTop + canvasHeight - 10;
    }

    private int layoutViewportHeight() {
        return layoutViewportBottom() - layoutViewportTop();
    }

    private int layoutPageTop() {
        return layoutViewportTop() - (int) Math.round(layoutScrollPixels);
    }

    private double layoutMaxScroll() {
        return Math.max(0.0D, restrictedContentHeight() - layoutViewportHeight());
    }

    private boolean scrollLayout(double mouseX, double mouseY, double scrollY) {
        if (!inside(mouseX, mouseY, restrictedColumnX(), layoutViewportTop(),
            restrictedColumnWidth(), layoutViewportHeight())) return false;
        if (scrollY == 0.0D) return true;
        layoutTargetScrollPixels = Math.max(0.0D, Math.min(layoutMaxScroll(),
            layoutTargetScrollPixels - scrollY * 14.0D));
        return true;
    }

    private boolean beginLayoutScrollbarDrag(double mouseX, double mouseY) {
        if (layoutMaxScroll() <= 0.0D) return false;
        int x = restrictedColumnX() + restrictedColumnWidth();
        if (!inside(mouseX, mouseY, x, layoutViewportTop(), 9, layoutViewportHeight())) {
            return false;
        }
        layoutScrollbarDragging = true;
        updateLayoutScrollbar(mouseY);
        return true;
    }

    private void updateLayoutScrollbar(double mouseY) {
        int height = layoutViewportHeight();
        int contentHeight = restrictedContentHeight();
        int thumbHeight = Math.max(24,
            (int) Math.round(height * height / (double) Math.max(1, contentHeight)));
        double travel = Math.max(1.0D, height - thumbHeight);
        double relative = mouseY - layoutViewportTop() - thumbHeight / 2.0D;
        double value = Math.max(0.0D, Math.min(1.0D, relative / travel)) * layoutMaxScroll();
        layoutScrollPixels = value;
        layoutTargetScrollPixels = value;
        applyDeltaSlotLayout();
    }

    private boolean beginLayoutContentDrag(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, restrictedColumnX(), layoutViewportTop(),
            restrictedColumnWidth() - 8, layoutViewportHeight())) return false;
        if (restrictedLargeInventorySlotAt(mouseX, mouseY) >= 0
            || storageCellAt(mouseX, mouseY) != null
            || curioIdentifierAt(mouseX, mouseY) != null
            || inside(mouseX, mouseY, safetySelectorX(), safetySelectorY(),
                LARGE_SLOT + 6, LARGE_SLOT + 6)) return false;
        for (Slot slot : menu.slots) {
            if (slot.x < 0 || slot.y < 0) continue;
            if (inside(mouseX, mouseY, leftPos + slot.x, topPos + slot.y, 16, 16)) return false;
        }
        layoutContentDragging = true;
        layoutContentLastY = mouseY;
        return true;
    }

    private void dragLayoutContent(double mouseY) {
        double delta = mouseY - layoutContentLastY;
        layoutContentLastY = mouseY;
        double value = Math.max(0.0D, Math.min(layoutMaxScroll(),
            layoutScrollPixels - delta));
        layoutScrollPixels = value;
        layoutTargetScrollPixels = value;
        applyDeltaSlotLayout();
    }
    private record StorageInfo(String identifier, ItemStack carrier, DeltaPackItem pack,
                               GridBackingStore deltaStore, IItemHandler handler,
                               int columns, int rows, int visibleRows, double scrollPixels) {}

    private enum DropPreviewState {
        ACCEPT,
        SWAP,
        REJECT
    }

    private record StorageCell(String identifier, int cell, ItemStack stack) {}

    private void captureStorageHelp(String helpKey) {
        if (helpKey != null) storageHelpTooltipKey = helpKey;
    }

    private void drawSection(GuiGraphics g, int x, int y, int width, int height, Component title) {
        g.fill(x, y, x + width, y + height, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_LOW);
        g.renderOutline(x, y, width, height, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        g.fill(x, y + 17, x + width, y + 18, 0xFF283332);
        g.drawString(font, title, x + 7, y + 5, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
    }

    private void drawHealthAccessories(GuiGraphics g, int mouseX, int mouseY) {
        int x = healthAccessoryX();
        int y = healthAccessoryY();
        int width = healthAccessoryWidth();
        int height = healthAccessoryHeight();
        drawSection(g, x, y, width, height,
            Component.translatable("status.xero_delta.accessories"));
        List<HealthAccessorySlot> slots = healthAccessorySlots();
        int viewportTop = y + 19;
        int viewportBottom = y + height - 3;
        int contentHeight = slots.size() * HEALTH_ACCESSORY_ROW;
        healthAccessoryScroll = Math.max(0.0D, Math.min(healthAccessoryScroll,
            Math.max(0, contentHeight - (viewportBottom - viewportTop))));
        int transitionX = (int) Math.round(transition.horizontalOffset());
        viewport.enableScissor(g, x + 1 + transitionX, viewportTop,
            x + width - 1 + transitionX, viewportBottom);
        for (int index = 0; index < slots.size(); index++) {
            HealthAccessorySlot slot = slots.get(index);
            int rowY = viewportTop + index * HEALTH_ACCESSORY_ROW
                - (int) Math.round(healthAccessoryScroll);
            if (rowY + HEALTH_ACCESSORY_ROW <= viewportTop || rowY >= viewportBottom) continue;
            boolean hovered = inside(mouseX, mouseY, x + 4, rowY + 2,
                width - 12, HEALTH_ACCESSORY_ROW - 3);
            g.fill(x + 4, rowY + 2, x + width - 8, rowY + HEALTH_ACCESSORY_ROW - 1,
                hovered ? 0xD022302D : 0xB00B1212);
            g.renderOutline(x + 4, rowY + 2, width - 12,
                HEALTH_ACCESSORY_ROW - 3, hovered ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
            g.fill(x + 7, rowY + 4, x + 25, rowY + 22,
                slot.active() ? com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER : 0xD0181818);
            g.renderOutline(x + 7, rowY + 4, 18, 18,
                slot.active() ? com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT : 0xFF633838);
            if (!slot.stack().isEmpty()) g.renderItem(slot.stack(), x + 8, rowY + 5);
            String label = slotLabel(slot);
            int maxText = Math.max(20, width - 52);
            if (font.width(label) > maxText) {
                label = font.plainSubstrByWidth(label,
                    Math.max(1, maxText - font.width("..."))) + "...";
            }
            g.drawString(font, label, x + 31, rowY + 8,
                slot.active() ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        }
        g.disableScissor();
        drawHealthAccessoryScrollbar(g, slots.size(), x, y, width, height);
    }

    private void drawHealthAccessoryScrollbar(GuiGraphics g, int slotCount,
                                               int x, int y, int width, int height) {
        int viewport = height - 22;
        int content = slotCount * HEALTH_ACCESSORY_ROW;
        if (content <= viewport) return;
        int trackX = x + width - 5;
        int trackY = y + 20;
        int thumb = Math.max(20, viewport * viewport / content);
        int travel = Math.max(1, viewport - thumb);
        int thumbY = trackY + (int) Math.round(
            healthAccessoryScroll / Math.max(1.0D, content - viewport) * travel);
        g.fill(trackX, trackY, trackX + 2, trackY + viewport, 0x88313C3B);
        g.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumb, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
    }

    private List<HealthAccessorySlot> healthAccessorySlots() {
        if (minecraft == null || minecraft.player == null) return List.of();
        return CuriosApi.getCuriosInventory(minecraft.player).map(curios -> {
            Map<String, top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler> sorted =
                new LinkedHashMap<>();
            curios.getCurios().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
            List<HealthAccessorySlot> result = new ArrayList<>();
            sorted.forEach((identifier, handler) -> {
                if (!handler.isVisible()) return;
                for (int index = 0; index < handler.getSlots(); index++) {
                    result.add(new HealthAccessorySlot(identifier, index,
                        handler.getStacks().getStackInSlot(index).copy(),
                        curios.isSlotActive(identifier, index)));
                }
            });
            return List.copyOf(result);
        }).orElse(List.of());
    }

    private String slotLabel(HealthAccessorySlot slot) {
        String type = Component.translatable("curios.identifier." + slot.identifier()).getString();
        if (type.equals("curios.identifier." + slot.identifier())) {
            type = slot.identifier().replace('_', ' ');
        }
        String item = slot.stack().isEmpty()
            ? Component.translatable("status.xero_delta.empty_slot").getString()
            : slot.stack().getHoverName().getString();
        return type + (slot.index() + 1) + "  " + item;
    }

    private HealthAccessorySlot healthAccessoryAt(double mouseX, double mouseY) {
        int x = healthAccessoryX();
        int y = healthAccessoryY();
        int width = healthAccessoryWidth();
        int height = healthAccessoryHeight();
        if (!inside(mouseX, mouseY, x + 4, y + 19, width - 12, height - 22)) return null;
        int index = (int) Math.floor((mouseY - y - 19 + healthAccessoryScroll)
            / HEALTH_ACCESSORY_ROW);
        List<HealthAccessorySlot> slots = healthAccessorySlots();
        return index >= 0 && index < slots.size() ? slots.get(index) : null;
    }

    private int healthAccessoryX() { return canvasX + EQUIPMENT_X; }
    private int healthAccessoryY() { return panelTop + HEADER_HEIGHT + 8; }
    private int healthAccessoryWidth() {
        return Math.max(80, Math.min(EQUIPMENT_WIDTH, canvasWidth - EQUIPMENT_X - 12));
    }
    private int healthAccessoryHeight() { return canvasHeight - HEADER_HEIGHT - 18; }

    private record HealthAccessorySlot(String identifier, int index,
                                       ItemStack stack, boolean active) {}

    private void applyDeltaSlotLayout() {
        // InventoryMenu: result 0, crafting 1-4, armor 5-8, inventory 9-35,
        // hotbar 36-44 and offhand 45.
        // Delta mode is exclusively owned by DeltaContainerLayoutController.
        // This layout is only the unrestricted status/inventory compatibility view.
        restrictedLayout = false;
        if (tab == Tab.HEALTH && !restrictedLayout) {
            for (int index = 0; index < menu.slots.size(); index++) {
                moveSlot(index, -1000, -1000);
            }
            return;
        }
        if (!restrictedLayout) {
            applyStandardStatusSlotLayout();
            return;
        }
        for (int index = 0; index < 5; index++) moveSlot(index, -1000, -1000);
        if (restrictedLayout) {
            int[] helmet = loadoutBounds(0);
            int[] chest = loadoutBounds(1);
            moveSlot(5, tab == Tab.CHARACTER ? helmet[0] - leftPos + 10 : -1000,
                tab == Tab.CHARACTER ? helmet[1] - topPos + 10 : -1000);
            moveSlot(6, tab == Tab.CHARACTER ? chest[0] - leftPos + 10 : -1000,
                tab == Tab.CHARACTER ? chest[1] - topPos + 10 : -1000);
            moveSlot(7, -1000, -1000);
            moveSlot(8, -1000, -1000);
        }
        for (int index = 0; index < 27; index++) {
            moveSlot(index + 9, -1000, -1000);
        }
        if (restrictedLayout) {
            moveSlot(36, -1000, -1000);
            moveSlot(37, -1000, -1000);
            moveSlot(38, -1000, -1000);
            int[] knife = loadoutBounds(3);
            moveSlot(39, tab == Tab.CHARACTER ? knife[0] - leftPos + 10 : -1000,
                tab == Tab.CHARACTER ? knife[1] - topPos + 10 : -1000);
            for (int index = 0; index < PocketSlotLayout.COUNT; index++) {
                PocketSlotLayout.Bounds bounds = pocketCellBounds(index);
                moveSlot(index + 40, bounds.x() - leftPos + 1,
                    bounds.y() - topPos + 1 + (int) Math.round(layoutScrollPixels));
            }
        }
        moveSlot(45, -1000, -1000);
    }

    /** Places vanilla InventoryMenu slots inside this screen's equipment,
     * inventory and hotbar panels when the Delta grid layout is disabled. */
    private void applyStandardStatusSlotLayout() {
        restoreSlotLayout();
        int equipmentX = EQUIPMENT_X + 8;
        int equipmentY = HEADER_HEIGHT + 30;
        for (int index = 0; index < 4; index++) {
            moveSlot(5 + index, equipmentX, equipmentY + index * 18);
        }
        moveSlot(45, equipmentX + 36, equipmentY + 54);

        int craftingX = EQUIPMENT_X + 76;
        int craftingY = HEADER_HEIGHT + 34;
        for (int index = 0; index < 4; index++) {
            moveSlot(1 + index, craftingX + index % 2 * 18,
                craftingY + index / 2 * 18);
        }
        moveSlot(0, craftingX + 58, craftingY + 9);

        int inventoryX = STORAGE_X + 8;
        int inventoryY = 62;
        for (int index = 0; index < 27; index++) {
            moveSlot(9 + index, inventoryX + index % 9 * 18,
                inventoryY + index / 9 * 18);
        }
        int hotbarY = 154;
        for (int index = 0; index < 9; index++) {
            moveSlot(36 + index, inventoryX + index * 18, hotbarY);
        }
    }
    private void moveSlot(int index, int x, int y) {
        if (index < 0 || index >= menu.slots.size()) return;
        Slot slot = menu.slots.get(index);
        if (restrictedLayout && x >= EQUIPMENT_X && x > -900 && y > -900) {
            y -= (int) Math.round(layoutScrollPixels);
            int absoluteY = topPos + y;
            if (absoluteY + 16 <= layoutViewportTop() || absoluteY >= layoutViewportBottom()) {
                x = -1000;
                y = -1000;
            }
        }
        SlotFieldUtil.setX(slot, x);
        SlotFieldUtil.setY(slot, y);
    }

    private void restoreSlotLayout() {
        int count = Math.min(menu.slots.size(), originalSlotX.length);
        for (int index = 0; index < count; index++) {
            SlotFieldUtil.setX(menu.slots.get(index), originalSlotX[index]);
            SlotFieldUtil.setY(menu.slots.get(index), originalSlotY[index]);
        }
    }

    private void renderItemTooltipWithDurability(GuiGraphics graphics, ItemStack stack,
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
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }
    private void drawExtraTooltips(GuiGraphics g, int mouseX, int mouseY) {
        PlayerStatusClientState state = PlayerStatusClientState.INSTANCE;
        boolean carrying = !menu.getCarried().isEmpty();
        int right = canvasX + canvasWidth;
        int closeX = right - 26;
        int mailX = closeX - 24;
        int balanceWidth = TradingUi.balanceWidth(font, state.balance());
        int balanceX = Math.max(canvasX + 190, mailX - balanceWidth - 8);
        if (inside(mouseX, mouseY, balanceX, panelTop + 7, balanceWidth, 16)) {
            TradingUi.renderBalanceTooltip(g, font, state.balance(), mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, safetySelectorX(), safetySelectorY(),
            LARGE_SLOT + 6, LARGE_SLOT + 6)) {
            if (restrictedLayout) {
                if (inside(mouseX, mouseY, safetySelectorX() + 1,
                    safetySelectorY() + LARGE_SLOT - 7, 14, 14)) {
                    g.renderTooltip(font, Component.translatable(
                        "status.xero_delta.elimination_protected"), mouseX, mouseY);
                }
                return;
            }
            ItemStack equipped = equippedSafetyBox();
            if (equipped.isEmpty()) {
                g.renderTooltip(font, Component.translatable("safety_box.xero_delta.change_hint"), mouseX, mouseY);
            } else if (!carrying) {
                g.renderTooltip(font, equipped, mouseX, mouseY);
            }
            return;
        }
        if (restrictedLayout && inside(mouseX, mouseY, safetyPinX(),
            safetySectionY() + 2, 16, 15)) {
            g.renderTooltip(font, Component.translatable(
                DeltaInventoryUiState.safetyBoxPinned()
                    ? "status.xero_delta.unpin_safety_box"
                    : "status.xero_delta.pin_safety_box"), mouseX, mouseY);
            return;
        }
        if (restrictedLayout) {
            int[] cardBounds = carrierSlotBounds("card_holder");
            if (inside(mouseX, mouseY, cardBounds[0] + 1,
                cardBounds[1] + cardBounds[3] - 14, 14, 14)) {
                g.renderTooltip(font, Component.translatable(
                    "status.xero_delta.elimination_protected"), mouseX, mouseY);
                return;
            }
        }
        if (renderActiveEffectTooltip(g, mouseX, mouseY)) return;
        if (tab == Tab.HEALTH) {
            int modelLeft = canvasX + MODEL_X;
            int modelTop = panelTop + HEADER_HEIGHT + 4;
            for (PlayerStatusUi.Part part : PlayerStatusUi.Part.values()) {
                int[] bounds = healthBodyPartBounds(part, modelLeft, modelTop);
                if (inside(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[2])) {
                    PlayerStatusUi.renderTooltip(g, font, part,
                        PlayerStatusUi.value(PlayerStatusClientState.INSTANCE, part), mouseX, mouseY);
                    return;
                }
            }
            HealthAccessorySlot slot = healthAccessoryAt(mouseX, mouseY);
            if (slot != null && !slot.stack().isEmpty()) {
                g.renderTooltip(font, slot.stack(), mouseX, mouseY);
            }
        }
    }

    private static TradingUiScale.Viewport adaptiveViewport(int physicalWidth, int physicalHeight) {
        float scale = InventoryLayoutScale.fitViewportScale(
            physicalWidth, physicalHeight, CANVAS_WIDTH + 16, CANVAS_HEIGHT + 16,
            StatusEffectHudState.inventoryLayoutScale());
        int logicalWidth = Math.max(CANVAS_WIDTH + 8, (int) Math.floor(physicalWidth / scale));
        int logicalHeight = Math.max(CANVAS_HEIGHT + 8, (int) Math.floor(physicalHeight / scale));
        int scaledWidth = Math.round(logicalWidth * scale);
        int scaledHeight = Math.round(logicalHeight * scale);
        return new TradingUiScale.Viewport(physicalWidth, physicalHeight, logicalWidth, logicalHeight,
            scale, (physicalWidth - scaledWidth) / 2, (physicalHeight - scaledHeight) / 2);
    }

    private void refreshInventoryLayoutScale() {
        float current = StatusEffectHudState.inventoryLayoutScale();
        if (Float.compare(current, appliedInventoryLayoutScale) == 0) return;
        int physicalWidth = viewport.physicalWidth() > 0 ? viewport.physicalWidth() : width;
        int physicalHeight = viewport.physicalHeight() > 0 ? viewport.physicalHeight() : height;
        STORAGE_CELL = InventoryLayoutScale.cellSize(18, current);
        viewport = adaptiveViewport(physicalWidth, physicalHeight);
        width = viewport.logicalWidth();
        height = viewport.logicalHeight();
        canvasWidth = Math.max(CANVAS_WIDTH, width - 16);
        canvasHeight = Math.max(CANVAS_HEIGHT, height - 16);
        panelTop = Math.max(4, (height - canvasHeight) / 2);
        canvasX = Math.max(4, (width - canvasWidth) / 2);
        imageWidth = canvasWidth;
        imageHeight = canvasHeight;
        leftPos = canvasX;
        topPos = panelTop;
        applyDeltaSlotLayout();
        appliedInventoryLayoutScale = current;
    }
    public double itemDetailMouseX(double physicalX) {
        if (sharedDeltaLayout) return physicalX;
        if (ItemDetailOverlay.isSourcePanelOpen(this)) return physicalX;
        return viewport.mouseXDouble(physicalX);
    }

    public double itemDetailMouseY(double physicalY) {
        if (sharedDeltaLayout) return physicalY;
        if (ItemDetailOverlay.isSourcePanelOpen(this)) return physicalY;
        return viewport.mouseYDouble(physicalY);
    }

    public int itemDetailLogicalX(int physicalX) {
        if (sharedDeltaLayout) return physicalX;
        return (int) Math.round(viewport.mouseXDouble(physicalX)
            - transition.horizontalOffset());
    }

    public int itemDetailLogicalY(int physicalY) {
        if (sharedDeltaLayout) return physicalY;
        return (int) Math.round(viewport.mouseYDouble(physicalY));
    }

    public int itemDetailLogicalSize(int physicalSize) {
        if (sharedDeltaLayout) return physicalSize;
        return Math.max(1, (int) Math.round(physicalSize / viewport.scale()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sharedDeltaLayout) return super.mouseClicked(mouseX, mouseY, button);
        double x = viewport.mouseXDouble(mouseX);
        double y = viewport.mouseYDouble(mouseY);
        if (button == 0 && "DRAG".equalsIgnoreCase(Config.INSTANCE.inventoryModelControl.get())
            && inside(x, y, canvasX + MODEL_X, panelTop + HEADER_HEIGHT,
                MODEL_WIDTH, canvasHeight - HEADER_HEIGHT)) {
            modelDragging = true;
            return true;
        }
        boolean sourcePanel = ItemDetailOverlay.isSourcePanelOpen(this);
        double overlayX = sourcePanel ? mouseX : x;
        double overlayY = sourcePanel ? mouseY : y;
        boolean popupHit = ItemDetailOverlay.isPopupAt(this, overlayX, overlayY);
        if (popupHit && ItemDetailOverlay.mouseClicked(this, overlayX, overlayY, button)) return true;
        if (!popupHit && button == 0 && ItemDetailOverlay.isOpen(this)) {
            boolean reselectingCarrier = selectedCurioIdentifier != null
                && selectedCurioIdentifier.equals(curioIdentifierAt(x, y));
            if (!reselectingCarrier
                && tryMoveDetailSelection(mouseX, mouseY, x, y)) return true;
            ItemDetailOverlay.mouseClicked(this, overlayX, overlayY, button);
            clearDetailSelection();
        }
        if (restrictedLayout && button == 2
            && InventorySorterCompat.middleClickSortingEnabled()
            && menu.getCarried().isEmpty()) {
            StorageCell storage = shortcutStorageCellAt(x, y);
            if (storage != null) {
                ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
                    storage.identifier(), EquippedStorageShortcutPacket.SORT, storage.cell()));
                return true;
            }
        }
        int right = canvasX + canvasWidth;
        int closeX = right - 26;
        int mailX = closeX - 24;
        if (tab == Tab.HEALTH) {
            if (button == 0 && inside(x, y, closeX, panelTop + 4, 20, 22)) {
                onClose();
                return true;
            }
            if (button == 0 && inside(x, y, mailX, panelTop + 4,
                MailOverlayRenderer.SIZE, MailOverlayRenderer.SIZE)) {
                MailClientState.INSTANCE.requestOpen();
                ModNetwork.sendToServer(MailActionPacket.open());
                return true;
            }
            if (button == 0 && inside(x, y, canvasX + 8, panelTop + 4, 78, 22)) {
                switchTab(Tab.CHARACTER);
                return true;
            }
            HealthAccessorySlot accessory = healthAccessoryAt(x, y);
            if (button == 0 && accessory != null && accessory.active()) {
                ModNetwork.sendToServer(new CurioSlotSwapPacket(
                    accessory.identifier(), accessory.index()));
                return true;
            }
            boolean embeddedInventory = restrictedLayout && inside(x, y,
                restrictedColumnX(), panelTop + HEADER_HEIGHT + 8,
                restrictedColumnWidth() + 9, canvasHeight - HEADER_HEIGHT - 18);
            if (!embeddedInventory && !(restrictedLayout && insideDiscardZone(x, y))) {
                return inside(x, y, canvasX, panelTop, canvasWidth, canvasHeight);
            }
        }
        if (restrictedLayout && button == 0 && insideDiscardZone(x, y)
            && minecraft != null && minecraft.player != null
            && !minecraft.player.containerMenu.getCarried().isEmpty()) {
            slotClicked(null, -999, button, ClickType.PICKUP);
            return true;
        }
        if (restrictedLayout && button == 0 && menu.getCarried().isEmpty()
            && PlayerStatusClientState.INSTANCE.layoutClick()) {
            int pocketSlot = pocketInventorySlotAt(x, y);
            if (pocketSlot >= 4 && minecraft != null && minecraft.player != null) {
                ItemStack pocketStack = minecraft.player.getInventory().getItem(pocketSlot);
                if (!pocketStack.isEmpty()) {
                    long now = System.currentTimeMillis();
                    String key = "pockets:" + pocketSlot;
                    if (Screen.hasShiftDown() || (key.equals(lastItemClickKey)
                        && now - lastItemClickAt <= 280L)) {
                        ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
                            "pockets", EquippedStorageShortcutPacket.QUICK_MOVE, pocketSlot));
                        clearItemGesture();
                    } else {
                        lastItemClickKey = key;
                        lastItemClickAt = now;
                        armedItem = null;
                        armedPocketSlot = pocketSlot;
                        armedDragStack = pocketStack.copy();
                        selectedStorageItem = null;
                        selectedPocketSlot = pocketSlot;
                        itemDragStartX = x;
                        itemDragStartY = y;
                        itemDragPickedUp = false;

                    }
                    return true;
                }
            }
        }
        if (restrictedLayout && button == 0 && menu.getCarried().isEmpty()) {
            StorageCell clicked = shortcutStorageCellAt(x, y);
            if (clicked != null && !clicked.stack().isEmpty()) {
                if (Screen.hasShiftDown()) {
                    ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
                        clicked.identifier(), EquippedStorageShortcutPacket.QUICK_MOVE,
                        clicked.cell()));
                    clearItemGesture();
                    return true;
                }
                if (PlayerStatusClientState.INSTANCE.layoutClick()) {
                    long now = System.currentTimeMillis();
                    String key = clicked.identifier() + ":" + clicked.cell();
                    if (key.equals(lastItemClickKey) && now - lastItemClickAt <= 280L) {
                        ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
                            clicked.identifier(), EquippedStorageShortcutPacket.QUICK_MOVE,
                            clicked.cell()));
                        clearItemGesture();
                    } else {
                        lastItemClickKey = key;
                        lastItemClickAt = now;
                        armedItem = clicked;
                        armedDragStack = clicked.stack().copy();
                        itemDragStartX = x;
                        itemDragStartY = y;
                        itemDragPickedUp = false;
                        selectedStorageItem = clicked;
                        selectedPocketSlot = -1;
                    }
                    return true;
                }
            }
        }
        if (restrictedLayout && button == 0
            && inside(x, y, safetyPinX(), safetySectionY() + 2, 16, 15)) {
            DeltaInventoryUiState.setSafetyBoxPinned(
                !DeltaInventoryUiState.safetyBoxPinned());
            layoutScrollPixels = Math.min(layoutScrollPixels, layoutMaxScroll());
            layoutTargetScrollPixels = Math.min(layoutTargetScrollPixels, layoutMaxScroll());
            applyDeltaSlotLayout();
            return true;
        }
        if (restrictedLayout && (button == 0 || button == 1)) {
            StorageCell safetyCell = safetyBoxStorageCellAt(x, y);
            if (safetyCell != null) {
                sendStorageClick(safetyCell, button, x, y);
                return true;
            }
        }
        if (restrictedLayout && button == 1
            && "card_holder".equals(curioIdentifierAt(x, y))) {
            return true;
        }
        if (restrictedLayout && button == 0) {
            if (inside(x, y, safetySelectorX(), safetySelectorY(),
                LARGE_SLOT + 6, LARGE_SLOT + 6)) {
                ItemStack carried = menu.getCarried();
                if (!carried.isEmpty() && !(carried.getItem() instanceof
                    com.xtdpotato.xero_delta.item.SafetyBoxItem)) {
                    ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
                        "safety_box", EquippedStorageShortcutPacket.INSERT_CARRIED, -1));
                } else {
                    openSafetyBoxPickerOrNotice();
                }
                return true;
            }
            String curioIdentifier = curioIdentifierAt(x, y);
            if (curioIdentifier != null) {
                ItemStack carried = menu.getCarried();
                if (carried.getCount() == 1
                    && carried.getItem() instanceof DeltaPackItem pack
                    && curioIdentifier.equals(pack.slotIdentifier())) {
                    ModNetwork.sendToServer(new CurioSlotSwapPacket(curioIdentifier, 0));
                } else if (shouldInsertFromCarrierSelector(curioIdentifier, carried)) {
                    ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
                        curioIdentifier, EquippedStorageShortcutPacket.INSERT_CARRIED, -1));
                } else if ("card_holder".equals(curioIdentifier)) {
                    if (PlayerStatusClientState.INSTANCE.canChangeBc()) {
                        minecraft.setScreen(new CardHolderPickerScreen(this));
                    }
                } else if (carried.isEmpty() && !accessorySlot(curioIdentifier).stack().isEmpty()) {
                    AccessorySlot accessory = accessorySlot(curioIdentifier);
                    armedCurioIdentifier = curioIdentifier;
                    armedDragStack = accessory.stack().copy();
                    itemDragStartX = x;
                    itemDragStartY = y;
                    itemDragPickedUp = false;

                } else {
                    ModNetwork.sendToServer(new CurioSlotSwapPacket(curioIdentifier, 0));
                }
                return true;
            }
        }
        if (restrictedLayout && button == 0 && beginLayoutScrollbarDrag(x, y)) {
            return true;
        }
        if (restrictedLayout && button == 0 && beginBackpackScrollbarDrag(x, y)) {
            return true;
        }
        if (restrictedLayout && button == 0 && beginBackpackContentDrag(x, y)) {
            return true;
        }
        if (restrictedLayout && button == 0) {
            int equipmentSlot = restrictedEquipmentSlotAt(x, y);
            if (equipmentSlot >= 0) {
                ItemStack equipped = equipmentSlot == 0
                    ? minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                    : minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                if (menu.getCarried().isEmpty() && !equipped.isEmpty()) {
                    armedEquipmentSlot = equipmentSlot;
                    armedDragStack = equipped.copy();
                    itemDragStartX = x;
                    itemDragStartY = y;
                    itemDragPickedUp = false;

                } else {
                    ModNetwork.sendToServer(new PlayerEquipmentSlotClickPacket(equipmentSlot));
                }
                return true;
            }
        }
        if (restrictedLayout && button == 0 && beginLayoutContentDrag(x, y)) {
            return true;
        }
        if (restrictedLayout && button == 1) {
            StorageCell storageCell = shortcutStorageCellAt(x, y);
            if (sendStorageClick(storageCell, button, x, y)) return true;
        }
        if (restrictedLayout && (button == 0 || button == 1)) {
            int[] knifeBounds = loadoutBounds(3);
            if (inside(x, y, knifeBounds[0], knifeBounds[1],
                knifeBounds[2], knifeBounds[3])) {
                if (button == 0 && PlayerStatusClientState.INSTANCE.canChangeBc()) {
                    minecraft.setScreen(new KnifePickerScreen(this));
                }
                return true;
            }
        }
        if (button == 0) {
            if (restrictedLayout) {
                StorageCell storageCell = shortcutStorageCellAt(x, y);
                if (sendStorageClick(storageCell, 0, x, y)) return true;
                int inventorySlot = restrictedLargeInventorySlotAt(x, y);
                if (inventorySlot >= 0) {
                    ItemStack selected = minecraft.player.getInventory().getItem(inventorySlot);
                    if (menu.getCarried().isEmpty() && !selected.isEmpty()) {
                        armedLargeInventorySlot = inventorySlot;
                        armedDragStack = selected.copy();
                        itemDragStartX = x;
                        itemDragStartY = y;
                        itemDragPickedUp = false;
                        int visual = inventorySlot == 2 ? 2 : inventorySlot == 0 ? 4 : 5;

                    } else {
                        ModNetwork.sendToServer(new com.xtdpotato.xero_delta.network.PlayerLayoutSlotClickPacket(
                            inventorySlot));
                    }
                    return true;
                }
            }
            if (inside(x, y, closeX, panelTop + 4, 20, 22)) {
                onClose();
                return true;
            }
            if (inside(x, y, mailX, panelTop + 4,
                MailOverlayRenderer.SIZE, MailOverlayRenderer.SIZE)) {
                MailClientState.INSTANCE.requestOpen();
                ModNetwork.sendToServer(MailActionPacket.open());
                return true;
            }
            if (inside(x, y, canvasX + 8, panelTop + 4, 78, 22)) {
                switchTab(Tab.CHARACTER);
                return true;
            }
            if (inside(x, y, canvasX + 86, panelTop + 4, 78, 22)) {
                switchTab(Tab.HEALTH);
                return true;
            }
            if (!restrictedLayout && inside(x, y, safetySelectorX(), safetySelectorY(),
                LARGE_SLOT + 6, LARGE_SLOT + 6)) {
                openSafetyBoxPickerOrNotice();
                return true;
            }
            if (inside(x, y, canvasX + MODEL_X, panelTop + HEADER_HEIGHT,
                MODEL_WIDTH, canvasHeight - HEADER_HEIGHT)) {
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.mouseDragged(this, mouseX, mouseY, button)
            || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if ((armedItem != null || armedPocketSlot >= 4 || armedEquipmentSlot >= 0
            || armedLargeInventorySlot >= 0 || armedCurioIdentifier != null) && button == 0) {
            double logicalX = viewport.mouseXDouble(mouseX);
            double logicalY = viewport.mouseYDouble(mouseY);
            double dx = logicalX - itemDragStartX;
            double dy = logicalY - itemDragStartY;
            if (!itemDragPickedUp && dx * dx + dy * dy >= 9.0D) {
                itemDragPickedUp = true;
                ItemDetailOverlay.close(this);
            }
            if (itemDragPickedUp) return true;
        }
        if (modelDragging && button == 0) {
            modelYaw += (float) viewport.deltaX(dragX) * 2.0F;
            return true;
        }
        if (backpackContentDragArmed && button == 0) {
            dragBackpackContent(viewport.mouseYDouble(mouseY));
            return true;
        }
        if (backpackScrollbarDragging && button == 0) {
            updateBackpackScrollbar(viewport.mouseYDouble(mouseY));
            return true;
        }
        if (layoutContentDragging && button == 0) {
            dragLayoutContent(viewport.mouseYDouble(mouseY));
            return true;
        }
        if (layoutScrollbarDragging && button == 0) {
            updateLayoutScrollbar(viewport.mouseYDouble(mouseY));
            return true;
        }
        return super.mouseDragged(viewport.mouseXDouble(mouseX), viewport.mouseYDouble(mouseY), button,
            viewport.deltaX(dragX), viewport.deltaY(dragY));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.mouseReleased(this, mouseX, mouseY, button)
            || super.mouseReleased(mouseX, mouseY, button);
        if (modelDragging && button == 0) {
            modelDragging = false;
            return true;
        }
        if ((armedItem != null || armedPocketSlot >= 4 || armedEquipmentSlot >= 0
            || armedLargeInventorySlot >= 0 || armedCurioIdentifier != null) && button == 0) {
            StorageCell deferredSource = armedItem;
            int deferredPocketSlot = armedPocketSlot;
            int deferredEquipmentSlot = armedEquipmentSlot;
            int deferredLargeInventorySlot = armedLargeInventorySlot;
            String deferredCurioIdentifier = armedCurioIdentifier;
            ItemStack virtualStack = armedDragStack.copy();
            String sourceId = deferredSourceId(deferredSource, deferredPocketSlot,
                deferredEquipmentSlot, deferredLargeInventorySlot,
                deferredCurioIdentifier, virtualStack);
            boolean dragged = itemDragPickedUp;
            clearItemGesture();
            if (!dragged) {
                openDeferredItemDetail(deferredSource, deferredPocketSlot,
                    deferredEquipmentSlot, deferredLargeInventorySlot,
                    deferredCurioIdentifier);
                return true;
            }
            double x = viewport.mouseXDouble(mouseX);
            double y = viewport.mouseYDouble(mouseY);
            if (insideDiscardZonePhysical(mouseX, mouseY)) {
                if (!sourceId.isBlank()) ModNetwork.sendToServer(new ItemDetailActionPacket(
                    sourceId, ItemDetailActionPacket.DISCARD));
                return true;
            }
            if (inside(x, y, safetySelectorX(), safetySelectorY(),
                LARGE_SLOT + 6, LARGE_SLOT + 6)) {
                if (!sourceId.isBlank()
                    && !(virtualStack.getItem() instanceof SafetyBoxItem)) {
                    ModNetwork.sendToServer(new InventorySourceToEquippedStoragePacket(
                        sourceId, "safety_box", -1, false));
                }
                return true;
            }
            String curioTarget = curioIdentifierAt(x, y);
            if (curioTarget != null) {
                if (virtualStack.getCount() == 1
                    && virtualStack.getItem() instanceof DeltaPackItem pack
                    && curioTarget.equals(pack.slotIdentifier())) {
                    if (!curioTarget.equals(deferredCurioIdentifier) && !sourceId.isBlank()) {
                        ModNetwork.sendToServer(new CarrierReplacePacket(sourceId));
                    }
                } else if (shouldInsertFromCarrierSelector(curioTarget, virtualStack)
                    && !sourceId.isBlank()) {
                    ModNetwork.sendToServer(new InventorySourceToEquippedStoragePacket(
                        sourceId, curioTarget, -1, false));
                }
                return true;
            }
            if (dropIntoRestrictedEquipmentTarget(x, y, virtualStack,
                sourceId, deferredCurioIdentifier)) return true;
            int pocketTarget = pocketInventorySlotAt(x, y);
            if (pocketTarget >= 4) {
                ItemSize size = ClientDataCache.INSTANCE.getSize(virtualStack);
                if (size.width() != 1 || size.height() != 1) return true;
                if (!("player|" + pocketTarget).equals(sourceId) && !sourceId.isBlank()) {
                    ModNetwork.sendToServer(new InventorySourceToEquippedStoragePacket(
                        sourceId, "pockets", pocketTarget, false));
                }
                return true;
            }
            StorageCell destination = shortcutStorageCellAt(x, y);
            if (destination != null) {
                if (deferredSource != null
                    && deferredSource.identifier().equals(destination.identifier())
                    && deferredSource.cell() == destination.cell()) return true;
                int cell = destination.cell();
                boolean rotated = GridBackingStore.isRotated(virtualStack);
                Set<Integer> ignoredAnchors =
                    deferredSource != null
                        && deferredSource.identifier().equals(destination.identifier())
                        ? Set.of(deferredSource.cell()) : Set.of();
                GridBackingStore.PlacementResult placement = resolveStoragePlacement(
                    destination, virtualStack, x, y, ignoredAnchors);
                if (placement != null) {
                    if (!placement.isAccepted()) return true;
                    cell = placement.y() * storageColumns(destination.identifier())
                        + placement.x();
                    rotated = placement.rotated();
                }
                if (!sourceId.isBlank()) ModNetwork.sendToServer(
                    new InventorySourceToEquippedStoragePacket(
                        sourceId, destination.identifier(), cell, rotated));
                return true;
            }
            int inventorySlot = restrictedLargeInventorySlotAt(x, y);
            if (inventorySlot >= 0) {
                Slot target = inventoryMenuSlot(inventorySlot);
                if (target != null && !("player|" + inventorySlot).equals(sourceId)
                    && !sourceId.isBlank()) {
                    ModNetwork.sendToServer(new InventorySourceToMenuPacket(
                        sourceId, menu.containerId, target.index, false));
                }
                return true;
            }
            return true;
        }
        if (backpackContentDragArmed && button == 0) {
            if (!backpackContentDragged && pendingBackpackCell != null) {
                sendStorageClick(pendingBackpackCell, 0,
                    viewport.mouseXDouble(mouseX), viewport.mouseYDouble(mouseY));
            }
            backpackContentDragArmed = false;
            backpackContentDragged = false;
            pendingBackpackCell = null;
            return true;
        }
        if (backpackScrollbarDragging && button == 0) {
            backpackScrollbarDragging = false;
            return true;
        }
        if (layoutContentDragging && button == 0) {
            layoutContentDragging = false;
            return true;
        }
        if (layoutScrollbarDragging && button == 0) {
            layoutScrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(viewport.mouseXDouble(mouseX), viewport.mouseYDouble(mouseY), button);
    }

    private void openDeferredItemDetail(StorageCell storage, int pocketSlot,
                                        int equipmentSlot, int inventorySlot,
                                        String curioIdentifier) {
        if (storage != null && !storage.stack().isEmpty()) {
            openItemDetail(storage);
            return;
        }
        if (minecraft == null || minecraft.player == null) return;
        if (pocketSlot >= 4) {
            ItemStack stack = minecraft.player.getInventory().getItem(pocketSlot);
            if (stack.isEmpty()) return;
            selectedStorageItem = null;
            selectedPocketSlot = pocketSlot;
            selectedEquipmentSlot = -1;
            selectedLargeInventorySlot = -1;
            selectedCurioIdentifier = null;
            int[] bounds = pocketBounds(pocketSlot);
            ItemDetailOverlay.open(this, stack, false, "player|" + pocketSlot,
                bounds[0], bounds[1], bounds[2], bounds[3]);
            return;
        }
        if (equipmentSlot >= 0) {
            ItemStack stack = equipmentSlot == 0
                ? minecraft.player.getItemBySlot(EquipmentSlot.HEAD)
                : minecraft.player.getItemBySlot(EquipmentSlot.CHEST);
            if (stack.isEmpty()) return;
            selectedStorageItem = null;
            selectedPocketSlot = -1;
            selectedEquipmentSlot = equipmentSlot;
            selectedLargeInventorySlot = -1;
            selectedCurioIdentifier = null;
            int[] bounds = loadoutBounds(equipmentSlot);
            ItemDetailOverlay.open(this, stack, false,
                "player|" + (equipmentSlot == 0 ? 39 : 38),
                bounds[0], bounds[1], bounds[2], bounds[3]);
            return;
        }
        if (inventorySlot >= 0) {
            ItemStack stack = minecraft.player.getInventory().getItem(inventorySlot);
            if (stack.isEmpty()) return;
            selectedStorageItem = null;
            selectedPocketSlot = -1;
            selectedEquipmentSlot = -1;
            selectedLargeInventorySlot = inventorySlot;
            selectedCurioIdentifier = null;
            int visual = inventorySlot == 2 ? 2 : inventorySlot == 0 ? 4 : 5;
            int[] bounds = loadoutBounds(visual);
            ItemDetailOverlay.open(this, stack, false, "player|" + inventorySlot,
                bounds[0], bounds[1], bounds[2], bounds[3]);
            return;
        }
        if (curioIdentifier != null
            && ("chest_rig".equals(curioIdentifier) || "backpack".equals(curioIdentifier))) {
            AccessorySlot accessory = accessorySlot(curioIdentifier);
            if (accessory.stack().isEmpty()) return;
            selectedStorageItem = null;
            selectedPocketSlot = -1;
            selectedEquipmentSlot = -1;
            selectedLargeInventorySlot = -1;
            selectedCurioIdentifier = curioIdentifier;
            int[] bounds = carrierSlotBounds(curioIdentifier);
            ItemDetailOverlay.open(this, accessory.stack(), false,
                TradingInventorySources.sourceIdForCurioSlot(
                    curioIdentifier, 0, accessory.stack()),
                bounds[0], bounds[1], bounds[2], bounds[3]);
        }
    }

    private boolean tryMoveDetailSelection(double physicalX, double physicalY,
                                           double logicalX, double logicalY) {
        boolean target = shortcutStorageCellAt(logicalX, logicalY) != null
            || pocketInventorySlotAt(logicalX, logicalY) >= 4
            || restrictedEquipmentSlotAt(logicalX, logicalY) >= 0
            || restrictedLargeInventorySlotAt(logicalX, logicalY) >= 0
            || curioIdentifierAt(logicalX, logicalY) != null;
        if (!restrictedLayout || !target) return false;
        clearItemGesture();
        if (selectedStorageItem != null) {
            armedItem = selectedStorageItem;
            armedDragStack = selectedStorageItem.stack().copy();
        } else if (selectedPocketSlot >= 4 && minecraft != null && minecraft.player != null) {
            armedPocketSlot = selectedPocketSlot;
            armedDragStack = minecraft.player.getInventory().getItem(selectedPocketSlot).copy();
            Slot source = inventoryMenuSlot(selectedPocketSlot);
            if (source == null || !source.hasItem()) return false;
        } else if (selectedEquipmentSlot >= 0 && minecraft != null && minecraft.player != null) {
            armedEquipmentSlot = selectedEquipmentSlot;
            armedDragStack = selectedEquipmentSlot == 0
                ? minecraft.player.getItemBySlot(EquipmentSlot.HEAD).copy()
                : minecraft.player.getItemBySlot(EquipmentSlot.CHEST).copy();
        } else if (selectedLargeInventorySlot >= 0 && minecraft != null
            && minecraft.player != null) {
            armedLargeInventorySlot = selectedLargeInventorySlot;
            armedDragStack = minecraft.player.getInventory()
                .getItem(selectedLargeInventorySlot).copy();
            Slot source = inventoryMenuSlot(selectedLargeInventorySlot);
            if (source == null || !source.hasItem()) return false;
        } else if (selectedCurioIdentifier != null) {
            AccessorySlot accessory = accessorySlot(selectedCurioIdentifier);
            if (accessory.stack().isEmpty()) return false;
            armedCurioIdentifier = selectedCurioIdentifier;
            armedDragStack = accessory.stack().copy();
        } else {
            return false;
        }
        itemDragPickedUp = true;
        boolean handled = mouseReleased(physicalX, physicalY, 0);
        if (handled) {
            ItemDetailOverlay.close(this);
            clearDetailSelection();
        }
        return handled;
    }

    private void clearDetailSelection() {
        selectedStorageItem = null;
        selectedPocketSlot = -1;
        selectedEquipmentSlot = -1;
        selectedLargeInventorySlot = -1;
        selectedCurioIdentifier = null;
    }

    private boolean dropIntoRestrictedEquipmentTarget(double mouseX, double mouseY,
                                                      ItemStack stack,
                                                      String sourceId,
                                                      String sourceCurio) {
        if (stack == null || stack.isEmpty()) return false;
        int equipment = restrictedEquipmentSlotAt(mouseX, mouseY);
        if (equipment >= 0) {
            EquipmentSlot slot = equipment == 0 ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
            if (minecraft != null && minecraft.player != null && stack.getCount() == 1
                && PlayerEquipmentSync.canEquip(minecraft.player, stack, slot)) {
                int inventoryIndex = equipment == 0 ? 39 : 38;
                Slot target = inventoryMenuSlot(inventoryIndex);
                if (target != null && !("player|" + inventoryIndex).equals(sourceId)
                    && sourceId != null && !sourceId.isBlank()) {
                    ModNetwork.sendToServer(new InventorySourceToMenuPacket(
                        sourceId, menu.containerId, target.index, false));
                }
            }
            return true;
        }
        if (!(stack.getItem() instanceof DeltaPackItem pack) || stack.getCount() != 1) {
            return false;
        }
        String target = curioIdentifierAt(mouseX, mouseY);
        if (target == null || !target.equals(pack.slotIdentifier())) return false;
        if (target.equals(sourceCurio)) return true;
        if (sourceId != null && !sourceId.isBlank()) {
            ModNetwork.sendToServer(new CarrierReplacePacket(sourceId));
        }
        return true;
    }

    private String deferredSourceId(StorageCell storage, int pocketSlot,
                                    int equipmentSlot, int inventorySlot,
                                    String curioIdentifier, ItemStack stack) {
        if (storage != null) {
            return TradingInventorySources.sourceIdForCurio(
                storage.identifier(), 0, storage.cell(), storage.stack());
        }
        if (pocketSlot >= 4) return "player|" + pocketSlot;
        if (equipmentSlot >= 0) return "player|" + (equipmentSlot == 0 ? 39 : 38);
        if (inventorySlot >= 0) return "player|" + inventorySlot;
        return curioIdentifier == null ? ""
            : TradingInventorySources.sourceIdForCurioSlot(curioIdentifier, 0, stack);
    }

    private Slot inventoryMenuSlot(int inventoryIndex) {
        if (minecraft == null || minecraft.player == null) return null;
        for (Slot slot : menu.slots) {
            if (slot.container == minecraft.player.getInventory()
                && slot.getContainerSlot() == inventoryIndex) return slot;
        }
        return null;
    }

    private void clearItemGesture() {
        armedItem = null;
        armedPocketSlot = -1;
        armedEquipmentSlot = -1;
        armedLargeInventorySlot = -1;
        armedCurioIdentifier = null;
        armedDragStack = ItemStack.EMPTY;
        itemDragPickedUp = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.mouseScrolled(this, mouseX, mouseY, scrollY)
            || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (ItemDetailOverlay.isSourcePanelOpen(this)) {
            return ItemDetailOverlay.mouseScrolled(this, mouseX, mouseY, scrollY);
        }
        double x = viewport.mouseXDouble(mouseX);
        double y = viewport.mouseYDouble(mouseY);
        if (tab == Tab.HEALTH && inside(x, y, healthAccessoryX(), healthAccessoryY(),
            healthAccessoryWidth(), healthAccessoryHeight())) {
            int content = healthAccessorySlots().size() * HEALTH_ACCESSORY_ROW;
            int viewportHeight = healthAccessoryHeight() - 22;
            healthAccessoryScroll = Math.max(0.0D, Math.min(
                Math.max(0, content - viewportHeight),
                healthAccessoryScroll - scrollY * HEALTH_ACCESSORY_ROW * 1.5D));
            return true;
        }
        if (restrictedLayout && safetyBoxStorageCellAt(x, y) != null) return true;
        if (restrictedLayout && scrollBackpackGrid(x, y, scrollY)) return true;
        if (restrictedLayout && scrollLayout(x, y, scrollY)) return true;
        if (tab == Tab.HEALTH) return true;
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && ItemDetailOverlay.handleEscape(this)) return true;
        if (keyCode == 256) {
            onClose();
            return true;
        }
        if (restrictedLayout && minecraft != null
            && minecraft.options.keyDrop.matches(keyCode, scanCode)
            && menu.getCarried().isEmpty()) {
            double physicalX = minecraft.mouseHandler.xpos()
                * minecraft.getWindow().getGuiScaledWidth()
                / (double) minecraft.getWindow().getScreenWidth();
            double physicalY = minecraft.mouseHandler.ypos()
                * minecraft.getWindow().getGuiScaledHeight()
                / (double) minecraft.getWindow().getScreenHeight();
            StorageCell storage = shortcutStorageCellAt(
                viewport.mouseXDouble(physicalX), viewport.mouseYDouble(physicalY));
            if (storage != null && !storage.stack().isEmpty()) {
                ModNetwork.sendToServer(new EquippedStorageShortcutPacket(
                    storage.identifier(),
                    Screen.hasControlDown()
                        ? EquippedStorageShortcutPacket.DROP_STACK
                        : EquippedStorageShortcutPacket.DROP_ONE,
                    storage.cell()));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean isHealthTabOpen() {
        if (sharedDeltaLayout) return DeltaContainerLayoutController.isHealthTabOpen(this);
        return tab == Tab.HEALTH;
    }

    public boolean hasEmbeddedDeltaInventory() {
        return sharedDeltaLayout;
    }

    private void updateSmoothScroll() {
        long now = System.nanoTime();
        if (lastScrollFrameNanos == 0L) {
            lastScrollFrameNanos = now;
            return;
        }
        double elapsedSeconds = Math.max(0.0D, Math.min(0.10D,
            (now - lastScrollFrameNanos) / 1_000_000_000.0D));
        lastScrollFrameNanos = now;
        double blend = 1.0D - Math.exp(-elapsedSeconds * 18.0D);

        if (!backpackScrollbarDragging && !backpackContentDragArmed) {
            backpackScrollPixels += (backpackTargetScrollPixels
                - backpackScrollPixels) * blend;
            if (Math.abs(backpackTargetScrollPixels - backpackScrollPixels) < 0.02D) {
                backpackScrollPixels = backpackTargetScrollPixels;
            }
        }
        if (!layoutScrollbarDragging && !layoutContentDragging) {
            double previous = layoutScrollPixels;
            layoutScrollPixels += (layoutTargetScrollPixels - layoutScrollPixels) * blend;
            if (Math.abs(layoutTargetScrollPixels - layoutScrollPixels) < 0.02D) {
                layoutScrollPixels = layoutTargetScrollPixels;
            }
            if (restrictedLayout && Math.abs(previous - layoutScrollPixels) > 0.001D) {
                applyDeltaSlotLayout();
            }
        }
    }

    @Override
    public void containerTick() {
        getRecipeBookComponent().tick();
        boolean shouldUseSharedLayout = PlayerStatusClientState.INSTANCE.layoutEnabled()
            && minecraft.player != null && !minecraft.player.isCreative();
        if (sharedDeltaLayout != shouldUseSharedLayout) {
            minecraft.setScreen(new PlayerStatusScreen(parent, isHealthTabOpen()));
            return;
        }
        if (sharedDeltaLayout) return;
        transition.tick(minecraft);
        if (restrictedLayout) {
            double maximum = layoutMaxScroll();
            layoutScrollPixels = Math.max(0.0D, Math.min(maximum, layoutScrollPixels));
            layoutTargetScrollPixels = Math.max(0.0D,
                Math.min(maximum, layoutTargetScrollPixels));
        }
    }

    @Override
    public void onClose() {
        if (sharedDeltaLayout) {
            DeltaContainerLayoutController.closeWithTransition(this, this::returnToParent);
            return;
        }
        transition.beginClose(this::returnToParent);
    }

    private void returnToParent() {
        if (minecraft == null || minecraft.player == null) return;
        if (parent instanceof CreativeModeInventoryScreen creative) {
            minecraft.player.containerMenu = creative.getMenu();
        } else if (parent instanceof InventoryScreen && !(parent instanceof PlayerStatusScreen)) {
            PlayerStatusScreenState.bypassNextInventoryReplacement();
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        DeltaContainerLayoutController.restore(this);
        restoreSlotLayout();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderVanillaSlotWeightBadges(GuiGraphics g) {
        for (Slot slot : menu.slots) {
            if (slot.x < 0 || slot.y < 0 || slot.getItem().isEmpty()) continue;
            if (restrictedLayout && (slot.index >= 40 && slot.index <= 44
                || tab == Tab.CHARACTER && (slot.index == 5 || slot.index == 6
                    || slot.index >= 36 && slot.index <= 39))) continue;
            drawItemWeightBadge(g, slot.getItem(), leftPos + slot.x - 1,
                topPos + slot.y - 1, 18, 18);
        }
    }

    private void drawItemWeightBadge(GuiGraphics g, ItemStack stack,
                                     int x, int y, int width, int height) {
        if (!com.xtdpotato.xero_delta.data.ItemWeightDisplayPolicy.shouldShow(stack)) return;
        double kilograms = ClientDataCache.INSTANCE.getWeight(stack)
            * Math.max(1, stack.getCount());
        String text = formatBadgeWeight(kilograms);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, ITEM_WEIGHT_BADGE_Z);
        // Cover vanilla stack counts completely while the weight view is active;
        // the replacement badge represents the total weight of the whole stack.
        g.fill(x + 1, y + Math.max(1, height - 10), x + width - 1, y + height - 1,
            0x66080D0F);
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(0.5F, 0.5F, 1.0F);
        int logicalWidth = width * 2;
        int logicalHeight = height * 2;
        int contentWidth = font.width(text) + 10;
        int startX = Math.max(2, logicalWidth - contentWidth - 2);
        int baseline = logicalHeight - 11;
        drawMiniWeightIcon(g, startX, baseline - 1);
        g.drawString(font, text, startX + 9, baseline, 0xFFF1F4F2, false);
        g.pose().popPose();
    }

    private static void drawMiniWeightIcon(GuiGraphics g, int x, int y) {
        int color = 0xFFE8ECEA;
        g.fill(x + 2, y, x + 6, y + 1, color);
        g.fill(x + 1, y + 1, x + 7, y + 3, color);
        g.fill(x, y + 3, x + 8, y + 9, color);
        g.fill(x + 2, y + 5, x + 6, y + 8, 0xD0080D0F);
    }

    private static String formatBadgeWeight(double value) {
        BigDecimal decimal = BigDecimal.valueOf(Math.max(0.0D, value));
        int scale = value < 10.0D ? 1 : 0;
        return decimal.setScale(scale, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString();
    }

    private void drawNamedCurioSlot(GuiGraphics g, String identifier, int x, int y) {
        AccessorySlot accessory = accessorySlot(identifier);
        boolean occupied = !accessory.stack().isEmpty();
        g.fill(x - 1, y - 1, x + 19, y + 19, occupied ? 0xD0222D30 : com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        g.renderOutline(x - 1, y - 1, 20, 20, occupied ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        if (occupied) g.renderItem(accessory.stack(), x, y);
        Component label = occupied ? accessory.stack().getHoverName()
            : Component.translatable("status.xero_delta.empty_slot");
        String text = label.getString();
        int available = STORAGE_WIDTH - 48;
        if (font.width(text) > available) {
            text = font.plainSubstrByWidth(text,
                Math.max(1, available - font.width("..."))) + "...";
        }
        g.drawString(font, text, x + 25, y + 5,
            occupied ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
    }

    private boolean renderAccessoryTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (!menu.getCarried().isEmpty()) return false;
        String identifier = curioIdentifierAt(mouseX, mouseY);
        if (identifier == null) return false;
        AccessorySlot accessory = accessorySlot(identifier);
        if (!accessory.stack().isEmpty()) {
            g.renderTooltip(font, accessory.stack(), mouseX, mouseY);
        }
        return true;
    }

    private String curioIdentifierAt(double mouseX, double mouseY) {
        if (restrictedLayout && !inside(mouseX, mouseY,
            restrictedColumnX(), layoutViewportTop(),
            restrictedColumnWidth(), layoutViewportHeight())) return null;
        for (String identifier : List.of("chest_rig", "backpack", "card_holder")) {
            if (carrierSectionHeight(identifier) <= 0) continue;
            int[] bounds = carrierSlotBounds(identifier);
            if (inside(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3])) {
                return identifier;
            }
        }
        return null;
    }

    private AccessorySlot accessorySlot(String identifier) {
        if (minecraft == null || minecraft.player == null) {
            return new AccessorySlot(identifier, ItemStack.EMPTY);
        }
        return CuriosApi.getCuriosInventory(minecraft.player)
            .flatMap(handler -> handler.getStacksHandler(identifier))
            .filter(handler -> handler.getSlots() > 0)
            .map(handler -> new AccessorySlot(
                identifier, handler.getStacks().getStackInSlot(0)))
            .orElseGet(() -> new AccessorySlot(identifier, ItemStack.EMPTY));
    }

    private record AccessorySlot(String identifier, ItemStack stack) {}

    private static String formatWeight(double value) {
        return BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString() + "KG";
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
