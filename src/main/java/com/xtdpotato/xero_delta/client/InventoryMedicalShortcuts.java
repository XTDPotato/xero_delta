package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.item.MedicalItem;
import com.xtdpotato.xero_delta.item.MedicalUseRules;
import com.xtdpotato.xero_delta.network.MedicalWheelUsePacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;
import com.xtdpotato.xero_delta.screen.material.Material2Icon;
import com.mojang.math.Axis;

/** Per-screen shortcut selection; opening this view never moves inventory stacks. */
public final class InventoryMedicalShortcuts {
    private int expanded = -1;
    private int page;
    private final String[] selectedSources = {"", "", ""};
    private int x, y, cell, gap;

    private List<MedicalWheelClient.Entry> entries(int category) {
        return MedicalUseClientState.INSTANCE.withReservedEntry(
            MedicalWheelClient.inventoryEntries(Minecraft.getInstance().player)).stream()
            .filter(entry -> category(entry) == category).toList();
    }

    private int category(MedicalWheelClient.Entry entry) {
        if (MedicalUseRules.isHealthItem(entry.stack())) return 0;
        MedicalItem item = (MedicalItem) entry.stack().getItem();
        return MedicalUseRules.isInjuryTreatment(item.treatment()) ? 2 : 1;
    }

    private MedicalWheelClient.Entry selected(int category) {
        List<MedicalWheelClient.Entry> choices = entries(category);
        return choices.stream().filter(e -> e.sourceId().equals(selectedSources[category]))
            .findFirst().orElse(choices.isEmpty() ? null : choices.getFirst());
    }

    public void render(GuiGraphics g, int left, int top, int size, int mouseX, int mouseY) {
        x = left; y = top; cell = Math.max(22, size); gap = 4;
        for (int i = 0; i < 3; i++) {
            int cx = x + i * (cell + gap);
            drawEntry(g, selected(i), cx, y, mouseX, mouseY);
            g.fill(cx, y - 9, cx + cell, y, expanded == i
                ? DeltaInventoryTheme.ACCENT : DeltaInventoryTheme.BORDER);
            drawArrow(g, cx + cell / 2, y - 5, expanded == i ? 90 : -90, 0.67F);
        }
        if (expanded < 0) return;
        List<MedicalWheelClient.Entry> choices = entries(expanded);
        int pages = Math.max(1, (choices.size() + 2) / 3);
        page = Math.min(page, pages - 1);
        int popupY = y - cell - 28;
        int width = 3 * cell + 2 * gap;
        g.fill(x - 5, popupY - 4, x + width + 5, y - 12, DeltaInventoryTheme.STORAGE);
        g.renderOutline(x - 5, popupY - 4, width + 10, cell + 20, DeltaInventoryTheme.BORDER);
        for (int i = 0; i < 3 && page * 3 + i < choices.size(); i++) {
            drawEntry(g, choices.get(page * 3 + i), x + i * (cell + gap), popupY, mouseX, mouseY);
        }
        drawArrow(g, x + 4, popupY + cell + 6, 180, 0.8F);
        g.drawCenteredString(Minecraft.getInstance().font, (page + 1) + "/" + pages,
            x + width / 2, popupY + cell + 2, DeltaInventoryTheme.MUTED);
        drawArrow(g, x + width - 4, popupY + cell + 6, 0, 0.8F);
    }

    private static void drawArrow(GuiGraphics g, int x, int y, float angle, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        g.pose().scale(scale, scale, 1);
        Material2Icon.CHEVRON_RIGHT.render(g, 0, 0, DeltaInventoryTheme.TEXT);
        g.pose().popPose();
    }

    private void drawEntry(GuiGraphics g, MedicalWheelClient.Entry entry, int cx, int cy, int mx, int my) {
        DeltaGridCellRenderer.render(g, cx, cy, cell);
        if (entry == null) return;
        var mc = Minecraft.getInstance();
        var use = MedicalUseClientState.INSTANCE;
        boolean using = use.isUsing(entry);
        GridItemRenderer.renderSizedItem(g, mc.font, entry.stack(), cx + 2, cy + 2,
            cell - 4, cell - 4, false, false, false);
        boolean enabled = MedicalUseRules.canUseWheelItem(mc.player, entry.stack())
            && !DownedClientState.INSTANCE.interactionLocked();
        g.pose().pushPose();
        g.pose().translate(0, 0, GridItemRenderer.BOUND_ICON_Z + 10);
        if (!enabled && !using) g.fill(cx + 1, cy + 1, cx + cell - 1, cy + cell - 1, 0x66070B0C);
        if (using) {
            g.renderOutline(cx + 1, cy + 1, cell - 2, cell - 2, DeltaInventoryTheme.ACCENT);
        }
        if (inside(mx, my, cx, cy, cell, cell)) {
            g.renderOutline(cx, cy, cell, cell, DeltaInventoryTheme.ACCENT);
            g.renderTooltip(mc.font, using ? Component.translatable("medical.xero_delta.using",
                entry.stack().getHoverName()) : enabled ? entry.stack().getHoverName()
                : Component.translatable("medical_wheel.xero_delta.unavailable"), mx, my);
        }
        g.pose().popPose();
    }

    public boolean click(double mx, double my, int button) {
        if (cell == 0) return false;
        if (expanded >= 0) {
            int py = y - cell - 28;
            List<MedicalWheelClient.Entry> choices = entries(expanded);
            for (int i = 0; i < 3 && page * 3 + i < choices.size(); i++) {
                if (inside(mx, my, x + i * (cell + gap), py, cell, cell)) {
                    if (button == 0) {
                        selectedSources[expanded] = choices.get(page * 3 + i).sourceId();
                        expanded = -1;
                    }
                    return true;
                }
            }
            if (inside(mx, my, x - 5, py - 4, 3 * cell + 2 * gap + 10, cell + 20)) {
                if (button == 0 && my >= py + cell) {
                    int pages = Math.max(1, (choices.size() + 2) / 3);
                    page = Math.floorMod(page + (mx < x + cell ? -1 : 1), pages);
                }
                return true;
            }
        }
        for (int i = 0; i < 3; i++) {
            int cx = x + i * (cell + gap);
            if (inside(mx, my, cx, y - 9, cell, 9)) {
                if (button == 0) { expanded = expanded == i ? -1 : i; page = 0; }
                return true;
            }
            if (inside(mx, my, cx, y, cell, cell)) {
                var mc = Minecraft.getInstance();
                var entry = selected(i);
                if (button == 0 && entry != null && mc.getConnection() != null
                    && !MedicalUseClientState.INSTANCE.active()
                    && mc.player != null && mc.player.containerMenu.getCarried().isEmpty()
                    && !DownedClientState.INSTANCE.interactionLocked()
                    && MedicalUseRules.canUseWheelItem(mc.player, entry.stack())) {
                    ModNetwork.sendToServer(new MedicalWheelUsePacket(entry.id().toString(), entry.sourceId()));
                    selectedSources[i] = entry.sourceId();
                }
                return true;
            }
        }
        expanded = -1;
        return false;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
