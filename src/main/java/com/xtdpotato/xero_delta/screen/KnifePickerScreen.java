package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.KnifeAccessClientState;
import com.xtdpotato.xero_delta.client.KnifeDisplayStatsResolver;
import com.xtdpotato.xero_delta.client.PlayerStatusClientState;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import com.xtdpotato.xero_delta.client.TradingUi;
import com.xtdpotato.xero_delta.data.KnifeDisplayStats;
import com.xtdpotato.xero_delta.data.TaczCompatibilityRules;
import com.xtdpotato.xero_delta.network.KnifeSelectPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Full-screen selector for unlocked LR Tactical Workshop knife skins. */
public final class KnifePickerScreen extends Screen {
    private static final ResourceLocation CLOSE_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/cross_button");
    private static final ResourceLocation CLOSE_HOVERED_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/cross_button_highlighted");
    private static final int HEADER_HEIGHT = 48;
    private static final int MARGIN = 22;
    private static final int ROW_HEIGHT = 58;
    private static final int BUTTON_WIDTH = 126;
    private static final int BUTTON_HEIGHT = 26;


    private final Screen parent;
    private final ScreenTransition transition = new ScreenTransition();
    private final List<KnifeEntry> entries = new ArrayList<>();
    private int selected = -1;
    private double scroll;
    private double targetScroll;
    private boolean draggingList;
    private boolean draggingScrollbar;
    private double dragStartY;
    private double dragStartScroll;
    private String pendingEquippedId = "";

