package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.client.PlayerStatusClientState;
import com.xtdpotato.xero_delta.client.QualityIconRenderer;import com.xtdpotato.xero_delta.client.ScreenTransition;
import com.xtdpotato.xero_delta.client.TradingUi;
import com.xtdpotato.xero_delta.data.SafetyBoxSkinCatalog;
import com.xtdpotato.xero_delta.item.ModItems;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.SafetyBoxSkinSelectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/** Full-screen cosmetic selector for the ultimate safety box. */
public final class SafetyBoxSkinPickerScreen extends Screen {
    private static final ResourceLocation CLOSE_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/cross_button");
    private static final ResourceLocation CLOSE_HOVERED_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/cross_button_highlighted");
    private static final int HEADER_HEIGHT = 48;
    private static final int MARGIN = 22;
    private static final int ROW_HEIGHT = 68;
    private static final int BUTTON_WIDTH = 136;
    private static final int BUTTON_HEIGHT = 27;


    private final Screen parent;
    private final ScreenTransition transition = new ScreenTransition();
    private final List<SkinEntry> skins = new ArrayList<>();
    private int selected;
    private double scroll;
    private double targetScroll;
    private boolean draggingList;
    private boolean draggingScrollbar;
    private double dragStartY;
    private double dragStartScroll;
    private String pendingSkin = "";

