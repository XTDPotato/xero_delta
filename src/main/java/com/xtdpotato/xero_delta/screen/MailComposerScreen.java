package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.xtdpotato.xero_delta.mail.MailCommandFormatter;
import com.xtdpotato.xero_delta.network.MailActionPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/** Creative-only visual mail composer. */
public final class MailComposerScreen extends Screen {
    private static final float FOREGROUND_Z = 1_000.0F;
    static record DraftAttachment(String type, long amount, ItemStack stack, String value, String label) {}

    private final Screen parent;
    private final ScreenTransition transition = new ScreenTransition();
    private EditBox titleBox;
    private EditBox senderBox;
    private EditBox recipientsBox;
    private final EditBox[] bodyLines = new EditBox[16];
    private final List<DraftAttachment> attachments = new ArrayList<>();
    private final Deque<List<String>> undo = new ArrayDeque<>();
    private final Deque<List<String>> redo = new ArrayDeque<>();
    private boolean restoring;
    private int focusedBodyLine;
    private String error = "";
    private String toast = "";
    private long toastUntil;
    private boolean recipientPickerOpen;
    private boolean colorPickerOpen;
    private final Set<String> pickedRecipients = new LinkedHashSet<>();
    private String draftTitle = "";
    private String draftSender = "";
    private String draftRecipients = "";
    private List<String> draftBody = java.util.Collections.nCopies(16, "");
    private int bodyScrollLine;

