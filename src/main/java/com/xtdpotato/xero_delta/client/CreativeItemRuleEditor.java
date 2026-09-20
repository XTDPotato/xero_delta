package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.ServerItemRules;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.ItemSizeRule;
import com.xtdpotato.xero_delta.network.CreativeRuleBatchPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material2Icon;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Attached creative-inventory editor for staged quality, size and price rules. */
public final class CreativeItemRuleEditor {
    public static final int WIDTH = CreativeRuleEditorLayout.WIDTH;
    public static final int HEIGHT = CreativeRuleEditorLayout.HEIGHT;
    private static final int ACCENT = 0xFF62D2A7;
    private static final ResourceLocation HISTORY_ARROW = ResourceLocation.fromNamespaceAndPath(
        "xero_delta", "textures/gui/action/return.png");
    private static final ResourceLocation INFO_ICON = ResourceLocation.fromNamespaceAndPath(
        "xero_delta", "textures/gui/action/info.png");
    private static final ResourceLocation CLOSE_ICON = ResourceLocation.fromNamespaceAndPath(
        "xero_delta", "textures/gui/action/close.png");
    private static final List<String> QUALITIES = List.of(
        "gray", "green", "blue", "purple", "gold", "red");
    private static final int[] QUALITY_COLORS = {
        0xFF7A8585, 0xFF48B96B, 0xFF438DD8, 0xFF9A62D5, 0xFFE2B94F, 0xFFD94D4D
    };

    private static CreativeModeInventoryScreen owner;
    private static int panelX;
    private static int panelY;
    private static boolean qualityEnabled;
    private static boolean sizeEnabled;
    private static boolean priceEnabled;
    private static boolean editing;
    private static CreativeRuleEditorLayout.Tab tab = CreativeRuleEditorLayout.Tab.QUALITY;
    private static boolean allType;
    private static String selectedQuality = "gray";
    private static final Map<String, String> pendingQualities = new LinkedHashMap<>();
    private static final Map<String, Long> pendingSizes = new LinkedHashMap<>();
    private static final Deque<EditorSnapshot> undo = new ArrayDeque<>();
    private static final Deque<EditorSnapshot> redo = new ArrayDeque<>();
    private static final Map<String, ItemStack> selectedItems = new LinkedHashMap<>();
    private static String priceText = "";
    private static int sizeWidth = 1;
    private static int sizeHeight = 1;
    private static boolean rotateTexture = true;
    private static int textureMode;
    private static int proportionalScale = 1;
    private static SliderDrag sliderDrag = SliderDrag.NONE;
    private static boolean priceFocused;
    private static int panelScroll;
    private static int visibleHeight = HEIGHT;
    private static Component hoveredTooltip;
    private static boolean selecting;
    private static boolean boxSelecting;
    private static boolean consumingSlotInput;
    private static int panelButton = -1;
    private static int consumingButton = -1;
    private static boolean panelRendered;
    private static double selectionStartX;
    private static double selectionStartY;
    private static double selectionMouseX;
    private static double selectionMouseY;

    private CreativeItemRuleEditor() {}

    public static void setVisibleHeight(int height) {
        visibleHeight = Math.max(CreativeRuleEditorLayout.MIN_HEIGHT, Math.min(HEIGHT, height));
        panelScroll = CreativeRuleEditorLayout.clampScroll(tab, visibleHeight, panelScroll);
    }

    public static int visibleHeight() {
        return visibleHeight;
    }
    public static boolean isVisible(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && CreativeRuleEditorLayout.eligible(
            screen instanceof CreativeModeInventoryScreen,
            minecraft.player.isCreative(), minecraft.player.hasPermissions(2));
    }

    public static void setPanelRendered(boolean rendered) {
        panelRendered = rendered;
    }

    private static boolean isInteractive(Screen screen) {
        return panelRendered && isVisible(screen);
    }

