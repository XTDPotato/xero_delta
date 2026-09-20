package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.ClientGridRotation;
import com.xtdpotato.xero_delta.client.DeltaContainerLayoutController;
import com.xtdpotato.xero_delta.client.DeltaGridCellRenderer;
import com.xtdpotato.xero_delta.client.GridItemRenderer;
import com.xtdpotato.xero_delta.client.ItemDetailOverlay;
import com.xtdpotato.xero_delta.client.LootSearchOverlay;
import com.xtdpotato.xero_delta.client.InventoryLayoutScale;
import com.xtdpotato.xero_delta.client.PlayerStatusClientState;
import com.xtdpotato.xero_delta.client.LoadoutSlotBackgroundRenderer;
import com.xtdpotato.xero_delta.client.ScreenLayerResolver;
import com.xtdpotato.xero_delta.client.StorageSectionHeaderRenderer;
import com.xtdpotato.xero_delta.client.TradingUi;
import com.xtdpotato.xero_delta.client.StatusEffectHudState;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import com.xtdpotato.xero_delta.util.SlotFieldUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Locale;
import java.util.Set;

/** Delta-style searchable corpse layout with a smooth independent loot column. */
public final class CorpseScreen extends AbstractContainerScreen<CorpseMenu> {
    private static final int WIDTH = 450;
    private static final int HEIGHT = 326;
    private static final int RIGHT_X = 196;
    private static final int LOOT_CONTENT_WIDTH = 214;
    private static final int VIEW_TOP = 34;
    private static final int PLAYER_CONTENT_BOTTOM = 466;
    private static final int PRIMARY_CARD_WIDTH = 72;
    private static final int LARGE_SLOT = 36;
    private static final int STORAGE_REGION_GAP = 2;
    private static final int POCKET_GAP = 2;
    private static final int POCKET_SECTION_HEIGHT = 50;
    private static final int EMPTY_CARRIER_SECTION_HEIGHT = 64;
    private static final int SECTION_GAP = 6;
    private static final int PRIMARY_ROW_WIDTH = PRIMARY_CARD_WIDTH * 2 + SECTION_GAP;
    private static final int EQUIPMENT_ROW_OFFSET = 26;
    private static final int PRIMARY_BASE_Y = 42;
    private static final int EQUIPMENT_BASE_Y = 84;
    private static final int EARNINGS_BASE_Y = 126;
    private static final int CHEST_RIG_BASE_Y = 164;
    // Corpse and player inventory use the same neutral grid chrome. Red is
    // reserved for danger states and must not change the container geometry.
    private static final ResourceLocation LOCK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "textures/quality/lock.png");

    private double lootScroll;
    private double targetLootScroll;
    private boolean scrollbarDragging;
    private boolean contentDragging;
    private double lastDragY;
    private double playerScroll;
    private double targetPlayerScroll;
    private boolean playerScrollbarDragging;
    private boolean playerContentDragging;
    private double playerLastDragY;
    private String storageHelpTooltipKey;
    private long lastScrollFrameNanos;

    public CorpseScreen(CorpseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        titleLabelX = -1000;
        inventoryLabelX = -1000;
    }

    /** Width occupied by the corpse column when the shared Delta player panel is active. */
    public int embeddedNativeWidth() {
        return lootPanelWidth() + 10;
    }

    @Override
    protected void init() {
        imageHeight = Math.max(HEIGHT, height - 8);
        imageWidth = RIGHT_X + embeddedNativeWidth();
        super.init();
        applyLootSlotPositions();
        applyPlayerSlotPositions();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        imageWidth = RIGHT_X + embeddedNativeWidth();
        storageHelpTooltipKey = null;
        tickScrollAnimation();
        applyLootSlotPositions();
        applyPlayerSlotPositions();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Rendered by the container mixin while the native corpse transform is still active. */
    public void renderPresentationOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        enableLootScissor(graphics, false);
        drawCorpsePresentationHover(graphics, mouseX, mouseY);
        drawLootSlotLabels(graphics);
        graphics.disableScissor();
        Slot corpseSlot = corpsePresentationSlotAt(mouseX - leftPos, mouseY - topPos);
        if (corpseSlot != null && !DeltaContainerLayoutController.isActive(this)
            && !PlayerStatusClientState.INSTANCE.layoutEnabled()) {
            renderCorpsePresentationTooltip(graphics, mouseX, mouseY);
        } else if (!DeltaContainerLayoutController.isActive(this)
            && !PlayerStatusClientState.INSTANCE.layoutEnabled()) {
            renderTooltip(graphics, mouseX, mouseY);
        }
        boolean activeLayout = DeltaContainerLayoutController.isActive(this);
        if (activeLayout) graphics.pose().pushPose();
        try {
            if (activeLayout) {
                graphics.pose().translate(0.0F, 0.0F, ScreenLayerResolver.finalPass());
            }
            StorageSectionHeaderRenderer.renderTooltip(graphics, font,
                storageHelpTooltipKey, mouseX, mouseY);
        } finally {
            if (activeLayout) graphics.pose().popPose();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }


    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (!DeltaContainerLayoutController.isActive(this)) {
            graphics.fill(0, 0, width, height, 0xB0060A0B);
            graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF00A1012);
            graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF64716E);
            drawPlayerPanel(graphics);
        }
        drawLootPanel(graphics, mouseX, mouseY);
    }

    private void drawPlayerPanel(GuiGraphics graphics) {
        int x = leftPos + 8;
        int y = topPos + 10;
        int panelWidth = RIGHT_X - 16;
        graphics.fill(x, y, x + panelWidth, topPos + 164, 0xD50E1618);
        graphics.renderOutline(x, y, panelWidth, 154, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, Component.translatable("container.inventory"),
            x + 8, y + 8, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        for (int index = CorpseMenu.PLAYER_SLOT_START; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.x >= 0 && slot.y >= 0) drawSlotBackground(graphics, slot, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        }
    }

    private void drawPlayerLayout(GuiGraphics graphics, int panelX) {
        int cardX = panelX + 8;
        int equipmentY = playerY(42);
        if (playerVisible(equipmentY, 180)) {
            graphics.fill(cardX, equipmentY, cardX + 266, equipmentY + 180, 0xC40C1315);
            graphics.renderOutline(cardX, equipmentY, 266, 180, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
            graphics.drawString(font, Component.translatable("status.xero_delta.equipment"),
                cardX + 5, equipmentY + 4, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        }

        int primaryX = cardX + 8;
        int primaryY = playerY(52);
        drawPlayerCard(graphics, primaryX, primaryY, 72, 36, "1");
        drawPlayerCard(graphics, primaryX, primaryY + 42, 72, 36, "2");

        int equipmentX = cardX + 94;
        drawPlayerCard(graphics, equipmentX, primaryY, 36, 36,
            Component.translatable("status.xero_delta.helmet_short").getString());
        drawPlayerCard(graphics, equipmentX + 40, primaryY, 36, 36,
            Component.translatable("status.xero_delta.chest_short").getString());
        drawPlayerCard(graphics, equipmentX + 80, primaryY, 36, 36, "3");
        drawPlayerCard(graphics, equipmentX + 120, primaryY, 36, 36, "4");

        if (minecraft != null && minecraft.player != null && playerVisible(primaryY, 36)) {
            ItemStack helmet = minecraft.player.getItemBySlot(EquipmentSlot.HEAD);
            ItemStack chest = minecraft.player.getItemBySlot(EquipmentSlot.CHEST);
            if (!helmet.isEmpty()) renderLargeItem(graphics, helmet,
                equipmentX + 2, primaryY + 2, 2.0F);
            if (!chest.isEmpty()) renderLargeItem(graphics, chest,
                equipmentX + 42, primaryY + 2, 2.0F);
        }

        int pocketsY = playerY(160);
        if (playerVisible(pocketsY, 36)) {
            graphics.drawString(font, Component.translatable("status.xero_delta.pockets"),
                cardX + 8, pocketsY - 11, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
            drawPocketStrip(graphics, cardX + 8, pocketsY, true);
        }

        drawPlayerAccessoryCard(graphics, cardX, playerY(230), "chest_rig",
            Component.translatable("status.xero_delta.chest_rig"));
        drawPlayerAccessoryCard(graphics, cardX, playerY(290), "backpack",
            Component.translatable("status.xero_delta.backpack"));
        drawPlayerAccessoryCard(graphics, cardX, playerY(350), "card_holder",
            Component.translatable("status.xero_delta.card_holder"));
        drawPlayerAccessoryCard(graphics, cardX, playerY(410), "safety_box",
            Component.translatable("status.xero_delta.safety_box"));
    }

    private void drawPlayerCard(GuiGraphics graphics, int x, int y,
                                int width, int height, String label) {
        if (!playerVisible(y, height)) return;
        graphics.fill(x, y, x + width, y + height, 0xC40C1315);
        graphics.renderOutline(x, y, width, height, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, font.plainSubstrByWidth(label,
            Math.max(1, width - 5)), x + 3, y + 3, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
    }

    private void drawPlayerAccessoryCard(GuiGraphics graphics, int x, int y,
                                         String identifier, Component label) {
        if (!playerVisible(y, 48)) return;
        graphics.fill(x, y, x + 266, y + 48, 0xC40C1315);
        graphics.renderOutline(x, y, 266, 48, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, label, x + 7, y + 8, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        ItemStack stack = playerAccessoryStack(identifier);
        int slotX = x + 224;
        int slotY = y + 6;
        graphics.fill(slotX, slotY, slotX + 36, slotY + 36, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        graphics.renderOutline(slotX, slotY, 36, 36, stack.isEmpty() ? com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT : com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
        if (!stack.isEmpty()) renderLargeItem(graphics, stack, slotX + 2, slotY + 2, 2.0F);
    }

    private void drawPocketStrip(GuiGraphics graphics, int x, int y, boolean playerSide) {
        if (playerSide ? !playerVisible(y, 36) : !visible(y, 36)) return;
        graphics.fill(x, y, x + 180, y + 36, 0xC40C1315);
        graphics.renderOutline(x, y, 180, 36, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        for (int index = 0; index < 5; index++) {
            if (index > 0) graphics.fill(x + index * 36, y + 1,
                x + index * 36 + 1, y + 35, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
            graphics.drawString(font, String.valueOf(index + 5),
                x + index * 36 + 3, y + 3, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        }
    }

    private ItemStack playerAccessoryStack(String identifier) {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        try {
            return CuriosApi.getCuriosInventory(minecraft.player)
                .flatMap(handler -> handler.getStacksHandler(identifier))
                .filter(handler -> handler.getSlots() > 0)
                .map(handler -> handler.getStacks().getStackInSlot(0))
                .orElse(ItemStack.EMPTY);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private void drawLootPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + lootPanelLocalX();
        int y = topPos + 10;
        int panelWidth = lootPanelWidth();
        graphics.fill(x, y, x + panelWidth, topPos + imageHeight - 10, 0xE0121516);
        graphics.renderOutline(x, y, panelWidth, imageHeight - 20, Material3Theme.OUTLINE_VARIANT);
        CorpseEntity corpse = corpse();
        Component owner = corpse == null ? title : Component.translatable(
            "corpse.xero_delta.searching", corpse.ownerName());
        graphics.drawString(font, owner, x + 9, y + 7, 0xFFFFC6C0, false);

        enableLootScissor(graphics, false);
        boolean insideView = insideLootViewport(mouseX - leftPos, mouseY - topPos);
        drawLootSections(graphics, x, insideView ? mouseX : Integer.MIN_VALUE,
            insideView ? mouseY : Integer.MIN_VALUE);
        for (int index = 0; index < CorpseMenu.CORPSE_SLOTS; index++) {
            Slot slot = menu.slots.get(index);
            if (slot.x < 0 || slot.y < 0) continue;
            if (corpsePresentationBounds(slot) != null) continue;
            drawSlotBackground(graphics, slot, Material3Theme.OUTLINE_VARIANT);
        }
        graphics.disableScissor();
        drawLootScrollbar(graphics, x + panelWidth - 7);
    }

    private void drawLootSections(GuiGraphics graphics, int panelX, int mouseX, int mouseY) {
        int cardX = panelX + 16;
        int primaryX = cardX + primaryRowOffset();
        drawSectionCard(graphics, primaryX, lootY(PRIMARY_BASE_Y),
            PRIMARY_CARD_WIDTH, LARGE_SLOT, "");
        drawSectionCard(graphics, primaryX + PRIMARY_CARD_WIDTH + SECTION_GAP,
            lootY(PRIMARY_BASE_Y), PRIMARY_CARD_WIDTH, LARGE_SLOT, "");
        drawEmptyLoadoutBackground(graphics, primaryX, lootY(PRIMARY_BASE_Y),
            PRIMARY_CARD_WIDTH, LARGE_SLOT, CorpseMenu.PRIMARY_ONE_SLOT,
            LoadoutSlotBackgroundRenderer.Kind.PRIMARY);
        drawEmptyLoadoutBackground(graphics, primaryX + PRIMARY_CARD_WIDTH + SECTION_GAP,
            lootY(PRIMARY_BASE_Y), PRIMARY_CARD_WIDTH, LARGE_SLOT,
            CorpseMenu.PRIMARY_TWO_SLOT, LoadoutSlotBackgroundRenderer.Kind.PRIMARY);

        int equipmentY = lootY(EQUIPMENT_BASE_Y);
        int equipmentX = cardX + EQUIPMENT_ROW_OFFSET;
        drawSectionCard(graphics, equipmentX, equipmentY, LARGE_SLOT, LARGE_SLOT, "");
        drawSectionCard(graphics, equipmentX + 42, equipmentY, LARGE_SLOT, LARGE_SLOT, "");
        drawSectionCard(graphics, equipmentX + 84, equipmentY, LARGE_SLOT, LARGE_SLOT, "");
        drawSectionCard(graphics, equipmentX + 126, equipmentY, LARGE_SLOT, LARGE_SLOT, "");
        drawEmptyLoadoutBackground(graphics, equipmentX, equipmentY,
            LARGE_SLOT, LARGE_SLOT, CorpseMenu.HELMET_SLOT,
            LoadoutSlotBackgroundRenderer.Kind.HELMET);
        drawEmptyLoadoutBackground(graphics, equipmentX + 42, equipmentY,
            LARGE_SLOT, LARGE_SLOT, CorpseMenu.CHEST_SLOT,
            LoadoutSlotBackgroundRenderer.Kind.CHESTPLATE);
        drawEmptyLoadoutBackground(graphics, equipmentX + 84, equipmentY,
            LARGE_SLOT, LARGE_SLOT, CorpseMenu.SIDEARM_SLOT,
            LoadoutSlotBackgroundRenderer.Kind.SIDEARM);
        drawKnifeLockedBackground(graphics, equipmentX + 126, equipmentY, LARGE_SLOT, LARGE_SLOT);
        CorpseEntity corpse = corpse();
        if (corpse != null && !corpse.knifeItem().isEmpty()
            && visible(equipmentY, LARGE_SLOT)) {
            GridItemRenderer.renderSizedItem(graphics, font, corpse.knifeItem(),
                equipmentX + 126, equipmentY, LARGE_SLOT, LARGE_SLOT,
                GridBackingStore.isRotated(corpse.knifeItem()),
                ClientDataCache.INSTANCE.shouldRotateTexture(corpse.knifeItem()),
                ClientDataCache.INSTANCE.shouldStretchTexture(corpse.knifeItem()),
                ClientDataCache.INSTANCE.proportionalTextureScale(corpse.knifeItem()));
            DeltaContainerLayoutController.renderExternalItemWeightBadge(this, graphics,
                corpse.knifeItem(), equipmentX + 126, equipmentY, LARGE_SLOT, LARGE_SLOT);
        }

        int earningsY = lootY(EARNINGS_BASE_Y);
        if (visible(earningsY, 30)) {
            long earnings = menu.raidEarnings();
            graphics.fill(cardX, earningsY, cardX + lootContentWidth(),
                earningsY + 30, 0xCE241C1B);
            graphics.renderOutline(cardX, earningsY, lootContentWidth(), 30,
                earnings > 0L ? 0xFFFFC24F : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
            TradingUi.drawDetailedAmount(graphics, font, earnings,
                cardX + 8, earningsY + 10, earnings > 0L ? 0xFFFFD477 : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
            int buttonWidth = 96;
            int buttonX = cardX + lootContentWidth() - buttonWidth - 5;
            boolean enabled = earnings > 1L;
            boolean hovered = enabled && inside(mouseX, mouseY,
                buttonX, earningsY + 5, buttonWidth, 20);
            graphics.fill(buttonX, earningsY + 5, buttonX + buttonWidth, earningsY + 25,
                !enabled ? 0x77303938 : hovered ? 0xDD2C5C4C : 0xBB1D4035);
            graphics.renderOutline(buttonX, earningsY + 5, buttonWidth, 20,
                !enabled ? com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT : hovered ? 0xFFF1F6F3 : com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
            graphics.drawCenteredString(font,
                Component.translatable("corpse.xero_delta.claim_raid_earnings"),
                buttonX + buttonWidth / 2, earningsY + 11, enabled ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
        }

        drawCorpseCarrierSection(graphics, cardX, CHEST_RIG_BASE_Y,
            CorpseMenu.CHEST_RIG_SLOT, Component.translatable("status.xero_delta.chest_rig"),
            "storage_help.xero_delta.chest_rig", mouseX, mouseY);
        drawCorpsePocketSection(graphics, cardX, corpsePocketsBaseY(), mouseX, mouseY);
        drawCorpseCarrierSection(graphics, cardX, corpseBackpackBaseY(),
            CorpseMenu.BACKPACK_SLOT, Component.translatable("status.xero_delta.backpack"),
            "storage_help.xero_delta.backpack", mouseX, mouseY);
    }

    private void drawCorpsePocketSection(GuiGraphics graphics, int x, int baseY,
                                         int mouseX, int mouseY) {
        int y = lootY(baseY);
        if (!visible(y, pocketSectionHeight())) return;
        graphics.fill(x, y, x + lootContentWidth(), y + pocketSectionHeight(), 0xC40C1315);
        graphics.renderOutline(x, y, lootContentWidth(), pocketSectionHeight(), com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        int used = 0;
        for (int index = CorpseMenu.POCKET_START; index <= CorpseMenu.POCKET_END; index++) {
            if (!menu.slots.get(index).getItem().isEmpty()) used++;
        }
        captureStorageHelp(StorageSectionHeaderRenderer.render(graphics, font,
            Component.translatable("status.xero_delta.pockets"),
            used, CorpseMenu.POCKET_END - CorpseMenu.POCKET_START + 1,
            "storage_help.xero_delta.pockets",
            x + 4, y + 5, 1.0F, mouseX, mouseY));
        int slotY = y + 24;
        for (int index = 0; index < 5; index++) {
            int cellSize = storageCell();
            int slotX = x + 6 + index * (cellSize + POCKET_GAP);
            graphics.fill(slotX, slotY, slotX + cellSize, slotY + cellSize,
                com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
            graphics.renderOutline(slotX, slotY, cellSize, cellSize, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        }

    }

    private void drawCorpseCarrierSection(GuiGraphics graphics, int x, int baseY,
                                          int slotIndex, Component title,
                                          String helpKey, int mouseX, int mouseY) {
        int sectionHeight = corpseCarrierSectionHeight(slotIndex);
        int y = lootY(baseY);
        if (!visible(y, sectionHeight)) return;
        graphics.fill(x, y, x + lootContentWidth(), y + sectionHeight, 0xC40C1315);
        graphics.renderOutline(x, y, lootContentWidth(), sectionHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        ItemStack carrier = menu.slots.get(slotIndex).getItem();
        GridBackingStore headerStore = carrier.getItem() instanceof DeltaPackItem headerPack
            ? new GridBackingStore(carrier, headerPack.gridWidth(), headerPack.gridHeight())
            : null;
        int used = StorageSectionHeaderRenderer.usedCells(headerStore);
        int total = headerStore == null ? 0 : headerStore.getWidth() * headerStore.getHeight();
        captureStorageHelp(StorageSectionHeaderRenderer.render(graphics, font, title,
            used, total, helpKey, x + 4, y + 5, 1.0F, mouseX, mouseY));
        graphics.fill(x, y + 17, x + lootContentWidth(), y + 18, 0xFF283332);

        int slotX = x + 6;
        int slotY = y + 22;
        graphics.fill(slotX, slotY, slotX + LARGE_SLOT, slotY + LARGE_SLOT, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        graphics.renderOutline(slotX, slotY, LARGE_SLOT, LARGE_SLOT,
            carrier.isEmpty() ? com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT : com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
        if (!(carrier.getItem() instanceof DeltaPackItem pack)) return;

        GridBackingStore store = new GridBackingStore(carrier,
            pack.gridWidth(), pack.gridHeight());
        int cellSize = storageCell();
        PackRegionLayout layout = new PackRegionLayout(pack,
            cellSize, STORAGE_REGION_GAP);
        int[] origin = corpseStorageGridOrigin(slotIndex);
        renderCorpseStorageGrid(graphics, store, layout,
            new int[]{leftPos + origin[0], topPos + origin[1]});
    }

    private void renderCorpseStorageGrid(GuiGraphics graphics, GridBackingStore store,
                                         PackRegionLayout layout, int[] origin) {
        int gridX = origin[0];
        int gridY = origin[1];
        int cellSize = storageCell();
        for (PackRegionLayout.Rect region : layout.regionBounds()) {
            for (int localY = 0; localY < region.height(); localY += cellSize) {
                for (int localX = 0; localX < region.width(); localX += cellSize) {
                    int x = gridX + region.x() + localX;
                    int y = gridY + region.y() + localY;
                    DeltaGridCellRenderer.render(graphics, x, y, cellSize);
                }
            }
            graphics.renderOutline(gridX + region.x(), gridY + region.y(),
                region.width(), region.height(), 0xFF6A8079);
        }
        for (int index = 0; index < store.getSize(); index++) {
            ItemStack stack = store.getItemRaw(index % store.getWidth(), index / store.getWidth());
            if (stack.isEmpty()) continue;
            PackRegionLayout.Rect bounds = corpseStorageItemBounds(layout, store, index, stack);
            if (bounds == null) continue;
            int itemX = gridX + bounds.x();
            int itemY = gridY + bounds.y();
            GridItemRenderer.renderSizedItem(graphics, font, stack,
                itemX, itemY, bounds.width(), bounds.height(),
                GridBackingStore.isRotated(stack),
                ClientDataCache.INSTANCE.shouldRotateTexture(stack),
                ClientDataCache.INSTANCE.shouldStretchTexture(stack),
                ClientDataCache.INSTANCE.proportionalTextureScale(stack));
            graphics.renderOutline(itemX, itemY, bounds.width(), bounds.height(), com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
            DeltaContainerLayoutController.renderExternalItemWeightBadge(this, graphics,
                stack, itemX, itemY, bounds.width(), bounds.height());
        }
    }

    /** Uses the exact same footprint calculation for paint, hit testing and overlays. */
    private static PackRegionLayout.Rect corpseStorageItemBounds(
        PackRegionLayout layout, GridBackingStore store, int index, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        int column = index % store.getWidth();
        int row = index / store.getWidth();
        var size = GridBackingStore.sizeOfStored(stack);
        return layout.footprintBounds(column, row, size.width(), size.height());
    }

    private void drawSectionCard(GuiGraphics graphics, int x, int y,
                                 int width, int height, String label) {
        if (!visible(y, height)) return;
        graphics.fill(x, y, x + width, y + height, 0xC40C1315);
        graphics.renderOutline(x, y, width, height, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, font.plainSubstrByWidth(label,
            Math.max(1, width - 5)), x + 3, y + 3, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
    }

    /** Draws semantic corpse slots at their full Delta card size. */
    public boolean renderCorpsePresentationSlot(GuiGraphics graphics, Slot slot) {
        if (!isPresentationSlot(slot)) return false;
        int[] bounds = corpsePresentationBounds(slot);
        if (bounds == null || slot.getItem().isEmpty()) return true;
        enableLootScissor(graphics, true);
        try {
            if (LootSearchOverlay.renderHiddenSlot(this, graphics, slot)) return true;
            ItemStack stack = slot.getItem();
            GridItemRenderer.renderSizedItem(graphics, font, stack,
                bounds[0], bounds[1], bounds[2], bounds[3],
                GridBackingStore.isRotated(stack),
                ClientDataCache.INSTANCE.shouldRotateTexture(stack),
                ClientDataCache.INSTANCE.shouldStretchTexture(stack),
                ClientDataCache.INSTANCE.proportionalTextureScale(stack));
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 350);
            graphics.renderOutline(bounds[0], bounds[1], bounds[2], bounds[3], com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
            graphics.pose().popPose();
            DeltaContainerLayoutController.renderExternalItemWeightBadge(this, graphics,
                stack, bounds[0], bounds[1], bounds[2], bounds[3]);
            return true;
            } finally {
            graphics.disableScissor();
        }
    }

    /** Local GUI bounds used by rendering, hit testing and the loot-search mask. */
    public int[] corpsePresentationBounds(Slot slot) {
        if (!isPresentationSlot(slot)) return null;
        int x = lootPanelLocalX() + 16;
        int y;
        int width;
        int height;
        switch (slot.index) {
            case CorpseMenu.PRIMARY_ONE_SLOT -> {
                x += primaryRowOffset();
                y = PRIMARY_BASE_Y;
                width = PRIMARY_CARD_WIDTH;
                height = LARGE_SLOT;
            }
            case CorpseMenu.PRIMARY_TWO_SLOT -> {
                x += primaryRowOffset() + PRIMARY_CARD_WIDTH + SECTION_GAP;
                y = PRIMARY_BASE_Y;
                width = PRIMARY_CARD_WIDTH;
                height = LARGE_SLOT;
            }
            case CorpseMenu.HELMET_SLOT -> {
                x += EQUIPMENT_ROW_OFFSET;
                y = EQUIPMENT_BASE_Y;
                width = LARGE_SLOT;
                height = LARGE_SLOT;
            }
            case CorpseMenu.CHEST_SLOT -> {
                x += EQUIPMENT_ROW_OFFSET + 42;
                y = EQUIPMENT_BASE_Y;
                width = LARGE_SLOT;
                height = LARGE_SLOT;
            }
            case CorpseMenu.SIDEARM_SLOT -> {
                x += EQUIPMENT_ROW_OFFSET + 84;
                y = EQUIPMENT_BASE_Y;
                width = LARGE_SLOT;
                height = LARGE_SLOT;
            }
            case CorpseMenu.CHEST_RIG_SLOT -> {
                x += 6;
                y = CHEST_RIG_BASE_Y + 22;
                width = LARGE_SLOT;
                height = LARGE_SLOT;
            }
            case CorpseMenu.BACKPACK_SLOT -> {
                x += 6;
                y = corpseBackpackBaseY() + 22;
                width = LARGE_SLOT;
                height = LARGE_SLOT;
            }

            default -> {
                if (slot.index < CorpseMenu.POCKET_START
                    || slot.index > CorpseMenu.POCKET_END) return null;
                int cellSize = storageCell();
                x += 6 + (slot.index - CorpseMenu.POCKET_START)
                    * (cellSize + POCKET_GAP);
                y = corpsePocketsBaseY() + 24;
                width = cellSize;
                height = cellSize;
            }
        }
        y -= (int) Math.round(lootScroll);
        if (y + height < VIEW_TOP || y > viewBottom()) return null;
        return new int[]{x, y, width, height};
    }

    public Slot corpsePresentationSlotAt(double localX, double localY) {
        if (!insideLootViewport(localX, localY)) return null;
        for (int index = 0; index <= CorpseMenu.BACKPACK_SLOT; index++) {
            Slot slot = menu.slots.get(index);
            int[] bounds = corpsePresentationBounds(slot);
            if (bounds != null && inside(localX, localY,
                bounds[0], bounds[1], bounds[2], bounds[3])) return slot;
        }
        return null;
    }

    /** Suppresses vanilla's 16 px highlight for a custom, full-size corpse card. */
    public boolean suppressesVanillaSlotHighlight(int slotX, int slotY) {
        for (int index = 0; index <= CorpseMenu.BACKPACK_SLOT; index++) {
            Slot slot = menu.slots.get(index);
            int[] bounds = corpsePresentationBounds(slot);
            if (bounds != null && slot.x == slotX && slot.y == slotY) return true;
        }
        return false;
    }

    /** Hit result for an item or empty cell inside a worn corpse carrier. */
    public CorpseStorageTarget corpseStorageTargetAt(double localX, double localY) {
        if (!insideLootViewport(localX, localY)) return null;
        for (int carrierSlot : new int[]{CorpseMenu.CHEST_RIG_SLOT,
            CorpseMenu.BACKPACK_SLOT}) {
            ItemStack carrier = menu.slots.get(carrierSlot).getItem();
            if (!(carrier.getItem() instanceof DeltaPackItem pack)) continue;
            int[] origin = corpseStorageGridOrigin(carrierSlot);
            int gridX = origin[0];
            int gridY = origin[1];
            int cellSize = storageCell();
            PackRegionLayout layout = new PackRegionLayout(pack,
                cellSize, STORAGE_REGION_GAP);
            PackRegionLayout.Cell cell = layout.cellAt(localX - gridX, localY - gridY);
            if (cell == null) continue;
            GridBackingStore store = new GridBackingStore(carrier,
                pack.gridWidth(), pack.gridHeight(), 0,
                stack -> GridBackingStore.isBlockedInEquippedStorage(
                    pack.slotIdentifier(), stack));
            int anchor = store.findAnchorIndexAt(cell.column(), cell.row());
            ItemStack stack = anchor < 0 ? ItemStack.EMPTY
                : store.getItemRaw(anchor % store.getWidth(), anchor / store.getWidth()).copy();
            PackRegionLayout.Rect bounds = anchor < 0
                ? layout.cellBounds(cell.column(), cell.row())
                : corpseStorageItemBounds(layout, store, anchor, stack);
            if (bounds == null) continue;
            return new CorpseStorageTarget(carrierSlot,
                cell.row() * store.getWidth() + cell.column(), anchor, stack,
                leftPos + gridX + bounds.x(), topPos + gridY + bounds.y(),
                bounds.width(), bounds.height(), store, layout,
                (localX - gridX - layout.cellBounds(cell.column(), cell.row()).x())
                    / cellSize,
                (localY - gridY - layout.cellBounds(cell.column(), cell.row()).y())
                    / cellSize);
        }
        return null;
    }

    /** Draws the destination footprint for cross-panel and corpse-storage drags. */
    public boolean renderCarriedPlacementPreview(GuiGraphics graphics,
                                                  double localX, double localY,
                                                  ItemStack carried,
                                                  CorpseStorageTarget source) {
        if (carried == null || carried.isEmpty()) return false;
        CorpseStorageTarget target = corpseStorageTargetAt(localX, localY);
        if (target != null) {
            Set<Integer> ignoredAnchors = source != null
                && source.carrierSlot() == target.carrierSlot()
                && source.anchor() >= 0
                    ? Set.of(source.anchor()) : Set.of();
            GridBackingStore.PlacementResult placement = target.store()
                .resolveCursorPlacement(
                    target.cell() % target.store().getWidth(),
                    target.cell() / target.store().getWidth(), carried,
                    target.fractionX(), target.fractionY(),
                    ClientGridRotation.allowAutoRotate(), ignoredAnchors);
            var size = GridBackingStore.orientedSize(carried, placement.rotated());
            PackRegionLayout.Rect rect = target.layout().footprintBounds(
                placement.x(), placement.y(), size.width(), size.height());
            if (rect == null) {
                rect = target.layout().cellBounds(
                    target.cell() % target.store().getWidth(),
                    target.cell() / target.store().getWidth());
            }
            if (rect != null) {
                int[] origin = corpseStorageGridOrigin(target.carrierSlot());
                drawPlacementPreview(graphics,
                    leftPos + origin[0] + rect.x(),
                    topPos + origin[1] + rect.y(),
                    rect.width(), rect.height(), placement.isAccepted());
            }
            return true;
        }

        Slot slot = corpsePresentationSlotAt(localX, localY);
        if (slot == null) return false;
        int[] bounds = corpsePresentationBounds(slot);
        if (bounds == null) return false;
        drawPlacementPreview(graphics, leftPos + bounds[0], topPos + bounds[1],
            bounds[2], bounds[3], slot.mayPlace(carried));
        return true;
    }

    private int[] corpseStorageGridOrigin(int carrierSlot) {
        int baseY = carrierSlot == CorpseMenu.CHEST_RIG_SLOT
            ? CHEST_RIG_BASE_Y : corpseBackpackBaseY();
        return new int[]{
            lootPanelLocalX() + 16 + 6 + LARGE_SLOT + SECTION_GAP,
            baseY + 22 - (int) Math.round(lootScroll)
        };
    }

    private void drawPlacementPreview(GuiGraphics graphics, int x, int y,
                                             int width, int height,
                                             boolean accepted) {
        int fill = accepted ? 0x8849D79A : 0x88E05252;
        int border = accepted ? 0xFF6FE8B2 : 0xFFFF6767;
        enableLootScissor(graphics, false);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 720.0F);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        graphics.renderOutline(x, y, width, height, border);
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    public record CorpseStorageTarget(int carrierSlot, int cell, int anchor,
                                      ItemStack stack, int x, int y,
                                      int width, int height,
                                      GridBackingStore store,
                                      PackRegionLayout layout,
                                      double fractionX, double fractionY) {
        public String sourceId(int corpseId) {
            return "corpse_storage|" + corpseId + "|" + carrierSlot + "|" + anchor;
        }
    }

    private static boolean isPresentationSlot(Slot slot) {
        return slot != null && slot.index >= CorpseMenu.PRIMARY_ONE_SLOT
            && slot.index <= CorpseMenu.BACKPACK_SLOT;
    }

    private void drawCorpsePresentationHover(GuiGraphics graphics,
                                               int mouseX, int mouseY) {
        Slot slot = corpsePresentationSlotAt(mouseX - leftPos, mouseY - topPos);
        if (slot == null || LootSearchOverlay.isHiddenSlot(this, slot)) return;
        int[] bounds = corpsePresentationBounds(slot);
        if (bounds == null) return;
        int x = leftPos + bounds[0];
        int y = topPos + bounds[1];
        ItemStack stack = slot.getItem();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.fill(x, y, x + bounds[2], y + bounds[3], com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        if (!stack.isEmpty()) {
            GridItemRenderer.renderSizedItem(graphics, font, stack,
                x, y, bounds[2], bounds[3],
                GridBackingStore.isRotated(stack),
                ClientDataCache.INSTANCE.shouldRotateTexture(stack),
                ClientDataCache.INSTANCE.shouldStretchTexture(stack),
                ClientDataCache.INSTANCE.proportionalTextureScale(stack));
            DeltaContainerLayoutController.renderExternalItemWeightBadge(this, graphics,
                stack, x, y, bounds[2], bounds[3]);
        }
        graphics.fill(x, y, x + bounds[2], y + bounds[3], 0x339FA8A6);
        graphics.renderOutline(x, y, bounds[2], bounds[3], 0xFFE8EEEC);
        graphics.pose().popPose();
    }

    private void renderCorpsePresentationTooltip(GuiGraphics graphics,
                                                 int mouseX, int mouseY) {
        Slot slot = corpsePresentationSlotAt(mouseX - leftPos, mouseY - topPos);
        if (slot != null && !slot.getItem().isEmpty()) {
            graphics.renderTooltip(font, slot.getItem(), mouseX, mouseY);
        }
    }


    private void drawEmptyLoadoutBackground(GuiGraphics graphics, int x, int y,
                                            int width, int height, int slotIndex,
                                            LoadoutSlotBackgroundRenderer.Kind kind) {
        if (!visible(y, height) || slotIndex < 0 || slotIndex >= menu.slots.size()
            || !menu.slots.get(slotIndex).getItem().isEmpty()) return;
        LoadoutSlotBackgroundRenderer.render(graphics, x, y, width, height, kind);
    }

    private void drawKnifeLockedBackground(GuiGraphics graphics, int x, int y,
                                           int width, int height) {
        if (!visible(y, height)) return;
        for (int step = 2; step < Math.min(width, height) - 2; step++) {
            graphics.fill(x + step, y + step, x + step + 1, y + step + 2, 0x887D8987);
        }
        int lockSize = Math.min(11, Math.max(7, width / 4));
        graphics.blit(LOCK_TEXTURE, x + 3, y + height - lockSize - 3,
            0, 0, lockSize, lockSize, 16, 16);
    }
    private void captureStorageHelp(String helpKey) {
        if (helpKey != null) storageHelpTooltipKey = helpKey;
    }

    private void drawLootSlotLabels(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        drawPresentationLabel(graphics, CorpseMenu.PRIMARY_ONE_SLOT, "1");
        drawPresentationLabel(graphics, CorpseMenu.PRIMARY_TWO_SLOT, "2");

        graphics.pose().popPose();
    }

    private void drawPresentationLabel(GuiGraphics graphics, int slotIndex, String label) {
        int[] bounds = corpsePresentationBounds(menu.slots.get(slotIndex));
        if (bounds == null) return;
        com.xtdpotato.xero_delta.client.LoadoutLabelRenderer.renderKeyCap(
            graphics, font, label, leftPos + bounds[0] + 3,
            topPos + bounds[1] + 2, 1.0F);
    }

    private int lootYLocal(int baseY) {
        return baseY - (int) Math.round(lootScroll);
    }

    private void drawSlotBackground(GuiGraphics graphics, Slot slot, int color) {
        int x = leftPos + slot.x - 1;
        int y = topPos + slot.y - 1;
        graphics.fill(x, y, x + 18, y + 18, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        graphics.renderOutline(x, y, 18, 18, color);
    }

    private void drawLootScrollbar(GuiGraphics graphics, int x) {
        int top = topPos + VIEW_TOP;
        int height = viewBottom() - VIEW_TOP;
        graphics.fill(x, top, x + 3, top + height, 0xFF1A2224);
        double max = maxScroll();
        int thumbHeight = Math.min(height, Math.max(24,
            (int) Math.round(height * height / (double) (lootContentBottom() - VIEW_TOP))));
        int thumbY = top + (int) Math.round((height - thumbHeight)
            * lootScroll / Math.max(1.0D, max));
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
    }

    private void drawPlayerScrollbar(GuiGraphics graphics, int x) {
        int top = topPos + VIEW_TOP;
        int height = viewBottom() - VIEW_TOP;
        graphics.fill(x, top, x + 3, top + height, 0xFF1A2224);
        double max = playerMaxScroll();
        int thumbHeight = Math.max(24,
            (int) Math.round(height * height / (double) (PLAYER_CONTENT_BOTTOM - VIEW_TOP)));
        int thumbY = top + (int) Math.round((height - thumbHeight)
            * playerScroll / Math.max(1.0D, max));
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
    }

    private void applyPlayerSlotPositions() {
        if (DeltaContainerLayoutController.isActive(this)) return;
        int playerMainStart = CorpseMenu.PLAYER_SLOT_START;
        for (int index = 0; index < 27; index++) {
            Slot slot = menu.slots.get(playerMainStart + index);
            SlotFieldUtil.setX(slot, 18 + index % 9 * 18);
            SlotFieldUtil.setY(slot, 48 + index / 9 * 18);
        }
        int hotbarStart = playerMainStart + 27;
        for (int index = 0; index < 9; index++) {
            Slot slot = menu.slots.get(hotbarStart + index);
            SlotFieldUtil.setX(slot, 18 + index * 18);
            SlotFieldUtil.setY(slot, 112);
        }
    }

    private void renderPlayerLayoutTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null || minecraft == null || minecraft.player == null
            || !inside(mouseX, mouseY, leftPos + 10, topPos + VIEW_TOP,
                RIGHT_X - 20, viewBottom() - VIEW_TOP)) return;

        int equipmentX = leftPos + 112;
        int equipmentY = playerY(52);
        ItemStack stack = ItemStack.EMPTY;
        if (insideVisiblePlayerRect(mouseX, mouseY, equipmentX, equipmentY, 36, 36)) {
            stack = minecraft.player.getItemBySlot(EquipmentSlot.HEAD);
        } else if (insideVisiblePlayerRect(mouseX, mouseY,
            equipmentX + 40, equipmentY, 36, 36)) {
            stack = minecraft.player.getItemBySlot(EquipmentSlot.CHEST);
        } else {
            String[] identifiers = {"chest_rig", "backpack", "card_holder", "safety_box"};
            int[] baseY = {230, 290, 350, 410};
            for (int index = 0; index < identifiers.length; index++) {
                int x = leftPos + 242;
                int y = playerY(baseY[index]) + 6;
                if (!insideVisiblePlayerRect(mouseX, mouseY, x, y, 36, 36)) continue;
                stack = playerAccessoryStack(identifiers[index]);
                break;
            }
        }
        if (!stack.isEmpty()) graphics.renderTooltip(font, stack, mouseX, mouseY);
    }

    private boolean insideVisiblePlayerRect(double mouseX, double mouseY,
                                            int x, int y, int width, int height) {
        return y >= topPos + VIEW_TOP && y + height <= topPos + viewBottom()
            && inside(mouseX, mouseY, x, y, width, height);
    }

    private void applyLootSlotPositions() {
        for (int index = 0; index < CorpseMenu.CORPSE_SLOTS; index++) {
            Slot slot = menu.slots.get(index);
            if (index >= CorpseMenu.RESERVED_CORPSE_SLOT) {
                SlotFieldUtil.setX(slot, -1000);
                SlotFieldUtil.setY(slot, -1000);
                continue;
            }
            int[] bounds = corpsePresentationBounds(slot);
            if (bounds == null) {
                SlotFieldUtil.setX(slot, -1000);
                SlotFieldUtil.setY(slot, -1000);
                continue;
            }
            int y = bounds[1];
            if (y < VIEW_TOP || y + 16 > viewBottom()) {
                SlotFieldUtil.setX(slot, -1000);
                SlotFieldUtil.setY(slot, -1000);
            } else {
                SlotFieldUtil.setX(slot, bounds[0] + Math.max(0, (bounds[2] - 16) / 2));
                SlotFieldUtil.setY(slot, y + Math.max(0, (bounds[3] - 16) / 2));
            }
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
    }

    private void tickScrollAnimation() {
        targetLootScroll = clampScroll(targetLootScroll);
        lootScroll = clampScroll(lootScroll);
        long now = System.nanoTime();
        if (lastScrollFrameNanos == 0L) lastScrollFrameNanos = now;
        double elapsed = Math.min(0.10D, Math.max(0.0D,
            (now - lastScrollFrameNanos) / 1_000_000_000.0D));
        lastScrollFrameNanos = now;
        double blend = 1.0D - Math.exp(-elapsed * 18.0D);
        if (!scrollbarDragging && !contentDragging) {
            lootScroll += (targetLootScroll - lootScroll) * blend;
            if (Math.abs(targetLootScroll - lootScroll) < 0.02D) lootScroll = targetLootScroll;
        }
        if (!playerScrollbarDragging && !playerContentDragging) {
            playerScroll += (targetPlayerScroll - playerScroll) * blend;
            if (Math.abs(targetPlayerScroll - playerScroll) < 0.02D) {
                playerScroll = targetPlayerScroll;
            }
        }
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        double localX = nativeLocalX(mouseX);
        double localY = nativeLocalY(mouseY);
        
        if (inside(localX, localY, lootPanelLocalX(), VIEW_TOP,
            lootPanelWidth(), viewBottom() - VIEW_TOP)) {
            targetLootScroll = clampScroll(targetLootScroll
                + (scrollY > 0.0D ? -32.0D : scrollY < 0.0D ? 32.0D : 0.0D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localX = nativeLocalX(mouseX);
        double localY = nativeLocalY(mouseY);
        double nativeX = localX + leftPos;
        double nativeY = localY + topPos;
        int earningsY = lootY(EARNINGS_BASE_Y);
        int buttonWidth = 96;
        int buttonX = leftPos + lootPanelLocalX() + 16
            + lootContentWidth() - buttonWidth - 5;
        if (button == 0 && menu.raidEarnings() > 1L
            && insideLootViewport(localX, localY)
            && inside(nativeX, nativeY, buttonX, earningsY + 5, buttonWidth, 20)) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(
                    menu.containerId, CorpseMenu.CLAIM_RAID_EARNINGS);
            }
            return true;
        }

        // Let the shared Delta controller own corpse slots as external container
        // slots so detail popups, drag gestures and selected-item moves use the
        // same path as every other container.
        if (DeltaContainerLayoutController.mouseClicked(this, mouseX, mouseY, button)) {
            return true;
        }
        Slot presentationSlot = corpsePresentationSlotAt(localX, localY);
        if (presentationSlot != null && (button == 0 || button == 1)
            && !menu.getCarried().isEmpty()) {
            slotClicked(presentationSlot, presentationSlot.index, button,
                hasShiftDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP);
            return true;
        }
        if (presentationSlot != null && button == 0
            && !presentationSlot.getItem().isEmpty()) {
            int[] bounds = corpsePresentationBounds(presentationSlot);
            if (bounds != null) {
                int boundsX = leftPos + bounds[0];
                int boundsY = topPos + bounds[1];
                int right = boundsX + bounds[2];
                int bottom = boundsY + bounds[3];
                int screenX = DeltaContainerLayoutController.nativeScreenX(this, boundsX);
                int screenY = DeltaContainerLayoutController.nativeScreenY(this, boundsY);
                ItemDetailOverlay.open(this, presentationSlot.getItem(), false,
                    "container|" + presentationSlot.index,
                    screenX, screenY,
                    Math.max(1, DeltaContainerLayoutController.nativeScreenX(this, right) - screenX),
                    Math.max(1, DeltaContainerLayoutController.nativeScreenY(this, bottom) - screenY));
                return true;
            }
        }
        int scrollbarX = leftPos + lootPanelLocalX() + lootPanelWidth() - 7;
        if (button == 0 && inside(nativeX, nativeY, scrollbarX,
            topPos + VIEW_TOP, 7, viewBottom() - VIEW_TOP)) {
            scrollbarDragging = true;
            updateScrollbar(nativeY);
            return true;
        }
        if (button == 0 && inside(nativeX, nativeY, leftPos + lootPanelLocalX(),
            topPos + VIEW_TOP, lootPanelWidth() - 8, viewBottom() - VIEW_TOP)
            && !overCorpseSlot(mouseX, mouseY)) {
            contentDragging = true;
            lastDragY = nativeY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        double nativeY = DeltaContainerLayoutController.nativeMouseY(this, mouseY);
        if (button == 0 && playerScrollbarDragging) {
            updatePlayerScrollbar(nativeY);
            return true;
        }
        if (button == 0 && playerContentDragging) {
            double delta = nativeY - playerLastDragY;
            playerLastDragY = nativeY;
            playerScroll = clampPlayerScroll(playerScroll - delta);
            targetPlayerScroll = playerScroll;
            applyPlayerSlotPositions();
            return true;
        }
        if (button == 0 && scrollbarDragging) {
            updateScrollbar(nativeY);
            return true;
        }
        if (button == 0 && contentDragging) {
            double delta = nativeY - lastDragY;
            lastDragY = nativeY;
            lootScroll = clampScroll(lootScroll - delta);
            targetLootScroll = lootScroll;
            applyLootSlotPositions();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double localX = nativeLocalX(mouseX);
        double localY = nativeLocalY(mouseY);
        if ((button == 0 || button == 1)
            && !menu.getCarried().isEmpty()) {
            Slot presentationSlot = corpsePresentationSlotAt(
                localX, localY);
            if (presentationSlot != null) {
                slotClicked(presentationSlot, presentationSlot.index,
                    button, ClickType.PICKUP);
                return true;
            }
        }
        if (button == 0 && (playerScrollbarDragging || playerContentDragging
            || scrollbarDragging || contentDragging)) {
            playerScrollbarDragging = false;
            playerContentDragging = false;
            scrollbarDragging = false;
            contentDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updatePlayerScrollbar(double mouseY) {
        int trackTop = topPos + VIEW_TOP;
        int trackHeight = viewBottom() - VIEW_TOP;
        double progress = Math.max(0.0D, Math.min(1.0D,
            (mouseY - trackTop) / trackHeight));
        playerScroll = progress * playerMaxScroll();
        targetPlayerScroll = playerScroll;
        applyPlayerSlotPositions();
    }

    private void updateScrollbar(double mouseY) {
        int trackTop = topPos + VIEW_TOP;
        int trackHeight = viewBottom() - VIEW_TOP;
        double progress = Math.max(0.0D, Math.min(1.0D,
            (mouseY - trackTop) / trackHeight));
        lootScroll = progress * maxScroll();
        targetLootScroll = lootScroll;
        applyLootSlotPositions();
    }

    private boolean insideNativeSlot(double mouseX, double mouseY, Slot slot) {
        return slot != null && slot.x >= 0 && slot.y >= 0
            && inside(mouseX, mouseY, leftPos + slot.x, topPos + slot.y, 16, 16);
    }
    private boolean overPlayerSlot(double mouseX, double mouseY) {
        for (int index = CorpseMenu.PLAYER_SLOT_START; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.x >= 0 && inside(mouseX, mouseY,
                leftPos + slot.x, topPos + slot.y, 16, 16)) return true;
        }
        return false;
    }

    private boolean overCorpseSlot(double mouseX, double mouseY) {
        double localX = nativeLocalX(mouseX);
        double localY = nativeLocalY(mouseY);
        if (corpseStorageTargetAt(localX, localY) != null) return true;
        if (corpsePresentationSlotAt(localX, localY) != null) return true;
        for (int index = 0; index < CorpseMenu.CORPSE_SLOTS; index++) {
            Slot slot = menu.slots.get(index);
            if (slot.x >= 0 && slot.y >= 0
                && inside(mouseX, mouseY, leftPos + slot.x, topPos + slot.y, 16, 16)) return true;
        }
        return false;
    }

    private CorpseEntity corpse() {
        if (minecraft == null || minecraft.level == null) return null;
        Entity entity = minecraft.level.getEntity(menu.corpseEntityId());
        return entity instanceof CorpseEntity corpse ? corpse : null;
    }

    private int playerY(int baseY) {
        return topPos + baseY - (int) Math.round(playerScroll);
    }

    private boolean playerVisible(int y, int height) {
        return y + height >= topPos + VIEW_TOP && y <= topPos + viewBottom();
    }

    private double playerMaxScroll() {
        return Math.max(0.0D, PLAYER_CONTENT_BOTTOM - viewBottom());
    }

    private double clampPlayerScroll(double value) {
        return Math.max(0.0D, Math.min(playerMaxScroll(), value));
    }

    private int lootY(int baseY) {
        return topPos + baseY - (int) Math.round(lootScroll);
    }

    private double nativeLocalX(double screenX) {
        return DeltaContainerLayoutController.nativeMouseX(this, screenX) - leftPos;
    }

    private double nativeLocalY(double screenY) {
        return DeltaContainerLayoutController.nativeMouseY(this, screenY) - topPos;
    }

    private int lootPanelLocalX() {
        return DeltaContainerLayoutController.isActive(this) ? 0 : RIGHT_X;
    }

    private int viewBottom() {
        return imageHeight - 14;
    }

    private int embeddedLootShift() {
        return DeltaContainerLayoutController.isActive(this) ? RIGHT_X : 0;
    }

    private int lootPanelWidth() {
        return lootContentWidth() + 30;
    }

    private int primaryRowOffset() {
        return (lootContentWidth() - PRIMARY_ROW_WIDTH) / 2;
    }

    private int pocketSectionHeight() {
        return Math.max(POCKET_SECTION_HEIGHT, 24 + storageCell() + 8);
    }

    private int lootContentWidth() {
        int cell = storageCell();
        int required = Math.max(LOOT_CONTENT_WIDTH, 12 + 5 * cell + 4 * POCKET_GAP);
        for (int slotIndex : new int[]{CorpseMenu.CHEST_RIG_SLOT, CorpseMenu.BACKPACK_SLOT}) {
            if (menu.slots.get(slotIndex).getItem().getItem() instanceof DeltaPackItem pack) {
                PackRegionLayout layout = new PackRegionLayout(pack, cell, STORAGE_REGION_GAP);
                required = Math.max(required, 12 + LARGE_SLOT + SECTION_GAP + layout.width());
            }
        }
        return required;
    }

    private int corpseCarrierSectionHeight(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
            return EMPTY_CARRIER_SECTION_HEIGHT;
        }
        ItemStack stack = menu.slots.get(slotIndex).getItem();
        if (!(stack.getItem() instanceof DeltaPackItem pack)) {
            return EMPTY_CARRIER_SECTION_HEIGHT;
        }
        PackRegionLayout layout = new PackRegionLayout(pack,
            storageCell(), STORAGE_REGION_GAP);
        return 22 + Math.max(LARGE_SLOT, layout.height()) + 6;
    }

    /** Uses the same user-configured cell edge as the standalone player inventory. */
    private int storageCell() {
        // The native corpse column has its own transform. The player's fitted
        // pixel size cancels out the setting when the left panel is width-limited.
        return InventoryLayoutScale.cellSize(18,
            StatusEffectHudState.inventoryLayoutScale());
    }

    private boolean insideLootViewport(double localX, double localY) {
        return lootViewport().contains(localX, localY);
    }

    private CorpseLootViewport lootViewport() {
        return new CorpseLootViewport(lootPanelLocalX() + 1, VIEW_TOP,
            lootPanelLocalX() + lootPanelWidth() - 8, viewBottom());
    }

    /** GuiGraphics scissors use screen coordinates, independently of the pose. */
    private void enableLootScissor(GuiGraphics graphics, boolean slotLocal) {
        int offsetX = slotLocal ? 0 : leftPos;
        int offsetY = slotLocal ? 0 : topPos;
        CorpseLootViewport clip = lootViewport().screenBounds(
            graphics.pose().last().pose(), offsetX, offsetY);
        graphics.enableScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
    }

    private int corpsePocketsBaseY() {
        return CHEST_RIG_BASE_Y
            + corpseCarrierSectionHeight(CorpseMenu.CHEST_RIG_SLOT) + SECTION_GAP;
    }

    private int corpseBackpackBaseY() {
        return corpsePocketsBaseY() + pocketSectionHeight() + SECTION_GAP;
    }

    private int lootContentBottom() {
        int playerBottom = corpseBackpackBaseY()
            + corpseCarrierSectionHeight(CorpseMenu.BACKPACK_SLOT) + 10;
        return playerBottom;
    }


    private boolean isPlayerCorpse() {
        CorpseEntity corpse = corpse();
        return corpse == null || corpse.ownerId() != null;
    }

    private boolean visible(int y, int height) {
        return y + height >= topPos + VIEW_TOP && y <= topPos + viewBottom();
    }

    private double maxScroll() {
        return Math.max(0.0D, lootContentBottom() - viewBottom());
    }

    private double clampScroll(double value) {
        return Math.max(0.0D, Math.min(maxScroll(), value));
    }

    private void renderLargeItem(GuiGraphics graphics, ItemStack stack,
                                 int x, int y, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 30.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + height;
    }

    private static String formatCurrency(long value) {
        if (value >= 100_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0D);
        }
        if (value >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0D);
        }
        return Long.toString(Math.max(0L, value));
    }
}