    public SafetyBoxSkinPickerScreen(Screen parent) {
        super(Component.translatable("safety_box.xero_delta.skin.picker_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        skins.clear();
        for (String skinId : SafetyBoxSkinCatalog.skins()) {
            skins.add(new SkinEntry(skinId, qualityFor(skinId), accentFor(skinId),
                SafetyBoxSkinCatalog.isUnlockedByDefault(skinId)));
        }
        selected = Math.max(0, indexOf(currentSkinId()));
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
        transition.pop(graphics);
        transition.drawFade(graphics, width, height);
    }

    private void drawAmbientBackground(GuiGraphics graphics) {
        int start = detailX();
        for (int i = 0; i < 8; i++) {
            int inset = i * 17;
            int alpha = Math.max(2, 19 - i * 2);
            graphics.fill(start + inset, HEADER_HEIGHT + 18 + inset,
                width - MARGIN - inset, height - MARGIN - inset,
                alpha << 24 | 0x302037);
        }
        graphics.fill(0, height - 42, width, height, 0xB2070B0C);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, HEADER_HEIGHT, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, title, MARGIN, 15, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        graphics.drawString(font, Component.translatable("safety_box.xero_delta.skin.picker_subtitle"),
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
        graphics.drawString(font, Component.translatable("safety_box.xero_delta.skin.available"),
            x + 11, y + 10, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        graphics.drawString(font, Integer.toString(skins.size()), x + w - 20, y + 10, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        int rowsY = y + 30;
        int rowsH = h - 38;
        int rowW = w - 20;
        graphics.enableScissor(x + 1, rowsY, x + w - 1, rowsY + rowsH);
        for (int i = 0; i < skins.size(); i++) {
            int rowY = rowsY + i * ROW_HEIGHT - (int) Math.round(scroll);
            if (rowY + ROW_HEIGHT <= rowsY || rowY >= rowsY + rowsH) continue;
            drawRow(graphics, skins.get(i), i, x + 8, rowY, rowW, mouseX, mouseY);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, x + w - 6, rowsY, rowsH, mouseX, mouseY);
    }

    private void drawRow(GuiGraphics graphics, SkinEntry entry, int index, int x, int y, int w,
                         int mouseX, int mouseY) {
        boolean selectedRow = selected == index;
        boolean hovered = inside(mouseX, mouseY, x, y + 3, w, ROW_HEIGHT - 6);
        boolean inUse = entry.id().equals(currentSkinId());
        graphics.fill(x, y + 3, x + w, y + ROW_HEIGHT - 3,
            selectedRow ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY_CONTAINER : hovered ? com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_HIGH : com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        graphics.renderOutline(x, y + 3, w, ROW_HEIGHT - 6,
            selectedRow ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : hovered ? 0xFF718184 : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.fill(x + 7, y + 10, x + 55, y + 58, entry.accent() & 0x66FFFFFF);
        graphics.renderOutline(x + 7, y + 10, 48, 48, entry.accent());
        graphics.pose().pushPose();
        graphics.pose().translate(x + 15, y + 18, 20.0F);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.renderItem(topBoxStack(), 0, 0);
        graphics.pose().popPose();
        int textX = x + 65;
        int available = Math.max(32, w - 78);
        Component name = Component.translatable("safety_box.xero_delta.skin." + entry.id());
        graphics.drawString(font, font.plainSubstrByWidth(name.getString(), available),
            textX, y + 16, entry.unlocked() ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        graphics.drawString(font, Component.translatable(
            "safety_box.xero_delta.quality." + entry.quality()), textX, y + 35,
            entry.accent(), false);
        if (!entry.unlocked()) {
            graphics.fill(x + w - 4, y + 3, x + w, y + ROW_HEIGHT - 3, 0xFF626B6D);
        } else if (inUse) {
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
        SkinEntry entry = selectedEntry();
        if (entry == null) return;

        int infoX = x + 18;
        int infoW = Math.max(150, Math.min(320, w * 43 / 100));
        QualityIconRenderer.render(graphics, entry.quality(),
            infoX, y + 18, 14);
        Component quality = Component.translatable("safety_box.xero_delta.quality." + entry.quality());
        Component name = Component.translatable("safety_box.xero_delta.skin." + entry.id());
        graphics.drawString(font, quality.copy().append("  ").append(name),
            infoX + 22, y + 18, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        graphics.fill(infoX, y + 38, infoX + infoW, y + 39, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, Component.translatable("safety_box.xero_delta.skin.attribute"),
            infoX, y + 52, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        Component attribute = Component.translatable("safety_box.xero_delta.skin.attribute_cosmetic");
        graphics.drawString(font, attribute, infoX + infoW - font.width(attribute), y + 52,
            com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        graphics.fill(infoX, y + 68, infoX + infoW, y + 69, 0xFF263235);
        int descriptionY = y + 83;
        for (FormattedCharSequence line : font.split(Component.translatable(
            "safety_box.xero_delta.skin.description." + entry.id()), infoW)) {
            graphics.drawString(font, line, infoX, descriptionY, 0xFFB5BDBE, false);
            descriptionY += font.lineHeight + 3;
        }
        drawPreview(graphics, entry, x + infoW + 42, y + 24,
            Math.max(70, w - infoW - 60), Math.max(76, h - 92));
        drawActions(graphics, entry, mouseX, mouseY);
    }

    private void drawPreview(GuiGraphics graphics, SkinEntry entry, int x, int y, int w, int h) {
        if (w <= 20 || h <= 20) return;
        graphics.fill(x, y, x + w, y + h, 0x6A11191B);
        graphics.renderOutline(x, y, w, h, entry.accent());
        for (int stripe = 0; stripe < w; stripe += 20) {
            graphics.fill(x + stripe, y, Math.min(x + stripe + 5, x + w), y + h,
                entry.accent() & 0x1FFFFFFF);
        }
        int scale = Math.max(3, Math.min(10, Math.min(w - 28, h - 28) / 18));
        int rendered = 16 * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(x + (w - rendered) / 2.0F,
            y + (h - rendered) / 2.0F, 40.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(topBoxStack(), 0, 0);
        graphics.pose().popPose();
        graphics.fill(x, y + h - 4, x + w, y + h, entry.accent());
    }

    private void drawActions(GuiGraphics graphics, SkinEntry entry, int mouseX, int mouseY) {
        int primaryX = width - MARGIN - BUTTON_WIDTH;
        int primaryY = height - MARGIN - BUTTON_HEIGHT;
        int detailsX = primaryX - BUTTON_WIDTH - 10;
        drawButton(graphics, detailsX, primaryY, BUTTON_WIDTH, BUTTON_HEIGHT,
            Component.translatable("safety_box.xero_delta.skin.view_details"), true,
            0xFF304A50, mouseX, mouseY);

        boolean inUse = entry.id().equals(currentSkinId());
        boolean permitted = PlayerStatusClientState.INSTANCE.canChangeBc();
        boolean topEquipped = topBoxEquipped();
        Component label;
        boolean enabled;
        int color;
        if (inUse && topEquipped) {
            label = Component.translatable("safety_box.xero_delta.in_use");
            enabled = false;
            color = 0xFF263933;
        } else if (!entry.unlocked()) {
            label = Component.translatable("safety_box.xero_delta.go_get");
            enabled = true;
            color = 0xFF305C50;
            graphics.drawString(font, Component.translatable(
                "safety_box.xero_delta.skin.temporarily_locked"),
                primaryX + BUTTON_WIDTH - font.width(Component.translatable(
                    "safety_box.xero_delta.skin.temporarily_locked")), primaryY - 16,
                0xFFD8A865, false);
        } else if (!topEquipped) {
            label = Component.translatable("safety_box.xero_delta.skin.equip_top_first");
            enabled = false;
            color = 0xFF2B3234;
        } else if (!permitted) {
            label = Component.translatable("safety_box.xero_delta.no_permission");
            enabled = false;
            color = 0xFF2B3234;
        } else {
            label = Component.translatable("safety_box.xero_delta.use");
            enabled = true;
            color = 0xFF167D5D;
        }
        drawButton(graphics, primaryX, primaryY, BUTTON_WIDTH, BUTTON_HEIGHT,
            label, enabled, color, mouseX, mouseY);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h, Component label,
                            boolean enabled, int baseColor, int mouseX, int mouseY) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, hovered ? 0xFF2AAE82 : baseColor);
        graphics.renderOutline(x, y, w, h, hovered ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : enabled ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawCenteredString(font, label, x + w / 2, y + 9, enabled ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
    }

    private void drawCloseButton(GuiGraphics graphics, int mouseX, int mouseY) {
        TradingUi.drawBackButton(graphics, font, width, mouseX, mouseY);
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y, int h,
                               int mouseX, int mouseY) {
        int content = skins.size() * ROW_HEIGHT;
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
        if (inside(mouseX, mouseY, width - 30, 8, 22, 22)) {
            onClose();
            return true;
        }
        int rowsY = listY() + 30;
        int rowsH = listHeight() - 38;
        int content = skins.size() * ROW_HEIGHT;
        if (content > rowsH
            && inside(mouseX, mouseY, listX() + listWidth() - 11, rowsY, 12, rowsH)) {
            draggingScrollbar = true;
            dragStartY = mouseY;
            dragStartScroll = targetScroll;
            return true;
        }
        if (inside(mouseX, mouseY, listX() + 8, rowsY, listWidth() - 20, rowsH)) {
            int index = (int) Math.floor((mouseY - rowsY + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < skins.size()) selected = index;
            draggingList = true;
            dragStartY = mouseY;
            dragStartScroll = targetScroll;
            return true;
        }
        SkinEntry entry = selectedEntry();
        if (entry == null) return super.mouseClicked(mouseX, mouseY, button);
        int primaryX = width - MARGIN - BUTTON_WIDTH;
        int primaryY = height - MARGIN - BUTTON_HEIGHT;
        if (inside(mouseX, mouseY, primaryX - BUTTON_WIDTH - 10, primaryY,
            BUTTON_WIDTH, BUTTON_HEIGHT)) {
            minecraft.player.displayClientMessage(Component.translatable(
                "safety_box.xero_delta.skin.details_hint",
                Component.translatable("safety_box.xero_delta.skin." + entry.id())), false);
            return true;
        }
        if (inside(mouseX, mouseY, primaryX, primaryY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (!entry.unlocked()) {
                minecraft.player.displayClientMessage(Component.translatable(
                    "safety_box.xero_delta.skin.acquisition_hint"), false);
                return true;
            }
            if (!topBoxEquipped() || !PlayerStatusClientState.INSTANCE.canChangeBc()
                || entry.id().equals(currentSkinId())) return true;
            pendingSkin = entry.id();
            ModNetwork.sendToServer(new SafetyBoxSkinSelectPacket(entry.id()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && (draggingList || draggingScrollbar)) {
            int visible = listHeight() - 38;
            int content = skins.size() * ROW_HEIGHT;
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

    private ItemStack equippedTopBox() {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        return CuriosApi.getCuriosInventory(minecraft.player).map(curios -> {
            var handler = curios.getStacksHandler("safety_box").orElse(null);
            if (handler == null || handler.getStacks().getSlots() < 1) return ItemStack.EMPTY;
            ItemStack stack = handler.getStacks().getStackInSlot(0);
            if (stack.isEmpty() || !SafetyBoxSkinCatalog.TOP_BOX_ID.equals(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) return ItemStack.EMPTY;
            return stack;
        }).orElse(ItemStack.EMPTY);
    }

    private boolean topBoxEquipped() {
        return !equippedTopBox().isEmpty();
    }

    private String currentSkinId() {
        if (!pendingSkin.isBlank()) return pendingSkin;
        ItemStack stack = equippedTopBox();
        return stack.isEmpty() ? SafetyBoxSkinCatalog.DEFAULT_SKIN
            : SafetyBoxSkinCatalog.normalize(stack.getOrDefault(
                ModDataComponents.SAFETY_BOX_SKIN.get(), SafetyBoxSkinCatalog.DEFAULT_SKIN));
    }

    private static ItemStack topBoxStack() {
        return ModItems.SAFETY_BOX_3X3.get().getDefaultInstance();
    }

    private SkinEntry selectedEntry() {
        return selected >= 0 && selected < skins.size() ? skins.get(selected) : null;
    }

    private int indexOf(String skinId) {
        for (int i = 0; i < skins.size(); i++) {
            if (skins.get(i).id().equals(skinId)) return i;
        }
        return 0;
    }

    private void revealSelected() {
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
        int max = Math.max(0, skins.size() * ROW_HEIGHT - (listHeight() - 38));
        targetScroll = Math.max(0.0D, Math.min(max, targetScroll));
        scroll = Math.max(0.0D, Math.min(max, scroll));
    }

    private int listX() { return MARGIN; }
    private int listY() { return HEADER_HEIGHT + 14; }
    private int listWidth() { return Math.max(190, Math.min(300, width * 29 / 100)); }
    private int listHeight() { return Math.max(110, height - listY() - 18); }
    private int detailX() { return listX() + listWidth() + 16; }

    private static String qualityFor(String id) {
        return switch (id) {
            case "zero_player", "wheel_of_fate" -> "gold";
            case "gilded_glow" -> "red";
            default -> "purple";
        };
    }

    private static int accentFor(String id) {
        return switch (id) {
            case "gilded_glow" -> 0xFFE26F4B;
            case "gekeluosi_secret" -> 0xFF869099;
            case "zero_player" -> 0xFFE99155;
            case "watcher" -> 0xFF6B8FDB;
            case "wheel_of_fate" -> 0xFFD7B45D;
            default -> 0xFF63D4B0;
        };
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public void tick() {
        super.tick();
        transition.tick(minecraft);
        if (!pendingSkin.isBlank()) {
            ItemStack stack = equippedTopBox();
            if (!stack.isEmpty() && pendingSkin.equals(stack.getOrDefault(
                ModDataComponents.SAFETY_BOX_SKIN.get(), SafetyBoxSkinCatalog.DEFAULT_SKIN))) {
                pendingSkin = "";
            }
        }
    }

    @Override protected void renderBlurredBackground(float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        transition.beginClose(() -> Minecraft.getInstance().setScreen(parent));
    }

    private record SkinEntry(String id, String quality, int accent, boolean unlocked) {}
}