    public static void render(CreativeModeInventoryScreen screen, GuiGraphics graphics,
                              int x, int y, int mouseX, int mouseY) {
        ensureOwner(screen);
        panelRendered = true;
        panelX = x;
        panelY = y;
        Font font = Minecraft.getInstance().font;
        Material3Theme.refreshFromConfig();
        hoveredTooltip = null;
        renderSlotSelection(screen, graphics);
        if (boxSelecting) {
            int left = (int) Math.min(selectionStartX, selectionMouseX);
            int top = (int) Math.min(selectionStartY, selectionMouseY);
            int right = (int) Math.max(selectionStartX, selectionMouseX);
            int bottom = (int) Math.max(selectionStartY, selectionMouseY);
            graphics.fill(left, top, right, bottom, 0x334FD9A4);
            border(graphics, left, top, Math.max(1, right - left),
                Math.max(1, bottom - top), ACCENT);
        }
        graphics.fill(x, y, x + WIDTH, y + visibleHeight, Material3Theme.SURFACE);
        border(graphics, x, y, WIDTH, visibleHeight, Material3Theme.OUTLINE_VARIANT);
        Material2Icon.TUNE.render(graphics, x + 14, y + 13, Material3Theme.PRIMARY);
        graphics.drawString(font, Component.translatable("creative_rules.xero_delta.title"),
            x + 26, y + 9, Material3Theme.TEXT, false);
        drawToggle(graphics, font, x + WIDTH - 38, y + 4, editing, mouseX, mouseY);
        tooltip(mouseX, mouseY, x + WIDTH - 38, y + 4, 30, 18,
            Component.translatable("creative_rules.xero_delta.edit_mode"));

        for (var candidate : CreativeRuleEditorLayout.Tab.values()) {
            int tx = x + 6 + candidate.ordinal() * 60;
            drawSegment(graphics, font, tx, y + 28, 58, 20,
                Component.translatable("creative_rules.xero_delta.tab_" + candidate.key),
                tab == candidate, mouseX, mouseY);
        }
        drawSegment(graphics, font, x + 8, y + 52, 85, 18,
            Component.translatable("creative_rules.xero_delta.scope_exact"), !allType, mouseX, mouseY);
        drawSegment(graphics, font, x + 97, y + 52, 85, 18,
            Component.translatable("creative_rules.xero_delta.scope_type"), allType, mouseX, mouseY);
        int footer = y + CreativeRuleEditorLayout.footerTop(visibleHeight);
        int bodyY = y + CreativeRuleEditorLayout.BODY_TOP;
        int contentMouseY = mouseY >= bodyY && mouseY < footer
            ? mouseY + panelScroll : Integer.MIN_VALUE;
        graphics.enableScissor(x + 1, bodyY, x + WIDTH - 1, footer);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, -panelScroll, 0.0F);
        switch (tab) {
            case QUALITY -> renderQuality(graphics, font, x, bodyY, mouseX, contentMouseY);
            case SIZE -> renderSize(graphics, font, x, bodyY, mouseX, contentMouseY);
            case PRICE -> renderPrice(graphics, font, x, bodyY, mouseX, contentMouseY);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
        int maximum = CreativeRuleEditorLayout.maxScroll(tab, visibleHeight);
        if (maximum > 0) {
            int extent = footer - bodyY;
            int thumb = Math.min(extent, Math.max(8, extent * extent / tab.contentHeight));
            int thumbY = bodyY + panelScroll * (extent - thumb) / maximum;
            graphics.fill(x + WIDTH - 4, thumbY, x + WIDTH - 2, thumbY + thumb, Material3Theme.PRIMARY);
        }
        graphics.fill(x + 1, footer, x + WIDTH - 1, y + visibleHeight - 1, Material3Theme.SURFACE_CONTAINER);
        drawHistoryIcon(graphics, x + 6, footer + 5, false, !undo.isEmpty(), mouseX, mouseY);
        drawHistoryIcon(graphics, x + 30, footer + 5, true, !redo.isEmpty(), mouseX, mouseY);
        drawIconButton(graphics, x + 54, footer + 5, Material2Icon.RESTART_ALT,
            activeCount() > 0, mouseX, mouseY, "creative_rules.xero_delta.clear_draft");
        drawButton(graphics, font, x + 80, footer + 5, 78, 20,
            Component.translatable("creative_rules.xero_delta.save"),
            canSaveActive(), mouseX, mouseY);
        drawIconButton(graphics, x + 162, footer + 5, Material2Icon.INFO,
            true, mouseX, mouseY, "creative_rules.xero_delta.shortcuts_tooltip");
        if (hoveredTooltip != null) graphics.renderTooltip(font, hoveredTooltip, mouseX, mouseY);
    }

    private static void renderQuality(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        g.drawString(font, Component.translatable("creative_rules.xero_delta.selected", activeCount()),
            x + 8, y + 4, Material3Theme.TEXT_MUTED, false);
        for (int index = 0; index < QUALITIES.size(); index++) {
            int sx = x + 8 + index * 29;
            Material2Drawing.roundedRect(g, sx, y + 22, 23, 22, 4, QUALITY_COLORS[index]);
            if (QUALITIES.get(index).equals(selectedQuality)) {
                Material2Icon.CHECK.render(g, sx + 12, y + 33, 0xFFFFFFFF);
                Material2Drawing.outlineRoundedRect(g, sx, y + 22, 23, 22, 4, 1, Material3Theme.TEXT);
            }
            tooltip(mx, my, sx, y + 22, 23, 22, Component.translatable("quality.xero_delta." + QUALITIES.get(index)));
        }
        g.drawString(font, Component.translatable("quality.xero_delta." + selectedQuality),
            x + 8, y + 52, qualityColor(selectedQuality), false);
    }

    private static void renderSize(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        drawStepSlider(g, font, x + 8, y + CreativeRuleEditorLayout.sliderTop(0), WIDTH - 16,
            Component.translatable("creative_rules.xero_delta.width"), sizeWidth, 1, 10, true, mx, my);
        drawStepSlider(g, font, x + 8, y + CreativeRuleEditorLayout.sliderTop(1), WIDTH - 16,
            Component.translatable("creative_rules.xero_delta.height"), sizeHeight, 1, 10, true, mx, my);
        Component rotation = Component.translatable("creative_rules.xero_delta.rotate_texture");
        g.drawString(font, font.plainSubstrByWidth(rotation.getString(), WIDTH - 54),
            x + 8, y + 59, Material3Theme.TEXT, false);
        tooltip(mx, my, x + 8, y + 54, WIDTH - 16, 18, rotation);
        drawToggle(g, font, x + WIDTH - 38, y + 54, rotateTexture, mx, my);
        String[] modes = {"texture_original", "texture_stretch", "texture_proportional"};
        for (int index = 0; index < modes.length; index++) {
            drawSegment(g, font, x + 8 + index * 58, y + 80, 56, 20,
                Component.translatable("creative_rules.xero_delta." + modes[index]),
                textureMode == index, mx, my);
        }
        if (textureMode == 2) drawStepSlider(g, font, x + 8, y + CreativeRuleEditorLayout.sliderTop(2), WIDTH - 16,
            Component.translatable("creative_rules.xero_delta.proportional_scale"), proportionalScale,
            1, 31, true, mx, my);
    }

