package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.GridItemRenderer;
import com.xtdpotato.xero_delta.client.PlayerStatusClientState;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import com.xtdpotato.xero_delta.client.TradingUi;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.network.CardHolderSelectPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/** Full-screen selector for card holders already owned by the player. */
public final class CardHolderPickerScreen extends Screen {
    private static final ResourceLocation CLOSE_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/cross_button");
    private static final ResourceLocation CLOSE_HOVERED_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/cross_button_highlighted");
    private static final TagKey<Item> CARD_HOLDERS = TagKey.create(Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath("curios", "card_holder"));
    private static final int MARGIN = 18;
    private static final int HEADER_HEIGHT = 42;
    private static final int ROW_HEIGHT = 54;
    private static final int BUTTON_WIDTH = 116;
    private static final int BUTTON_HEIGHT = 28;

    private final Screen parent;
    private final ScreenTransition transition = new ScreenTransition();
    private final List<Entry> entries = new ArrayList<>();
    private int selected = -1;
    private double scroll;
    private double targetScroll;
    private boolean dragging;
    private double dragStartY;
    private double dragStartScroll;
    private String pendingItemId = "";

    public CardHolderPickerScreen(Screen parent) {
        super(Component.translatable("card_holder.xero_delta.picker_title"));
        this.parent = parent;
        collectEntries();
        if (!entries.isEmpty()) selected = 0;
    }

