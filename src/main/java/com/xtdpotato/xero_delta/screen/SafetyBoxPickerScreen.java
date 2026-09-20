package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.PlayerStatusClientState;
import com.xtdpotato.xero_delta.client.QualityIconRenderer;
import com.xtdpotato.xero_delta.client.SafetyBoxAccessClientState;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import com.xtdpotato.xero_delta.client.TradingUi;
import com.xtdpotato.xero_delta.data.SafetyBoxSkinCatalog;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.SafetyBoxSelectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Full-screen Delta-style selector for all registered safety boxes. */
public final class SafetyBoxPickerScreen extends Screen {
    private static final ResourceLocation CLOSE_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/cross_button");
    private static final ResourceLocation CLOSE_HOVERED_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/cross_button_highlighted");
    private static final int HEADER_HEIGHT = 48;
    private static final int MARGIN = 22;
    private static final int ROW_HEIGHT = 66;
    private static final int BUTTON_WIDTH = 72;
    private static final int BUTTON_HEIGHT = 16;


    private final Screen parent;
    private final ScreenTransition transition = new ScreenTransition();
    private final List<BoxEntry> boxes = new ArrayList<>();
    private int selected = -1;
    private double scroll;
    private double targetScroll;
    private boolean draggingList;
    private boolean draggingScrollbar;
    private double dragStartY;
    private double dragStartScroll;
    private String pendingEquippedId = "";
    private Component resultMessage;
    private boolean resultSuccess;

