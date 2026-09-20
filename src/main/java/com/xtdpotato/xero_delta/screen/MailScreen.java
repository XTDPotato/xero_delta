package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material2Icon;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.FtbQuestIntegration;
import com.xtdpotato.xero_delta.client.MailClientState;
import com.xtdpotato.xero_delta.client.MailTextFormatter;
import com.xtdpotato.xero_delta.client.RecipeViewerIntegration;
import com.xtdpotato.xero_delta.client.TradingUi;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import com.xtdpotato.xero_delta.mail.MailAttachment;
import com.xtdpotato.xero_delta.network.MailActionPacket;
import com.xtdpotato.xero_delta.network.MailSyncPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class MailScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int CARD_HEIGHT = 43;
    private static final float FOREGROUND_Z = 1_000.0F;
    private final Screen returnScreen;
    private final MailClientState state = MailClientState.INSTANCE;
    private final ScreenTransition transition = new ScreenTransition();
    private UUID selectedId;
    private EditBox searchBox;
    private double listScrollPixels;
    private double listTargetPixels;
    private long listAnimationNanos;
    private boolean listPointerDown;
    private boolean listContentDragging;
    private boolean listScrollbarDragging;
    private double listPressY;
    private double listPressScrollPixels;
    private double listLastDragY;
    private long listLastDragNanos;
    private double listDragVelocity;
    private double listScrollbarGrabOffset;
    private int detailScroll;
    private int attachmentScroll;
    private final Set<UUID> selectedAttachments = new LinkedHashSet<>();
    private UUID attachmentAnchor;
    private boolean partialClaimMode;
    private long deleteReadReadyAt;
    private long seenRevision = -1L;
    private long openedAt = System.currentTimeMillis();
    private String toast = "";
    private boolean toastSuccess = true;
    private long toastUntil;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private MailSyncPacket.AttachmentView hoveredAttachment;

    public MailScreen(Screen returnScreen, UUID selectedId) {
        super(Component.translatable("screen.xero_delta.mail"));
        this.returnScreen = returnScreen;
        this.selectedId = selectedId;
    }

    @Override
    protected void init() {
        int x = panelX(), y = panelY();
        searchBox = addRenderableWidget(new Material3CompactEditBox(font, x + 15, y + 43, leftWidth() - 30, 19,
            Component.translatable("mail.xero_delta.search")));
        searchBox.setHint(Component.translatable("mail.xero_delta.search_hint"));
        searchBox.setMaxLength(128);
        searchBox.setResponder(ignored -> resetListScroll());
        if (selectedId == null && !state.messages().isEmpty()) selectedId = state.messages().getFirst().id();
        ModNetwork.sendToServer(MailActionPacket.refresh());
    }

    public void acceptServerSelection(UUID selected) {
        if (selected != null) selectedId = selected;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderSharpBackground(graphics);
        transition.push(graphics);
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, FOREGROUND_Z);
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight(), left = leftWidth();
        hoveredStack = ItemStack.EMPTY;
        hoveredAttachment = null;
        Material2Drawing.roundedRect(graphics, x, y, w, h,
            Material3Theme.RADIUS_MEDIUM, Material3Theme.SURFACE_CONTAINER);
        border(graphics, x, y, w, h, 0xFF506168);
        graphics.fill(x, y, x + left, y + h, Material3Theme.SURFACE_CONTAINER_HIGH);
        graphics.fill(x + left, y, x + left + 1, y + h, Material3Theme.OUTLINE_VARIANT);
        graphics.drawString(font, Component.translatable("mail.xero_delta.inbox", state.unreadCount()),
            x + 15, y + 16, 0xFFF2F6F4, false);
        drawCloseIcon(graphics, x + w - 29, y + 9, mouseX, mouseY);
        if (state.canCompose()) drawIconButton(graphics, x + w - 57, y + 9, "+", mouseX, mouseY);

        syncRevision();
        drawMailList(graphics, mouseX, mouseY, x, y, left, h);
        drawMailboxActions(graphics, mouseX, mouseY, x, y, left, h);
        drawDetails(graphics, mouseX, mouseY, x + left + 1, y, w - left - 1, h);
        // Screen#render() calls renderBackground(), which runs Minecraft's blur pass.
        // Rendering it here would blur/cover every custom element drawn above and only
        // leave the EditBox sharp. Render registered widgets directly instead.
        for (var renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        drawToast(graphics, x, y, w);
        if (!hoveredStack.isEmpty()) {
            graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
        } else if (hoveredAttachment != null) {
            drawValueAttachmentTooltip(graphics, hoveredAttachment, mouseX, mouseY);
        }
        graphics.flush();
        graphics.pose().popPose();
        transition.pop(graphics);
        transition.drawFade(graphics, width, height);
    }

    private void syncRevision() {
        if (seenRevision == state.revision()) return;
        seenRevision = state.revision();
        if (!state.resultMessage().isBlank()) {
            toast = Component.translatable(state.resultMessage(), state.resultValue()).getString();
            toastSuccess = state.success(); toastUntil = System.currentTimeMillis() + 2600L;
        }
        if (selectedId != null && state.find(selectedId) == null) selectedId = null;
        if (selectedId == null && !filtered().isEmpty()) selectedId = filtered().getFirst().id();
        MailSyncPacket.MessageView selected = state.find(selectedId);
        if (selected == null) {
            selectedAttachments.clear();
            partialClaimMode = false;
        } else {
            selectedAttachments.removeIf(id -> selected.attachments().stream()
                .noneMatch(value -> value.id().equals(id) && value.isClaimable() && !value.claimed()));
            if (selected.attachments().stream()
                .noneMatch(value -> value.isClaimable() && !value.claimed())) {
                partialClaimMode = false;
            }
        }
    }

    private void drawMailList(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        List<MailSyncPacket.MessageView> values = filtered();
        int top = y + 70, bottom = y + height - 72;
        int viewportHeight = Math.max(1, bottom - top);
        double maxScroll = maxListScroll(values.size(), viewportHeight);
        updateSmoothListScroll(maxScroll);
        int firstIndex = Math.max(0, (int) Math.floor(listScrollPixels / CARD_HEIGHT));
        double pixelOffset = listScrollPixels - firstIndex * CARD_HEIGHT;
        graphics.enableScissor(x + 5, top, x + width - 8, bottom);
        for (int i = firstIndex; i < values.size(); i++) {
            var mail = values.get(i);
            int rowY = top + (int) Math.round((i - firstIndex) * CARD_HEIGHT - pixelOffset);
            if (rowY >= bottom) break;
            boolean selected = mail.id().equals(selectedId);
            boolean hovered = !listContentDragging && !listScrollbarDragging
                && mouseX >= x + 8 && mouseX < x + width - 10
                && mouseY >= Math.max(top, rowY) && mouseY < Math.min(bottom, rowY + 38);
            graphics.fill(x + 8, rowY, x + width - 10, rowY + 38,
                selected ? 0xFF42535A : hovered ? 0xE437454B : 0xB82D373C);
            if (selected) border(graphics, x + 8, rowY, width - 18, 38, 0xFFFFFFFF);
            boolean readStyle = mail.read() || selected;
            if (!readStyle) graphics.fill(x + 12, rowY + 8, x + 15, rowY + 30, 0xFFFFC857);
            String title = mail.title();
            int titleX = x + (readStyle ? 14 : 20);
            boolean hasAttachments = !mail.attachments().isEmpty();
            int textRightPadding = hasAttachments ? 42 : 20;
            TradingUi.drawMarquee(graphics, font, title, titleX, rowY + 7,
                width - (titleX - x) - textRightPadding,
                readStyle ? 0xFF7E898C : 0xFFFFFFFF, i * 17L);
            String meta = mail.senderName() + " · " + TIME.format(Instant.ofEpochMilli(mail.createdAtEpochMillis())
                .atZone(ZoneId.systemDefault()));
            TradingUi.drawMarquee(graphics, font, meta, titleX, rowY + 22,
                width - (titleX - x) - textRightPadding,
                readStyle ? 0xFF606C70 : 0xFFB4C1C3, i * 23L);
            if (hasAttachments) {
                boolean hasClaimable = mail.attachments().stream()
                    .anyMatch(MailSyncPacket.AttachmentView::isClaimable);
                boolean allClaimed = hasClaimable && mail.attachments().stream()
                    .filter(MailSyncPacket.AttachmentView::isClaimable)
                    .allMatch(MailSyncPacket.AttachmentView::claimed);
                drawMailAttachmentIcon(graphics, x + width - 35, rowY + 9, allClaimed);
            }
        }
        graphics.disableScissor();
        drawMailListScrollbar(graphics, x, top, width, viewportHeight, values.size(), mouseX, mouseY);
        if (values.isEmpty()) graphics.drawCenteredString(font,
            Component.translatable("mail.xero_delta.empty"), x + width / 2, top + 30, 0xFF879497);
    }

    private void updateSmoothListScroll(double maxScroll) {
        listTargetPixels = clamp(listTargetPixels, 0.0D, maxScroll);
        listScrollPixels = clamp(listScrollPixels, 0.0D, maxScroll);
        long now = System.nanoTime();
        if (listAnimationNanos == 0L) listAnimationNanos = now;
        double seconds = Math.min(0.05D, Math.max(0.0D, (now - listAnimationNanos) / 1_000_000_000.0D));
        listAnimationNanos = now;
        if (listContentDragging || listScrollbarDragging) return;
        double factor = 1.0D - Math.exp(-18.0D * seconds);
        listScrollPixels += (listTargetPixels - listScrollPixels) * factor;
        if (Math.abs(listTargetPixels - listScrollPixels) < 0.05D) listScrollPixels = listTargetPixels;
    }

    private void drawMailListScrollbar(GuiGraphics graphics, int x, int top, int width,
                                       int viewportHeight, int itemCount, int mouseX, int mouseY) {
        int trackX = x + width - 7;
        graphics.fill(trackX, top, trackX + 3, top + viewportHeight, 0x7A1A2327);
        double maxScroll = maxListScroll(itemCount, viewportHeight);
        if (maxScroll <= 0.0D) return;
        int contentHeight = Math.max(viewportHeight, itemCount * CARD_HEIGHT);
        int thumbHeight = Math.max(18, viewportHeight * viewportHeight / contentHeight);
        int travel = Math.max(1, viewportHeight - thumbHeight);
        int thumbY = top + (int) Math.round(travel * listScrollPixels / maxScroll);
        boolean hovered = inside(mouseX, mouseY, trackX - 2, thumbY, 7, thumbHeight);
        int color = listScrollbarDragging ? 0xFFFFFFFF : hovered ? 0xFFD8E2DF : 0xFF859397;
        graphics.fill(trackX - 1, thumbY, trackX + 4, thumbY + thumbHeight, color);
    }

    private void drawMailboxActions(GuiGraphics graphics, int mouseX, int mouseY,
                                    int x, int y, int width, int height) {
        int countY = y + height - 62;
        String count = state.mailboxCount() + "/" + state.mailboxLimit();
        int countWidth = font.width(count);
        int center = x + width / 2;
        graphics.drawString(font, count, center - countWidth / 2 - 7, countY, 0xFFAAB6B4, false);
        int helpX = center + countWidth / 2 + 1;
        drawDiamondHelp(graphics, helpX, countY - 2, mouseX, mouseY);
        if (inside(mouseX, mouseY, helpX - 1, countY - 3, 13, 13)) {
            graphics.renderTooltip(font, Component.translatable("mail.xero_delta.capacity_tip"), mouseX, mouseY);
        }
        int gap = 5, buttonY = y + height - 43, buttonW = (width - 21 - gap) / 2;
        drawTextButton(graphics, x + 8, buttonY, buttonW, 25,
            Component.translatable("mail.xero_delta.claim_inbox").getString(), true, mouseX, mouseY);
        String deleteText = deleteReadButtonText();
        drawTextButton(graphics, x + 8 + buttonW + gap, buttonY, buttonW, 25,
            deleteText, deleteReadReadyAt == 0L || System.currentTimeMillis() >= deleteReadReadyAt,
            mouseX, mouseY);
    }

    private String deleteReadButtonText() {
        if (deleteReadReadyAt == 0L) return Component.translatable("mail.xero_delta.delete_read").getString();
        long remaining = Math.max(0L, deleteReadReadyAt - System.currentTimeMillis());
        if (remaining <= 0L) return Component.translatable("mail.xero_delta.confirm_delete").getString();
        return Component.translatable("mail.xero_delta.delete_countdown", (remaining + 999L) / 1000L).getString();
    }

    private void drawDetails(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        MailSyncPacket.MessageView mail = state.find(selectedId);
        if (mail == null) {
            graphics.drawCenteredString(font, Component.translatable("mail.xero_delta.select"),
                x + width / 2, y + height / 2, 0xFF879497);
            return;
        }
        graphics.drawString(font, mail.title(), x + 22, y + 18, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("mail.xero_delta.from", mail.senderName()),
            x + 22, y + 36, 0xFF9DA9AC, false);
        graphics.drawString(font, TIME.format(Instant.ofEpochMilli(mail.createdAtEpochMillis())
            .atZone(ZoneId.systemDefault())), x + 22, y + 51, 0xFF728084, false);
        String mailId = "ID: " + mail.id();
        String visibleId = font.plainSubstrByWidth(mailId, Math.max(60, width / 2 - 24));
        graphics.drawString(font, visibleId, x + width - 22 - font.width(visibleId), y + 51,
            0xFF728084, false);
        graphics.fill(x + 20, y + 68, x + width - 20, y + 69, 0xFF3E4A4F);

        boolean hasAttachments = !mail.attachments().isEmpty();
        boolean hasUnclaimed = mail.attachments().stream()
            .anyMatch(value -> value.isClaimable() && !value.claimed());
        int separatorY = hasAttachments ? y + height - 94 : y + height - 54;
        int cursorY = y + 82 - detailScroll;
        int bodyWidth = width - 50;
        graphics.enableScissor(x + 18, y + 72, x + width - 18, separatorY - 7);
        for (String rawLine : mail.text().split("\\n", -1)) {
            var wrapped = font.split(MailTextFormatter.parse(rawLine), bodyWidth);
            if (wrapped.isEmpty()) cursorY += 11;
            for (var line : wrapped) {
                graphics.drawString(font, line, x + 24, cursorY, 0xFFFFFFFF, false);
                cursorY += 11;
            }
        }
        graphics.disableScissor();

        if (hasAttachments) {
            graphics.fill(x + 20, separatorY, x + width - 20, separatorY + 1, 0xFF3E4A4F);
            if (hasUnclaimed) {
                graphics.drawString(font, Component.translatable("mail.xero_delta.mail_attachments"),
                    x + 24, separatorY + 9, 0xFFD8E1DF, false);
            }
            drawAttachmentStrip(graphics, mail, x, attachmentStartY(mail, y, width),
                width, mouseX, mouseY);
        }
        drawMailActionButton(graphics, mail, x, y, width, height, mouseX, mouseY);
    }
    private void drawAttachmentStrip(GuiGraphics graphics, MailSyncPacket.MessageView mail,
                                     int x, int y, int width, int mouseX, int mouseY) {
        int stripWidth = attachmentStripWidth(width);
        int visible = Math.max(1, (stripWidth - 48) / 36);
        attachmentScroll = Math.max(0, Math.min(attachmentScroll,
            Math.max(0, mail.attachments().size() - visible)));
        for (int i = attachmentScroll; i < Math.min(mail.attachments().size(), attachmentScroll + visible); i++) {
            var attachment = mail.attachments().get(i);
            int cellX = x + 24 + (i - attachmentScroll) * 36;
            boolean hover = inside(mouseX, mouseY, cellX, y, 32, 32);
            boolean selected = selectedAttachments.contains(attachment.id());
            graphics.fill(cellX, y, cellX + 32, y + 32,
                selected ? 0xFF50626A : hover ? 0xE13C4C52 : 0xC52D383D);
            border(graphics, cellX, y, 32, 32,
                selected ? 0xFFFFFFFF : attachment.claimed() ? 0xFF4A5659 : 0xFF657A82);
            if (!attachment.stack().isEmpty()) {
                graphics.renderItem(attachment.stack(), cellX + 8, y + 8);
                graphics.renderItemDecorations(font, attachment.stack(), cellX + 8, y + 8);
                if (hover) hoveredStack = attachment.stack();
            } else {
                String icon = switch (attachment.type()) {
                    case CURRENCY -> "¤";
                    case EXPERIENCE_POINTS -> "✦";
                    case EXPERIENCE_LEVELS -> "L";
                    case RECIPE -> "R";
                    case FTB_TASK -> "?";
                    case ITEM -> "";
                };
                graphics.drawCenteredString(font, icon, cellX + 16, y + 12,
                    attachment.claimed() ? 0xFF718083 : 0xFFFFD36A);
                if (hover && isValueAttachment(attachment)) hoveredAttachment = attachment;
            }
            if (attachment.claimed()) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 40.0F);
                graphics.fill(cellX, y, cellX + 32, y + 32, 0x80000000);
                drawCheckIcon(graphics, cellX + 5, y + 5);
                graphics.pose().popPose();
            }
        }
        if (mail.attachments().size() > visible) {
            graphics.drawString(font, "‹", x + 13, y + 12,
                attachmentScroll > 0 ? 0xFFFFFFFF : 0xFF586367, false);
            graphics.drawString(font, "›", x + stripWidth - 17, y + 12,
                attachmentScroll + visible < mail.attachments().size() ? 0xFFFFFFFF : 0xFF586367, false);
        }
    }
    private void drawMailActionButton(GuiGraphics graphics, MailSyncPacket.MessageView mail,
                                      int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean hasUnclaimed = mail.attachments().stream()
            .anyMatch(value -> value.isClaimable() && !value.claimed());
        int selectedCount = (int) mail.attachments().stream()
            .filter(value -> selectedAttachments.contains(value.id()) && value.isClaimable() && !value.claimed())
            .count();
        String label = !hasUnclaimed
            ? Component.translatable("mail.xero_delta.delete_mail").getString()
            : partialClaimMode
                ? (selectedCount > 0
                    ? Component.translatable("mail.xero_delta.claim_selected", selectedCount).getString()
                    : Component.translatable("mail.xero_delta.claim").getString())
                : Component.translatable("mail.xero_delta.claim_all").getString();
        int buttonW = Math.max(92, font.width(label) + 22);
        int buttonX = x + width - buttonW - 22;
        if (hasUnclaimed) {
            drawPartialClaimToggle(graphics, buttonX, y + height - 62,
                partialClaimMode, mouseX, mouseY);
        }
        boolean active = !hasUnclaimed || !partialClaimMode || selectedCount > 0;
        drawTextButton(graphics, buttonX, y + height - 40,
            buttonW, 25, label, active, mouseX, mouseY);
    }
    private void drawPartialClaimToggle(GuiGraphics graphics, int x, int y,
                                        boolean checked, int mouseX, int mouseY) {
        String label = Component.translatable("mail.xero_delta.partial_claim").getString();
        boolean hovered = inside(mouseX, mouseY, x, y, Math.max(72, font.width(label) + 23), 19);
        int boxColor = hovered ? 0xFF718086 : 0xFF526167;
        graphics.fill(x, y + 1, x + 15, y + 16, checked ? 0xFF44585F : 0xFF202A2E);
        border(graphics, x, y + 1, 15, 15, boxColor);
        if (checked) drawSmallCheckIcon(graphics, x + 2, y + 2);
        graphics.drawString(font, label, x + 21, y + 5,
            hovered ? 0xFFFFFFFF : 0xFFAAB6B8, false);
    }

    private void drawMailAttachmentIcon(GuiGraphics graphics, int x, int y,
                                        boolean claimed) {
        int color = claimed ? 0xFF465258 : 0xFFF2F5F4;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 25.0F);
        if (claimed) {
            graphics.fill(x + 4, y + 8, x + 18, y + 18, color);
            graphics.fill(x + 10, y + 8, x + 12, y + 18, 0x5530393D);
            // Pixel-stepped lid opened roughly 35 degrees toward the upper-left.
            graphics.fill(x + 1, y + 5, x + 9, y + 7, color);
            graphics.fill(x + 4, y + 3, x + 12, y + 5, color);
            graphics.fill(x + 7, y + 1, x + 15, y + 3, color);
        } else {
            graphics.fill(x + 3, y + 8, x + 19, y + 18, color);
            graphics.fill(x + 2, y + 6, x + 20, y + 9, color);
            graphics.fill(x + 10, y + 6, x + 12, y + 18, 0x88333D41);
            graphics.fill(x + 5, y + 2, x + 10, y + 5, color);
            graphics.fill(x + 12, y + 2, x + 17, y + 5, color);
            graphics.fill(x + 8, y + 4, x + 14, y + 7, color);
        }
        graphics.pose().popPose();
    }

    private static void drawCheckIcon(GuiGraphics graphics, int x, int y) {
        int color = 0xFFFFFFFF;
        for (int i = 0; i < 6; i++) {
            graphics.fill(x + 2 + i, y + 11 + i, x + 5 + i, y + 14 + i, color);
        }
        for (int i = 0; i < 11; i++) {
            graphics.fill(x + 7 + i, y + 16 - i, x + 10 + i, y + 19 - i, color);
        }
    }

    private static void drawSmallCheckIcon(GuiGraphics graphics, int x, int y) {
        int color = 0xFFFFFFFF;
        for (int i = 0; i < 3; i++) {
            graphics.fill(x + 1 + i, y + 6 + i, x + 3 + i, y + 8 + i, color);
        }
        for (int i = 0; i < 6; i++) {
            graphics.fill(x + 4 + i, y + 8 - i, x + 6 + i, y + 10 - i, color);
        }
    }

    private static int attachmentStripWidth(int detailWidth) {
        return Math.max(68, detailWidth - 150);
    }
    private void drawAttachment(GuiGraphics graphics, MailSyncPacket.AttachmentView attachment, UUID mailId,
                                int x, int y, int width, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 30;
        graphics.fill(x, y, x + width, y + 30, hovered ? 0xE13C4C52 : 0xC52D383D);
        border(graphics, x, y, width, 30, attachment.claimed() ? 0xFF526063 : 0xFF657A82);
        int textX = x + 9;
        if (!attachment.stack().isEmpty()) {
            graphics.renderItem(attachment.stack(), x + 7, y + 7);
            graphics.renderItemDecorations(font, attachment.stack(), x + 7, y + 7);
            textX = x + 30;
            if (mouseX >= x + 5 && mouseX < x + 27 && mouseY >= y + 4 && mouseY < y + 27) {
                hoveredStack = attachment.stack();
            }
        }
        graphics.drawString(font, attachmentLabel(attachment), textX, y + 10,
            attachment.claimed() ? 0xFF788487 : 0xFFE7EEEC, false);
        if (attachment.isClaimable()) {
            String button = attachment.claimed()
                ? Component.translatable("mail.xero_delta.claimed").getString()
                : Component.translatable("mail.xero_delta.claim").getString();
            int buttonWidth = Math.max(44, font.width(button) + 12);
            int bx = x + width - buttonWidth - 5;
            boolean buttonHover = !attachment.claimed() && mouseX >= bx && mouseX < bx + buttonWidth
                && mouseY >= y + 5 && mouseY < y + 25;
            graphics.fill(bx, y + 5, bx + buttonWidth, y + 25,
                attachment.claimed() ? 0xFF3A4447 : buttonHover ? 0xFF70924C : 0xFF56733B);
            graphics.drawCenteredString(font, button, bx + buttonWidth / 2, y + 11,
                attachment.claimed() ? 0xFF7D898B : 0xFFFFFFFF);
        }
    }

    private String attachmentLabel(MailSyncPacket.AttachmentView attachment) {
        if (!attachment.label().isBlank()) return attachment.label();
        return switch (attachment.type()) {
            case CURRENCY -> Component.translatable("mail.xero_delta.currency",
                TradingUi.formatDetailed(attachment.amount())).getString();
            case EXPERIENCE_POINTS -> Component.translatable("mail.xero_delta.xp", attachment.amount()).getString();
            case EXPERIENCE_LEVELS -> Component.translatable("mail.xero_delta.levels", attachment.amount()).getString();
            case ITEM -> attachment.stack().getHoverName().getString() + " x" + attachment.stack().getCount();
            case RECIPE -> attachment.label().isBlank() ? "查看配方" : attachment.label();
            case FTB_TASK -> attachment.label().isBlank() ? "打开任务" : attachment.label();
        };
    }

    private void drawValueAttachmentTooltip(GuiGraphics graphics,
                                            MailSyncPacket.AttachmentView attachment,
                                            int mouseX, int mouseY) {
        String title = attachment.label().isBlank() ? switch (attachment.type()) {
            case CURRENCY -> Component.translatable("mail.xero_delta.currency_short").getString();
            case EXPERIENCE_POINTS -> Component.translatable("mail.xero_delta.xp_short").getString();
            case EXPERIENCE_LEVELS -> Component.translatable("mail.xero_delta.levels_short").getString();
            default -> "";
        } : attachment.label();
        String compact = TradingUi.format(attachment.amount());
        String compactText = switch (attachment.type()) {
            case CURRENCY -> compact;
            case EXPERIENCE_POINTS -> compact + " XP";
            case EXPERIENCE_LEVELS -> compact + " LV";
            default -> compact;
        };
        String full = Component.translatable("mail.xero_delta.full_amount",
            TradingUi.formatDetailed(attachment.amount())).getString();
        int boxWidth = Math.max(118, Math.max(font.width(title) + 16,
            Math.max(font.width(compactText) + 42, font.width(full) + 42)));
        int boxHeight = 52;
        int x = Math.max(4, Math.min(width - boxWidth - 4, mouseX + 12));
        int y = Math.max(4, Math.min(height - boxHeight - 4, mouseY + 12));
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xFA11191D);
        border(graphics, x, y, boxWidth, boxHeight, 0xFF74878D);
        graphics.drawString(font, title, x + 8, y + 6, 0xFFFFFFFF, false);
        if (attachment.type() == MailAttachment.Type.CURRENCY) {
            TradingUi.drawAmount(graphics, font, attachment.amount(), x + 8, y + 21, 0xFFFFD36A);
        } else {
            ItemStack icon = attachment.type() == MailAttachment.Type.EXPERIENCE_POINTS
                ? Items.EXPERIENCE_BOTTLE.getDefaultInstance()
                : Items.ENCHANTING_TABLE.getDefaultInstance();
            graphics.renderItem(icon, x + 7, y + 18);
            graphics.drawString(font, compactText, x + 28, y + 23, 0xFFFFD36A, false);
        }
        graphics.drawString(font, full, x + 28, y + 38, 0xFFAAB7B9, false);
    }

    private static boolean isValueAttachment(MailSyncPacket.AttachmentView attachment) {
        return attachment.type() == MailAttachment.Type.CURRENCY
            || attachment.type() == MailAttachment.Type.EXPERIENCE_POINTS
            || attachment.type() == MailAttachment.Type.EXPERIENCE_LEVELS;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (transition.closing()) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight(), left = leftWidth();
        if (inside(mouseX, mouseY, x + w - 29, y + 9, 20, 20)) {
            playClick(); onClose(); return true;
        }
        if (state.canCompose() && inside(mouseX, mouseY, x + w - 57, y + 9, 20, 20)) {
            playClick(); minecraft.setScreen(new MailComposerScreen(this)); return true;
        }
        int gap = 5, batchY = y + h - 43, batchW = (left - 21 - gap) / 2;
        if (inside(mouseX, mouseY, x + 8, batchY, batchW, 25)) {
            playClick(); ModNetwork.sendToServer(MailActionPacket.claimInbox()); return true;
        }
        if (inside(mouseX, mouseY, x + 8 + batchW + gap, batchY, batchW, 25)) {
            long now = System.currentTimeMillis();
            boolean hasUnclaimed = state.messages().stream()
                .anyMatch(mail -> mail.read() && mail.attachments().stream()
                    .anyMatch(value -> value.isClaimable() && !value.claimed()));
            if (!hasUnclaimed) {
                deleteReadReadyAt = 0L;
                playClick(); ModNetwork.sendToServer(MailActionPacket.deleteRead());
            } else if (deleteReadReadyAt == 0L) {
                deleteReadReadyAt = now + 3_000L;
                toast = Component.translatable("mail.xero_delta.delete_warning").getString();
                toastSuccess = false; toastUntil = now + 3_000L; playClick();
            } else if (now >= deleteReadReadyAt) {
                deleteReadReadyAt = 0L;
                playClick(); ModNetwork.sendToServer(MailActionPacket.deleteRead());
            }
            return true;
        }
        List<MailSyncPacket.MessageView> values = filtered();
        int top = y + 70, bottom = y + h - 72, viewportHeight = Math.max(1, bottom - top);
        if (beginListScrollbarDrag(mouseX, mouseY, x, top, left, viewportHeight, values.size())) {
            return true;
        }
        if (mouseX >= x + 8 && mouseX < x + left - 10 && mouseY >= top && mouseY < bottom) {
            listPointerDown = true;
            listContentDragging = false;
            listPressY = mouseY;
            listPressScrollPixels = listScrollPixels;
            listLastDragY = mouseY;
            listLastDragNanos = System.nanoTime();
            listDragVelocity = 0.0D;
            return true;
        }
        MailSyncPacket.MessageView mail = state.find(selectedId);
        if (mail != null) {
            int detailX = x + left + 1;
            String mailId = "ID: " + mail.id();
            int idWidth = Math.min(font.width(mailId), Math.max(60, (w - left - 1) / 2 - 24));
            if (inside(mouseX, mouseY, detailX + (w - left - 1) - 22 - idWidth, y + 47,
                idWidth + 2, 15)) {
                minecraft.keyboardHandler.setClipboard(mail.id().toString());
                toast = Component.translatable("mail.xero_delta.copied_id").getString();
                toastSuccess = true; toastUntil = System.currentTimeMillis() + 1800L;
                playClick(); return true;
            }
            boolean hasUnclaimed = mail.attachments().stream()
                .anyMatch(value -> value.isClaimable() && !value.claimed());
            int selectedCount = (int) mail.attachments().stream()
                .filter(value -> selectedAttachments.contains(value.id()) && value.isClaimable() && !value.claimed())
                .count();
            String actionLabel = !hasUnclaimed
                ? Component.translatable("mail.xero_delta.delete_mail").getString()
                : partialClaimMode
                    ? (selectedCount > 0
                        ? Component.translatable("mail.xero_delta.claim_selected", selectedCount).getString()
                        : Component.translatable("mail.xero_delta.claim").getString())
                    : Component.translatable("mail.xero_delta.claim_all").getString();
            int actionW = Math.max(92, font.width(actionLabel) + 22);
            int detailW = w - left - 1;
            int actionX = detailX + detailW - actionW - 22;
            int partialWidth = Math.max(72,
                font.width(Component.translatable("mail.xero_delta.partial_claim")) + 23);
            if (hasUnclaimed && inside(mouseX, mouseY,
                actionX, y + h - 64, partialWidth, 19)) {
                partialClaimMode = !partialClaimMode;
                if (!partialClaimMode) selectedAttachments.clear();
                playClick();
                return true;
            }
            if (inside(mouseX, mouseY, actionX, y + h - 40, actionW, 25)) {
                if (hasUnclaimed && partialClaimMode && selectedCount <= 0) return true;
                playClick();
                if (!hasUnclaimed) ModNetwork.sendToServer(MailActionPacket.delete(mail.id()));
                else if (partialClaimMode) ModNetwork.sendToServer(
                    MailActionPacket.claimSelected(mail.id(), selectedAttachments));
                else ModNetwork.sendToServer(MailActionPacket.claimAll(mail.id()));
                return true;
            }
            int stripY = attachmentStartY(mail, y, detailW);
            int visible = Math.max(1, (attachmentStripWidth(detailW) - 48) / 36);
            int stripX = detailX + 24;
            if (inside(mouseX, mouseY, stripX, stripY, visible * 36, 32)) {
                int attachmentIndex = attachmentScroll + ((int) mouseX - stripX) / 36;
                if (attachmentIndex >= 0 && attachmentIndex < mail.attachments().size()) {
                    var attachment = mail.attachments().get(attachmentIndex);
                    playClick();
                    if (attachment.isClaimable() && !attachment.claimed()) {
                        selectAttachment(mail, attachmentIndex);
                    } else if (attachment.type() == MailAttachment.Type.RECIPE) openRecipe(attachment.value());
                    else if (attachment.type() == MailAttachment.Type.FTB_TASK) openTask(attachment.value(), attachment.label());
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        List<MailSyncPacket.MessageView> values = filtered();
        int top = panelY() + 70, bottom = panelY() + panelHeight() - 72;
        int viewportHeight = Math.max(1, bottom - top);
        double maxScroll = maxListScroll(values.size(), viewportHeight);
        if (listScrollbarDragging) {
            int contentHeight = Math.max(viewportHeight, values.size() * CARD_HEIGHT);
            int thumbHeight = Math.max(18, viewportHeight * viewportHeight / contentHeight);
            int travel = Math.max(1, viewportHeight - thumbHeight);
            double desiredThumbY = mouseY - top - listScrollbarGrabOffset;
            setListScrollImmediately(maxScroll * clamp(desiredThumbY / travel, 0.0D, 1.0D), maxScroll);
            return true;
        }
        if (!listPointerDown) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (!listContentDragging && Math.abs(mouseY - listPressY) >= 3.0D) listContentDragging = true;
        if (!listContentDragging) return true;
        double next = clamp(listPressScrollPixels - (mouseY - listPressY), 0.0D, maxScroll);
        long now = System.nanoTime();
        double seconds = Math.max(0.001D, (now - listLastDragNanos) / 1_000_000_000.0D);
        listDragVelocity = (listLastDragY - mouseY) / seconds;
        listLastDragY = mouseY;
        listLastDragNanos = now;
        setListScrollImmediately(next, maxScroll);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseReleased(mouseX, mouseY, button);
        if (listScrollbarDragging) {
            listScrollbarDragging = false;
            return true;
        }
        if (!listPointerDown) return super.mouseReleased(mouseX, mouseY, button);
        listPointerDown = false;
        if (listContentDragging) {
            listContentDragging = false;
            int viewportHeight = Math.max(1, panelHeight() - 142);
            double maxScroll = maxListScroll(filtered().size(), viewportHeight);
            listTargetPixels = clamp(listScrollPixels + listDragVelocity * 0.14D, 0.0D, maxScroll);
            listDragVelocity = 0.0D;
            return true;
        }
        selectMailAt(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listTop = panelY() + 70, listBottom = panelY() + panelHeight() - 72;
        if (inside(mouseX, mouseY, panelX() + 5, listTop, leftWidth() - 10, listBottom - listTop)) {
            double maxScroll = maxListScroll(filtered().size(), Math.max(1, listBottom - listTop));
            listTargetPixels = clamp(listTargetPixels - scrollY * CARD_HEIGHT * 0.9D, 0.0D, maxScroll);
            return true;
        } else {
            MailSyncPacket.MessageView mail = state.find(selectedId);
            int detailWidth = panelWidth() - leftWidth() - 1;
            int stripY = mail == null ? -100 : attachmentStartY(mail, panelY(), detailWidth);
            if (mail != null && inside(mouseX, mouseY, panelX() + leftWidth() + 20, stripY - 5,
                Math.max(36, attachmentStripWidth(detailWidth) - 20), 42)) {
                attachmentScroll = Math.max(0, attachmentScroll + (scrollY < 0 ? 1 : -1));
            } else detailScroll = Math.max(0, detailScroll + (scrollY < 0 ? 22 : -22));
        }
        return true;
    }

    private boolean beginListScrollbarDrag(double mouseX, double mouseY, int x, int top,
                                           int width, int viewportHeight, int itemCount) {
        double maxScroll = maxListScroll(itemCount, viewportHeight);
        if (maxScroll <= 0.0D) return false;
        int trackX = x + width - 7;
        if (!inside(mouseX, mouseY, trackX - 3, top, 9, viewportHeight)) return false;
        int contentHeight = Math.max(viewportHeight, itemCount * CARD_HEIGHT);
        int thumbHeight = Math.max(18, viewportHeight * viewportHeight / contentHeight);
        int travel = Math.max(1, viewportHeight - thumbHeight);
        int thumbY = top + (int) Math.round(travel * listScrollPixels / maxScroll);
        listScrollbarGrabOffset = inside(mouseX, mouseY, trackX - 3, thumbY, 9, thumbHeight)
            ? mouseY - thumbY : thumbHeight / 2.0D;
        listScrollbarDragging = true;
        listPointerDown = false;
        listContentDragging = false;
        double desiredThumbY = mouseY - top - listScrollbarGrabOffset;
        setListScrollImmediately(maxScroll * clamp(desiredThumbY / travel, 0.0D, 1.0D), maxScroll);
        return true;
    }

    private void selectMailAt(double mouseX, double mouseY) {
        int x = panelX(), y = panelY(), left = leftWidth();
        int top = y + 70, bottom = y + panelHeight() - 72;
        if (!inside(mouseX, mouseY, x + 8, top, left - 18, bottom - top)) return;
        double contentY = mouseY - top + listScrollPixels;
        int index = (int) Math.floor(contentY / CARD_HEIGHT);
        if (contentY - index * CARD_HEIGHT >= 38.0D) return;
        List<MailSyncPacket.MessageView> values = filtered();
        if (index < 0 || index >= values.size()) return;
        selectedId = values.get(index).id();
        detailScroll = 0; attachmentScroll = 0;
        selectedAttachments.clear(); attachmentAnchor = null; partialClaimMode = false; playClick();
        ModNetwork.sendToServer(MailActionPacket.read(selectedId));
    }

    private void resetListScroll() {
        listScrollPixels = 0.0D;
        listTargetPixels = 0.0D;
        listDragVelocity = 0.0D;
    }

    private void setListScrollImmediately(double value, double maxScroll) {
        listScrollPixels = clamp(value, 0.0D, maxScroll);
        listTargetPixels = listScrollPixels;
        listAnimationNanos = System.nanoTime();
    }

    private static double maxListScroll(int itemCount, int viewportHeight) {
        return Math.max(0.0D, itemCount * (double) CARD_HEIGHT - viewportHeight);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void selectAttachment(MailSyncPacket.MessageView mail, int index) {
        UUID clicked = mail.attachments().get(index).id();
        partialClaimMode = true;
        if (Screen.hasShiftDown() && attachmentAnchor != null) {
            int anchor = -1;
            for (int i = 0; i < mail.attachments().size(); i++) {
                if (mail.attachments().get(i).id().equals(attachmentAnchor)) { anchor = i; break; }
            }
            if (anchor >= 0) {
                if (!Screen.hasControlDown()) selectedAttachments.clear();
                for (int i = Math.min(anchor, index); i <= Math.max(anchor, index); i++) {
                    var value = mail.attachments().get(i);
                    if (value.isClaimable() && !value.claimed()) selectedAttachments.add(value.id());
                }
            }
        } else if (partialClaimMode || Screen.hasControlDown()) {
            if (!selectedAttachments.remove(clicked)) selectedAttachments.add(clicked);
            attachmentAnchor = clicked;
        } else {
            selectedAttachments.clear(); selectedAttachments.add(clicked); attachmentAnchor = clicked;
        }
    }

    private int attachmentStartY(MailSyncPacket.MessageView mail, int panelY, int detailWidth) {
        return panelY + panelHeight() - 64;
    }

    private void openRecipe(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
        RecipeViewerIntegration.openRecipes(BuiltInRegistries.ITEM.get(id).getDefaultInstance());
    }

    private void openTask(String value, String label) {
        try {
            long id = Long.parseLong(value);
            FtbQuestIntegration.open(new FtbQuestIntegration.Match(id, Component.literal(label), Component.literal(label)));
        } catch (NumberFormatException ignored) {
        }
    }

    private List<MailSyncPacket.MessageView> filtered() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return state.messages();
        List<MailSyncPacket.MessageView> result = new ArrayList<>();
        for (var mail : state.messages()) {
            if (mail.title().toLowerCase(Locale.ROOT).contains(query)
                || mail.senderName().toLowerCase(Locale.ROOT).contains(query)
                || mail.text().toLowerCase(Locale.ROOT).contains(query)
                || mail.id().toString().contains(query)) result.add(mail);
        }
        return result;
    }

    private void drawToast(GuiGraphics graphics, int x, int y, int width) {
        if (toast.isBlank() || System.currentTimeMillis() >= toastUntil) return;
        int tw = Math.min(width - 40, font.width(toast) + 24);
        int tx = x + (width - tw) / 2;
        graphics.fill(tx, y + panelHeight() - 31, tx + tw, y + panelHeight() - 10,
            toastSuccess ? 0xED3E673D : 0xED7B3D3D);
        graphics.drawCenteredString(font, toast, tx + tw / 2, y + panelHeight() - 24, 0xFFFFFFFF);
    }

    private void drawCloseIcon(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 20, 20);
        g.fill(x, y, x + 20, y + 20, hovered ? 0xFF5C6F76 : 0xCC303B3F);
        for (int i = 0; i < 8; i++) {
            g.fill(x + 6 + i, y + 6 + i, x + 8 + i, y + 8 + i, 0xFFF4F7F6);
            g.fill(x + 13 - i, y + 6 + i, x + 15 - i, y + 8 + i, 0xFFF4F7F6);
        }
    }

    private void drawIconButton(GuiGraphics g, int x, int y, String icon, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 20, 20);
        g.fill(x, y, x + 20, y + 20, hovered ? 0xFF6C8751 : 0xCC425536);
        g.drawCenteredString(font, icon, x + 10, y + 6, 0xFFFFFFFF);
    }

    private void drawTextButton(GuiGraphics g, int x, int y, int w, int h, String text,
                                boolean active, int mouseX, int mouseY) {
        boolean hovered = active && inside(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, !active ? 0xFF303A3E : hovered ? 0xFF5A737B : 0xFF3B4B51);
        border(g, x, y, w, h, hovered ? 0xFF91A5AA : 0xFF53656B);
        g.drawCenteredString(font, font.plainSubstrByWidth(text, w - 8), x + w / 2,
            y + (h - 8) / 2, active ? 0xFFFFFFFF : 0xFF748083);
    }

    private void drawDiamondHelp(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x - 1, y - 1, 13, 13);
        Material2Icon.INFO.render(g, x + 6, y + 6,
            hovered ? 0xFFFFFFFF : 0xFF829195);
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public void tick() {
        super.tick();
        transition.tick(minecraft);
    }

    @Override
    public void onClose() {
        transition.beginClose(() -> minecraft.setScreen(returnScreen));
    }

    private int panelWidth() { return Math.min(820, width - 28); }
    private int panelHeight() { return Math.min(460, height - 28); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return (height - panelHeight()) / 2; }
    private int leftWidth() { return Math.min(260, Math.max(190, panelWidth() / 3)); }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Avoids the level blur pass so mailbox text and one-pixel borders stay pixel-sharp. */
    void renderSharpBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xB0101518);
    }
}

