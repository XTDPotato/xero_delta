package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.TradingUi;
import com.xtdpotato.xero_delta.client.FtbQuestIntegration;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Modal attachment type chooser used by the creative mail composer. */
final class MailAttachmentPickerScreen extends Screen {
    private static final float FOREGROUND_Z = 1_400.0F;
    private final MailComposerScreen parent;
    private final ScreenTransition transition = new ScreenTransition();
    private EditBox amountBox;
    private EditBox displayBox;
    private EditBox labelBox;
    private EditBox taskBox;
    private String savedAmount = "";
    private String savedDisplay = "";
    private String savedLabel = "";
    private String savedTask = "";
    private String error = "";

    MailAttachmentPickerScreen(MailComposerScreen parent) {
        super(Component.translatable("mail.xero_delta.add_attachment"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = panelX(), y = panelY();
        amountBox = addRenderableWidget(new Material3CompactEditBox(font, x + 24, y + 61, 166, 20,
            Component.translatable("mail.xero_delta.amount")));
        amountBox.setHint(Component.translatable("mail.xero_delta.amount"));
        amountBox.setFilter(value -> value.isEmpty() || value.matches("\\d{0,12}"));
        amountBox.setValue(savedAmount);
        displayBox = addRenderableWidget(new Material3CompactEditBox(font, x + 24, y + 88, 492, 20,
            Component.translatable("mail.xero_delta.display_text")));
        displayBox.setHint(Component.translatable("mail.xero_delta.display_text"));
        displayBox.setMaxLength(128); displayBox.setValue(savedDisplay);
        labelBox = addRenderableWidget(new Material3CompactEditBox(font, x + 24, y + 151, 270, 20,
            Component.translatable("mail.xero_delta.action_label")));
        labelBox.setHint(Component.translatable("mail.xero_delta.action_label"));
        labelBox.setMaxLength(128); labelBox.setValue(savedLabel);
        taskBox = addRenderableWidget(new Material3CompactEditBox(font, x + 24, y + 238, 270, 20,
            Component.translatable("mail.xero_delta.task_id")));
        taskBox.setHint(Component.translatable("mail.xero_delta.task_id"));
        taskBox.setMaxLength(256); taskBox.setValue(savedTask);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Material3Theme.SCRIM);
        transition.push(g);
        g.flush(); g.pose().pushPose(); g.pose().translate(0, 0, FOREGROUND_Z);
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight();
        Material2Drawing.roundedRect(g, x, y, w, h, Material3Theme.RADIUS_MEDIUM,
            Material3Theme.SURFACE_CONTAINER);
        border(g, x, y, w, h, Material3Theme.OUTLINE_VARIANT);
        g.drawString(font, title, x + 18, y + 17, Material3Theme.TEXT, false);
        drawClose(g, x + w - 28, y + 10, mouseX, mouseY);

        g.drawString(font, Component.translatable("mail.xero_delta.amount_attachment"),
            x + 24, y + 43, 0xFFFFD36A, false);
        drawTypeButton(g, x + 202, y + 57, 96, 28,
            Component.translatable("mail.xero_delta.currency_short").getString(), ItemStack.EMPTY, true, mouseX, mouseY);
        drawTypeButton(g, x + 304, y + 57, 96, 28,
            Component.translatable("mail.xero_delta.xp_short").getString(), Items.EXPERIENCE_BOTTLE.getDefaultInstance(), false, mouseX, mouseY);
        drawTypeButton(g, x + 406, y + 57, 110, 28,
            Component.translatable("mail.xero_delta.levels_short").getString(), Items.ENCHANTING_TABLE.getDefaultInstance(), false, mouseX, mouseY);

        g.drawString(font, Component.translatable("mail.xero_delta.item_actions"),
            x + 24, y + 112, 0xFFFFD36A, false);
        drawTypeButton(g, x + 304, y + 147, 102, 28,
            Component.translatable("mail.xero_delta.add_item").getString(), Items.STICK.getDefaultInstance(), false, mouseX, mouseY);
        drawTypeButton(g, x + 412, y + 147, 104, 28,
            Component.translatable("mail.xero_delta.add_recipe").getString(), Items.CRAFTING_TABLE.getDefaultInstance(), false, mouseX, mouseY);

        g.drawString(font, Component.translatable("mail.xero_delta.task_attachment"),
            x + 24, y + 205, 0xFFFFD36A, false);
        drawTypeButton(g, x + 304, y + 234, 102, 28,
            Component.translatable("mail.xero_delta.pick_task_short").getString(), Items.WRITABLE_BOOK.getDefaultInstance(), false, mouseX, mouseY);
        drawTypeButton(g, x + 412, y + 234, 104, 28,
            Component.translatable("mail.xero_delta.add_task_id").getString(), Items.WRITABLE_BOOK.getDefaultInstance(), false, mouseX, mouseY);
        if (!error.isBlank()) g.drawString(font, error, x + 24, y + h - 26, 0xFFFF8D82, false);
        for (var renderable : renderables) renderable.render(g, mouseX, mouseY, partialTick);
        g.flush(); g.pose().popPose();
        transition.pop(g);
        transition.drawFade(g, width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (transition.closing()) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int x = panelX(), y = panelY(), w = panelWidth();
        if (inside(mouseX, mouseY, x + w - 28, y + 10, 20, 20)) { onClose(); return true; }
        if (inside(mouseX, mouseY, x + 202, y + 57, 96, 28)) return addAmount("currency");
        if (inside(mouseX, mouseY, x + 304, y + 57, 96, 28)) return addAmount("experience");
        if (inside(mouseX, mouseY, x + 406, y + 57, 110, 28)) return addAmount("levels");
        if (inside(mouseX, mouseY, x + 304, y + 147, 102, 28)) {
            saveFields();
            minecraft.setScreen(MailItemPickerScreen.attachment(parent, true, stacks -> {
                List<MailComposerScreen.DraftAttachment> values = new ArrayList<>();
                for (ItemStack stack : stacks) values.add(new MailComposerScreen.DraftAttachment(
                    "item", 0L, stack.copy(), "", ""));
                parent.addAttachments(values);
            }));
            return true;
        }
        if (inside(mouseX, mouseY, x + 412, y + 147, 104, 28)) {
            saveFields();
            minecraft.setScreen(MailItemPickerScreen.attachment(parent, false, stacks -> {
                List<MailComposerScreen.DraftAttachment> values = new ArrayList<>();
                for (ItemStack stack : stacks) values.add(new MailComposerScreen.DraftAttachment(
                    "recipe", 0L, stack.copyWithCount(1),
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    savedLabel.isBlank() ? "查看配方" : savedLabel));
                parent.addAttachments(values);
            }));
            return true;
        }
        if (inside(mouseX, mouseY, x + 304, y + 234, 102, 28)) {
            saveFields();
            minecraft.setScreen(new MailFtbTaskPickerScreen(parent, this::addSelectedTask));
            return true;
        }
        if (inside(mouseX, mouseY, x + 412, y + 234, 104, 28)) {
            saveFields();
            if (savedTask.isBlank()) {
                error = Component.translatable("mail.xero_delta.error.task_id").getString();
            } else {
                parent.addAttachments(List.of(new MailComposerScreen.DraftAttachment("ftb_task", 0L,
                    Items.WRITABLE_BOOK.getDefaultInstance(), savedTask,
                    savedLabel.isBlank() ? "打开任务" : savedLabel)));
                taskBox.setValue(""); savedTask = ""; error = "";
                transition.beginClose(() -> minecraft.setScreen(parent));
            }
            return true;
        }
        return false;
    }

    private void addSelectedTask(FtbQuestIntegration.Match task) {
        if (task == null) return;
        String label = savedLabel.isBlank()
            ? task.questTitle().getString() + "：" + task.taskTitle().getString() : savedLabel;
        parent.addAttachments(List.of(new MailComposerScreen.DraftAttachment("ftb_task", 0L,
            Items.WRITABLE_BOOK.getDefaultInstance(), Long.toString(task.taskId()), label)));
        error = "";
    }

    private boolean addAmount(String type) {
        saveFields();
        try {
            long amount = Long.parseLong(savedAmount);
            if (amount <= 0L) throw new NumberFormatException();
            parent.addAttachments(List.of(new MailComposerScreen.DraftAttachment(
                type, amount, ItemStack.EMPTY, "", savedDisplay)));
            amountBox.setValue(""); displayBox.setValue("");
            savedAmount = ""; savedDisplay = ""; error = "";
            transition.beginClose(() -> minecraft.setScreen(parent));
        } catch (NumberFormatException ignored) {
            error = Component.translatable("mail.xero_delta.error.amount").getString();
        }
        return true;
    }

    private void saveFields() {
        savedAmount = amountBox == null ? savedAmount : amountBox.getValue();
        savedDisplay = displayBox == null ? savedDisplay : displayBox.getValue();
        savedLabel = labelBox == null ? savedLabel : labelBox.getValue();
        savedTask = taskBox == null ? savedTask : taskBox.getValue();
    }

    @Override
    public void tick() {
        super.tick();
        transition.tick(minecraft);
    }

    @Override public void onClose() {
        saveFields();
        transition.beginClose(() -> minecraft.setScreen(parent));
    }

    private void drawTypeButton(GuiGraphics g, int x, int y, int w, int h, String text,
                                ItemStack icon, boolean coin, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? 0xFF5B727A : 0xFF3B4B51);
        border(g, x, y, w, h, hover ? 0xFFFFFFFF : 0xFF586B72);
        int textX = x + 9;
        if (coin) { TradingUi.drawAmount(g, font, 0L, x + 8, y + 9, 0x00FFFFFF); textX = x + 24; }
        else if (!icon.isEmpty()) { g.renderItem(icon, x + 7, y + 6); textX = x + 28; }
        g.drawString(font, font.plainSubstrByWidth(text, w - (textX - x) - 5), textX, y + 10, 0xFFFFFFFF, false);
    }

    private void drawClose(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, 20, 20);
        g.fill(x, y, x + 20, y + 20, hover ? 0xFF6C5555 : 0xFF453C3C);
        for (int i = 0; i < 7; i++) {
            g.fill(x + 5 + i, y + 6 + i, x + 7 + i, y + 8 + i, 0xFFFFFFFF);
            g.fill(x + 11 - i, y + 6 + i, x + 13 - i, y + 8 + i, 0xFFFFFFFF);
        }
    }

    private int panelWidth() { return Math.min(550, width - 24); }
    private int panelHeight() { return Math.min(310, height - 24); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return (height - panelHeight()) / 2; }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color);
    }
}