    private static void renderPrice(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        g.drawString(font, Component.translatable("creative_rules.xero_delta.selected", activeCount()),
            x + 8, y + 4, Material3Theme.TEXT_MUTED, false);
        int clearX = x + WIDTH - 30;
        Material2Drawing.smoothRoundedPanel(g, x + 8, y + 22, WIDTH - 44, 22, 4,
            priceFocused ? Material3Theme.PRIMARY : Material3Theme.OUTLINE_VARIANT, Material3Theme.SURFACE_CONTAINER_HIGH);
        String shown = priceText.isEmpty()
            ? Component.translatable("creative_rules.xero_delta.price_placeholder").getString() : priceText;
        g.drawString(font, font.plainSubstrByWidth(shown, WIDTH - 56), x + 13, y + 29,
            priceText.isEmpty() ? Material3Theme.TEXT_MUTED : Material3Theme.TEXT, false);
        if (priceFocused && (System.currentTimeMillis() / 500L & 1L) == 0L) {
            int cursor = Math.min(clearX - 10, x + 13 + font.width(priceText));
            g.fill(cursor, y + 27, cursor + 1, y + 39, Material3Theme.TEXT);
        }
        drawIconButton(g, clearX, y + 23, Material2Icon.CLOSE, !priceText.isEmpty(),
            mx, my, "creative_rules.xero_delta.clear");
        drawButton(g, font, x + 8, y + 54, WIDTH - 16, 22,
            Component.translatable("creative_rules.xero_delta.reference_value"),
            !selectedItems.isEmpty(), mx, my);
    }

    public static boolean mousePressed(Screen screen, double mouseX, double mouseY, int button) {
        if (!(screen instanceof CreativeModeInventoryScreen creative) || !isInteractive(screen)) {
            return false;
        }
        ensureOwner(creative);
        if (inside(mouseX, mouseY, panelX, panelY - PlayerStatusPanelRenderer.COMPACT_HEIGHT - 4,
            WIDTH, PlayerStatusPanelRenderer.COMPACT_HEIGHT + 4)) return false;
        if (inside(mouseX, mouseY, panelX, panelY, WIDTH, visibleHeight)) {
            panelButton = button;
            if (button != 0) return true;
            handlePanelClick(mouseX, mouseY);
            return true;
        }
        priceFocused = false;
        if (!qualityEnabled && !sizeEnabled && !priceEnabled) return false;
        if (slotAt(creative, mouseX, mouseY) == null
            && !insideCreativeGrid(creative, mouseX, mouseY)) return false;
        consumingSlotInput = true;
        consumingButton = button;
        if (button == 1) {
            Slot slot = slotAt(creative, mouseX, mouseY);
            if (slot != null && activeSelectionContains(slot.getItem())) {
                rememberUndo();
                removeActiveDraft(slot.getItem());
            }
        }
        if (button != 0) return true;
        selecting = true;
        boxSelecting = false;
        selectionStartX = selectionMouseX = mouseX;
        selectionStartY = selectionMouseY = mouseY;
        return true;
    }

    public static boolean mouseDragged(Screen screen, double mouseX, double mouseY, int button) {
        if (!isInteractive(screen)) {
            sliderDrag = SliderDrag.NONE;
            selecting = false;
            boxSelecting = false;
            consumingSlotInput = false;
            consumingButton = -1;
            return false;
        }
        if (screen == owner && sliderDrag != SliderDrag.NONE && button == 0) {
            updateSlider(mouseX);
            return true;
        }
        if (screen == owner && panelButton == button) return true;
        if (screen != owner || !consumingSlotInput || button != consumingButton) return false;
        if (!selecting || button != 0) return true;
        selectionMouseX = mouseX;
        selectionMouseY = mouseY;
        double dx = mouseX - selectionStartX;
        double dy = mouseY - selectionStartY;
        if (dx * dx + dy * dy >= 16.0D) boxSelecting = true;
        return true;
    }

    public static boolean mouseReleased(Screen screen, double mouseX, double mouseY, int button) {
        if (screen == owner && panelButton == button) {
            panelButton = -1;
            if (sliderDrag != SliderDrag.NONE) {
                updateSlider(mouseX);
                sliderDrag = SliderDrag.NONE;
            }
            return true;
        }
        if (!isInteractive(screen)) {
            sliderDrag = SliderDrag.NONE;
            selecting = false;
            boxSelecting = false;
            consumingSlotInput = false;
            consumingButton = -1;
            return false;
        }
        if (screen == owner && sliderDrag != SliderDrag.NONE && button == 0) {
            updateSlider(mouseX);
            sliderDrag = SliderDrag.NONE;
            return true;
        }
        if (screen != owner || !consumingSlotInput || button != consumingButton) return false;
        if (selecting && button == 0) {
            selectionMouseX = mouseX;
            selectionMouseY = mouseY;
            if (boxSelecting) selectBox(owner, Screen.hasControlDown());
            else {
                Slot slot = overEditorChrome(mouseX, mouseY)
                    ? null : slotAt(owner, mouseX, mouseY);
                if (slot != null) clickItem(slot.getItem(), Screen.hasControlDown());
            }
        }
        selecting = false;
        boxSelecting = false;
        consumingSlotInput = false;
        consumingButton = -1;
        panelButton = -1;
        return true;
    }