    private void collectEntries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        CuriosApi.getCuriosInventory(minecraft.player)
            .flatMap(curios -> curios.getStacksHandler("card_holder"))
            .filter(handler -> handler.getSlots() > 0)
            .map(handler -> handler.getStacks().getStackInSlot(0))
            .filter(stack -> !stack.isEmpty())
            .ifPresent(stack -> entries.add(new Entry(-2, stack.copy(), true)));
        ItemStack carried = minecraft.player.containerMenu.getCarried();
        if (isCardHolder(carried)) entries.add(new Entry(-1, carried.copy(), false));
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (isCardHolder(stack)) entries.add(new Entry(slot, stack.copy(), false));
        }
    }

    private static boolean isCardHolder(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(CARD_HOLDERS);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tickSmoothScroll();
        graphics.fill(0, 0, width, height, 0xF0060B0D);
        transition.push(graphics);
        graphics.fill(0, 0, width, HEADER_HEIGHT, 0xFF101719);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, 0xFF33403F);
        graphics.drawString(font, title, MARGIN, 11, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        graphics.drawString(font, Component.translatable(
            "card_holder.xero_delta.picker_subtitle"), MARGIN, 25, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        drawCloseButton(graphics, mouseX, mouseY);
        drawList(graphics, mouseX, mouseY);
        drawDetails(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        transition.pop(graphics);
        transition.drawFade(graphics, width, height);
    }

    private void drawCloseButton(GuiGraphics graphics, int mouseX, int mouseY) {
        TradingUi.drawBackButton(graphics, font, width, mouseX, mouseY);
    }

    private void drawList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = listX();
        int y = listY();
        int listWidth = listWidth();
        int listHeight = listHeight();
        graphics.fill(x, y, x + listWidth, y + listHeight, 0xE80D1416);
        graphics.renderOutline(x, y, listWidth, listHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, Component.translatable(
            "card_holder.xero_delta.available"), x + 9, y + 8, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        int rowsY = y + 28;
        int rowsHeight = listHeight - 36;
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(
                "card_holder.xero_delta.none_available"),
                x + listWidth / 2, rowsY + 20, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
            return;
        }
        graphics.enableScissor(x + 1, rowsY, x + listWidth - 7, rowsY + rowsHeight);
        for (int index = 0; index < entries.size(); index++) {
            int rowY = rowsY + index * ROW_HEIGHT - (int) Math.round(scroll);
            if (rowY + ROW_HEIGHT <= rowsY || rowY >= rowsY + rowsHeight) continue;
            Entry entry = entries.get(index);
            boolean hovered = inside(mouseX, mouseY, x + 5, rowY + 2,
                listWidth - 15, ROW_HEIGHT - 4);
            int background = index == selected ? 0xFF243B35
                : hovered ? 0xFF202D2D : 0xFF141D1F;
            graphics.fill(x + 5, rowY + 2, x + listWidth - 10,
                rowY + ROW_HEIGHT - 2, background);
            graphics.renderOutline(x + 5, rowY + 2, listWidth - 15,
                ROW_HEIGHT - 4, index == selected ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
            // 34px leaves a 32px item surface after the renderer's one-pixel
            // inset, keeping the 16px item sprite on an exact 2x scale.
            renderCardItem(graphics, entry.stack(), x + 10, rowY + 10, 34, 34);
            graphics.drawString(font, entry.stack().getHoverName(),
                x + 58, rowY + 13, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
            Component state = entry.equipped()
                ? Component.translatable("card_holder.xero_delta.equipped")
                : Component.translatable("card_holder.xero_delta.owned");
            graphics.drawString(font, state, x + 58, rowY + 29,
                entry.equipped() ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, x + listWidth - 5, rowsY, rowsHeight);
    }

    private void drawDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = listX() + listWidth() + 14;
        int y = listY();
        int detailWidth = width - x - MARGIN;
        int detailHeight = listHeight();
        graphics.fill(x, y, x + detailWidth, y + detailHeight, 0xE80D1416);
        graphics.renderOutline(x, y, detailWidth, detailHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        Entry entry = selectedEntry();
        if (entry == null) {
            graphics.drawCenteredString(font, Component.translatable(
                "card_holder.xero_delta.select_prompt"),
                x + detailWidth / 2, y + detailHeight / 2, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
            return;
        }
        // 66px produces an exact 64px (4x) item surface instead of the
        // fractional 70px scale that made card-holder previews look blurred.
        renderCardItem(graphics, entry.stack(), x + 18, y + 22, 66, 66);
        graphics.drawString(font, entry.stack().getHoverName(), x + 106, y + 28, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT, false);
        if (entry.stack().getItem() instanceof DeltaPackItem pack) {
            graphics.drawString(font, Component.translatable(
                "card_holder.xero_delta.capacity", pack.capacity(),
                pack.gridWidth(), pack.gridHeight()), x + 106, y + 49, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        }
        graphics.drawString(font, Component.translatable(
            "card_holder.xero_delta.description"), x + 18, y + 116, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        drawEquipButton(graphics, entry, x + detailWidth - BUTTON_WIDTH - 16,
            y + detailHeight - BUTTON_HEIGHT - 14, mouseX, mouseY);
    }

    private void renderCardItem(GuiGraphics graphics, ItemStack stack,
                                int x, int y, int width, int height) {
        GridItemRenderer.renderSizedItem(graphics, font, stack, x, y, width, height,
            GridBackingStore.isRotated(stack),
            ClientDataCache.INSTANCE.shouldRotateTexture(stack),
            true, ClientDataCache.INSTANCE.proportionalTextureScale(stack));
    }

    private void drawEquipButton(GuiGraphics graphics, Entry entry, int x, int y,
                                 int mouseX, int mouseY) {
        boolean permitted = PlayerStatusClientState.INSTANCE.canChangeBc();
        boolean equipped = entry.equipped() || isEquipped(entry.stack());
        boolean enabled = permitted && !equipped && entry.sourceSlot() >= -1;
        boolean hovered = enabled && inside(mouseX, mouseY, x, y,
            BUTTON_WIDTH, BUTTON_HEIGHT);
        graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT,
            !permitted ? 0xFF2B3234 : equipped ? 0xFF273F37
                : hovered ? 0xFF4BAA82 : 0xFF357B5D);
        graphics.renderOutline(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
            hovered ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : equipped ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : com.xtdpotato.xero_delta.screen.material.Material3Theme.OUTLINE_VARIANT);
        Component label = Component.translatable(!permitted
            ? "card_holder.xero_delta.no_permission"
            : equipped ? "card_holder.xero_delta.equipped"
                : "card_holder.xero_delta.equip");
        graphics.drawCenteredString(font, label, x + BUTTON_WIDTH / 2, y + 10,
            enabled || equipped ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y, int height) {
        int visibleHeight = height;
        int contentHeight = entries.size() * ROW_HEIGHT;
        if (contentHeight <= visibleHeight) return;
        graphics.fill(x, y, x + 3, y + height, 0xFF0A1012);
        int thumbHeight = Math.max(20, height * height / contentHeight);
        int travel = height - thumbHeight;
        int thumbY = y + (int) Math.round(travel * scroll
            / Math.max(1, contentHeight - visibleHeight));
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (inside(mouseX, mouseY, width - 30, 8, 22, 22)) {
            onClose();
            return true;
        }
        int rowsY = listY() + 28;
        int rowsHeight = listHeight() - 36;
        if (inside(mouseX, mouseY, listX() + 5, rowsY,
            listWidth() - 15, rowsHeight)) {
            int index = (int) Math.floor((mouseY - rowsY + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < entries.size()) selected = index;
            dragging = true;
            dragStartY = mouseY;
            dragStartScroll = targetScroll;
            return true;
        }
        Entry entry = selectedEntry();
        int detailX = listX() + listWidth() + 14;
        int buttonX = width - MARGIN - BUTTON_WIDTH - 16;
        int buttonY = listY() + listHeight() - BUTTON_HEIGHT - 14;
        if (entry != null && entry.sourceSlot() >= -1
            && PlayerStatusClientState.INSTANCE.canChangeBc()
            && !isEquipped(entry.stack())
            && inside(mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            pendingItemId = itemId(entry.stack());
            ModNetwork.sendToServer(new CardHolderSelectPacket(
                entry.sourceSlot(), pendingItemId));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && dragging) {
            targetScroll = dragStartScroll - (mouseY - dragStartY);
            clampScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, listX(), listY(), listWidth(), listHeight())) {
            targetScroll -= scrollY * 24.0D;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void tickSmoothScroll() {
        scroll += (targetScroll - scroll) * 0.24D;
        if (Math.abs(targetScroll - scroll) < 0.05D) scroll = targetScroll;
    }

    private void clampScroll() {
        int maximum = Math.max(0, entries.size() * ROW_HEIGHT - (listHeight() - 36));
        targetScroll = Math.max(0.0D, Math.min(maximum, targetScroll));
        scroll = Math.max(0.0D, Math.min(maximum, scroll));
    }

    private boolean isEquipped(ItemStack stack) {
        String id = itemId(stack);
        if (!pendingItemId.isBlank() && pendingItemId.equals(id)) return true;
        if (minecraft == null || minecraft.player == null) return false;
        return CuriosApi.getCuriosInventory(minecraft.player)
            .flatMap(curios -> curios.getStacksHandler("card_holder"))
            .filter(handler -> handler.getSlots() > 0)
            .map(handler -> itemId(handler.getStacks().getStackInSlot(0)).equals(id))
            .orElse(false);
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private Entry selectedEntry() {
        return selected >= 0 && selected < entries.size() ? entries.get(selected) : null;
    }

    private int listX() { return MARGIN; }
    private int listY() { return HEADER_HEIGHT + 14; }
    private int listWidth() { return Math.max(210, Math.min(310, width * 32 / 100)); }
    private int listHeight() { return Math.max(120, height - listY() - MARGIN); }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void onClose() {
        transition.beginClose(() -> Minecraft.getInstance().setScreen(parent));
    }

    @Override
    public void tick() {
        super.tick();
        transition.tick(minecraft);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Match the knife and safety-box pickers: the fullscreen UI stays sharp. */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    private record Entry(int sourceSlot, ItemStack stack, boolean equipped) {}
}

