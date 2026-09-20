package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.network.LootSearchHoverPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.screen.CorpseScreen;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Draws fully opaque unknown-item covers and the animated search icon. */
public final class LootSearchOverlay {
    private static final ResourceLocation SEARCH_ICON = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "textures/gui/loot_search.png");
    private static final int MASK_COLOR = 0xFF5B5F5E;
    private static final int MASK_BORDER = 0xFFD0D6D3;
    private static final int MASK_INNER_BORDER = 0xFF888F8C;
    private static final int TEAM_MARK_COLOR = 0xFF65D6FF;
    private static final int ICON_SIZE = 12;
    private static final double PATH_RADIUS = 2.0D;
    private static final long PATH_PERIOD_MILLIS = 800L;

    private static int lastHoverContainer = -1;
    private static int lastHoverSlot = -1;
    private static long lastHoverSentAt;

    private LootSearchOverlay() {
    }

    /** Returns true when vanilla item rendering must be cancelled. */
    public static boolean renderHiddenSlot(AbstractContainerScreen<?> screen,
                                           GuiGraphics graphics, Slot renderedSlot) {
        AbstractContainerMenu menu = screen.getMenu();
        if (!LootSearchClientState.INSTANCE.isActiveFor(menu.containerId)) return false;
        Slot anchor = anchorFor(menu, renderedSlot);
        if (anchor == null || !LootSearchClientState.INSTANCE.isHidden(
            menu.containerId, anchor.index)) return false;
        if (anchor != renderedSlot) return true;

        Bounds bounds = bounds(screen, menu, anchor);
        graphics.fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(), MASK_COLOR);
        graphics.renderOutline(bounds.x, bounds.y, bounds.width, bounds.height, MASK_BORDER);
        if (bounds.width > 4 && bounds.height > 4) {
            graphics.renderOutline(bounds.x + 1, bounds.y + 1,
                bounds.width - 2, bounds.height - 2, MASK_INNER_BORDER);
        }
        if (LootSearchClientState.INSTANCE.isCurrent(menu.containerId, anchor.index)) {
            int[] offset = searchOffset();
            int iconX = bounds.x + (bounds.width - ICON_SIZE) / 2 + offset[0];
            int iconY = bounds.y + (bounds.height - ICON_SIZE) / 2 + offset[1];
            graphics.blit(SEARCH_ICON, iconX, iconY, ICON_SIZE, ICON_SIZE,
                0, 0, 16, 16, 16, 16);
        }
        if (LootSearchClientState.INSTANCE.isTeammateSearching(menu.containerId, anchor.index)) {
            drawTeamMark(graphics, bounds.right() - 10, bounds.y + 2);
        }
        return true;
    }

    private static void drawTeamMark(GuiGraphics graphics, int x, int y) {
        // Compact two-person silhouette; kept in the GUI pass so it scales crisply.
        graphics.fill(x + 1, y, x + 4, y + 3, TEAM_MARK_COLOR);
        graphics.fill(x, y + 4, x + 5, y + 8, TEAM_MARK_COLOR);
        graphics.fill(x + 6, y + 1, x + 9, y + 4, TEAM_MARK_COLOR);
        graphics.fill(x + 5, y + 5, x + 10, y + 9, TEAM_MARK_COLOR);
    }

    public static Slot hiddenAnchorAt(AbstractContainerScreen<?> screen,
                                      double mouseX, double mouseY) {
        AbstractContainerMenu menu = screen.getMenu();
        if (!LootSearchClientState.INSTANCE.isActiveFor(menu.containerId)) return null;
        double localX = mouseX - screen.getGuiLeft();
        double localY = mouseY - screen.getGuiTop();
        Slot hit = screen instanceof CorpseScreen corpse
            ? corpse.corpsePresentationSlotAt(localX, localY)
            : ContainerGridHelper.hitSlot(menu, localX, localY);
        Slot anchor = anchorFor(menu, hit);
        if (anchor != null && LootSearchClientState.INSTANCE.isHidden(
            menu.containerId, anchor.index)) return anchor;
        // Corpse hit testing already accounts for the clipped scrolling window.
        // Falling back to full card bounds would make the header/footer searchable.
        if (screen instanceof CorpseScreen) return null;

        for (Slot candidate : menu.slots) {
            if (!LootSearchClientState.INSTANCE.isHidden(menu.containerId, candidate.index)) continue;
            Bounds bounds = bounds(screen, menu, candidate);
            if (localX >= bounds.x && localX < bounds.right()
                && localY >= bounds.y && localY < bounds.bottom()) return candidate;
        }
        return null;
    }

    /** Returns true when a slot belongs to an item that is still hidden by loot search. */
    public static boolean isHiddenSlot(AbstractContainerScreen<?> screen, Slot slot) {
        if (screen == null || slot == null) return false;
        AbstractContainerMenu menu = screen.getMenu();
        Slot anchor = anchorFor(menu, slot);
        return anchor != null && LootSearchClientState.INSTANCE.isHidden(
            menu.containerId, anchor.index);
    }

    public static void handleHover(AbstractContainerScreen<?> screen,
                                   double mouseX, double mouseY) {
        AbstractContainerMenu menu = screen.getMenu();
        Slot anchor = hiddenAnchorAt(screen, mouseX, mouseY);
        if (anchor == null || LootSearchClientState.INSTANCE.isCurrent(
            menu.containerId, anchor.index)) {
            lastHoverContainer = -1;
            lastHoverSlot = -1;
            return;
        }
        long now = Util.getMillis();
        if (lastHoverContainer == menu.containerId && lastHoverSlot == anchor.index
            && now - lastHoverSentAt < 500L) return;
        lastHoverContainer = menu.containerId;
        lastHoverSlot = anchor.index;
        lastHoverSentAt = now;
        ModNetwork.sendToServer(new LootSearchHoverPacket(menu.containerId, anchor.index));
    }

    private static Slot anchorFor(AbstractContainerMenu menu, Slot slot) {
        if (slot == null) return null;
        Slot anchor = ContainerGridHelper.footprintAnchorFor(
            menu, slot, ClientDataCache.INSTANCE::getSize);
        return anchor == null ? slot : anchor;
    }

    private static Bounds bounds(AbstractContainerScreen<?> screen,
                                 AbstractContainerMenu menu, Slot anchor) {
        if (screen instanceof CorpseScreen corpse) {
            int[] custom = corpse.corpsePresentationBounds(anchor);
            if (custom != null) {
                return new Bounds(custom[0], custom[1], custom[2], custom[3]);
            }
        }
        ItemStack stack = anchor.getItem();
        ItemSize size = stack.isEmpty() ? new ItemSize(1, 1)
            : ContainerGridHelper.orientedSize(stack, ClientDataCache.INSTANCE::getSize);
        Set<Slot> cells = ContainerGridHelper.footprintCells(menu, anchor, size);
        if (cells.size() == size.width() * size.height()) {
            int minX = cells.stream().mapToInt(slot -> slot.x).min().orElse(anchor.x);
            int minY = cells.stream().mapToInt(slot -> slot.y).min().orElse(anchor.y);
            int maxX = cells.stream().mapToInt(slot -> slot.x).max().orElse(anchor.x);
            int maxY = cells.stream().mapToInt(slot -> slot.y).max().orElse(anchor.y);
            return new Bounds(minX, minY,
                maxX + ContainerGridHelper.SLOT_FACE_SIZE - minX,
                maxY + ContainerGridHelper.SLOT_FACE_SIZE - minY);
        }
        return new Bounds(anchor.x, anchor.y,
            Math.max(ContainerGridHelper.SLOT_FACE_SIZE,
                size.width() * ContainerGridHelper.SLOT_STEP - 2),
            Math.max(ContainerGridHelper.SLOT_FACE_SIZE,
                size.height() * ContainerGridHelper.SLOT_STEP - 2));
    }

    private static int[] searchOffset() {
        double cycle = Math.floorMod(Util.getMillis(), PATH_PERIOD_MILLIS)
            / (double) PATH_PERIOD_MILLIS * 4.0D;
        int segment = Math.min(3, (int) cycle);
        double fraction = cycle - segment;
        double[][] points = {
            {0.0D, -PATH_RADIUS}, {-PATH_RADIUS, 0.0D},
            {0.0D, PATH_RADIUS}, {PATH_RADIUS, 0.0D}, {0.0D, -PATH_RADIUS}
        };
        int x = (int) Math.round(points[segment][0]
            + (points[segment + 1][0] - points[segment][0]) * fraction);
        int y = (int) Math.round(points[segment][1]
            + (points[segment + 1][1] - points[segment][1]) * fraction);
        return new int[]{x, y};
    }

    private record Bounds(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }
    }
}
