package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.MailClientState;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import com.xtdpotato.xero_delta.mail.MailPayloadParser;
import com.xtdpotato.xero_delta.network.MailActionPacket;
import com.xtdpotato.xero_delta.network.MailSyncPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Searchable sent-mail history with details, multi-selection, recall, resend and delete. */
final class MailSentScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int ROW_HEIGHT = 58;
    private final Screen parent;
    private final MailClientState state = MailClientState.INSTANCE;
    private final ScreenTransition transition = new ScreenTransition();
    private final Set<UUID> selected = new LinkedHashSet<>();
    private EditBox search;
    private UUID selectionAnchor;
    private UUID detailId;
    private int listScroll;
    private int detailScroll;

    MailSentScreen(Screen parent) {
        super(Component.translatable("mail.xero_delta.sent_mail"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = panelX(), y = panelY();
        search = addRenderableWidget(new Material3CompactEditBox(font, x + 18, y + 42,
            leftWidth() - 36, 20, Component.translatable("mail.xero_delta.search_sent")));
        search.setHint(Component.translatable("mail.xero_delta.search_sent"));
        search.setMaxLength(128);
        search.setResponder(ignored -> listScroll = 0);
        ModNetwork.sendToServer(MailActionPacket.refresh());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Material3Theme.SCRIM);
        transition.push(g);
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight();
        int split = x + leftWidth();
        Material2Drawing.roundedRect(g, x, y, w, h, Material3Theme.RADIUS_MEDIUM,
            Material3Theme.SURFACE_CONTAINER);
        border(g, x, y, w, h, 0xFF596B72);
        g.fill(split, y + 38, split + 1, y + h - 12, Material3Theme.OUTLINE_VARIANT);
        g.drawString(font, title, x + 18, y + 17, Material3Theme.TEXT, false);
        drawClose(g, x + w - 28, y + 10, mouseX, mouseY);

        List<MailSyncPacket.SentView> values = filtered();
        int top = y + 70, bottom = y + h - 48;
        int visibleRows = Math.max(1, (bottom - top) / ROW_HEIGHT);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, values.size() - visibleRows)));
        g.enableScissor(x + 10, top, split - 8, bottom);
        for (int i = listScroll; i < Math.min(values.size(), listScroll + visibleRows + 1); i++) {
            MailSyncPacket.SentView sent = values.get(i);
            int rowY = top + (i - listScroll) * ROW_HEIGHT;
            drawSentRow(g, sent, x + 14, rowY, leftWidth() - 28, mouseX, mouseY);
        }
        g.disableScissor();
        if (values.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("mail.xero_delta.no_sent_mail"),
                x + leftWidth() / 2, (top + bottom) / 2, 0xFF879497);
        }

        drawDetail(g, selectedDetail(), split + 14, y + 44,
            x + w - split - 28, h - 60, mouseX, mouseY);
        if (!selected.isEmpty()) {
            String count = Component.translatable("mail.xero_delta.selected_count", selected.size()).getString();
            g.drawString(font, count, x + 16, y + h - 30, 0xFFFFD36A, false);
            drawButton(g, split - 172, y + h - 38, 76, 24,
                Component.translatable("mail.xero_delta.resend").getString(), mouseX, mouseY);
            drawButton(g, split - 90, y + h - 38, 76, 24,
                Component.translatable("mail.xero_delta.delete_sent").getString(), mouseX, mouseY);
        }
        for (var renderable : renderables) renderable.render(g, mouseX, mouseY, partialTick);
        transition.pop(g);
        transition.drawFade(g, width, height);
    }

    private void drawSentRow(GuiGraphics g, MailSyncPacket.SentView sent, int x, int y, int width,
                             int mouseX, int mouseY) {
        boolean chosen = selected.contains(sent.id());
        boolean detail = sent.id().equals(detailId);
        boolean hover = inside(mouseX, mouseY, x, y, width, ROW_HEIGHT - 5);
        g.fill(x, y, x + width, y + ROW_HEIGHT - 5,
            detail ? 0xEE3A4A50 : hover ? 0xDD344247 : 0xCC2D383D);
        border(g, x, y, width, ROW_HEIGHT - 5, chosen || detail ? 0xFFFFFFFF : 0xFF506168);
        if (chosen) g.fill(x + 4, y + 4, x + 8, y + ROW_HEIGHT - 9, 0xFFFFD36A);
        g.drawString(font, font.plainSubstrByWidth(sent.title(), width - 118),
            x + 14, y + 8, sent.retracted() ? 0xFF9BA5A7 : 0xFFFFFFFF, false);
        String meta = sent.recipients() + " · " + TIME.format(Instant.ofEpochMilli(
            sent.createdAtEpochMillis()).atZone(ZoneId.systemDefault()));
        g.drawString(font, font.plainSubstrByWidth(meta, width - 118),
            x + 14, y + 28, 0xFF93A1A4, false);
        String label = Component.translatable(sent.retracted()
            ? "mail.xero_delta.resend" : "mail.xero_delta.retract").getString();
        drawButton(g, x + width - 92, y + 7, 80, 20, label, mouseX, mouseY);
        if (sent.retracted()) {
            drawButton(g, x + width - 92, y + 30, 80, 18,
                Component.translatable("mail.xero_delta.delete_sent").getString(), mouseX, mouseY);
        }
    }

    private void drawDetail(GuiGraphics g, MailSyncPacket.SentView sent, int x, int y,
                            int width, int height, int mouseX, int mouseY) {
        g.fill(x, y, x + width, y + height, 0xA9242E32);
        border(g, x, y, width, height, 0xFF46585F);
        if (sent == null) {
            g.drawCenteredString(font, Component.translatable("mail.xero_delta.select_sent_detail"),
                x + width / 2, y + height / 2, 0xFF879497);
            return;
        }
        MailPayloadParser.Draft draft;
        try {
            draft = MailPayloadParser.parse(sent.payloadJson(), "");
        } catch (RuntimeException ignored) {
            draft = new MailPayloadParser.Draft(sent.title(), "", "", List.of());
        }
        g.drawString(font, draft.title(), x + 12, y + 12, 0xFFFFFFFF, false);
        g.drawString(font, Component.translatable("mail.xero_delta.recipients")
            .append(": " + sent.recipients()), x + 12, y + 29, 0xFF9DAAAC, false);
        int contentTop = y + 50, contentBottom = y + height - 10;
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String paragraph : draft.text().split("\n", -1)) {
            List<FormattedCharSequence> wrapped = font.split(Component.literal(paragraph), Math.max(30, width - 30));
            if (wrapped.isEmpty()) lines.add(Component.literal("").getVisualOrderText());
            else lines.addAll(wrapped);
        }
        if (!draft.attachments().isEmpty()) {
            lines.add(Component.literal("").getVisualOrderText());
            lines.add(Component.translatable("mail.xero_delta.attachments").getVisualOrderText());
            for (var attachment : draft.attachments()) {
                String value = !attachment.stack().isEmpty() ? attachment.stack().getHoverName().getString()
                    : attachment.amount() > 0 ? Long.toString(attachment.amount())
                    : attachment.label().isBlank() ? attachment.value() : attachment.label();
                lines.add(Component.literal("• " + value).getVisualOrderText());
            }
        }
        int visible = Math.max(1, (contentBottom - contentTop) / 12);
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, lines.size() - visible)));
        g.enableScissor(x + 8, contentTop, x + width - 8, contentBottom);
        for (int i = detailScroll; i < Math.min(lines.size(), detailScroll + visible + 1); i++) {
            g.drawString(font, lines.get(i), x + 12, contentTop + (i - detailScroll) * 12,
                0xFFE1E7E5, false);
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (transition.closing()) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int x = panelX(), y = panelY(), w = panelWidth(), split = x + leftWidth();
        if (inside(mouseX, mouseY, x + w - 28, y + 10, 20, 20)) { onClose(); return true; }
        if (!selected.isEmpty() && inside(mouseX, mouseY, split - 172, y + panelHeight() - 38, 76, 24)) {
            for (UUID id : List.copyOf(selected)) ModNetwork.sendToServer(MailActionPacket.resendSent(id));
            return true;
        }
        if (!selected.isEmpty() && inside(mouseX, mouseY, split - 90, y + panelHeight() - 38, 76, 24)) {
            for (UUID id : List.copyOf(selected)) ModNetwork.sendToServer(MailActionPacket.deleteSent(id));
            selected.clear();
            return true;
        }
        int top = y + 70, index = listScroll + ((int) mouseY - top) / ROW_HEIGHT;
        List<MailSyncPacket.SentView> values = filtered();
        if (mouseX >= x + 14 && mouseX < split - 14 && mouseY >= top && index >= 0 && index < values.size()) {
            MailSyncPacket.SentView sent = values.get(index);
            int rowY = top + (index - listScroll) * ROW_HEIGHT;
            int rowX = x + 14, rowWidth = leftWidth() - 28;
            if (inside(mouseX, mouseY, rowX + rowWidth - 92, rowY + 7, 80, 20)) {
                ModNetwork.sendToServer(sent.retracted()
                    ? MailActionPacket.resendSent(sent.id()) : MailActionPacket.retract(sent.id()));
                return true;
            }
            if (sent.retracted() && inside(mouseX, mouseY,
                rowX + rowWidth - 92, rowY + 30, 80, 18)) {
                ModNetwork.sendToServer(MailActionPacket.deleteSent(sent.id()));
                selected.remove(sent.id());
                return true;
            }
            select(values, sent.id());
            detailId = sent.id();
            detailScroll = 0;
            return true;
        }
        return false;
    }

    private void select(List<MailSyncPacket.SentView> values, UUID clicked) {
        if (Screen.hasShiftDown() && selectionAnchor != null) {
            int start = indexOf(values, selectionAnchor), end = indexOf(values, clicked);
            if (start >= 0 && end >= 0) {
                if (!Screen.hasControlDown()) selected.clear();
                for (int i = Math.min(start, end); i <= Math.max(start, end); i++) selected.add(values.get(i).id());
            }
        } else if (Screen.hasControlDown()) {
            if (!selected.remove(clicked)) selected.add(clicked);
            selectionAnchor = clicked;
        } else {
            selected.clear();
            selected.add(clicked);
            selectionAnchor = clicked;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int split = panelX() + leftWidth();
        if (mouseX < split) listScroll = Math.max(0, listScroll + (scrollY < 0 ? 1 : -1));
        else detailScroll = Math.max(0, detailScroll + (scrollY < 0 ? 1 : -1));
        return true;
    }

    private List<MailSyncPacket.SentView> filtered() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return state.sentMessages();
        return state.sentMessages().stream().filter(sent -> sent.title().toLowerCase(Locale.ROOT).contains(query)
            || sent.recipients().toLowerCase(Locale.ROOT).contains(query)
            || sent.payloadJson().toLowerCase(Locale.ROOT).contains(query)
            || TIME.format(Instant.ofEpochMilli(sent.createdAtEpochMillis())
                .atZone(ZoneId.systemDefault())).contains(query)).toList();
    }

    private MailSyncPacket.SentView selectedDetail() {
        if (detailId == null) return null;
        return state.sentMessages().stream().filter(value -> value.id().equals(detailId)).findFirst().orElse(null);
    }

    private static int indexOf(List<MailSyncPacket.SentView> values, UUID id) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).id().equals(id)) return i;
        return -1;
    }

    @Override
    public void tick() {
        super.tick();
        transition.tick(minecraft);
    }

    @Override public void onClose() {
        transition.beginClose(() -> minecraft.setScreen(parent));
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String text, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? 0xFF566A71 : 0xFF39484D);
        g.drawCenteredString(font, text, x + w / 2, y + Math.max(4, (h - 8) / 2), 0xFFFFFFFF);
    }

    private void drawClose(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, 20, 20);
        g.fill(x, y, x + 20, y + 20, hover ? 0xFF6C5555 : 0xFF453C3C);
        for (int i = 0; i < 7; i++) {
            g.fill(x + 5 + i, y + 6 + i, x + 7 + i, y + 8 + i, 0xFFFFFFFF);
            g.fill(x + 11 - i, y + 6 + i, x + 13 - i, y + 8 + i, 0xFFFFFFFF);
        }
    }

    private int leftWidth() { return Math.max(280, panelWidth() * 46 / 100); }
    private int panelWidth() { return Math.min(980, width - 24); }
    private int panelHeight() { return Math.min(520, height - 24); }
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