    public SafetyBoxPickerScreen(Screen parent) {
        super(Component.translatable("safety_box.xero_delta.picker_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String previous = selectedEntry() == null ? "" : selectedEntry().itemId();
        boxes.clear();
        BuiltInRegistries.ITEM.stream()
            .filter(item -> item instanceof SafetyBoxItem)
            .map(item -> new BoxEntry(BuiltInRegistries.ITEM.getKey(item).toString(),
                item.getDefaultInstance()))
            .sorted(Comparator.comparingInt(entry -> boxOrder(entry.itemId())))
            .forEach(boxes::add);
        String preferred = previous.isBlank() ? equippedBoxId() : previous;
        selected = indexOf(preferred);
        if (selected < 0 && !boxes.isEmpty()) selected = 0;
        revealSelected();
        clampScroll();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tickSmoothScroll();
        graphics.fill(0, 0, width, height, com.xtdpotato.xero_delta.screen.material.Material3Theme.BACKGROUND);
        drawAmbientBackground(graphics);
        transition.push(graphics);
        drawHeader(graphics, mouseX, mouseY);
        drawList(graphics, mouseX, mouseY);
        drawDetails(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawSelectionResult(graphics, mouseX, mouseY);
        transition.pop(graphics);
        transition.drawFade(graphics, width, height);
    }

    private void drawAmbientBackground(GuiGraphics graphics) {
        int centerX = Math.max(width / 2, detailX());
        for (int i = 0; i < 7; i++) {
            int inset = i * 18;
            int alpha = Math.max(3, 18 - i * 2);
            graphics.fill(centerX - 150 + inset, HEADER_HEIGHT + 22 + inset,
                width - MARGIN - inset, height - MARGIN - inset,
                alpha << 24 | 0x17342D);
        }
        graphics.fill(0, height - 42, width, height, 0xB2070B0C);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, HEADER_HEIGHT, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, title, MARGIN, 15, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        graphics.drawString(font, Component.translatable("safety_box.xero_delta.picker_subtitle"),
            MARGIN + font.width(title) + 14, 15, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        drawCloseButton(graphics, mouseX, mouseY);
    }

    private void drawList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = listX();
        int y = listY();
        int w = listWidth();
        int h = listHeight();
        graphics.fill(x, y, x + w, y + h, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_LOW);
        graphics.renderOutline(x, y, w, h, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, Component.translatable("safety_box.xero_delta.available_boxes"),
            x + 11, y + 10, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        graphics.drawString(font, Integer.toString(boxes.size()), x + w - 20, y + 10, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);

        int rowsY = y + 30;
        int rowsH = h - 38;
        int rowW = w - 20;
        graphics.enableScissor(x + 1, rowsY, x + w - 1, rowsY + rowsH);
        for (int i = 0; i < boxes.size(); i++) {
            int rowY = rowsY + i * ROW_HEIGHT - (int) Math.round(scroll);
            if (rowY + ROW_HEIGHT <= rowsY || rowY >= rowsY + rowsH) continue;
            drawRow(graphics, boxes.get(i), i, x + 8, rowY, rowW, mouseX, mouseY);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, x + w - 6, rowsY, rowsH, mouseX, mouseY);
    }

    private void drawRow(GuiGraphics graphics, BoxEntry entry, int index, int x, int y, int w,
                         int mouseX, int mouseY) {
        boolean selectedRow = index == selected;
        boolean hovered = inside(mouseX, mouseY, x, y + 3, w, ROW_HEIGHT - 6);
        boolean unlocked = isUnlocked(entry.itemId());
        boolean equipped = isEquipped(entry.itemId());
        graphics.fill(x, y + 3, x + w, y + ROW_HEIGHT - 3,
            selectedRow ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY_CONTAINER : hovered ? com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_HIGH : com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        graphics.renderOutline(x, y + 3, w, ROW_HEIGHT - 6,
            selectedRow ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : hovered ? 0xFF718184 : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        int quality = qualityColor(entry.itemId());
        graphics.fill(x + 7, y + 10, x + 53, y + 56, quality & 0x66FFFFFF);
        graphics.renderOutline(x + 7, y + 10, 46, 46, quality);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 14, y + 17, 20.0F);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.renderItem(entry.stack(), 0, 0);
        graphics.pose().popPose();
        int textX = x + 63;
        int available = Math.max(32, w - 76);
        String name = font.plainSubstrByWidth(entry.stack().getHoverName().getString(), available);
        graphics.drawString(font, name, textX, y + 14, unlocked ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        SafetyBoxItem box = (SafetyBoxItem) entry.stack().getItem();
        Component capacity = Component.translatable("safety_box.xero_delta.capacity_short",
            box.getGridWidth() * box.getGridHeight(), box.getGridWidth(), box.getGridHeight());
        graphics.drawString(font, capacity, textX, y + 33, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        if (!unlocked) {
            graphics.fill(x + w - 4, y + 3, x + w, y + ROW_HEIGHT - 3, 0xFF626B6D);
        } else if (equipped) {
            graphics.fill(x + w - 4, y + 3, x + w, y + ROW_HEIGHT - 3, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
            graphics.fill(x + w - 16, y + 12, x + w - 8, y + 20, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
        }
    }

    private void drawDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = detailX();
        int y = listY();
        int w = Math.max(0, width - x - MARGIN);
        int h = listHeight();
        graphics.fill(x, y, x + w, y + h, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE);
        graphics.renderOutline(x, y, w, h, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        BoxEntry entry = selectedEntry();
        if (entry == null) {
            graphics.drawCenteredString(font, Component.translatable("safety_box.xero_delta.none_owned"),
                x + w / 2, y + h / 2, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
            return;
        }

        int quality = qualityColor(entry.itemId());
        int infoX = x + 18;
        int infoW = Math.max(150, Math.min(310, w * 43 / 100));
        QualityIconRenderer.render(graphics, qualityKey(entry.itemId()),
            infoX, y + 18, 14);
        Component qualityName = Component.translatable(
            "safety_box.xero_delta.quality." + qualityKey(entry.itemId()));
        graphics.drawString(font, qualityName.copy().append("  ").append(entry.stack().getHoverName()),
            infoX + 22, y + 18, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        graphics.fill(infoX, y + 38, infoX + infoW, y + 39, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);

        SafetyBoxItem box = (SafetyBoxItem) entry.stack().getItem();
        graphics.drawString(font, Component.translatable("safety_box.xero_delta.capacity_label"),
            infoX, y + 51, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        Component capacity = Component.translatable("safety_box.xero_delta.capacity_value",
            box.getGridWidth() * box.getGridHeight(), box.getGridWidth(), box.getGridHeight());
        graphics.drawString(font, capacity, infoX + infoW - font.width(capacity), y + 51, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        graphics.fill(infoX, y + 67, infoX + infoW, y + 68, 0xFF263235);

        ResourceLocation itemId = ResourceLocation.tryParse(entry.itemId());
        String path = itemId == null ? "unknown" : itemId.getPath();
        int descriptionY = y + 82;
        for (FormattedCharSequence line : font.split(Component.translatable(
            "safety_box.xero_delta.description." + path), infoW)) {
            graphics.drawString(font, line, infoX, descriptionY, 0xFFB5BDBE, false);
            descriptionY += font.lineHeight + 3;
        }

        drawPreview(graphics, entry, x + infoW + 42, y + 24,
            Math.max(70, w - infoW - 60), Math.max(76, h - 92), quality);
        drawActions(graphics, entry, mouseX, mouseY);
    }

    private void drawPreview(GuiGraphics graphics, BoxEntry entry, int x, int y, int w, int h,
                             int quality) {
        if (w <= 20 || h <= 20) return;
        graphics.fill(x, y, x + w, y + h, 0x59131C1E);
        graphics.renderOutline(x, y, w, h, 0xFF263438);
        int scale = Math.max(2, Math.min(4, Math.min(w - 28, h - 28) / 18));
        int rendered = 16 * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(x + (w - rendered) / 2.0F,
            y + (h - rendered) / 2.0F, 40.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(entry.stack(), 0, 0);
        graphics.pose().popPose();
        graphics.fill(x, y + h - 3, x + w, y + h, quality);
    }

    private void drawActions(GuiGraphics graphics, BoxEntry entry, int mouseX, int mouseY) {
        int primaryX = width - MARGIN - BUTTON_WIDTH;
        int primaryY = height - MARGIN - BUTTON_HEIGHT;
        boolean unlocked = isUnlocked(entry.itemId());
        boolean equipped = isEquipped(entry.itemId());
        boolean permitted = PlayerStatusClientState.INSTANCE.canChangeBc();

        Component rights = rightsText(entry.itemId());
        graphics.drawString(font, rights, primaryX + BUTTON_WIDTH - font.width(rights),
            primaryY - 16, unlocked ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED : 0xFFD8A865, false);

        Component primaryLabel;
        boolean primaryEnabled;
        int primaryColor;
        if (equipped) {
            primaryLabel = Component.translatable("safety_box.xero_delta.in_use");
            primaryEnabled = false;
            primaryColor = 0xFF263933;
        } else if (!unlocked) {
            primaryLabel = Component.translatable("safety_box.xero_delta.go_get");
            primaryEnabled = true;
            primaryColor = 0xFF305C50;
        } else if (!permitted) {
            primaryLabel = Component.translatable("safety_box.xero_delta.no_permission");
            primaryEnabled = false;
            primaryColor = 0xFF2B3234;
        } else {
            primaryLabel = Component.translatable("safety_box.xero_delta.use");
            primaryEnabled = true;
            primaryColor = 0xFF167D5D;
        }
        drawButton(graphics, primaryX, primaryY, BUTTON_WIDTH, BUTTON_HEIGHT, primaryLabel,
            primaryEnabled, primaryColor, mouseX, mouseY);

        if (!unlocked) {
            int activateX = primaryX - BUTTON_WIDTH - 10;
            graphics.drawString(font, Component.translatable("safety_box.xero_delta.permission_cards", 0),
                activateX, primaryY - 16, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
            drawButton(graphics, activateX, primaryY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("safety_box.xero_delta.activate_permission"),
                false, 0xFF2B3234, mouseX, mouseY);
        }

        if (SafetyBoxSkinCatalog.TOP_BOX_ID.equals(entry.itemId())) {
            int auxiliaryY = primaryY - BUTTON_HEIGHT - 12;
            drawButton(graphics, primaryX, auxiliaryY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("safety_box.xero_delta.customize"), true,
                0xFF304A50, mouseX, mouseY);
        }
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h, Component label,
                            boolean enabled, int baseColor, int mouseX, int mouseY) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, hovered ? 0xFF2AAE82 : baseColor);
        graphics.renderOutline(x, y, w, h, hovered ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : enabled ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawCenteredString(font, label, x + w / 2,
            y + Math.max(2, (h - font.lineHeight) / 2), enabled ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
    }

    private void drawCloseButton(GuiGraphics graphics, int mouseX, int mouseY) {
        TradingUi.drawBackButton(graphics, font, width, mouseX, mouseY);
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y, int h,
                               int mouseX, int mouseY) {
        int content = boxes.size() * ROW_HEIGHT;
        if (content <= h) return;
        graphics.fill(x, y, x + 3, y + h, 0xFF080D0F);
        int thumbH = Math.max(24, h * h / content);
        int travel = h - thumbH;
        int thumbY = y + (int) Math.round(travel * scroll / Math.max(1, content - h));
        boolean hovered = inside(mouseX, mouseY, x - 3, thumbY, 9, thumbH);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbH,
            hovered || draggingScrollbar ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : 0xFF68777A);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (resultMessage != null) {
            if (inside(mouseX, mouseY, resultButtonX(), resultButtonY(), 64, 16)) {
                resultMessage = null;
            }
            return true;
        }
        if (inside(mouseX, mouseY, width - 30, 8, 22, 22)) {
            onClose();
            return true;
        }
        int rowsY = listY() + 30;
        int rowsH = listHeight() - 38;
        int content = boxes.size() * ROW_HEIGHT;
        if (content > rowsH
            && inside(mouseX, mouseY, listX() + listWidth() - 11, rowsY, 12, rowsH)) {
            draggingScrollbar = true;
            dragStartY = mouseY;
            dragStartScroll = targetScroll;
            return true;
        }
        if (inside(mouseX, mouseY, listX() + 8, rowsY, listWidth() - 20, rowsH)) {
            int index = (int) Math.floor((mouseY - rowsY + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < boxes.size()) selected = index;
            draggingList = true;
            dragStartY = mouseY;
            dragStartScroll = targetScroll;
            return true;
        }
        BoxEntry entry = selectedEntry();
        if (entry == null) return super.mouseClicked(mouseX, mouseY, button);
        int primaryX = width - MARGIN - BUTTON_WIDTH;
        int primaryY = height - MARGIN - BUTTON_HEIGHT;
        if (SafetyBoxSkinCatalog.TOP_BOX_ID.equals(entry.itemId())) {
            int auxiliaryY = primaryY - BUTTON_HEIGHT - 12;
            if (inside(mouseX, mouseY, primaryX, auxiliaryY,
                BUTTON_WIDTH, BUTTON_HEIGHT)) {
                minecraft.setScreen(new SafetyBoxSkinPickerScreen(this));
                return true;
            }
        }
        if (inside(mouseX, mouseY, primaryX, primaryY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (!isUnlocked(entry.itemId())) {
                minecraft.player.displayClientMessage(Component.translatable(
                    "safety_box.xero_delta.acquisition_hint"), false);
                return true;
            }
            if (!PlayerStatusClientState.INSTANCE.canChangeBc() || isEquipped(entry.itemId())) {
                return true;
            }
            pendingEquippedId = entry.itemId();
            ModNetwork.sendToServer(new SafetyBoxSelectPacket(entry.itemId()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && (draggingList || draggingScrollbar)) {
            int visible = listHeight() - 38;
            int content = boxes.size() * ROW_HEIGHT;
            double delta = mouseY - dragStartY;
            if (draggingScrollbar) {
                int thumbH = Math.max(24, visible * visible / Math.max(visible + 1, content));
                delta *= Math.max(0, content - visible)
                    / (double) Math.max(1, visible - thumbH);
                targetScroll = dragStartScroll + delta;
            } else {
                targetScroll = dragStartScroll - delta;
            }
            clampScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingList = false;
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, listX(), listY(), listWidth(), listHeight())) {
            targetScroll -= scrollY * 34.0D;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private Component rightsText(String itemId) {
        if (!isUnlocked(itemId)) return Component.translatable("safety_box.xero_delta.rights_locked");
        long expiresAt = SafetyBoxAccessClientState.INSTANCE.expiresAt(itemId);
        if (expiresAt == Long.MAX_VALUE) {
            return Component.translatable("safety_box.xero_delta.rights_permanent");
        }
        Duration value = Duration.ofMillis(Math.max(0L, expiresAt - System.currentTimeMillis()));
        long days = value.toDays();
        long hours = value.minusDays(days).toHours();
        return days > 0
            ? Component.translatable("safety_box.xero_delta.rights_days_hours", days, hours)
            : Component.translatable("safety_box.xero_delta.rights_hours", Math.max(1L, hours));
    }

    private boolean isUnlocked(String itemId) {
        return SafetyBoxAccessClientState.INSTANCE.isUnlocked(itemId);
    }

    private boolean isEquipped(String itemId) {
        return itemId.equals(pendingEquippedId) || itemId.equals(equippedBoxId());
    }

    private String equippedBoxId() {
        if (minecraft == null || minecraft.player == null) return "";
        return CuriosApi.getCuriosInventory(minecraft.player)
            .flatMap(curios -> curios.findFirstCurio(
                stack -> stack.getItem() instanceof SafetyBoxItem))
            .map(result -> BuiltInRegistries.ITEM.getKey(
                result.stack().getItem()).toString())
            .orElse("");
    }
    private void revealSelected() {
        if (selected < 0) return;
        int visible = listHeight() - 38;
        double top = selected * ROW_HEIGHT;
        double bottom = top + ROW_HEIGHT;
        if (top < targetScroll) targetScroll = top;
        else if (bottom > targetScroll + visible) targetScroll = bottom - visible;
    }

    private void tickSmoothScroll() {
        scroll += (targetScroll - scroll) * 0.28D;
        if (Math.abs(targetScroll - scroll) < 0.05D) scroll = targetScroll;
    }

    private void clampScroll() {
        int max = Math.max(0, boxes.size() * ROW_HEIGHT - (listHeight() - 38));
        targetScroll = Math.max(0.0D, Math.min(max, targetScroll));
        scroll = Math.max(0.0D, Math.min(max, scroll));
    }

    private int indexOf(String itemId) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).itemId().equals(itemId)) return i;
        }
        return -1;
    }

    private BoxEntry selectedEntry() {
        return selected >= 0 && selected < boxes.size() ? boxes.get(selected) : null;
    }

    private int listX() { return MARGIN; }
    private int listY() { return HEADER_HEIGHT + 14; }
    private int listWidth() { return Math.max(190, Math.min(300, width * 29 / 100)); }
    private int listHeight() { return Math.max(110, height - listY() - 18); }
    private int detailX() { return listX() + listWidth() + 16; }

    private static int boxOrder(String id) {
        if (id.endsWith("2x1")) return 0;
        if (id.endsWith("2x2")) return 1;
        if (id.endsWith("3x2")) return 2;
        if (id.endsWith("4x2")) return 3;
        if (id.endsWith("3x3")) return 4;
        return 99;
    }

    private static String qualityKey(String id) {
        if (id.endsWith("2x1")) return "green";
        if (id.endsWith("2x2")) return "blue";
        if (id.endsWith("3x3")) return "gold";
        return "purple";
    }

    private static int qualityColor(String id) {
        return switch (qualityKey(id)) {
            case "green" -> 0xFF37D79C;
            case "blue" -> 0xFF4EA5EA;
            case "gold" -> 0xFFF0B94D;
            default -> 0xFFB074E8;
        };
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public void tick() {
        super.tick();
        transition.tick(minecraft);
        if (!pendingEquippedId.isBlank() && pendingEquippedId.equals(equippedBoxId())) {
            pendingEquippedId = "";
        }
    }

    public void showSelectionResult(boolean success, Component message) {
        resultSuccess = success;
        resultMessage = message;
        if (!success) pendingEquippedId = "";
    }

    private void drawSelectionResult(GuiGraphics graphics, int mouseX, int mouseY) {
        if (resultMessage == null) return;
        int popupWidth = Math.max(210, Math.min(320, width - 40));
        int popupHeight = 84;
        int x = (width - popupWidth) / 2;
        int y = (height - popupHeight) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1200);
        graphics.fill(x, y, x + popupWidth, y + popupHeight, 0xF20A1114);
        graphics.renderOutline(x, y, popupWidth, popupHeight,
            resultSuccess ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : 0xFFE06A62);
        Component heading = Component.translatable(resultSuccess
            ? "safety_box.xero_delta.result_success"
            : "safety_box.xero_delta.result_failed");
        graphics.drawString(font, heading, x + 12, y + 11,
            resultSuccess ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : 0xFFE9877F, false);
        graphics.drawCenteredString(font,
            font.plainSubstrByWidth(resultMessage.getString(), popupWidth - 24),
            x + popupWidth / 2, y + 35, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT);
        drawButton(graphics, resultButtonX(), resultButtonY(), 64, 16,
            Component.translatable("gui.ok"), true,
            resultSuccess ? 0xFF167D5D : 0xFF6E3431, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private int resultButtonX() { return width / 2 - 32; }
    private int resultButtonY() { return height / 2 + 20; }

    @Override protected void renderBlurredBackground(float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        transition.beginClose(() -> Minecraft.getInstance().setScreen(parent));
    }

    private record BoxEntry(String itemId, ItemStack stack) {}
}