    public MailComposerScreen(Screen parent) {
        super(Component.translatable("mail.xero_delta.compose"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = panelX(), y = panelY(), w = panelWidth();
        titleBox = field(x + 88, y + 42, w - 110, 19, "mail.xero_delta.title");
        titleBox.setValue(draftTitle);
        senderBox = field(x + 88, y + 68, 190, 19, "mail.xero_delta.sender");
        senderBox.setValue(draftSender);
        recipientsBox = field(x + 365, y + 68, w - 451, 19, "mail.xero_delta.recipients");
        recipientsBox.setValue(draftRecipients);
        int bodyX = x + 22, bodyY = y + 126, bodyW = w - 270;
        for (int i = 0; i < bodyLines.length; i++) {
            final int line = i;
            bodyLines[i] = addRenderableWidget(new Material3CompactEditBox(font, bodyX + 5, bodyY + 5 + i * 19,
                bodyW - 10, 18, Component.translatable("mail.xero_delta.text")));
            bodyLines[i].setBordered(false); bodyLines[i].setMaxLength(1024);
            bodyLines[i].setResponder(value -> { focusedBodyLine = line; recordUndo(); });
            bodyLines[i].setValue(i < draftBody.size() ? draftBody.get(i) : "");
        }
        layoutBodyLines();
        recordUndo();
    }

    private EditBox field(int x, int y, int width, int height, String key) {
        EditBox box = addRenderableWidget(new Material3CompactEditBox(font, x, y, width, height, Component.translatable(key)));
        box.setHint(Component.translatable(key)); box.setMaxLength(1024); return box;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderSharpBackground(g);
        transition.push(g);
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, FOREGROUND_Z);
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight();
        Material2Drawing.roundedRect(g, x, y, w, h, Material3Theme.RADIUS_MEDIUM,
            Material3Theme.SURFACE_CONTAINER);
        border(g, x, y, w, h, Material3Theme.OUTLINE_VARIANT);
        g.drawString(font, title, x + 18, y + 17, 0xFFFFFFFF, false);
        drawButton(g, x + 78, y + 10, 64, 22,
            Component.translatable("mail.xero_delta.sent_mail").getString(), mouseX, mouseY);
        label(g, "mail.xero_delta.title", x + 20, y + 48);
        label(g, "mail.xero_delta.sender", x + 20, y + 74);
        label(g, "mail.xero_delta.recipients", x + 290, y + 74);
        drawButton(g, x + w - 80, y + 68, 58, 19,
            Component.translatable("mail.xero_delta.pick_recipients").getString(), mouseX, mouseY);
        label(g, "mail.xero_delta.text", x + 20, y + 103);
        drawButton(g, x + w - 278, y + 12, 30, 20, "↶", mouseX, mouseY);
        drawButton(g, x + w - 244, y + 12, 30, 20, "↷", mouseX, mouseY);
        drawButton(g, x + w - 210, y + 12, 42, 20,
            Component.translatable("mail.xero_delta.color").getString(), mouseX, mouseY);
        drawButton(g, x + w - 164, y + 12, 24, 20, "B", mouseX, mouseY);
        drawButton(g, x + w - 136, y + 12, 24, 20, "I", mouseX, mouseY);
        drawButton(g, x + w - 108, y + 12, 24, 20, "S", mouseX, mouseY);
        drawButton(g, x + w - 80, y + 12, 24, 20, "渐", mouseX, mouseY);
        drawClose(g, x + w - 24, y + 12, mouseX, mouseY);

        int bodyX = x + 22, bodyY = y + 126, bodyW = w - 270;
        g.fill(bodyX, bodyY, bodyX + bodyW, bodyY + 143, Material3Theme.SURFACE_CONTAINER_HIGH);
        border(g, bodyX, bodyY, bodyW, 143, 0xFF4C5D64);
        int sideX = x + w - 238;
        g.drawString(font, Component.translatable("mail.xero_delta.attachments"), sideX, y + 103, 0xFFFFD36A, false);
        drawButton(g, sideX, y + 122, 210, 23,
            "+ " + Component.translatable("mail.xero_delta.add_attachment").getString(), mouseX, mouseY);

        int attachmentY = y + 154;
        for (int i = 0; i < Math.min(9, attachments.size()); i++) {
            DraftAttachment attachment = attachments.get(i);
            g.fill(sideX, attachmentY + i * 25, sideX + 210, attachmentY + i * 25 + 21, 0xCC303B40);
            String text = attachmentText(attachment);
            g.drawString(font, font.plainSubstrByWidth(text, 174), sideX + 6, attachmentY + 6 + i * 25,
                0xFFE6ECEA, false);
            g.drawCenteredString(font, "−", sideX + 198, attachmentY + 6 + i * 25, 0xFFFF8E82);
        }
        if (attachments.size() > 9) g.drawString(font, "+" + (attachments.size() - 9), sideX, attachmentY + 227, 0xFF9DA9AC, false);

        if (!error.isBlank()) {
            g.drawString(font, error, x + 22, y + h - 29, 0xFFFF8D82, false);
        } else if (!toast.isBlank() && System.currentTimeMillis() < toastUntil) {
            g.drawString(font, toast, x + 22, y + h - 29, 0xFF91D8A8, false);
        }
        drawButton(g, x + w - 242, y + h - 38, 104, 24,
            Component.translatable("mail.xero_delta.copy_command").getString(), mouseX, mouseY);
        drawButton(g, x + w - 128, y + h - 38, 104, 24,
            Component.translatable("mail.xero_delta.send").getString(), mouseX, mouseY);
        // Do not call Screen#render() after the custom foreground: it invokes the
        // vanilla blur/background pass. Only the registered edit boxes belong here.
        for (var renderable : renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
        if (recipientPickerOpen) drawRecipientPicker(g, mouseX, mouseY);
        if (colorPickerOpen) drawColorPicker(g, mouseX, mouseY);
        g.flush();
        g.pose().popPose();
        transition.pop(g);
        transition.drawFade(g, width, height);
    }

    private void renderSharpBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xB0101518);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (transition.closing()) return true;
        if (colorPickerOpen && handleColorPickerClick(mouseX, mouseY, button)) return true;
        if (recipientPickerOpen && handleRecipientPickerClick(mouseX, mouseY, button)) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight(), sideX = x + w - 238;
        if (inside(mouseX, mouseY, x + w - 24, y + 12, 18, 20)) { onClose(); return true; }
        if (inside(mouseX, mouseY, x + 78, y + 10, 64, 22)) {
            captureDraft(); minecraft.setScreen(new MailSentScreen(this)); return true;
        }
        if (inside(mouseX, mouseY, x + w - 278, y + 12, 30, 20)) { undo(); return true; }
        if (inside(mouseX, mouseY, x + w - 244, y + 12, 30, 20)) { redo(); return true; }
        if (inside(mouseX, mouseY, x + w - 210, y + 12, 42, 20)) {
            colorPickerOpen = !colorPickerOpen; return true;
        }
        if (inside(mouseX, mouseY, x + w - 164, y + 12, 24, 20)) { appendToBody("&l"); return true; }
        if (inside(mouseX, mouseY, x + w - 136, y + 12, 24, 20)) { appendToBody("&o"); return true; }
        if (inside(mouseX, mouseY, x + w - 108, y + 12, 24, 20)) { appendToBody("&m"); return true; }
        if (inside(mouseX, mouseY, x + w - 80, y + 12, 24, 20)) {
            appendToBody("<gradient:#FFD76A:#FF6B6B>渐变文本</gradient>"); return true;
        }
        if (inside(mouseX, mouseY, x + w - 80, y + 68, 58, 19)) {
            recipientPickerOpen = !recipientPickerOpen;
            return true;
        }
        if (inside(mouseX, mouseY, sideX, y + 122, 210, 23)) {
            captureDraft();
            minecraft.setScreen(new MailAttachmentPickerScreen(this));
            return true;
        }
        int attachmentY = y + 154;
        for (int i = 0; i < Math.min(9, attachments.size()); i++) {
            if (inside(mouseX, mouseY, sideX + 184, attachmentY + i * 25, 26, 21)) {
                attachments.remove(i); return true;
            }
        }
        if (inside(mouseX, mouseY, x + w - 242, y + h - 38, 104, 24)) {
            copyCommand(); return true;
        }
        if (inside(mouseX, mouseY, x + w - 128, y + h - 38, 104, 24)) { sendMail(); return true; }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) { undo(); return true; }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) { redo(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawRecipientPicker(GuiGraphics g, int mouseX, int mouseY) {
        List<String> names = onlinePlayerNames();
        int visible = Math.min(7, names.size());
        int w = 224, h = 65 + visible * 23;
        int x = panelX() + panelWidth() - w - 22, y = panelY() + 92;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(x, y, x + w, y + h, 0xFA172126);
        border(g, x, y, w, h, 0xFF668087);
        g.drawString(font, Component.translatable("mail.xero_delta.online_recipients"),
            x + 10, y + 10, 0xFFFFFFFF, false);
        boolean allHover = inside(mouseX, mouseY, x + 8, y + 27, w - 16, 20);
        g.fill(x + 8, y + 27, x + w - 8, y + 47, allHover ? 0xFF40545B : 0xFF2C393E);
        g.renderOutline(x + 13, y + 31, 12, 12, 0xFF70848A);
        if ("*".equals(recipientsBox.getValue().trim())) g.fill(x + 16, y + 34, x + 22, y + 40, 0xFFFFD36A);
        g.drawString(font, Component.translatable("mail.xero_delta.all_players"),
            x + 32, y + 33, 0xFFE7EEEC, false);
        for (int i = 0; i < visible; i++) {
            String name = names.get(i);
            int rowY = y + 50 + i * 23;
            boolean hover = inside(mouseX, mouseY, x + 8, rowY, w - 16, 20);
            g.fill(x + 8, rowY, x + w - 8, rowY + 20, hover ? 0xFF40545B : 0xFF2C393E);
            g.renderOutline(x + 13, rowY + 4, 12, 12, 0xFF70848A);
            if (pickedRecipients.contains(name)) g.fill(x + 16, rowY + 7, x + 22, rowY + 13, 0xFFFFD36A);
            g.drawString(font, name, x + 32, rowY + 6, 0xFFE7EEEC, false);
        }
        if (names.isEmpty()) g.drawString(font, Component.translatable("mail.xero_delta.no_online_recipients"),
            x + 12, y + 54, 0xFF8C999C, false);
        g.pose().popPose();
    }

    private boolean handleRecipientPickerClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        List<String> names = onlinePlayerNames();
        int visible = Math.min(7, names.size());
        int w = 224, h = 65 + visible * 23;
        int x = panelX() + panelWidth() - w - 22, y = panelY() + 92;
        if (inside(mouseX, mouseY, x + 8, y + 27, w - 16, 20)) {
            pickedRecipients.clear(); recipientsBox.setValue("*"); recipientPickerOpen = false; return true;
        }
        for (int i = 0; i < visible; i++) {
            int rowY = y + 50 + i * 23;
            if (!inside(mouseX, mouseY, x + 8, rowY, w - 16, 20)) continue;
            String name = names.get(i);
            if (!pickedRecipients.remove(name)) pickedRecipients.add(name);
            recipientsBox.setValue(String.join(",", pickedRecipients));
            return true;
        }
        if (!inside(mouseX, mouseY, x, y, w, h)) {
            recipientPickerOpen = false;
            return false;
        }
        return true;
    }

    private List<String> onlinePlayerNames() {
        if (minecraft == null || minecraft.getConnection() == null) return List.of();
        return minecraft.getConnection().getOnlinePlayers().stream()
            .map(info -> info.getProfile().getName())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    void addAttachments(List<DraftAttachment> values) {
        if (values == null) return;
        int before = attachments.size();
        for (DraftAttachment value : values) {
            if (value != null && attachments.size() < 64) attachments.add(value);
        }
        if (attachments.size() > before) {
            showToast(Component.translatable("mail.xero_delta.attachment_added",
                attachments.size() - before).getString());
        }
    }

    private void captureDraft() {
        if (titleBox != null) draftTitle = titleBox.getValue();
        if (senderBox != null) draftSender = senderBox.getValue();
        if (recipientsBox != null) draftRecipients = recipientsBox.getValue();
        draftBody = currentBody();
    }

    private void layoutBodyLines() {
        int bodyX = panelX() + 27, bodyY = panelY() + 131, bodyW = panelWidth() - 280;
        for (int i = 0; i < bodyLines.length; i++) {
            if (bodyLines[i] == null) continue;
            int visibleIndex = i - bodyScrollLine;
            bodyLines[i].setX(bodyX);
            bodyLines[i].setY(bodyY + visibleIndex * 19);
            bodyLines[i].setWidth(bodyW);
            bodyLines[i].visible = visibleIndex >= 0 && visibleIndex < 7;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int bodyX = panelX() + 22, bodyY = panelY() + 126, bodyW = panelWidth() - 270;
        if (inside(mouseX, mouseY, bodyX, bodyY, bodyW, 143)) {
            bodyScrollLine = Math.max(0, Math.min(bodyLines.length - 7,
                bodyScrollLine + (scrollY < 0 ? 1 : -1)));
            layoutBodyLines();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void drawColorPicker(GuiGraphics g, int mouseX, int mouseY) {
        int[] colors = {0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA,
            0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF,
            0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF};
        int x = panelX() + panelWidth() - 210, y = panelY() + 36;
        g.pose().pushPose(); g.pose().translate(0, 0, 600);
        g.fill(x - 5, y - 5, x + 81, y + 81, 0xFA172126);
        border(g, x - 5, y - 5, 86, 86, 0xFF668087);
        for (int i = 0; i < colors.length; i++) {
            int cx = x + (i % 4) * 19, cy = y + (i / 4) * 19;
            g.fill(cx, cy, cx + 16, cy + 16, colors[i]);
            border(g, cx, cy, 16, 16, inside(mouseX, mouseY, cx, cy, 16, 16)
                ? 0xFFFFFFFF : 0xFF69777A);
        }
        g.pose().popPose();
    }

    private boolean handleColorPickerClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        int x = panelX() + panelWidth() - 210, y = panelY() + 36;
        if (!inside(mouseX, mouseY, x - 5, y - 5, 86, 86)) {
            colorPickerOpen = false; return false;
        }
        if (!inside(mouseX, mouseY, x, y, 76, 76)) return true;
        int column = ((int) mouseX - x) / 19, row = ((int) mouseY - y) / 19;
        int index = row * 4 + column;
        if (index >= 0 && index < 16) {
            appendToBody("&" + "0123456789abcdef".charAt(index));
            colorPickerOpen = false;
        }
        return true;
    }

    private void sendMail() {
        captureDraft();
        if (draftRecipients.isBlank()) {
            error = Component.translatable("mail.xero_delta.error.recipient").getString(); return;
        }
        String json = createPayloadJson();
        ModNetwork.sendToServer(MailActionPacket.send(draftRecipients, json));
        transition.beginClose(() -> minecraft.setScreen(parent));
    }

    private void copyCommand() {
        captureDraft();
        if (draftRecipients.isBlank()) {
            error = Component.translatable("mail.xero_delta.error.recipient").getString();
            return;
        }
        minecraft.keyboardHandler.setClipboard(MailCommandFormatter.format(
            draftRecipients, createPayloadJson()));
        error = "";
        showToast(Component.translatable("mail.xero_delta.command_copied").getString());
    }

    void showToast(String message) {
        toast = message == null ? "" : message;
        toastUntil = System.currentTimeMillis() + 2600L;
    }

    private String createPayloadJson() {
        JsonObject root = new JsonObject();
        root.addProperty("title", draftTitle);
        root.addProperty("sender", draftSender);
        root.addProperty("text", String.join("\n", draftBody));
        JsonArray array = new JsonArray();
        for (DraftAttachment attachment : attachments) {
            JsonObject value = new JsonObject(); value.addProperty("type", attachment.type());
            if (attachment.amount() > 0L) value.addProperty("amount", attachment.amount());
            if (!attachment.stack().isEmpty()) {
                String iconId = BuiltInRegistries.ITEM.getKey(attachment.stack().getItem()).toString();
                value.addProperty("item", iconId);
                value.addProperty("icon", iconId);
                value.addProperty("count", attachment.stack().getCount());
            }
            if (!attachment.value().isBlank()) value.addProperty(
                "ftb_task".equals(attachment.type()) ? "task" : "value", attachment.value());
            if (!attachment.label().isBlank()) value.addProperty("label", attachment.label());
            array.add(value);
        }
        root.add("attachment", array);
        return root.toString();
    }

    private void appendToBody(String text) {
        int index = Math.max(0, Math.min(bodyLines.length - 1, focusedBodyLine));
        bodyLines[index].setValue(bodyLines[index].getValue() + text);
        bodyLines[index].setFocused(true);
    }

    private void recordUndo() {
        if (restoring) return;
        List<String> snapshot = currentBody();
        if (!undo.isEmpty() && undo.peekLast().equals(snapshot)) return;
        undo.addLast(snapshot); while (undo.size() > 100) undo.removeFirst(); redo.clear();
    }

    private void undo() {
        if (undo.size() <= 1) return;
        redo.addLast(undo.removeLast()); restore(undo.peekLast());
    }

    private void redo() {
        if (redo.isEmpty()) return;
        List<String> value = redo.removeLast(); undo.addLast(value); restore(value);
    }

    private void restore(List<String> lines) {
        if (lines == null) return;
        restoring = true;
        for (int i = 0; i < bodyLines.length; i++) bodyLines[i].setValue(i < lines.size() ? lines.get(i) : "");
        restoring = false;
    }

    private List<String> currentBody() {
        List<String> values = new ArrayList<>(bodyLines.length);
        for (EditBox line : bodyLines) values.add(line == null ? "" : line.getValue());
        return List.copyOf(values);
    }

    private String attachmentText(DraftAttachment value) {
        if (!value.stack().isEmpty()) return value.type() + ": " + value.stack().getHoverName().getString();
        if (value.amount() > 0L) {
            String display = value.label().isBlank() ? Long.toString(value.amount()) : value.label();
            return value.type() + ": " + display;
        }
        return value.type() + ": " + value.value();
    }

    @Override
    public void tick() {
        super.tick();
        transition.tick(minecraft);
    }

    @Override public void onClose() {
        captureDraft();
        transition.beginClose(() -> minecraft.setScreen(parent));
    }
    private void label(GuiGraphics g, String key, int x, int y) { g.drawString(font, Component.translatable(key), x, y, 0xFFADB9B7, false); }
    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String text, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, h); g.fill(x, y, x + w, y + h, hover ? 0xFF5B727A : 0xFF3B4B51);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }
    private void drawClose(GuiGraphics g, int x, int y, int mx, int my) {
        boolean hover = inside(mx, my, x, y, 18, 20); g.fill(x, y, x + 18, y + 20, hover ? 0xFF6C5555 : 0xFF453C3C);
        for (int i = 0; i < 7; i++) {
            g.fill(x + 5 + i, y + 6 + i, x + 7 + i, y + 8 + i, 0xFFFFFFFF);
            g.fill(x + 11 - i, y + 6 + i, x + 13 - i, y + 8 + i, 0xFFFFFFFF);
        }
    }
    private int panelWidth() { return Math.min(780, width - 24); }
    private int panelHeight() { return Math.min(470, height - 24); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return (height - panelHeight()) / 2; }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color);
    }
}