    public KnifePickerScreen(Screen parent) {
        super(Component.translatable("knife.xero_delta.picker_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildEntries();
    }

    private void rebuildEntries() {
        String previous = selectedEntry() == null ? "" : selectedEntry().itemId();
        entries.clear();
        for (String itemId : KnifeAccessClientState.INSTANCE.unlocked()) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            ItemStack stack = Minecraft.getInstance().level == null ? ItemStack.EMPTY
                : KnifeAccessClientState.INSTANCE.stack(itemId,
                    Minecraft.getInstance().level.registryAccess());
            if (stack.isEmpty()) stack = BuiltInRegistries.ITEM.get(id).getDefaultInstance();
            if (!TaczCompatibilityRules.isLrTacticalMelee(stack)) continue;
            entries.add(new KnifeEntry(itemId, stack,
                KnifeDisplayStatsResolver.resolve(Minecraft.getInstance(), stack)));
        }
        entries.sort(Comparator.comparing(entry -> entry.stack().getHoverName().getString(),
            String.CASE_INSENSITIVE_ORDER));
        String preferred = previous.isBlank() ? KnifeAccessClientState.INSTANCE.selected() : previous;
        selected = indexOf(preferred);
        if (selected < 0 && !entries.isEmpty()) selected = 0;
        revealSelected();
        clampScroll();
    }

    private int indexOf(String itemId) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).itemId().equals(itemId)) return index;
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tickSmoothScroll();
        graphics.fill(0, 0, width, height, com.xtdpotato.xero_delta.screen.material.Material3Theme.BACKGROUND);
        transition.push(graphics);
        drawHeader(graphics, mouseX, mouseY);
        drawKnifeList(graphics, mouseX, mouseY);
        drawDetails(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        transition.pop(graphics);
        transition.drawFade(graphics, width, height);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, HEADER_HEIGHT, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, title, MARGIN, 14, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        graphics.drawString(font, Component.translatable("knife.xero_delta.picker_subtitle"),
            MARGIN + font.width(title) + 14, 14, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        int closeX = width - MARGIN - 22;
        boolean hovered = inside(mouseX, mouseY, closeX, 12, 22, 22);
        graphics.fill(closeX, 12, closeX + 22, 34, hovered ? 0xFF374649 : 0x8F202C2F);
        graphics.blitSprite(hovered ? CLOSE_HOVERED_SPRITE : CLOSE_SPRITE,
            closeX + 3, 15, 16, 16);
    }

    private void drawKnifeList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = listX();
        int y = listY();
        int listWidth = listWidth();
        int listHeight = listHeight();
        graphics.fill(x, y, x + listWidth, y + listHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_LOW);
        graphics.renderOutline(x, y, listWidth, listHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, Component.translatable("knife.xero_delta.unlocked"),
            x + 12, y + 10, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        graphics.drawString(font, Integer.toString(entries.size()),
            x + listWidth - 20, y + 10, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);

        int rowsY = y + 30;
        int rowsHeight = listHeight - 38;
        int rowWidth = listWidth - 20;
        graphics.enableScissor(x + 1, rowsY, x + listWidth - 1, rowsY + rowsHeight);
        for (int index = 0; index < entries.size(); index++) {
            int rowY = rowsY + index * ROW_HEIGHT - (int) Math.round(scroll);
            if (rowY + ROW_HEIGHT <= rowsY || rowY >= rowsY + rowsHeight) continue;
            drawKnifeRow(graphics, entries.get(index), index, x + 8, rowY,
                rowWidth, mouseX, mouseY);
        }
        graphics.disableScissor();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("knife.xero_delta.none_unlocked"),
                x + listWidth / 2, rowsY + rowsHeight / 2 - 4, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
        }
        drawScrollbar(graphics, x + listWidth - 6, rowsY, rowsHeight, mouseX, mouseY);
    }

    private void drawKnifeRow(GuiGraphics graphics, KnifeEntry entry, int index,
                              int x, int y, int rowWidth, int mouseX, int mouseY) {
        boolean selectedRow = index == selected;
        boolean hovered = inside(mouseX, mouseY, x, y + 3, rowWidth, ROW_HEIGHT - 6);
        graphics.fill(x, y + 3, x + rowWidth, y + ROW_HEIGHT - 3,
            selectedRow ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY_CONTAINER : hovered ? com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_HIGH : com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        graphics.renderOutline(x, y + 3, rowWidth, ROW_HEIGHT - 6,
            selectedRow ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : hovered ? 0xFF718487 : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        int quality = qualityColor(entry.stack());
        graphics.fill(x + 7, y + 10, x + 45, y + 48, quality);
        graphics.renderOutline(x + 7, y + 10, 38, 38, selectedRow ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : 0xFF566467);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 10, y + 13, 20.0F);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.renderItem(entry.stack(), 0, 0);
        graphics.pose().popPose();
        int textX = x + 54;
        int available = Math.max(24, rowWidth - 64);
        String name = font.plainSubstrByWidth(entry.stack().getHoverName().getString(), available);
        graphics.drawString(font, name, textX, y + 13, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        String id = font.plainSubstrByWidth(entry.itemId(), available);
        graphics.drawString(font, id, textX, y + 30, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        if (isEquipped(entry.itemId())) {
            graphics.fill(x + rowWidth - 4, y + 3, x + rowWidth, y + ROW_HEIGHT - 3, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
        }
    }

    private void drawDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = detailX();
        int y = listY();
        int detailWidth = Math.max(0, width - x - MARGIN);
        int detailHeight = listHeight();
        graphics.fill(x, y, x + detailWidth, y + detailHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE);
        graphics.renderOutline(x, y, detailWidth, detailHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        KnifeEntry entry = selectedEntry();
        if (entry == null) {
            graphics.drawCenteredString(font, Component.translatable("knife.xero_delta.select_prompt"),
                x + detailWidth / 2, y + detailHeight / 2 - 4, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
            return;
        }

        int innerX = x + 18;
        int innerY = y + 15;
        int innerWidth = Math.max(80, detailWidth - 36);
        int statsWidth = Math.max(178, Math.min(340, innerWidth * 54 / 100));
        int previewX = innerX + statsWidth + 14;
        int previewWidth = Math.max(64, x + detailWidth - 18 - previewX);
        drawStats(graphics, entry, innerX, innerY, statsWidth, detailHeight - 30);
        drawPreview(graphics, entry.stack(), previewX, innerY, previewWidth,
            detailHeight - BUTTON_HEIGHT - 46);
        drawEquipButton(graphics, entry, x + detailWidth - BUTTON_WIDTH - 16,
            y + detailHeight - BUTTON_HEIGHT - 14, mouseX, mouseY);
    }

    private void drawStats(GuiGraphics graphics, KnifeEntry entry, int x, int y, int statsWidth,
                           int availableHeight) {
        String name = font.plainSubstrByWidth(entry.stack().getHoverName().getString(), statsWidth);
        graphics.drawString(font, name, x, y, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        graphics.drawString(font, entry.itemId(), x, y + 15, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        graphics.fill(x, y + 30, x + statsWidth, y + 31, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        KnifeDisplayStats stats = entry.stats();
        int rowY = y + 40;
        if (stats.hasLevel()) {
            drawStatRow(graphics, "knife.xero_delta.stat.level", stats.level(), x, rowY, statsWidth);
            rowY += 17;
        }
        drawStatRow(graphics, "knife.xero_delta.stat.damage", stats.damage(), x, rowY, statsWidth);
        rowY += 17;
        drawStatRow(graphics, "knife.xero_delta.stat.armor_damage", stats.armorDamage(), x, rowY, statsWidth);
        rowY += 17;
        drawStatRow(graphics, "knife.xero_delta.stat.penetration", stats.penetration(), x, rowY, statsWidth);
        rowY += 17;
        drawStatRow(graphics, "knife.xero_delta.stat.headshot", stats.headshotMultiplier(), x, rowY, statsWidth);
        rowY += 17;
        drawStatRow(graphics, "knife.xero_delta.stat.sprint_speed", stats.sprintSpeed(), x, rowY, statsWidth);
        rowY += 17;
        drawStatRow(graphics, "knife.xero_delta.stat.walk_speed", stats.walkSpeed(), x, rowY, statsWidth);
        rowY += 17;
        drawStatRow(graphics, "knife.xero_delta.stat.attack_speed", stats.attackSpeed(), x, rowY, statsWidth);
        rowY += 17;
        drawStatRow(graphics, "knife.xero_delta.stat.attack_range", stats.attackRange(), x, rowY, statsWidth);
        rowY += 21;
        graphics.fill(x, rowY - 7, x + statsWidth, rowY - 6, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, Component.translatable("knife.xero_delta.stat.description"),
            x, rowY, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        rowY += 14;
        List<String> descriptions = stats.description().isEmpty()
            ? List.of(Component.translatable("knife.xero_delta.not_available").getString())
            : stats.description();
        int maxY = y + availableHeight - 8;
        for (String description : descriptions) {
            for (FormattedCharSequence line : font.split(Component.literal(description), statsWidth)) {
                if (rowY + font.lineHeight > maxY) return;
                graphics.drawString(font, line, x, rowY, 0xFFC3CCCA, false);
                rowY += font.lineHeight + 2;
            }
        }
    }

    private void drawStatRow(GuiGraphics graphics, String labelKey, String value,
                             int x, int y, int width) {
        graphics.drawString(font, Component.translatable(labelKey), x, y, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        int valueX = x + Math.min(width - 48, Math.max(86, width * 47 / 100));
        int valueWidth = Math.max(30, x + width - valueX);
        String clipped = font.plainSubstrByWidth(value, valueWidth);
        graphics.drawString(font, clipped, x + width - font.width(clipped), y, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
    }

    private void drawPreview(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, 0x8A111A1C);
        graphics.renderOutline(x, y, width, height, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.fill(x, y + height - 3, x + width, y + height, qualityColor(stack));
        int scale = Math.max(3, Math.min(9, Math.min(width - 18, height - 18) / 18));
        int rendered = 16 * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(x + (width - rendered) / 2.0F,
            y + (height - rendered) / 2.0F, 40.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private void drawEquipButton(GuiGraphics graphics, KnifeEntry entry, int x, int y,
                                 int mouseX, int mouseY) {
        boolean permitted = PlayerStatusClientState.INSTANCE.canChangeBc();
        boolean equipped = isEquipped(entry.itemId());
        boolean enabled = permitted && !equipped;
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
        int color = !permitted ? 0xFF2B3234 : equipped ? 0xFF273F37
            : hovered ? 0xFF4BAA82 : 0xFF357B5D;
        graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, color);
        graphics.renderOutline(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
            hovered ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : equipped ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        Component label = !permitted
            ? Component.translatable("knife.xero_delta.no_permission")
            : Component.translatable(equipped
                ? "knife.xero_delta.equipped" : "knife.xero_delta.equip");
        graphics.drawCenteredString(font, label, x + BUTTON_WIDTH / 2, y + 9,
            enabled || equipped ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y, int height,
                               int mouseX, int mouseY) {
        int contentHeight = entries.size() * ROW_HEIGHT;
        if (contentHeight <= height) return;
        graphics.fill(x, y, x + 3, y + height, 0xFF0A1012);
        int thumbHeight = Math.max(24, height * height / contentHeight);
        int travel = height - thumbHeight;
        int thumbY = y + (int) Math.round(travel * scroll / Math.max(1, contentHeight - height));
        boolean hovered = inside(mouseX, mouseY, x - 3, thumbY, 9, thumbHeight);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight,
            hovered || draggingScrollbar ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : 0xFF68787B);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (inside(mouseX, mouseY, width - 30, 8, 22, 22)) {
            onClose();
            return true;
        }
        int rowsY = listY() + 30;
        int rowsHeight = listHeight() - 38;
        int contentHeight = entries.size() * ROW_HEIGHT;
        if (contentHeight > rowsHeight
            && inside(mouseX, mouseY, listX() + listWidth() - 11, rowsY, 12, rowsHeight)) {
            draggingScrollbar = true;
            dragStartY = mouseY;
            dragStartScroll = targetScroll;
            return true;
        }
        if (inside(mouseX, mouseY, listX() + 8, rowsY, listWidth() - 20, rowsHeight)) {
            int index = (int) Math.floor((mouseY - rowsY + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < entries.size()) selected = index;
            draggingList = true;
            dragStartY = mouseY;
            dragStartScroll = targetScroll;
            return true;
        }
        KnifeEntry entry = selectedEntry();
        int buttonX = width - MARGIN - BUTTON_WIDTH - 16;
        int buttonY = listY() + listHeight() - BUTTON_HEIGHT - 14;
        if (entry != null && PlayerStatusClientState.INSTANCE.canChangeBc()
            && !isEquipped(entry.itemId())
            && inside(mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            pendingEquippedId = entry.itemId();
            ModNetwork.sendToServer(new KnifeSelectPacket(entry.itemId()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && (draggingList || draggingScrollbar)) {
            int visibleHeight = listHeight() - 38;
            int contentHeight = entries.size() * ROW_HEIGHT;
            double delta = mouseY - dragStartY;
            if (draggingScrollbar) {
                int thumbHeight = Math.max(24,
                    visibleHeight * visibleHeight / Math.max(visibleHeight + 1, contentHeight));
                delta *= Math.max(0, contentHeight - visibleHeight)
                    / (double) Math.max(1, visibleHeight - thumbHeight);
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
            targetScroll -= scrollY * 30.0D;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void revealSelected() {
        if (selected < 0) return;
        int visibleHeight = listHeight() - 38;
        double top = selected * ROW_HEIGHT;
        double bottom = top + ROW_HEIGHT;
        if (top < targetScroll) targetScroll = top;
        else if (bottom > targetScroll + visibleHeight) targetScroll = bottom - visibleHeight;
    }

    private void tickSmoothScroll() {
        scroll += (targetScroll - scroll) * 0.28D;
        if (Math.abs(targetScroll - scroll) < 0.05D) scroll = targetScroll;
    }

    private void clampScroll() {
        int maximum = Math.max(0, entries.size() * ROW_HEIGHT - (listHeight() - 38));
        targetScroll = Math.max(0.0D, Math.min(maximum, targetScroll));
        scroll = Math.max(0.0D, Math.min(maximum, scroll));
    }

    private boolean isEquipped(String itemId) {
        if (minecraft == null || minecraft.player == null) return false;
        int index = indexOf(itemId);
        return index >= 0 && com.xtdpotato.xero_delta.data.KnifeSkinRules.matches(
            minecraft.player.getInventory().getItem(com.xtdpotato.xero_delta.data.KnifeSkinRules.SLOT),
            entries.get(index).stack());
    }

    private KnifeEntry selectedEntry() {
        return selected >= 0 && selected < entries.size() ? entries.get(selected) : null;
    }

    private int listX() { return MARGIN; }
    private int listY() { return HEADER_HEIGHT + 14; }
    private int listWidth() { return Math.max(190, Math.min(310, width * 31 / 100)); }
    private int listHeight() { return Math.max(100, height - listY() - 18); }
    private int detailX() { return listX() + listWidth() + 14; }

    private static int qualityColor(ItemStack stack) {
        return switch (ClientDataCache.INSTANCE.getQuality(stack)) {
            case "red" -> 0xAA8A2525;
            case "gold" -> 0xAA8B6A20;
            case "purple" -> 0xAA633A86;
            case "blue" -> 0xAA285A84;
            case "green" -> 0xAA2E6E50;
            default -> 0xAA384447;
        };
    }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void tick() {
        super.tick();
        transition.tick(minecraft);
        if (!pendingEquippedId.isBlank()
            && pendingEquippedId.equals(KnifeAccessClientState.INSTANCE.selected())) {
            pendingEquippedId = "";
        }
    }

    @Override protected void renderBlurredBackground(float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        transition.beginClose(() -> Minecraft.getInstance().setScreen(parent));
    }

    private record KnifeEntry(String itemId, ItemStack stack, KnifeDisplayStats stats) {}
}
