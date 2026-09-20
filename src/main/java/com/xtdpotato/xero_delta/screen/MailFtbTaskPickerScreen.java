package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.FtbQuestIntegration;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Live FTB task list used by the creative mail attachment modal. */
final class MailFtbTaskPickerScreen extends Screen {
    private final Screen parent;
    private final ScreenTransition transition = new ScreenTransition();
    private final Consumer<FtbQuestIntegration.Match> callback;
    private List<FtbQuestIntegration.Match> tasks = List.of();
    private EditBox search;
    private int scroll;

    MailFtbTaskPickerScreen(Screen parent, Consumer<FtbQuestIntegration.Match> callback) {
        super(Component.translatable("mail.xero_delta.pick_task"));
        this.parent = parent; this.callback = callback;
    }

    @Override
    protected void init() {
        tasks = FtbQuestIntegration.allTasks();
        search = addRenderableWidget(new Material3CompactEditBox(font, panelX() + 18, panelY() + 40,
            panelWidth() - 36, 20, Component.translatable("mail.xero_delta.search_task")));
        search.setHint(Component.translatable("mail.xero_delta.search_task"));
        search.setResponder(ignored -> scroll = 0);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Material3Theme.SCRIM);
        transition.push(g);
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight();
        Material2Drawing.roundedRect(g, x, y, w, h, Material3Theme.RADIUS_MEDIUM,
            Material3Theme.SURFACE_CONTAINER);
        border(g, x, y, w, h, Material3Theme.OUTLINE_VARIANT);
        g.drawString(font, title, x + 18, y + 16, Material3Theme.TEXT, false);
        List<FtbQuestIntegration.Match> values = filtered();
        int top = y + 68, rowH = 38, visible = Math.max(1, (h - 82) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, values.size() - visible)));
        g.enableScissor(x + 10, top, x + w - 10, y + h - 10);
        for (int i = scroll; i < Math.min(values.size(), scroll + visible + 1); i++) {
            var task = values.get(i); int rowY = top + (i - scroll) * rowH;
            boolean hover = inside(mouseX, mouseY, x + 14, rowY, w - 28, 33);
            g.fill(x + 14, rowY, x + w - 14, rowY + 33, hover ? 0xFF405159 : 0xCC2D383D);
            border(g, x + 14, rowY, w - 28, 33, hover ? 0xFFFFFFFF : 0xFF53656B);
            String title = task.questTitle().getString() + "：" + task.taskTitle().getString();
            g.drawString(font, font.plainSubstrByWidth(title, w - 48), x + 23, rowY + 7, 0xFFFFFFFF, false);
            g.drawString(font, "ID: " + task.taskId(), x + 23, rowY + 20, 0xFF91A0A3, false);
        }
        g.disableScissor();
        if (values.isEmpty()) g.drawCenteredString(font, Component.translatable("mail.xero_delta.no_tasks"),
            x + w / 2, y + h / 2, 0xFF879497);
        for (var renderable : renderables) renderable.render(g, mouseX, mouseY, partialTick);
        transition.pop(g);
        transition.drawFade(g, width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (transition.closing()) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int top = panelY() + 68;
        int index = scroll + ((int) mouseY - top) / 38;
        List<FtbQuestIntegration.Match> values = filtered();
        if (mouseX >= panelX() + 14 && mouseX < panelX() + panelWidth() - 14
            && mouseY >= top && mouseY < panelY() + panelHeight() - 10
            && index >= 0 && index < values.size()) {
            FtbQuestIntegration.Match match = values.get(index);
            transition.beginClose(() -> {
                callback.accept(match);
                minecraft.setScreen(parent);
            });
            return true;
        }
        return false;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        scroll = Math.max(0, scroll + (sy < 0 ? 1 : -1)); return true;
    }
    @Override
    public void tick() {
        super.tick();
        transition.tick(minecraft);
    }
    @Override public void onClose() { transition.beginClose(() -> minecraft.setScreen(parent)); }

    private List<FtbQuestIntegration.Match> filtered() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return tasks;
        return tasks.stream().filter(task -> Long.toString(task.taskId()).contains(query)
            || task.questTitle().getString().toLowerCase(Locale.ROOT).contains(query)
            || task.taskTitle().getString().toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private int panelWidth() { return Math.min(520, width - 24); }
    private int panelHeight() { return Math.min(400, height - 24); }
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