    public static boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double deltaY) {
        if (screen != owner || !isInteractive(screen)
            || !inside(mouseX, mouseY, panelX, panelY, WIDTH, visibleHeight)) return false;
        if (deltaY != 0 && mouseY >= panelY + CreativeRuleEditorLayout.BODY_TOP
            && mouseY < panelY + CreativeRuleEditorLayout.footerTop(visibleHeight)) {
            panelScroll = CreativeRuleEditorLayout.clampScroll(tab, visibleHeight,
                panelScroll + (deltaY < 0.0D ? 20 : -20));
        }
        return true;
    }

    public static boolean keyPressed(Screen screen, int keyCode, int modifiers) {
        if (screen != owner || !isInteractive(screen)) return false;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && keyCode == GLFW.GLFW_KEY_Z) {
            undo();
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_S) {
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) saveAll();
            else saveActive();
            return true;
        }
        if (!priceFocused) return false;
        if (control && keyCode == GLFW.GLFW_KEY_V) {
            String pasted = Minecraft.getInstance().keyboardHandler.getClipboard().trim();
            if (pasted.matches("[0-9]{1,9}")) {
                rememberUndo();
                priceText = pasted;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !priceText.isEmpty()) {
            rememberUndo();
            priceText = priceText.substring(0, priceText.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (priceText.isEmpty()) return true;
            rememberUndo();
            priceText = "";
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            savePrice();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            priceFocused = false;
            return true;
        }
        return true;
    }

    public static boolean charTyped(Screen screen, char character) {
        if (screen != owner || !isInteractive(screen) || !priceFocused) return false;
        if (character >= '0' && character <= '9' && priceText.length() < 9) {
            rememberUndo();
            priceText += character;
        }
        return true;
    }

    public static void close(Screen screen) {
        if (screen != owner) return;
        owner = null;
        pendingQualities.clear();
        pendingSizes.clear();
        undo.clear();
        redo.clear();
        selectedItems.clear();
        priceText = "";
        priceFocused = false;
        selecting = false;
        boxSelecting = false;
        consumingSlotInput = false;
        consumingButton = -1;
        qualityEnabled = false;
        sizeEnabled = false;
        priceEnabled = false;
        panelButton = -1;
        editing = false;
        tab = CreativeRuleEditorLayout.Tab.QUALITY;
        allType = false;
        selectedQuality = "gray";
        sizeWidth = 1;
        sizeHeight = 1;
        rotateTexture = true;
        textureMode = 1;
        proportionalScale = 1;
        sliderDrag = SliderDrag.NONE;
        panelScroll = 0;
        visibleHeight = HEIGHT;
        hoveredTooltip = null;
        panelRendered = false;
    }

    private static void handlePanelClick(double mouseX, double mouseY) {
        int x = panelX;
        int y = panelY;
        if (inside(mouseX, mouseY, x + WIDTH - 38, y + 4, 30, 18)) {
            editing = !editing;
            updateMode();
            return;
        }
        for (var candidate : CreativeRuleEditorLayout.Tab.values()) {
            if (inside(mouseX, mouseY, x + 6 + candidate.ordinal() * 60, y + 28, 58, 20)) {
                tab = candidate;
                panelScroll = 0;
                priceFocused = false;
                updateMode();
                return;
            }
        }
        if (inside(mouseX, mouseY, x + 8, y + 52, WIDTH - 16, 18)) {
            boolean next = mouseX >= x + 97;
            if (next != allType) {
                rememberUndo();
                allType = next;
            }
            return;
        }
        int footer = y + CreativeRuleEditorLayout.footerTop(visibleHeight);
        if (mouseY >= footer) {
            if (inside(mouseX, mouseY, x + 6, footer + 5, 20, 20)) undo();
            else if (inside(mouseX, mouseY, x + 30, footer + 5, 20, 20)) redo();
            else if (inside(mouseX, mouseY, x + 54, footer + 5, 20, 20) && activeCount() > 0) {
                rememberUndo();
                clearActiveDraft();
            } else if (inside(mouseX, mouseY, x + 80, footer + 5, 78, 20)) {
                saveActive();
            }
            return;
        }
        if (mouseY < y + CreativeRuleEditorLayout.BODY_TOP) return;
        mouseY += panelScroll;
        y += CreativeRuleEditorLayout.BODY_TOP;
        priceFocused = false;
        if (tab == CreativeRuleEditorLayout.Tab.QUALITY) {
            for (int index = 0; index < QUALITIES.size(); index++) {
                if (inside(mouseX, mouseY, x + 8 + index * 29, y + 22, 23, 22)) {
                    if (!selectedQuality.equals(QUALITIES.get(index))) {
                        rememberUndo();
                        selectedQuality = QUALITIES.get(index);
                        pendingQualities.replaceAll((key, previous) -> selectedQuality);
                    }
                    return;
                }
            }
        } else if (tab == CreativeRuleEditorLayout.Tab.SIZE) {
            if (beginSliderDrag(mouseX, mouseY)) return;
            if (inside(mouseX, mouseY, x + WIDTH - 38, y + 54, 30, 18)) {
                rememberUndo();
                rotateTexture = !rotateTexture;
                refreshSizeDrafts();
            }
            for (int index = 0; index < 3; index++) {
                if (inside(mouseX, mouseY, x + 8 + index * 58, y + 80, 56, 20)
                    && textureMode != index) {
                    rememberUndo();
                    textureMode = index;
                    refreshSizeDrafts();
                }
            }
        } else {
            if (inside(mouseX, mouseY, x + 8, y + 22, WIDTH - 44, 22)) priceFocused = true;
            else if (inside(mouseX, mouseY, x + WIDTH - 30, y + 23, 20, 20)) {
                if (!priceText.isEmpty()) rememberUndo();
                priceText = "";
                priceFocused = true;
            } else if (inside(mouseX, mouseY, x + 8, y + 54, WIDTH - 16, 22)) fillReferencePrice();
        }
    }

    private static void updateMode() {
        qualityEnabled = editing && tab == CreativeRuleEditorLayout.Tab.QUALITY;
        sizeEnabled = editing && tab == CreativeRuleEditorLayout.Tab.SIZE;
        priceEnabled = editing && tab == CreativeRuleEditorLayout.Tab.PRICE;
        selecting = false;
        boxSelecting = false;
        sliderDrag = SliderDrag.NONE;
        priceFocused = false;
    }

    private static int activeCount() {
        return switch (tab) {
            case QUALITY -> pendingQualities.size();
            case SIZE -> pendingSizes.size();
            case PRICE -> selectedItems.size();
        };
    }

    private static boolean canSaveActive() {
        return tab == CreativeRuleEditorLayout.Tab.PRICE ? canSavePrice() : activeCount() > 0;
    }

    private static void saveActive() {
        switch (tab) {
            case QUALITY -> saveQuality();
            case SIZE -> saveSize();
            case PRICE -> savePrice();
        }
    }

    private static void clearActiveDraft() {
        switch (tab) {
            case QUALITY -> pendingQualities.clear();
            case SIZE -> pendingSizes.clear();
            case PRICE -> selectedItems.clear();
        }
    }

    private static void refreshSizeDrafts() {
        long rule = currentSizeRule().pack();
        pendingSizes.replaceAll((key, previous) -> rule);
    }

    private static void removeActiveDraft(ItemStack stack) {
        if (qualityEnabled) removeQualityDraft(stack);
        if (sizeEnabled) removeSizeDraft(stack);
        if (priceEnabled) removePriceSelection(stack);
    }

    private static void stageItem(ItemStack stack) {
        if (qualityEnabled) pendingQualities.put(qualityKey(stack), selectedQuality);
        if (sizeEnabled) pendingSizes.put(sizeKey(stack), currentSizeRule().pack());
        if (priceEnabled) selectedItems.put(priceKey(stack), stack.copyWithCount(1));
    }

    private static void clickItem(ItemStack stack, boolean additive) {
        if (stack == null || stack.isEmpty()
            || (!qualityEnabled && !sizeEnabled && !priceEnabled)) return;
        boolean alreadySelected = activeSelectionContains(stack);
        rememberUndo();
        if (alreadySelected && additive) {
            removeActiveDraft(stack);
            return;
        }
        if (!additive) clearActiveDraft();
        stageItem(stack);
    }

    private static void selectBox(CreativeModeInventoryScreen screen, boolean additive) {
        rememberUndo();
        if (!additive) clearActiveDraft();
        int left = (int) Math.min(selectionStartX, selectionMouseX);
        int top = (int) Math.min(selectionStartY, selectionMouseY);
        int right = (int) Math.max(selectionStartX, selectionMouseX);
        int bottom = (int) Math.max(selectionStartY, selectionMouseY);
        var seen = new java.util.HashSet<String>();
        for (Slot slot : creativeSlots(screen)) {
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            if (x + 16 <= left || x >= right || y + 16 <= top || y >= bottom) continue;
            ItemStack stack = slot.getItem();
            if (overEditorChrome(x + 8, y + 8)) continue;
            if (!seen.add(priceKey(stack))) continue;
            if (additive && activeSelectionContains(stack)) removeActiveDraft(stack);
            else stageItem(stack);
        }

    }

    private static void saveQuality() {
        if (pendingQualities.isEmpty()) return;
        ModNetwork.sendToServer(new CreativeRuleBatchPacket(
            Map.copyOf(pendingQualities), Map.of(), Map.of()));
        pendingQualities.clear();
        discardCommittedHistory(EditorSnapshot::withoutQuality);
    }

    private static void fillReferencePrice() {
        if (selectedItems.isEmpty()) return;
        ItemStack stack = selectedItems.values().iterator().next();
        long reference = ClientDataCache.INSTANCE.getReferencePrice(stack);
        rememberUndo();
        priceText = Long.toString(Math.max(0L, Math.min(999_999_999L, reference)));
        priceFocused = true;
    }

    private static void savePrice() {
        if (!canSavePrice()) return;
        long price;
        try {
            price = Long.parseLong(priceText);
        } catch (NumberFormatException ignored) {
            return;
        }
        Map<String, Long> edits = new LinkedHashMap<>();
        for (String key : selectedItems.keySet()) edits.put(key, price);
        ModNetwork.sendToServer(new CreativeRuleBatchPacket(Map.of(), edits, Map.of()));
        selectedItems.clear();
        priceText = "";
        priceFocused = false;
        discardCommittedHistory(EditorSnapshot::withoutPrice);
    }

    private static boolean canSavePrice() {
        if (selectedItems.isEmpty() || priceText.isEmpty()) return false;
        try {
            long value = Long.parseLong(priceText);
            return value >= 0L && value <= 999_999_999L;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void saveSize() {
        if (pendingSizes.isEmpty()) return;
        ModNetwork.sendToServer(new CreativeRuleBatchPacket(
            Map.of(), Map.of(), Map.copyOf(pendingSizes)));
        pendingSizes.clear();
        discardCommittedHistory(EditorSnapshot::withoutSize);
    }

    private static void rememberUndo() {
        undo.push(snapshot());
        while (undo.size() > 128) undo.removeLast();
        redo.clear();
    }

    private static void undo() {
        if (undo.isEmpty()) return;
        redo.push(snapshot());
        restore(undo.pop());
    }

    private static void redo() {
        if (redo.isEmpty()) return;
        undo.push(snapshot());
        restore(redo.pop());
    }

    private static void clearHistory() {
        undo.clear();
        redo.clear();
    }

    private static void discardCommittedHistory(UnaryOperator<EditorSnapshot> operation) {
        if (pendingQualities.isEmpty() && pendingSizes.isEmpty()
            && selectedItems.isEmpty() && priceText.isEmpty()) {
            clearHistory();
            return;
        }
        List<EditorSnapshot> undoSnapshots = undo.stream().map(operation).toList();
        List<EditorSnapshot> redoSnapshots = redo.stream().map(operation).toList();
        undo.clear();
        undo.addAll(undoSnapshots);
        redo.clear();
        redo.addAll(redoSnapshots);
    }

    private static EditorSnapshot snapshot() {
        Map<String, ItemStack> items = new LinkedHashMap<>();
        selectedItems.forEach((key, stack) -> items.put(key, stack.copyWithCount(1)));
        return new EditorSnapshot(Map.copyOf(pendingQualities), Map.copyOf(pendingSizes),
            Map.copyOf(items), priceText, selectedQuality, allType, sizeWidth, sizeHeight,
            rotateTexture, textureMode, proportionalScale);
    }

    private static void restore(EditorSnapshot snapshot) {
        pendingQualities.clear();
        pendingQualities.putAll(snapshot.qualities());
        pendingSizes.clear();
        pendingSizes.putAll(snapshot.sizes());
        selectedItems.clear();
        snapshot.items().forEach((key, stack) ->
            selectedItems.put(key, stack.copyWithCount(1)));
        priceText = snapshot.priceText();
        selectedQuality = snapshot.selectedQuality();
        allType = snapshot.allType();
        sizeWidth = snapshot.sizeWidth();
        sizeHeight = snapshot.sizeHeight();
        rotateTexture = snapshot.rotateTexture();
        textureMode = snapshot.textureMode();
        proportionalScale = snapshot.proportionalScale();
    }

    private static void saveAll() {
        Map<String, String> qualities = Map.copyOf(pendingQualities);
        Map<String, Long> sizes = Map.copyOf(pendingSizes);
        Map<String, Long> prices = new LinkedHashMap<>();
        if (canSavePrice()) {
            long price = Long.parseLong(priceText);
            selectedItems.keySet().forEach(key -> prices.put(key, price));
        }
        if (qualities.isEmpty() && sizes.isEmpty() && prices.isEmpty()) return;
        ModNetwork.sendToServer(new CreativeRuleBatchPacket(qualities, prices, sizes));
        if (!qualities.isEmpty()) pendingQualities.clear();
        if (!sizes.isEmpty()) pendingSizes.clear();
        if (!prices.isEmpty()) {
            selectedItems.clear();
            priceText = "";
            priceFocused = false;
        }
        if (!qualities.isEmpty()) discardCommittedHistory(EditorSnapshot::withoutQuality);
        if (!sizes.isEmpty()) discardCommittedHistory(EditorSnapshot::withoutSize);
        if (!prices.isEmpty()) discardCommittedHistory(EditorSnapshot::withoutPrice);
    }

    private static boolean activeSelectionContains(ItemStack stack) {
        return qualityEnabled && containsQualityDraft(stack)
            || sizeEnabled && containsSizeDraft(stack)
            || priceEnabled && containsPriceSelection(stack);
    }

    private static boolean containsQualityDraft(ItemStack stack) {
        return pendingQualities.containsKey(exactQualityKey(stack))
            || pendingQualities.containsKey(typeQualityKey(stack));
    }

    private static void removeQualityDraft(ItemStack stack) {
        pendingQualities.remove(exactQualityKey(stack));
        pendingQualities.remove(typeQualityKey(stack));
    }

    private static boolean containsSizeDraft(ItemStack stack) {
        return pendingSizes.containsKey(exactSizeKey(stack))
            || pendingSizes.containsKey(typeSizeKey(stack));
    }

    private static void removeSizeDraft(ItemStack stack) {
        pendingSizes.remove(exactSizeKey(stack));
        pendingSizes.remove(typeSizeKey(stack));
    }

    private static boolean containsPriceSelection(ItemStack stack) {
        return selectedItems.containsKey(exactPriceKey(stack))
            || selectedItems.containsKey(typePriceKey(stack));
    }

    private static void removePriceSelection(ItemStack stack) {
        selectedItems.remove(exactPriceKey(stack));
        selectedItems.remove(typePriceKey(stack));
    }

    private static String qualityKey(ItemStack stack) {
        return allType ? typeQualityKey(stack) : exactQualityKey(stack);
    }

    private static String priceKey(ItemStack stack) {
        return allType ? typePriceKey(stack) : exactPriceKey(stack);
    }

    private static String sizeKey(ItemStack stack) {
        return allType ? typeSizeKey(stack) : exactSizeKey(stack);
    }

    private static String exactQualityKey(ItemStack stack) {
        return ServerItemRules.exactKey(ModDataStorage.getKey(stack));
    }

    private static String typeQualityKey(ItemStack stack) {
        return ServerItemRules.exactKey(ModDataStorage.getIdOnlyKey(stack));
    }

    private static String exactSizeKey(ItemStack stack) {
        return ServerItemRules.exactKey(ModDataStorage.getKey(stack));
    }

    private static String typeSizeKey(ItemStack stack) {
        return ServerItemRules.exactKey(ModDataStorage.getIdOnlyKey(stack));
    }

    private static String exactPriceKey(ItemStack stack) {
        return ModDataStorage.getKey(stack);
    }

    private static String typePriceKey(ItemStack stack) {
        return ModDataStorage.getIdOnlyKey(stack);
    }

    private static ItemSizeRule currentSizeRule() {
        return new ItemSizeRule(new ItemSize(sizeWidth, sizeHeight), rotateTexture,
            textureMode == 1, textureMode == 2 ? proportionalScale : 0);
    }

    private static void renderSlotSelection(CreativeModeInventoryScreen screen,
                                            GuiGraphics graphics) {
        for (Slot slot : creativeSlots(screen)) {
            ItemStack stack = slot.getItem();
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            if (overEditorChrome(x + 8, y + 8)) continue;
            if (editing && activeSelectionContains(stack)) {
                graphics.fill(x, y, x + 16, y + 16, 0x334FD9A4);
                border(graphics, x, y, 16, 16, ACCENT);
            }
            if (sizeEnabled) {
                Long packed = pendingSizes.get(
                    ServerItemRules.exactKey(ModDataStorage.getKey(stack)));
                if (packed == null) packed = pendingSizes.get(
                    ServerItemRules.exactKey(ModDataStorage.getIdOnlyKey(stack)));
                if (packed != null) {
                    ItemSize size = ItemSizeRule.unpack(packed).size();
                    border(graphics, x - 1, y - 1, 18, 18, 0xFF66B9FF);
                    graphics.drawString(Minecraft.getInstance().font,
                        size.width() + "x" + size.height(), x + 1, y + 1,
                        0xFFFFFFFF, true);
                }
            }
            if (!qualityEnabled) continue;
            String quality = pendingQualities.get(
                ServerItemRules.exactKey(ModDataStorage.getKey(stack)));
            if (quality == null) quality = pendingQualities.get(
                ServerItemRules.exactKey(ModDataStorage.getIdOnlyKey(stack)));
            if (quality == null
                || CreativeRuleBatchPacket.REMOVE_QUALITY.equals(quality)) continue;
            int color = qualityColor(quality);
            graphics.fill(x, y + 13, x + 16, y + 16, color);
        }
    }

    private static Slot slotAt(CreativeModeInventoryScreen screen,
                               double mouseX, double mouseY) {
        for (Slot slot : creativeSlots(screen)) {
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            if (inside(mouseX, mouseY, x, y, 16, 16)) return slot;
        }
        return null;
    }

    private static boolean insideCreativeGrid(CreativeModeInventoryScreen screen,
                                              double mouseX, double mouseY) {
        List<Slot> slots = creativeSlots(screen);
        if (slots.isEmpty()) return false;
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (Slot slot : slots) {
            left = Math.min(left, screen.getGuiLeft() + slot.x);
            top = Math.min(top, screen.getGuiTop() + slot.y);
            right = Math.max(right, screen.getGuiLeft() + slot.x + 16);
            bottom = Math.max(bottom, screen.getGuiTop() + slot.y + 16);
        }
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private static List<Slot> creativeSlots(CreativeModeInventoryScreen screen) {
        return screen.getMenu().slots.stream()
            .filter(slot -> slot.index >= 0 && slot.index < 45
                && slot.isActive() && !slot.getItem().isEmpty())
            .toList();
    }

    private static void ensureOwner(CreativeModeInventoryScreen screen) {
        if (owner == screen) return;
        int height = visibleHeight;
        close(owner);
        owner = screen;
        setVisibleHeight(height);
    }

    private static int qualityColor(String quality) {
        int index = QUALITIES.indexOf(quality);
        return index < 0 ? QUALITY_COLORS[0] : QUALITY_COLORS[index];
    }

    private static boolean beginSliderDrag(double mouseX, double mouseY) {
        if (tab != CreativeRuleEditorLayout.Tab.SIZE) return false;
        int y = panelY + CreativeRuleEditorLayout.BODY_TOP;
        if (inside(mouseX, mouseY, panelX + 8, y + CreativeRuleEditorLayout.sliderTop(0), WIDTH - 16, 20)) {
            sliderDrag = SliderDrag.WIDTH;
        } else if (inside(mouseX, mouseY, panelX + 8, y + CreativeRuleEditorLayout.sliderTop(1), WIDTH - 16, 20)) {
            sliderDrag = SliderDrag.HEIGHT;
        } else if (textureMode == 2
            && inside(mouseX, mouseY, panelX + 8, y + CreativeRuleEditorLayout.sliderTop(2), WIDTH - 16, 20)) {
            sliderDrag = SliderDrag.PROPORTIONAL;
        } else {
            return false;
        }
        rememberUndo();
        updateSlider(mouseX);
        return true;
    }

    private static void updateSlider(double mouseX) {
        int minimum;
        int maximum;
        if (sliderDrag == SliderDrag.PROPORTIONAL) {
            minimum = 1;
            maximum = 31;
        } else {
            minimum = 1;
            maximum = 10;
        }
        int trackX = panelX + 10;
        int trackWidth = WIDTH - 20;
        double progress = Math.max(0.0D, Math.min(1.0D,
            (mouseX - trackX) / Math.max(1.0D, trackWidth - 1.0D)));
        int value = minimum + (int) Math.round(progress * (maximum - minimum));
        switch (sliderDrag) {
            case WIDTH -> sizeWidth = value;
            case HEIGHT -> sizeHeight = value;
            case PROPORTIONAL -> proportionalScale = value;
            default -> {
            }
        }
        refreshSizeDrafts();
    }

    private static void drawStepSlider(GuiGraphics graphics, Font font, int x, int y,
                                       int width, Component label, int value,
                                       int minimum, int maximum, boolean enabled,
                                       int mouseX, int mouseY) {
        int color = enabled ? Material3Theme.TEXT : Material3Theme.TEXT_MUTED;
        graphics.drawString(font, label, x, y, color, false);
        String shown = Integer.toString(value);
        graphics.drawString(font, shown, x + width - font.width(shown), y, color, false);
        int trackY = y + 12;
        Material2Drawing.roundedRect(graphics, x, trackY, width, 3, 1, Material3Theme.SURFACE_CONTAINER_HIGHEST);
        double progress = (value - minimum) / (double) Math.max(1, maximum - minimum);
        int knobX = x + (int) Math.round(progress * (width - 5));
        graphics.fill(x, trackY, knobX + 2, trackY + 3,
            enabled ? Material3Theme.PRIMARY : Material3Theme.OUTLINE_VARIANT);
        Material2Drawing.circle(graphics, knobX + 2, trackY + 1, 4,
            enabled ? Material3Theme.PRIMARY : Material3Theme.TEXT_MUTED);
    }

    private static void drawToggle(GuiGraphics graphics, Font font, int x, int y,
                                   boolean enabled, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 30, 18);
        Material2Drawing.roundedRect(graphics, x, y + 1, 30, 16, 8,
            enabled ? Material3Theme.PRIMARY : Material3Theme.SURFACE_CONTAINER_HIGHEST);
        if (hovered) Material2Drawing.roundedRect(graphics, x, y + 1, 30, 16, 8,
            Material3Theme.alpha(Material3Theme.TEXT, 24));
        Material2Drawing.circle(graphics, enabled ? x + 22 : x + 8, y + 9, 5,
            enabled ? Material3Theme.ON_PRIMARY : Material3Theme.TEXT_MUTED);
    }

    private static void drawSegment(GuiGraphics graphics, Font font, int x, int y,
                                    int width, int height, Component label,
                                    boolean selected, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        Material2Drawing.roundedRect(graphics, x, y, width, height, 4,
            selected ? Material3Theme.SECONDARY_CONTAINER
                : hovered ? Material3Theme.SURFACE_CONTAINER_HIGH : Material3Theme.SURFACE_CONTAINER);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(label.getString(), width - 6),
            x + width / 2, y + (height - 9) / 2,
            selected ? Material3Theme.ON_SECONDARY_CONTAINER : Material3Theme.TEXT_MUTED);
        if (font.width(label) > width - 6) tooltip(mouseX, mouseY, x, y, width, height, label);
    }

    private static void drawButton(GuiGraphics graphics, Font font, int x, int y,
                                   int width, int height, Component label,
                                   boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, width, height);
        Material2Drawing.roundedRect(graphics, x, y, width, height, 5,
            enabled ? Material3Theme.PRIMARY_CONTAINER : Material3Theme.SURFACE_CONTAINER_HIGH);
        if (hovered) Material2Drawing.roundedRect(graphics, x, y, width, height, 5,
            Material3Theme.alpha(Material3Theme.PRIMARY, 25));
        graphics.drawCenteredString(font, font.plainSubstrByWidth(label.getString(), width - 8),
            x + width / 2, y + (height - 9) / 2,
            enabled ? Material3Theme.ON_PRIMARY_CONTAINER : Material3Theme.alpha(Material3Theme.TEXT, 97));
        if (font.width(label) > width - 8) tooltip(mouseX, mouseY, x, y, width, height, label);
    }

    private static void tooltip(int mx, int my, int x, int y, int width, int height, Component text) {
        if (inside(mx, my, x, y, width, height)) hoveredTooltip = text;
    }

    private static void drawIconButton(GuiGraphics g, int x, int y, Material2Icon icon,
                                       boolean enabled, int mx, int my, String hint) {
        boolean hovered = inside(mx, my, x, y, 20, 20);
        if (hovered) Material2Drawing.roundedRect(g, x, y, 20, 20, 4, Material3Theme.SURFACE_CONTAINER_HIGHEST);
        int ink = enabled ? Material3Theme.TEXT : Material3Theme.alpha(Material3Theme.TEXT, 97);
        if (icon == Material2Icon.INFO || icon == Material2Icon.CLOSE) {
            g.setColor((ink >> 16 & 255) / 255.0F, (ink >> 8 & 255) / 255.0F,
                (ink & 255) / 255.0F, (ink >>> 24) / 255.0F);
            g.blit(icon == Material2Icon.INFO ? INFO_ICON : CLOSE_ICON,
                x + 4, y + 4, 12, 12, 0, 0, 16, 16, 16, 16);
            g.setColor(1, 1, 1, 1);
        } else {
            icon.render(g, x + 10, y + 10, ink);
        }
        tooltip(mx, my, x, y, 20, 20, Component.translatable(hint));
    }

    private static void drawHistoryIcon(GuiGraphics g, int x, int y, boolean redoIcon,
                                        boolean enabled, int mx, int my) {
        if (inside(mx, my, x, y, 20, 20)) {
            Material2Drawing.roundedRect(g, x, y, 20, 20, 4, Material3Theme.SURFACE_CONTAINER_HIGHEST);
        }
        g.pose().pushPose();
        g.pose().translate(redoIcon ? x + 16 : x + 4, y + 4, 0);
        if (redoIcon) g.pose().scale(-1, 1, 1);
        g.setColor(1, 1, 1, enabled ? 1.0F : 0.38F);
        g.blit(HISTORY_ARROW, 0, 0, 12, 12, 0, 0, 16, 16, 16, 16);
        g.setColor(1, 1, 1, 1);
        g.pose().popPose();
        tooltip(mx, my, x, y, 20, 20, Component.translatable(
            "creative_rules.xero_delta." + (redoIcon ? "redo" : "undo")));
    }

    private static void border(GuiGraphics graphics, int x, int y,
                               int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y,
                                  int width, int height) {
        return mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + height;
    }

    private static boolean overEditorChrome(double mouseX, double mouseY) {
        int summary = PlayerStatusPanelRenderer.COMPACT_HEIGHT + 4;
        return inside(mouseX, mouseY, panelX, panelY - summary, WIDTH, visibleHeight + summary);
    }

    private enum SliderDrag {
        NONE, WIDTH, HEIGHT, PROPORTIONAL
    }

    private record EditorSnapshot(Map<String, String> qualities,
                                  Map<String, Long> sizes,
                                  Map<String, ItemStack> items,
                                  String priceText,
                                  String selectedQuality,
                                  boolean allType,
                                  int sizeWidth,
                                  int sizeHeight,
                                  boolean rotateTexture,
                                  int textureMode,
                                  int proportionalScale) {
        private EditorSnapshot withoutQuality() {
            return new EditorSnapshot(Map.of(), sizes, items, priceText, selectedQuality,
                allType, sizeWidth, sizeHeight, rotateTexture, textureMode,
                proportionalScale);
        }

        private EditorSnapshot withoutSize() {
            return new EditorSnapshot(qualities, Map.of(), items, priceText, selectedQuality,
                allType, sizeWidth, sizeHeight, rotateTexture, textureMode,
                proportionalScale);
        }

        private EditorSnapshot withoutPrice() {
            return new EditorSnapshot(qualities, sizes, Map.of(), "", selectedQuality,
                allType, sizeWidth, sizeHeight, rotateTexture, textureMode,
                proportionalScale);
        }
    }
}
