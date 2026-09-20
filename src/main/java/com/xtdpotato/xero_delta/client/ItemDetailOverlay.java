package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.block.ModBlocks;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.MedicalUseRules;
import com.xtdpotato.xero_delta.item.ConsumableProfile;
import com.xtdpotato.xero_delta.item.RepairKitItem;
import com.xtdpotato.xero_delta.item.TimedUseItem;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.network.CarrierReplacePacket;
import com.xtdpotato.xero_delta.network.InspectRequestPacket;
import com.xtdpotato.xero_delta.network.ItemDetailActionPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.SplitItemStackPacket;
import com.xtdpotato.xero_delta.network.TradingActionPacket;
import com.xtdpotato.xero_delta.network.TradingSyncPacket;
import com.xtdpotato.xero_delta.network.WarehouseSourceTransferPacket;
import com.xtdpotato.xero_delta.screen.PersonalWarehouseScreen;
import com.xtdpotato.xero_delta.trading.BuiltinRecycleValueCatalog;
import com.xtdpotato.xero_delta.trading.TradingRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

public final class ItemDetailOverlay {
    private static final int MARGIN = 8;
    /** Kept outside the scrolling region so item details never cover source rows. */
    private static final int SOURCE_FOOTER_HEIGHT = 62;
    private static final long FADE = 200_000_000L;
    private static final ResourceLocation COIN = icon("textures/quality/coin.png");
    private static final ResourceLocation CLOSE = icon("textures/gui/action/close.png");
    private static final ResourceLocation STAR = icon("textures/gui/action/favorite.png");
    private static final ResourceLocation STAR_OFF = icon("textures/gui/action/favorite_off.png");
    private static final ResourceLocation SELL = icon("textures/gui/action/sell.png");
    private static final ResourceLocation BACK = icon("textures/gui/action/return.png");
    private static final ResourceLocation SPLIT = icon("textures/gui/action/split.png");
    private static final ResourceLocation SOURCE = icon("textures/gui/action/source.png");
    private static final ResourceLocation INFO = icon("textures/gui/action/info.png");
    private static final ResourceLocation REPLACE = icon("textures/gui/action/replace_virtual.png");
    private static final ResourceLocation USE = icon("textures/gui/action/use.png");
    private static final ResourceLocation DISCARD = icon("textures/gui/action/delete.png");
    private static final ResourceLocation LIST = icon("textures/gui/action/list.png");
    private static final WeakHashMap<Screen, Detail> DETAILS = new WeakHashMap<>();
    private static final WeakHashMap<Screen, Drag> DRAGS = new WeakHashMap<>();
    /** Consumes the release paired with a split-confirm press after the popup closes. */
    private static final WeakHashMap<Screen, Boolean> SUPPRESS_NEXT_RELEASE = new WeakHashMap<>();

    private ItemDetailOverlay() {}

    public static void open(Screen owner, ItemStack stack, boolean inspectable) {
        if (owner != null) open(owner, stack, inspectable,
            owner.width / 2 - 8, owner.height / 2 - 8, 16, 16);
    }

    public static void open(Screen owner, ItemStack stack, boolean inspectable,
                            int x, int y, int width, int height) {
        open(owner, stack, inspectable, resolveSourceId(stack), x, y, width, height);
    }

    public static void open(Screen owner, ItemStack stack, boolean inspectable, String sourceId,
                            int x, int y, int width, int height) {
        put(owner, stack, inspectable, sourceId, false, x, y, width, height);
    }

    /** Opens a detail card selected from an item inside the safety-box grid. */
    public static void openSafetyBoxGrid(Screen owner, ItemStack stack,
                                         int x, int y, int width, int height) {
        put(owner, stack, false, resolveSourceId(stack), false,
            x, y, width, height, true);
    }

    public static void openSourceOnly(Screen owner, ItemStack stack,
                                      int x, int y, int width, int height) {
        put(owner, stack, false, "", true, x, y, width, height);
    }

    private static void put(Screen owner, ItemStack stack, boolean inspectable, String sourceId,
                            boolean sourceOnly, int x, int y, int width, int height) {
        put(owner, stack, inspectable, sourceId, sourceOnly, x, y, width, height, false);
    }

    private static void put(Screen owner, ItemStack stack, boolean inspectable, String sourceId,
                            boolean sourceOnly, int x, int y, int width, int height,
                            boolean safetyBoxGridSource) {
        if (owner == null || stack == null || stack.isEmpty()) return;
        DRAGS.remove(owner);
        SUPPRESS_NEXT_RELEASE.remove(owner);
        DETAILS.put(owner, new Detail(stack.copy(),
            inspectable || stack.getItem() instanceof SafetyBoxItem,
            sourceId == null ? "" : sourceId, sourceOnly,
            new Bounds(x, y, Math.max(1, width), Math.max(1, height)),
            safetyBoxGridSource));
    }

    public static void updateAnchor(Screen owner, int x, int y, int width, int height,
                                    boolean visible) {
        Detail detail = DETAILS.get(owner);
        if (detail == null) return;
        detail.anchor = new Bounds(x, y, Math.max(1, width), Math.max(1, height));
        detail.anchorVisible = visible;
        if (!detail.moved) {
            detail.popupX = Integer.MIN_VALUE;
            detail.popupY = Integer.MIN_VALUE;
        }
    }

    public static void close(Screen owner) {
        Detail detail = DETAILS.get(owner);
        if (detail != null && detail.closing == 0L) detail.closing = System.nanoTime();
        DRAGS.remove(owner);
    }

    public static boolean isOpen(Screen owner) {
        return DETAILS.containsKey(owner);
    }

    /** True when the click belongs to the top-most detail UI rather than an inventory below it. */
    public static boolean isPopupAt(Screen owner, double mouseX, double mouseY) {
        Detail detail = DETAILS.get(owner);
        if (detail == null) return false;
        if (detail.sale || detail.sourcePanel) return true;
        return popup(owner, detail).contains(mouseX, mouseY);
    }

    public static boolean handleEscape(Screen owner) {
        Detail detail = DETAILS.get(owner);
        if (detail == null) return false;
        if (detail.sourcePanel) {
            DETAILS.remove(owner);
            DRAGS.remove(owner);
            return true;
        }
        else if (detail.sale) detail.saleClosing = System.nanoTime();
        else if (detail.split) {
            detail.split = false;
            detail.actionScroll = 0;
        }
        else close(owner);
        return true;
    }

    /** Source and uses panels render in unscaled screen space above embedded layouts. */
    public static boolean isSourcePanelOpen(Screen owner) {
        Detail detail = DETAILS.get(owner);
        return detail != null && detail.sourcePanel;
    }

    /** Whether the currently visible detail card originated from a safety-box cell. */
    public static boolean isSafetyBoxGridDetail(Screen owner) {
        Detail detail = DETAILS.get(owner);
        return detail != null && !detail.detailDismissed
            && detail.closing == 0L && detail.safetyBoxGridSource;
    }

    public static boolean mouseClicked(Screen owner, double mouseX, double mouseY, int button) {
        Detail d = DETAILS.get(owner);
        if (d == null) return false;
        if (d.sourcePanel) return button == 0
            ? sourceClick(owner, d, mouseX, mouseY) : true;
        if (d.sale) return button == 0
            ? saleClick(owner, d, mouseX, mouseY) : true;
        if (button != 0) return false;
        Bounds p = popup(owner, d);
        if (!p.contains(mouseX, mouseY)) {
            // Closing a detail popup must not swallow the click that selects the
            // next inventory target. Container and Delta-layout handlers validate
            // the destination and perform the move after this returns.
            close(owner);
            return false;
        }
        if (closeBounds(p).contains(mouseX, mouseY)) {
            close(owner);
            return true;
        }
        if (mouseY < p.y + ItemDetailLayout.HEADER_HEIGHT + 1) {
            if (dragBounds(p).contains(mouseX, mouseY)) {
                DRAGS.put(owner, new Drag((int) mouseX, (int) mouseY, p.x, p.y));
            }
            return true;
        }
        if (mouseY >= p.bottom() - 1) return true;
        ItemDetailLayout.Sections sections = cardSections(d, p);
        Bounds content = contentBounds(d, p);
        if (mouseY < p.y + sections.actionsTop()) {
            if (mouseY < p.y + sections.previewTop() + sections.previewFixedHeight()
                && !d.inspectable && favoriteHitBounds(content).contains(mouseX, mouseY)) {
                String key = ModDataStorage.getKey(d.stack);
                boolean wasFavorite = favorite(d.stack);
                TradingClientState.INSTANCE.toggleFavoriteOptimistic(key);
                ModNetwork.sendToServer(TradingActionPacket.favorite(key));
                d.toast = Component.translatable(wasFavorite
                    ? "item_detail.xero_delta.favorite_removed"
                    : "item_detail.xero_delta.favorite_added");
                d.toastAt = System.nanoTime();
            } else if (mouseY >= p.y + sections.previewTop() + sections.previewFixedHeight()
                && previewMaxScroll(d, content) > 0
                && previewScrollbarBounds(d, content).contains(mouseX, mouseY + (int) d.previewYScroll)) {
                d.previewDragging = true;
                updatePreviewScroll(d, content, mouseX);
            }
            return true;
        }
        if (mouseY >= p.y + sections.infoTop()) return true;
        mouseY -= sections.actionsOffset(itemPreviewRows(d), d.actionScroll);
        p = content;
        if (d.sourceOnly) {
            if (sourceOnlyBounds(d, p).contains(mouseX, mouseY)) openSourcePanel(d);
            return true;
        }
        if (d.inspectable && inspectBounds(d, p).contains(mouseX, mouseY)) {
            startInspection(owner);
            return true;
        }
        if (!d.split && canReplaceCarrier(d) && replaceBounds(d, p).contains(mouseX, mouseY)) {
            ModNetwork.sendToServer(new CarrierReplacePacket(d.sourceId));
            close(owner);
            return true;
        }
        if (!d.split && canEquipWeapon(d) && equipWeaponBounds(d, p).contains(mouseX, mouseY)) {
            ModNetwork.sendToServer(new ItemDetailActionPacket(
                d.sourceId, ItemDetailActionPacket.EQUIP_WEAPON));
            close(owner);
            return true;
        }
        if (!d.split && canUse(d) && useBounds(d, p).contains(mouseX, mouseY)) {
            if (!d.sourceId.isBlank() && !d.usePending) {
                d.usePending = true;
                ModNetwork.sendToServer(new ItemDetailActionPacket(
                    d.sourceId, ItemDetailActionPacket.USE));
            }
            return true;
        }
        if (!d.split && canDiscard(d) && discardBounds(d, p).contains(mouseX, mouseY)) {
            ModNetwork.sendToServer(new ItemDetailActionPacket(
                d.sourceId, ItemDetailActionPacket.DISCARD));
            // The source no longer exists after a successful discard. Remove the
            // popup immediately instead of leaving its stale stack during fade-out.
            DETAILS.remove(owner);
            DRAGS.remove(owner);
            return true;
        }
        if (d.split) {
            if (minusBounds(p).contains(mouseX, mouseY)) d.splitAmount = Math.max(1, d.splitAmount - 1);
            else if (plusBounds(p).contains(mouseX, mouseY)) d.splitAmount = Math.min(d.stack.getCount() - 1, d.splitAmount + 1);
            else if (splitSliderBounds(p).contains(mouseX, mouseY)) {
                d.splitDragging = true;
                updateSplitAmount(d, p, mouseX);
            } else if (cancelBounds(p).contains(mouseX, mouseY)) {
                d.split = false;
                d.actionScroll = 0;
            } else if (confirmBounds(p).contains(mouseX, mouseY)) {
                if (!d.sourceId.isBlank()) {
                    ModNetwork.sendToServer(new SplitItemStackPacket(d.sourceId, d.splitAmount));
                    // The popup is closed on mouse-press. Consume the matching
                    // mouse-release so vanilla cannot interpret it as an
                    // outside drop of the carried stack.
                    SUPPRESS_NEXT_RELEASE.put(owner, Boolean.TRUE);
                }
                close(owner);
            }
            return true;
        }
        if (sellBounds(d, p).contains(mouseX, mouseY)
            && externalContainerContext(owner)
            && nearMarket() && !d.sourceId.isBlank() && !d.isContainerSource()) {
            d.sale = true;
            d.saleOpened = System.nanoTime();
            return true;
        }
        if (returnBounds(d, p).contains(mouseX, mouseY)
            && externalContainerContext(owner)) {
            if ((d.isContainerSource() || d.sourceId.startsWith("corpse_storage|"))
                && owner instanceof AbstractContainerScreen<?>) {
                ModNetwork.sendToServer(new com.xtdpotato.xero_delta.network.InventorySourceQuickMovePacket(d.sourceId));
            } else if (owner instanceof PersonalWarehouseScreen warehouse && d.isWarehouseSource()) {
                warehouse.carrySelectedItem();
            } else if (owner instanceof PersonalWarehouseScreen && !d.sourceId.isBlank()) {
                ModNetwork.sendToServer(new WarehouseSourceTransferPacket(d.sourceId));
            }
            close(owner);
            return true;
        }
        if (splitBounds(d, p).contains(mouseX, mouseY)
            && d.stack.getCount() > 1 && !d.sourceId.isBlank() && !d.isContainerSource()) {
            d.split = true;
            d.actionScroll = Double.MAX_VALUE;
            d.splitAmount = Math.max(1, d.stack.getCount() / 2);
            return true;
        }
        if (sourceBounds(d, p).contains(mouseX, mouseY)) {
            openSourcePanel(d, false);
            return true;
        }
        if (usesBounds(d, p).contains(mouseX, mouseY)) {
            openSourcePanel(d, true);
            return true;
        }
        return true;
    }

    private static boolean saleClick(Screen owner, Detail d, double x, double y) {
        Bounds p = saleBounds(owner);
        if (d.saleClosing != 0L) return true;
        if (!p.contains(x, y) || saleClose(p).contains(x, y)) {
            d.saleClosing = System.nanoTime();
        } else if (recycleBounds(p).contains(x, y) && !d.pending) {
            d.pending = true;
            d.requestRevision = TradingClientState.INSTANCE.revision();
            ModNetwork.sendToServer(TradingActionPacket.recycle(d.sourceId, d.stack.getCount()));
        } else if (listBounds(p).contains(x, y)) {
            ModNetwork.sendToServer(TradingActionPacket.openOperator(d.sourceId));
            DETAILS.remove(owner);
        }
        return true;
    }

    private static void openSourcePanel(Detail detail) {
        openSourcePanel(detail, false);
    }

    private static void openSourcePanel(Detail detail, boolean uses) {
        detail.detailDismissed = true;
        detail.sourcePanel = true;
        detail.usesPanel = uses;
        detail.sourceOpened = System.nanoTime();
        detail.sourceClosing = 0L;
        detail.sourceScroll = 0.0D;
        detail.sourceTargetScroll = 0.0D;
        DRAGS.clear();
    }

    private static void startInspection(Screen owner) {
        Minecraft minecraft = Minecraft.getInstance();
        DETAILS.remove(owner);
        DRAGS.remove(owner);
        minecraft.setScreen(null);
        if (minecraft.player != null) {
            SafetyBoxInspectAnimation.start(minecraft.player.getId());
        }
        ModNetwork.sendToServer(new InspectRequestPacket());
    }

    private static boolean sourceClick(Screen owner, Detail detail,
                                       double mouseX, double mouseY) {
        Bounds panel = sourcePanelBounds(owner, detail.usesPanel);
        if (detail.sourceClosing != 0L) return true;
        if (sourceCloseBounds(panel).contains(mouseX, mouseY)) {
            detail.sourceClosing = System.nanoTime();
            return true;
        }
        if (!panel.contains(mouseX, mouseY)) return true;
        if (mouseY < sourceBodyTop(panel) || mouseY >= sourceBodyBottom(panel)) return true;
        double contentMouseY = mouseY + detail.sourceScroll;
        if (sourceRecipeBounds(panel).contains(mouseX, contentMouseY)) {
            boolean opened = detail.usesPanel
                ? RecipeViewerIntegration.openUses(detail.stack)
                : RecipeViewerIntegration.openRecipes(detail.stack);
            if (!recipeViewerName().isBlank() && opened) {
                detail.sourceClosing = System.nanoTime();
            }
            return true;
        }
        if (!detail.usesPanel && sourceMarketBounds(panel).contains(mouseX, contentMouseY)) {
            if (!nearMarket()) return true;
            TradingSyncPacket.ListingView listing = preferredListing(detail.stack);
            if (listing != null) {
                ModNetwork.sendToServer(TradingActionPacket.openMarketDetail(listing.id()));
            } else {
                ModNetwork.sendToServer(TradingActionPacket.openMarket("buy"));
            }
            DETAILS.remove(owner);
            return true;
        }
        if (detail.usesPanel) {
            java.util.List<FtbQuestIntegration.Match> matches =
                FtbQuestIntegration.findMatches(detail.stack);
            int row = sourceListRowAt(panel, mouseX, contentMouseY,
                sourceUsesListTop(panel), matches.size());
            if (row >= 0 && FtbQuestIntegration.open(matches.get(row))) {
                detail.sourceClosing = System.nanoTime();
            }
        } else {
            java.util.List<FtbQuestIntegration.RewardMatch> matches =
                FtbQuestIntegration.findRewardMatches(detail.stack);
            int row = sourceListRowAt(panel, mouseX, contentMouseY,
                sourceRewardListTop(panel), matches.size());
            if (row >= 0 && FtbQuestIntegration.open(matches.get(row))) {
                detail.sourceClosing = System.nanoTime();
            }
        }
        return true;
    }

    public static boolean mouseScrolled(Screen owner, double mouseX, double mouseY,
                                        double deltaY) {
        Detail detail = DETAILS.get(owner);
        if (detail == null) return false;
        if (!detail.sourcePanel) {
            Bounds card = popup(owner, detail);
            if (!card.contains(mouseX, mouseY) || detail.sale) return false;
            ItemDetailLayout.Sections sections = cardSections(detail, card);
            if (detail.previewDragging || detail.splitDragging) return true;
            if (mouseY < card.y + sections.previewTop() || mouseY >= card.bottom() - 1) return true;
            if (mouseY < card.y + sections.actionsTop()) {
                if (mouseY < card.y + sections.previewTop() + sections.previewFixedHeight()) return true;
                if (Screen.hasShiftDown() && previewMaxScroll(detail, card) > 0) {
                    detail.previewScroll = ItemDetailLayout.clampScroll(detail.previewScroll - deltaY * 24,
                        itemPreviewWidth(detail), itemPreviewBounds(detail, card).width);
                } else {
                    detail.previewYScroll = ItemDetailLayout.clampScroll(detail.previewYScroll - deltaY * 24,
                        sections.previewContent(), sections.previewHeight());
                }
            } else if (mouseY < card.y + sections.infoTop()) {
                detail.actionScroll = ItemDetailLayout.clampScroll(detail.actionScroll - deltaY * 24,
                    sections.actionsContent(), sections.actionsHeight());
            } else {
                detail.infoScroll = ItemDetailLayout.clampScroll(detail.infoScroll - deltaY * 24,
                    sections.infoContent(), sections.infoHeight());
            }
            return true;
        }
        Bounds panel = sourcePanelBounds(owner, detail.usesPanel);
        double maximum = sourceMaxScroll(detail, panel);
        if (panel.contains(mouseX, mouseY) && maximum > 0.0D) {
            detail.sourceTargetScroll = Math.max(0.0D, Math.min(maximum,
                detail.sourceTargetScroll - deltaY * 36.0D));
        }
        return true;
    }

    public static boolean mouseDragged(Screen owner, double x, double y, int button) {
        Detail d = DETAILS.get(owner);
        if (d != null && d.sourcePanel) return true;
        if (button == 0 && d != null && d.previewDragging) {
            updatePreviewScroll(d, popup(owner, d), x);
            return true;
        }
        if (button == 0 && d != null && d.split && d.splitDragging) {
            updateSplitAmount(d, popup(owner, d), x);
            return true;
        }
        Drag drag = DRAGS.get(owner);
        if (button != 0 || d == null || drag == null || d.sale) return false;
        int w = popupWidth(owner, d), h = popupHeight(owner, d);
        d.popupX = Math.max(MARGIN, Math.min(owner.width - w - MARGIN,
            drag.popupX + (int) x - drag.mouseX));
        d.popupY = Math.max(MARGIN, Math.min(owner.height - h - MARGIN,
            drag.popupY + (int) y - drag.mouseY));
        d.moved = true;
        return true;
    }

    public static boolean mouseReleased(Screen owner, double x, double y, int button) {
        if (button != 0) return false;
        if (SUPPRESS_NEXT_RELEASE.remove(owner) != null) return true;
        Detail d = DETAILS.get(owner);
        if (d != null && d.sourcePanel) return true;
        boolean splitDragged = d != null && d.splitDragging;
        boolean previewDragged = d != null && d.previewDragging;
        if (d != null) d.splitDragging = false;
        if (d != null) d.previewDragging = false;
        return splitDragged || previewDragged || DRAGS.remove(owner) != null;
    }

    /** Closes only the detail card after the server reserved the selected source. */
    public static void finishUse(boolean success) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen owner = minecraft.screen;
        if (owner == null) return;
        Detail detail = DETAILS.get(owner);
        if (detail == null || !detail.usePending) return;
        detail.usePending = false;
        if (!success) return;
        DETAILS.remove(owner);
        DRAGS.remove(owner);
    }

    public static void render(Screen owner, GuiGraphics g, int mouseX, int mouseY) {
        Detail d = DETAILS.get(owner);
        if (d == null) return;
        if (d.detailDismissed) {
            if (!d.sourcePanel) DETAILS.remove(owner);
            return;
        }
        if (d.closeAfter != 0L && System.nanoTime() >= d.closeAfter && d.closing == 0L) {
            close(owner);
        }
        if (d.pending && TradingClientState.INSTANCE.revision() > d.requestRevision) {
            d.pending = false;
            d.toast = TradingClientState.INSTANCE.success()
                ? Component.translatable("item_detail.xero_delta.sale_success",
                    TradingClientState.INSTANCE.messageValue())
                : Component.translatable(TradingClientState.INSTANCE.message());
            d.toastAt = System.nanoTime();
            if (TradingClientState.INSTANCE.success()) {
                d.saleClosing = System.nanoTime();
                close(owner);
            }
        }
        float alpha = alpha(owner, d);
        if (alpha <= 0.0F) return;
        renderCard(owner, g, d, mouseX, mouseY, alpha);
        if (d.sale) renderSale(owner, g, d, mouseX, mouseY);
    }

    /** Renders source/uses dialogs after callers have restored any local UI scale. */
    public static void renderModalTopmost(Screen owner, GuiGraphics graphics,
                                          int mouseX, int mouseY) {
        Detail detail = DETAILS.get(owner);
        if (detail != null && detail.sourcePanel) {
            renderSourcePanel(owner, graphics, detail, mouseX, mouseY);
        }
    }

    private static void renderCard(Screen owner, GuiGraphics g, Detail d,
                                   int mouseX, int mouseY, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        Bounds p = popup(owner, d);
        ClientDataCache cache = ClientDataCache.INSTANCE;
        String quality = cache.getQuality(d.stack);
        g.pose().pushPose();
        // Resolve a small foreground step from the active window instead of
        // assuming one absolute depth for vanilla and third-party containers.
        g.pose().translate(0.0F, 0.0F, ScreenLayerResolver.foreground());
        g.setColor(1, 1, 1, alpha);
        if (d.anchorVisible) {
            g.fill(d.anchor.x, d.anchor.y, d.anchor.right(),
                d.anchor.bottom(), color(0x44000000, alpha));
            g.renderOutline(d.anchor.x, d.anchor.y,
                d.anchor.width, d.anchor.height, color(0xFFFFFFFF, alpha));
        }
        g.fill(p.x, p.y, p.right(), p.bottom(), color(0xF40A1114, alpha));
        g.renderOutline(p.x, p.y, p.width, p.height, color(0xFF667577, alpha));
        int header = ItemDetailLayout.HEADER_HEIGHT;
        g.fill(p.x + 1, p.y + 1, p.right() - 1, p.y + header, color(0xFF263033, alpha));
        QualityIconRenderer.render(g, quality, p.x + 6,
            p.y + ItemDetailLayout.HEADER_PADDING, ItemDetailLayout.HEADER_TEXT_HEIGHT);
        g.drawString(mc.font, mc.font.plainSubstrByWidth(d.stack.getHoverName().getString(),
            Math.max(1, p.width - 36)), p.x + 20, p.y + ItemDetailLayout.HEADER_PADDING,
            color(0xFFF2F5F4, alpha), false);
        iconButton(g, closeBounds(p), CLOSE, closeBounds(p).contains(mouseX, mouseY), alpha);
        g.fill(p.x + 1, p.y + header, p.right() - 1, p.y + header + 1,
            color(0xFF2A3639, alpha));

        ItemDetailLayout.Sections sections = cardSections(d, p);
        Bounds content = contentBounds(d, p);
        Bounds preview = sectionBounds(p, sections.previewTop(), sections.previewHeight());
        if (preview.height > 0) {
            Bounds fixed = sectionBounds(p, sections.previewTop(), sections.previewFixedHeight());
            beginSection(g, fixed, 0);
            int previewTop = content.y + header + 2;
            if (!d.inspectable) {
                g.blit(COIN, content.x + 7, previewTop + 5, 9, 9, 0, 0, 16, 16, 16, 16);
                String price = String.format(Locale.ROOT, "%,d",
                    Math.max(0, cache.getReferencePrice(d.stack)));
                g.drawString(mc.font, mc.font.plainSubstrByWidth(price, Math.max(1, content.width - 46)),
                    content.x + 20, previewTop + 6, color(0xFFDCE3E1, alpha), false);
                iconButton(g, favoriteIconBounds(content), favorite(d.stack) ? STAR : STAR_OFF,
                    favoriteHitBounds(content).contains(mouseX, sectionMouseY(fixed, 0, mouseX, mouseY)), alpha);
            }
            endSection(g);
            Bounds image = sectionBounds(p, sections.previewTop() + sections.previewFixedHeight(),
                sections.previewImageHeight());
            if (image.height > 0) {
                int offset = -(int) d.previewYScroll;
                int my = sectionMouseY(image, offset, mouseX, mouseY);
                beginSection(g, image, offset);
                renderItemPreview(g, d, content, mouseX, my, alpha);
                endSection(g);
                renderSectionScrollbar(g, image,
                    sections.previewContent() - sections.previewFixedHeight(), d.previewYScroll, alpha);
            }
        }

        Bounds actions = sectionBounds(p, sections.actionsTop(), sections.actionsHeight());
        if (actions.height > 0) {
            int offset = sections.actionsOffset(itemPreviewRows(d), d.actionScroll);
            int my = sectionMouseY(actions, offset, mouseX, mouseY);
            beginSection(g, actions, offset);
            if (!d.inspectable) {
                int cell = Math.max(2, Math.min(4, content.width / 48));
                int matrixX = content.right() - cell * 6 - 7;
                int matrixY = actionRowY(d, content, 0) - cell * 4 - 8;
                drawMatrix(g, d.stack, matrixX, matrixY, cell, alpha);
                if (com.xtdpotato.xero_delta.data.ItemWeightDisplayPolicy.shouldShow(d.stack)) {
                    String weight = String.format(Locale.ROOT, "%.1fKG",
                        cache.getWeight(d.stack) * Math.max(1, d.stack.getCount()));
                    g.drawString(mc.font, mc.font.plainSubstrByWidth(weight,
                            Math.max(1, matrixX - content.x - 16)),
                        content.x + 7, matrixY + Math.max(0, cell * 4 - 8),
                        color(0xFF98A3A1, alpha), false);
                }
            }
            if (d.split) renderSplit(g, d, content, mouseX, my, alpha);
            else renderActions(owner, g, d, content, mouseX, my, alpha);
            endSection(g);
            renderSectionScrollbar(g, actions, sections.actionsContent(), d.actionScroll, alpha);
        }
        Bounds info = sectionBounds(p, sections.infoTop(), sections.infoHeight());
        if (info.height > 0 && !d.split) {
            int offset = info.y - (actionRowY(d, content, actionRows(d)) + 4) - (int) d.infoScroll;
            beginSection(g, info, offset);
            renderInformation(g, d, content, alpha);
            endSection(g);
            renderSectionScrollbar(g, info, sections.infoContent(), d.infoScroll, alpha);
        }
        if (d.toast != null && System.nanoTime() - d.toastAt < 2_400_000_000L) {
            String text = d.toast.getString();
            int w = Math.min(owner.width - 24, mc.font.width(text) + 34);
            int x = (owner.width - w) / 2;
            g.fill(x, 14, x + w, 42, 0xF02B4942);
            g.blit(COIN, x + 8, 23, 9, 9, 0, 0, 16, 16, 16, 16);
            g.drawString(mc.font, mc.font.plainSubstrByWidth(text, w - 28),
                x + 21, 24, 0xFFFFFFFF, false);
        }
        g.setColor(1, 1, 1, 1);
        g.pose().popPose();
    }

    private static Bounds sectionBounds(Bounds p, int top, int height) {
        return new Bounds(p.x + 1, p.y + top, Math.max(1, p.width - 2), height);
    }

    private static int sectionMouseY(Bounds viewport, int offset, int mouseX, int mouseY) {
        return viewport.contains(mouseX, mouseY) ? mouseY - offset : Integer.MIN_VALUE;
    }

    private static void beginSection(GuiGraphics g, Bounds viewport, int offset) {
        g.enableScissor(viewport.x, viewport.y, viewport.right(), viewport.bottom());
        g.pose().pushPose();
        g.pose().translate(0, offset, 0);
    }

    private static void endSection(GuiGraphics g) {
        g.pose().popPose();
        g.disableScissor();
    }

    private static void renderSectionScrollbar(GuiGraphics g, Bounds viewport, int contentHeight,
                                               double scroll, float alpha) {
        int maximum = contentHeight - viewport.height;
        if (maximum <= 0 || viewport.height <= 0) return;
        int thumb = ItemDetailLayout.previewThumbWidth(viewport.height, contentHeight);
        int thumbY = viewport.y + (int) ((viewport.height - thumb) * scroll / maximum);
        g.fill(viewport.right() - 3, viewport.y, viewport.right() - 1,
            viewport.bottom(), color(0xFF263033, alpha));
        g.fill(viewport.right() - 3, thumbY, viewport.right() - 1,
            thumbY + thumb, color(0xFF87918F, alpha));
    }


    private static Component description(Detail detail) {
        ConsumableProfile profile = ConsumableProfile.get(detail.stack);
        if (profile != null) {
            var result = Component.empty();
            var matcher = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?%?").matcher(profile.description());
            int end = 0;
            while (matcher.find()) {
                result.append(Component.literal(profile.description().substring(end, matcher.start())));
                result.append(Component.literal(matcher.group()).withStyle(
                    style -> style.withColor(0x00D6A0)));
                end = matcher.end();
            }
            return result.append(Component.literal(profile.description().substring(end)));
        }
        var minecraft = Minecraft.getInstance();
        var lines = detail.stack.getTooltipLines(Item.TooltipContext.of(minecraft.level),
            minecraft.player, TooltipFlag.NORMAL);
        var result = Component.empty();
        for (int index = 1; index < lines.size(); index++) {
            if (index > 1) result.append("\n");
            result.append(lines.get(index));
        }
        return result;
    }

    private static int informationHeight(Detail detail, int width) {
        ConsumableProfile profile = ConsumableProfile.get(detail.stack);
        int height = detail.stack.isDamageableItem() ? 34 : 0;
        if (profile != null) height += 18 + profile.effects().size() * 18 + 8;
        if (!description(detail).getString().isBlank()) {
            height += Minecraft.getInstance().font.split(description(detail), Math.max(20, width - 22)).size() * 12 + 12;
        }
        return height;
    }

    private static void renderInformation(GuiGraphics g, Detail detail, Bounds p, float alpha) {
        var font = Minecraft.getInstance().font;
        ConsumableProfile profile = ConsumableProfile.get(detail.stack);
        int y = actionRowY(detail, p, actionRows(detail)) + 4;
        if (detail.stack.isDamageableItem()) {
            renderItemDurability(g, detail.stack, p, y, alpha);
            y += 34;
        }
        if (profile != null) {
            g.drawString(font, "效果", p.x + 8, y, color(0xFF87918F, alpha), false);
            y += 18;
            int index = 0;
            for (String[] row : profile.effects()) {
                int ink = color(0xFFB6BFBD, alpha);
                int ix = p.x + 9, iy = y + 1;
                if (index++ == 0) {
                    // A compact clock glyph made from GUI primitives.
                    g.renderOutline(ix, iy, 9, 9, ink);
                    g.fill(ix + 4, iy + 2, ix + 5, iy + 6, ink);
                    g.fill(ix + 4, iy + 5, ix + 7, iy + 6, ink);
                } else if (row[0].contains("生命")) {
                    g.fill(ix + 3, iy, ix + 6, iy + 9, ink);
                    g.fill(ix, iy + 3, ix + 9, iy + 6, ink);
                } else {
                    String effect = switch(profile.kind()) {
                        case PAIN -> "pain_relief";
                        case HEARING -> "hearing_boost";
                        case WEIGHT -> "weight_boost";
                        case STAMINA, CAPACITY, RECOVERY -> "stamina_attribute_boost";
                        default -> "left_arm_injury";
                    };
                    g.blit(EffectIconResources.icon(effect), ix, iy, 10, 10, 0, 0, 18, 18, 18, 18);
                }
                int valueWidth = font.width(row[1]);
                String label = font.plainSubstrByWidth(row[0], Math.max(12, p.width - 40 - valueWidth));
                g.drawString(font, label, p.x + 24, y + 2, ink, false);
                g.drawString(font, row[1], p.right() - 9 - valueWidth, y + 2, ink, false);
                y += 18;
            }
            g.fill(p.x + 7, y, p.right() - 7, y + 1, color(0xFF2A3639, alpha));
            y += 8;
        }
        for (var line : font.split(description(detail), Math.max(20, p.width - 22))) {
            g.drawString(font, line, p.x + 8, y + 4, color(0xFF939E9B, alpha), false);
            y += 12;
        }
    }


    private static void renderItemDurability(GuiGraphics graphics, ItemStack stack,
                                             Bounds popup, int top, float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        int maximum = Math.max(1, stack.getMaxDamage());
        int current = Math.max(0, maximum - stack.getDamageValue());
        int muted = color(0xCCF2F5F4, alpha);
        int full = color(0xFFF2F5F4, alpha);
        graphics.drawString(minecraft.font,
            ConsumableProfile.get(stack) == null
                ? Component.translatable("item_detail.xero_delta.durability")
                : Component.literal(ConsumableProfile.get(stack).capacityLabel()),
            popup.x + 7, top + 3, muted, false);
        String currentText = Integer.toString(current);
        String suffix = "/" + maximum;
        int suffixWidth = minecraft.font.width(suffix);
        int currentWidth = minecraft.font.width(currentText);
        int right = popup.right() - 7;
        graphics.drawString(minecraft.font, currentText,
            right - suffixWidth - currentWidth, top + 3, full, false);
        graphics.drawString(minecraft.font, suffix,
            right - suffixWidth, top + 3, muted, false);

        int barX = popup.x + 7;
        int barY = top + 17;
        int barWidth = Math.max(1, popup.width - 14);
        int filled = Math.round(barWidth * current / (float) maximum);
        graphics.fill(barX, barY, barX + barWidth, barY + 4,
            color(0xFF596264, alpha));
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + 4,
                color(0xFFF2F5F4, alpha));
        }
        graphics.fill(barX, top + 28, barX + barWidth, top + 29,
            color(0xFF2A3639, alpha));
    }

    private static void renderActions(Screen owner, GuiGraphics g, Detail d, Bounds p,
                                      int mx, int my, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        if (d.sourceOnly) {
            button(g, mc, sourceOnlyBounds(d, p), SOURCE,
                Component.translatable("item_detail.xero_delta.source"), true,
                sourceOnlyBounds(d, p).contains(mx, my), alpha);
            return;
        }
        if (d.inspectable) {
            button(g, mc, inspectBounds(d, p), SOURCE,
                Component.translatable("safety_box.xero_delta.inspect"), true,
                inspectBounds(d, p).contains(mx, my), alpha);
            return;
        }
        button(g, mc, sellBounds(d, p), SELL,
            Component.translatable("item_detail.xero_delta.sell"),
            externalContainerContext(owner) && nearMarket() && !d.sourceId.isBlank() && !d.isContainerSource(),
            sellBounds(d, p).contains(mx, my), alpha);
        button(g, mc, returnBounds(d, p), BACK,
            Component.translatable(d.isWarehouseSource() || d.isContainerSource()
                ? "item_detail.xero_delta.carry" : "item_detail.xero_delta.return"),
            externalContainerContext(owner),
            returnBounds(d, p).contains(mx, my), alpha);
        button(g, mc, splitBounds(d, p), SPLIT,
            Component.translatable("item_detail.xero_delta.split"),
            d.stack.getCount() > 1 && !d.sourceId.isBlank() && !d.isContainerSource(),
            splitBounds(d, p).contains(mx, my), alpha);
        button(g, mc, sourceBounds(d, p), SOURCE,
            Component.translatable("item_detail.xero_delta.source"),
            true, sourceBounds(d, p).contains(mx, my), alpha);
        button(g, mc, usesBounds(d, p), INFO,
            Component.translatable("market.xero_delta.uses"),
            true, usesBounds(d, p).contains(mx, my), alpha);
        if (canReplaceCarrier(d)) {
            button(g, mc, replaceBounds(d, p), REPLACE,
                Component.translatable(replaceActionKey(owner, d)), true,
                replaceBounds(d, p).contains(mx, my), alpha);
        }
        if (canEquipWeapon(d)) {
            button(g, mc, equipWeaponBounds(d, p), REPLACE,
                Component.translatable("item_detail.xero_delta.equip_weapon"), true,
                equipWeaponBounds(d, p).contains(mx, my), alpha);
        }
        if (hasUseAction(d)) {
            button(g, mc, useBounds(d, p), USE,
                Component.translatable("item_detail.xero_delta.use"), canUse(d),
                useBounds(d, p).contains(mx, my), alpha);
        }
        if (canDiscard(d)) {
            button(g, mc, discardBounds(d, p), DISCARD,
                Component.translatable("item_detail.xero_delta.discard"), true,
                discardBounds(d, p).contains(mx, my), alpha);
        }
    }

    private static void renderSplit(GuiGraphics g, Detail d, Bounds p,
                                    int mx, int my, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        g.drawString(mc.font, Component.translatable("item_detail.xero_delta.quantity",
            d.splitAmount, d.stack.getCount()), p.x + 7, p.bottom() - 49,
            color(0xFFE5EAE8, alpha), false);
        symbolButton(g, mc, minusBounds(p), "-", minusBounds(p).contains(mx, my), alpha);
        symbolButton(g, mc, plusBounds(p), "+", plusBounds(p).contains(mx, my), alpha);
        Bounds slider = splitSliderBounds(p);
        boolean sliderHovered = slider.contains(mx, my) || d.splitDragging;
        int trackY = slider.y + slider.height / 2;
        g.fill(slider.x, trackY - 1, slider.right(), trackY + 1,
            color(sliderHovered ? 0xFF7CAFA0 : 0xFF536260, alpha));
        int thumbX = splitThumbX(d, slider);
        g.fill(thumbX - 2, slider.y, thumbX + 3, slider.bottom(),
            color(sliderHovered ? 0xFFF1F6F3 : 0xFF8FA19D, alpha));
        button(g, mc, cancelBounds(p), CLOSE, Component.translatable("gui.cancel"), true,
            cancelBounds(p).contains(mx, my), alpha);
        button(g, mc, confirmBounds(p), SPLIT,
            Component.translatable("item_detail.xero_delta.split"), true,
            confirmBounds(p).contains(mx, my), alpha);
    }

    private static void renderSale(Screen owner, GuiGraphics g, Detail d, int mx, int my) {
        float alpha = saleAlpha(d);
        if (alpha <= 0) {
            d.sale = false;
            d.saleClosing = 0;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Bounds p = saleBounds(owner);
        long unit = Math.max(0, ClientDataCache.INSTANCE.getReferencePrice(d.stack));
        String id = BuiltInRegistries.ITEM.getKey(d.stack.getItem()).toString();
        long recycle = multiply(BuiltinRecycleValueCatalog.resolve(id, unit), d.stack.getCount());
        long afterTax = TradingRules.sellerProceeds(multiply(unit, d.stack.getCount()));
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, ScreenLayerResolver.dialog());
        g.fill(0, 0, owner.width, owner.height, color(0xA80A1013, alpha));
        g.fill(p.x, p.y, p.right(), p.bottom(), color(0xF4182228, alpha));
        g.renderOutline(p.x, p.y, p.width, p.height, color(0xFF667577, alpha));
        g.drawString(mc.font, Component.translatable("item_detail.xero_delta.sale_title"),
            p.x + 10, p.y + 9, color(0xFFF1F5F3, alpha), false);
        iconButton(g, saleClose(p), CLOSE, saleClose(p).contains(mx, my), alpha);
        int left = Math.max(74, p.width * 42 / 100);
        int size = Math.max(30, Math.min(72, Math.min(left - 18, p.height - 62)));
        renderItemModel(g, d.stack, p.x + (left - size) / 2, p.y + 37, size, size);
        String label = d.stack.getHoverName().getString() + " x " + d.stack.getCount();
        g.drawCenteredString(mc.font, mc.font.plainSubstrByWidth(label, left - 12),
            p.x + left / 2, p.bottom() - 18, color(0xFFE1E7E5, alpha));
        int divider = p.x + left;
        g.fill(divider, p.y + 34, divider + 1, p.bottom() - 10,
            color(0xFF435156, alpha));
        priceRow(g, divider + 10, p.y + 45, p.right() - divider - 20,
            Component.translatable("item_detail.xero_delta.recycle_price"), recycle, alpha);
        button(g, mc, recycleBounds(p), SELL,
            Component.translatable("item_detail.xero_delta.sell"), !d.pending,
            recycleBounds(p).contains(mx, my), alpha);
        priceRow(g, divider + 10, p.y + 98, p.right() - divider - 20,
            Component.translatable("item_detail.xero_delta.market_after_tax"), afterTax, alpha);
        button(g, mc, listBounds(p), LIST,
            Component.translatable("item_detail.xero_delta.list"), !d.pending,
            listBounds(p).contains(mx, my), alpha);
        g.pose().popPose();
    }

    private static void renderSourcePanel(Screen owner, GuiGraphics graphics, Detail detail,
                                          int mouseX, int mouseY) {
        float alpha = sourceAlpha(detail);
        if (alpha <= 0.0F) return;
        Minecraft minecraft = Minecraft.getInstance();
        Bounds panel = sourcePanelBounds(owner, detail.usesPanel);
        tickSourceScroll(detail, panel);
        boolean recipeAvailable = !recipeViewerName().isBlank();
        boolean marketAvailable = nearMarket();

        graphics.pose().pushPose();
        // This dialog is rendered after embedded inventory viewports have restored
        // the physical GUI pose. Use the actual GUI dimensions rather than the
        // logical dimensions retained by PlayerStatusScreen.
        graphics.pose().translate(0.0F, 0.0F,
            ScreenLayerResolver.finalPass() + ScreenLayerResolver.dialog());
        graphics.fill(0, 0, guiWidth(), guiHeight(), color(0xB00A1013, alpha));
        graphics.fill(panel.x, panel.y, panel.right(), panel.bottom(),
            color(0xFA101A1F, alpha));
        graphics.renderOutline(panel.x, panel.y, panel.width, panel.height,
            color(0xFF4B6269, alpha));
        graphics.drawString(minecraft.font, Component.translatable(detail.usesPanel
                ? "market.xero_delta.uses" : "market.xero_delta.source"),
            panel.x + 12, panel.y + 11, color(0xFFF1F5F3, alpha), false);
        iconButton(graphics, sourceCloseBounds(panel), CLOSE,
            sourceCloseBounds(panel).contains(mouseX, mouseY), alpha);
        graphics.fill(panel.x + 10, panel.y + 37, panel.right() - 10, panel.y + 38,
            color(0xFF4B6269, alpha));

        int bodyTop = sourceBodyTop(panel);
        int bodyBottom = sourceBodyBottom(panel);
        int contentMouseY = (int) Math.round(mouseY + detail.sourceScroll);
        graphics.enableScissor(panel.x + 1, bodyTop, panel.right() - 1, bodyBottom);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, (float) -detail.sourceScroll, 0.0F);

        String viewer = recipeViewerName();
        Component recipeLabel = viewer.isBlank()
            ? Component.translatable("market.xero_delta.recipe_viewer_missing")
            : Component.translatable(detail.usesPanel
                ? "market.xero_delta.open_usage_viewer"
                : "market.xero_delta.open_recipe_viewer", viewer);
        Bounds recipe = sourceRecipeBounds(panel);
        button(graphics, minecraft, recipe, detail.usesPanel ? INFO : SOURCE,
            recipeLabel, recipeAvailable,
            recipe.contains(mouseX, contentMouseY), alpha);
        if (detail.usesPanel) {
            renderFtbUsageRows(graphics, detail, panel, mouseX, contentMouseY, alpha);
        } else {
            Bounds market = sourceMarketBounds(panel);
            button(graphics, minecraft, market, SOURCE,
                Component.translatable("market.xero_delta.source_market_purchase"),
                marketAvailable, market.contains(mouseX, contentMouseY), alpha);
            String namespace = BuiltInRegistries.ITEM.getKey(detail.stack.getItem()).getNamespace();
            int metadataY = market.bottom() + 8;
            String namespaceText = Component.translatable(
                "market.xero_delta.source_namespace", namespace).getString();
            graphics.drawString(minecraft.font,
                minecraft.font.plainSubstrByWidth(namespaceText, panel.width - 24),
                panel.x + 12, metadataY, color(0xFF9AA8A7, alpha), false);
            String priceText = Component.translatable(
                "market.xero_delta.recommended_price",
                TradingUi.formatDetailed(recommendedPriceToday(detail.stack))).getString();
            graphics.drawString(minecraft.font,
                minecraft.font.plainSubstrByWidth(priceText, panel.width - 24),
                panel.x + 12, metadataY + 18, color(0xFFE7ECEA, alpha), false);
            renderFtbRewardRows(graphics, detail, panel, mouseX, contentMouseY, alpha);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
        renderSourceScrollbar(graphics, detail, panel, alpha);
        renderSourceFooter(graphics, minecraft, detail, panel, alpha);
        graphics.pose().popPose();
    }

    private static void renderSourceFooter(GuiGraphics graphics, Minecraft minecraft,
                                           Detail detail, Bounds panel, float alpha) {
        Bounds footer = sourceFooterBounds(panel);
        graphics.fill(footer.x, footer.y, footer.right(), footer.bottom(),
            color(0xCC0A1114, alpha));
        graphics.renderOutline(footer.x, footer.y, footer.width, footer.height,
            color(0xFF4B6269, alpha));
        graphics.renderItem(detail.stack, footer.x + 5, footer.y + 5);

        int textX = footer.x + 26;
        int textWidth = Math.max(1, footer.width - 31);
        List<Component> lines = new ArrayList<>();
        try {
            lines.addAll(detail.stack.getTooltipLines(Item.TooltipContext.of(minecraft.level),
                minecraft.player, TooltipFlag.NORMAL));
        } catch (RuntimeException ignored) {
            lines.add(detail.stack.getHoverName());
        }
        if (lines.isEmpty()) lines.add(detail.stack.getHoverName());
        int y = footer.y + 5;
        int lineCount = Math.min(3, lines.size());
        for (int index = 0; index < lineCount; index++) {
            Component line = lines.get(index);
            graphics.drawString(minecraft.font,
                minecraft.font.plainSubstrByWidth(line.getString(), textWidth), textX, y,
                color(index == 0 ? 0xFFF1F5F3 : 0xFFB7C4C1, alpha), false);
            y += 9;
        }
    }
    private static void renderFtbRewardRows(GuiGraphics graphics, Detail detail,
                                             Bounds panel, int mouseX, int mouseY,
                                             float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        java.util.List<FtbQuestIntegration.RewardMatch> matches =
            FtbQuestIntegration.findRewardMatches(detail.stack);
        int top = sourceRewardListTop(panel);
        graphics.drawString(minecraft.font,
            Component.translatable("market.xero_delta.quest_reward_sources"),
            panel.x + 12, top - 14, color(0xFF9AA8A7, alpha), false);
        if (matches.isEmpty()) {
            graphics.drawString(minecraft.font,
                Component.translatable("market.xero_delta.quest_reward_unavailable"),
                panel.x + 12, top + 5, color(0xFF7F8B89, alpha), false);
            return;
        }
        int count = matches.size();
        for (int index = 0; index < count; index++) {
            FtbQuestIntegration.RewardMatch match = matches.get(index);
            drawSourceListRow(graphics, panel, top, index,
                Component.translatable("market.xero_delta.quest_reward_available",
                    match.questTitle(), match.rewardTitle()), mouseX, mouseY, alpha);
        }
    }

    private static void renderFtbUsageRows(GuiGraphics graphics, Detail detail,
                                            Bounds panel, int mouseX, int mouseY,
                                            float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        java.util.List<FtbQuestIntegration.Match> matches =
            FtbQuestIntegration.findMatches(detail.stack);
        int top = sourceUsesListTop(panel);
        if (matches.isEmpty()) {
            graphics.drawString(minecraft.font,
                Component.translatable("market.xero_delta.quest_usage_unavailable"),
                panel.x + 12, top + 5, color(0xFF7F8B89, alpha), false);
            return;
        }
        int count = matches.size();
        for (int index = 0; index < count; index++) {
            FtbQuestIntegration.Match match = matches.get(index);
            drawSourceListRow(graphics, panel, top, index,
                Component.translatable("market.xero_delta.quest_usage_available",
                    match.questTitle(), match.taskTitle()), mouseX, mouseY, alpha);
        }
    }

    private static void drawSourceListRow(GuiGraphics graphics, Bounds panel, int top,
                                          int index, Component label, int mouseX,
                                          int mouseY, float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        Bounds row = sourceListRow(panel, top, index);
        boolean hovered = row.contains(mouseX, mouseY);
        graphics.fill(row.x, row.y, row.right(), row.bottom(),
            color(hovered ? 0xFF40545B : 0x55000000, alpha));
        graphics.renderOutline(row.x, row.y, row.width, row.height,
            color(hovered ? 0xFF69DDB5 : 0xFF4B6269, alpha));
        graphics.drawString(minecraft.font,
            minecraft.font.plainSubstrByWidth(label.getString(), row.width - 12),
            row.x + 6, row.y + 8, color(hovered ? 0xFFF1F5F3 : 0xFF65D8B2, alpha), false);
    }

    private static TradingSyncPacket.ListingView preferredListing(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        TradingSyncPacket.ListingView preferred = null;
        for (TradingSyncPacket.ListingView listing : TradingClientState.INSTANCE.listings()) {
            if (listing.expired() || !sameMarketItem(stack, listing.stack())
                || minecraft.player != null && listing.sellerId().equals(minecraft.player.getUUID())) {
                continue;
            }
            if (preferred == null || unitPrice(listing) < unitPrice(preferred)) {
                preferred = listing;
            }
        }
        return preferred;
    }

    private static long recommendedPriceToday(ItemStack stack) {
        long fallback = Math.max(1L, ClientDataCache.INSTANCE.getPrice(stack));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return fallback;
        long gameTime = minecraft.level.getGameTime();
        long dayStart = gameTime - Math.floorMod(gameTime, 24_000L);
        long lowest = Long.MAX_VALUE;
        for (TradingSyncPacket.ListingView listing : TradingClientState.INSTANCE.listings()) {
            if (listing.expired() || listing.createdAt() < dayStart
                || !sameMarketItem(stack, listing.stack())) continue;
            lowest = Math.min(lowest, unitPrice(listing));
        }
        return lowest == Long.MAX_VALUE ? fallback : lowest;
    }

    private static boolean sameMarketItem(ItemStack first, ItemStack second) {
        return first != null && second != null && !first.isEmpty() && !second.isEmpty()
            && ItemStack.isSameItemSameComponents(first, second);
    }

    private static long unitPrice(TradingSyncPacket.ListingView listing) {
        int count = Math.max(1, listing.stack().getCount());
        return Math.max(1L, (listing.price() + count - 1L) / count);
    }

    private static String recipeViewerName() {
        return RecipeViewerIntegration.viewerName();
    }

    private static void priceRow(GuiGraphics g, int x, int y, int width,
                                 Component label, long value, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        g.fill(x, y, x + width, y + 26, color(0xB82A343A, alpha));
        g.drawString(mc.font, label, x + 6, y + 9, color(0xFFDDE4E2, alpha), false);
        String text = String.format(Locale.ROOT, "%,d", value);
        int tw = mc.font.width(text);
        g.blit(COIN, x + width - tw - 19, y + 8, 9, 9, 0, 0, 16, 16, 16, 16);
        g.drawString(mc.font, text, x + width - tw - 6, y + 9,
            color(0xFFE3E7E6, alpha), false);
    }

    private static void drawMatrix(GuiGraphics g, ItemStack stack,
                                   int x, int y, int cell, float alpha) {
        ItemSize size = ClientDataCache.INSTANCE.getSize(stack);
        for (int row = 0; row < 4; row++) for (int column = 0; column < 6; column++) {
            int px = x + column * cell, py = y + row * cell;
            boolean occupied = column < Math.min(6, Math.max(1, size.width()))
                && row < Math.min(4, Math.max(1, size.height()));
            g.fill(px, py, px + Math.max(1, cell - 1), py + Math.max(1, cell - 1),
                color(occupied ? 0xFFE8ECEB : 0xFF667170, alpha));
        }
    }

    private static int itemPreviewWidth(Detail detail) {
        return ItemDetailLayout.itemExtent(ClientDataCache.INSTANCE.getSize(detail.stack).width());
    }

    private static int itemPreviewRows(Detail detail) {
        return detail == null ? 1 : ClientDataCache.INSTANCE.getSize(detail.stack).height();
    }

    private static Bounds itemPreviewBounds(Detail detail, Bounds p) {
        return new Bounds(p.x + 8, p.y + ItemDetailLayout.HEADER_HEIGHT + 1 + ItemDetailLayout.PREVIEW_FIXED_HEIGHT,
            Math.max(1, p.width - 16), ItemDetailLayout.itemExtent(itemPreviewRows(detail)));
    }

    private static Bounds previewScrollbarBounds(Detail detail, Bounds p) {
        Bounds preview = itemPreviewBounds(detail, p);
        return new Bounds(preview.x, preview.bottom() + 2, preview.width, 6);
    }

    private static int previewMaxScroll(Detail detail, Bounds p) {
        return Math.max(0, itemPreviewWidth(detail) - itemPreviewBounds(detail, p).width);
    }

    private static void updatePreviewScroll(Detail detail, Bounds p, double mouseX) {
        Bounds track = previewScrollbarBounds(detail, p);
        detail.previewScroll = ItemDetailLayout.previewScrollAt(
            mouseX - track.x, track.width, itemPreviewWidth(detail));
    }

    private static void renderItemPreview(GuiGraphics g, Detail detail, Bounds p,
                                          int mouseX, int mouseY, float alpha) {
        Bounds preview = itemPreviewBounds(detail, p);
        int width = itemPreviewWidth(detail);
        detail.previewScroll = ItemDetailLayout.clampScroll(detail.previewScroll, width, preview.width);
        int x = preview.x + Math.max(0, (preview.width - width) / 2) - (int) detail.previewScroll;
        // Clip oversized previews instead of changing their per-cell scale.
        g.enableScissor(preview.x, preview.y, preview.right(), preview.bottom());
        renderItemModel(g, detail.stack, x, preview.y, width, preview.height);
        g.disableScissor();
        g.setColor(1, 1, 1, alpha);
        if (width > preview.width) {
            Bounds track = previewScrollbarBounds(detail, p);
            int thumb = ItemDetailLayout.previewThumbWidth(track.width, width);
            int thumbX = track.x + (int) ((track.width - thumb) * detail.previewScroll
                / (width - track.width));
            boolean hovered = track.contains(mouseX, mouseY) || detail.previewDragging;
            g.fill(track.x, track.y + 2, track.right(), track.y + 4, color(0xFF263033, alpha));
            g.fill(thumbX, track.y, thumbX + thumb, track.bottom(),
                color(hovered ? 0xFFF1F6F3 : 0xFF87918F, alpha));
        }
    }

    private static void renderItemModel(GuiGraphics g, ItemStack stack, int x, int y,
                                        int width, int height) {
        g.pose().pushPose();
        g.pose().translate(x, y, GridItemRenderer.ITEM_Z);
        g.pose().scale(width / 16.0F, height / 16.0F, 1);
        QualityItemBackground.pushSuppress();
        try {
            g.setColor(1, 1, 1, 1);
            g.renderItem(stack, 0, 0);
        } finally {
            QualityItemBackground.popSuppress();
            g.pose().popPose();
        }
    }

    private static void button(GuiGraphics g, Minecraft mc, Bounds p,
                               ResourceLocation icon, Component label, boolean enabled,
                               boolean hovered, float alpha) {
        g.fill(p.x, p.y, p.right(), p.bottom(), color(!enabled ? 0x77303938
            : hovered ? 0xDD2C5C4C : 0xBB111A1D, alpha));
        g.renderOutline(p.x, p.y, p.width, p.height, color(!enabled ? 0xFF52615F
            : hovered ? 0xFFF1F6F3 : 0xFF657674, alpha));
        int s = Math.max(7, Math.min(12, p.height - 4));
        if (REPLACE.equals(icon)) {
            drawReplaceIcon(g, p.x + 4, p.y + (p.height - s) / 2, s, alpha);
        } else {
            g.blit(icon, p.x + 4, p.y + (p.height - s) / 2, s, s,
                0, 0, 16, 16, 16, 16);
        }
        g.drawString(mc.font, mc.font.plainSubstrByWidth(label.getString(),
            Math.max(1, p.width - s - 16)), p.x + s + 7,
            p.y + Math.max(2, (p.height - 8) / 2),
            color(enabled ? 0xFFFFFFFF : 0xFF899391, alpha), false);
    }

    private static void symbolButton(GuiGraphics g, Minecraft mc, Bounds p,
                                     String symbol, boolean hovered, float alpha) {
        g.fill(p.x, p.y, p.right(), p.bottom(),
            color(hovered ? 0xDD2C5C4C : 0xBB111A1D, alpha));
        g.renderOutline(p.x, p.y, p.width, p.height,
            color(hovered ? 0xFFF1F6F3 : 0xFF657674, alpha));
        g.drawCenteredString(mc.font, symbol, p.x + p.width / 2,
            p.y + Math.max(2, (p.height - 8) / 2),
            color(0xFFFFFFFF, alpha));
    }

    private static void drawReplaceIcon(GuiGraphics g, int x, int y, int size, float alpha) {
        int arrowSize = Math.max(5, size / 2);
        g.blit(BACK, x, y, arrowSize, arrowSize,
            0, 0, 16, 16, 16, 16);
        g.pose().pushPose();
        g.pose().translate(x + size, y + size, 0);
        g.pose().scale(-1, -1, 1);
        g.blit(BACK, 0, 0, arrowSize, arrowSize,
            0, 0, 16, 16, 16, 16);
        g.pose().popPose();
    }
    private static void iconButton(GuiGraphics g, Bounds p, ResourceLocation icon,
                                   boolean hovered, float alpha) {
        g.fill(p.x - 1, p.y - 1, p.right() + 1, p.bottom() + 1,
            color(hovered ? 0xDD2C5C4C : 0x33000000, alpha));
        g.renderOutline(p.x - 1, p.y - 1, p.width + 2, p.height + 2,
            color(hovered ? 0xFFF1F6F3 : 0x00657674, alpha));
        blit(g, icon, p, alpha);
    }

    private static void updateSplitAmount(Detail d, Bounds popup, double mouseX) {
        Bounds slider = splitSliderBounds(popup);
        int maximum = Math.max(1, d.stack.getCount() - 1);
        double ratio = Math.max(0.0D, Math.min(1.0D,
            (mouseX - slider.x) / Math.max(1.0D, slider.width)));
        d.splitAmount = Math.max(1, Math.min(maximum,
            1 + (int) Math.round(ratio * Math.max(0, maximum - 1))));
    }

    private static int splitThumbX(Detail d, Bounds slider) {
        int maximum = Math.max(1, d.stack.getCount() - 1);
        double ratio = maximum <= 1 ? 0.0D
            : (d.splitAmount - 1) / (double) (maximum - 1);
        return slider.x + (int) Math.round(ratio * slider.width);
    }

    private static void blit(GuiGraphics g, ResourceLocation icon, Bounds p, float alpha) {
        g.setColor(1, 1, 1, alpha);
        g.blit(icon, p.x, p.y, p.width, p.height,
            0, 0, 16, 16, 16, 16);
    }

    private static float alpha(Screen owner, Detail d) {
        long now = System.nanoTime();
        if (d.closing != 0) {
            long elapsed = now - d.closing;
            if (elapsed >= FADE) {
                DETAILS.remove(owner);
                return 0;
            }
            return 1 - elapsed / (float) FADE;
        }
        return Math.min(1, (now - d.opened) / (float) FADE);
    }

    private static float saleAlpha(Detail d) {
        long now = System.nanoTime();
        if (d.saleClosing != 0)
            return Math.max(0, 1 - (now - d.saleClosing) / (float) FADE);
        return Math.min(1, (now - d.saleOpened) / (float) FADE);
    }

    private static float sourceAlpha(Detail d) {
        long now = System.nanoTime();
        if (d.sourceClosing != 0L) {
            long elapsed = now - d.sourceClosing;
            if (elapsed >= FADE) {
                d.sourcePanel = false;
                d.sourceClosing = 0L;
                return 0.0F;
            }
            return Math.max(0.0F, 1.0F - elapsed / (float) FADE);
        }
        return Math.min(1.0F, (now - d.sourceOpened) / (float) FADE);
    }

    private static Bounds popup(Screen owner, Detail d) {
        int width = popupWidth(owner, d), height = popupHeight(owner, d);
        if (d.popupX != Integer.MIN_VALUE) return new Bounds(
            Math.max(MARGIN, Math.min(owner.width - width - MARGIN, d.popupX)),
            Math.max(MARGIN, Math.min(owner.height - height - MARGIN, d.popupY)),
            width, height);
        int x = d.anchor.right() + MARGIN;
        if (x + width > owner.width - MARGIN) x = d.anchor.x - width - MARGIN;
        x = Math.max(MARGIN, Math.min(owner.width - width - MARGIN, x));
        int y = Math.max(MARGIN, Math.min(owner.height - height - MARGIN, d.anchor.y - 12));
        return new Bounds(x, y, width, height);
    }

    public static int previewWidth(Screen owner) {
        return ItemDetailLayout.resolveWidth(StatusEffectHudState.itemDetailAutoWidth(),
            StatusEffectHudState.itemDetailWidth(), ItemDetailLayout.BASE_WIDTH,
            StatusEffectHudState.itemDetailScale(), owner.width);
    }

    public static int previewHeight(Screen owner) {
        return ItemDetailLayout.resolveHeight(StatusEffectHudState.itemDetailAutoHeight(),
            StatusEffectHudState.itemDetailHeight(), 3, 0,
            StatusEffectHudState.itemDetailScale(), owner.height);
    }

    private static int popupWidth(Screen owner, Detail detail) {
        return ItemDetailLayout.resolveWidth(StatusEffectHudState.itemDetailAutoWidth(),
            StatusEffectHudState.itemDetailWidth(), adaptiveContentWidth(detail),
            StatusEffectHudState.itemDetailScale(), owner.width);
    }

    private static int popupHeight(Screen owner, Detail detail) {
        return ItemDetailLayout.resolveHeight(StatusEffectHudState.itemDetailAutoHeight(),
            StatusEffectHudState.itemDetailHeight(), actionRows(detail),
            (detail == null ? 0 : informationHeight(detail, popupWidth(owner, detail)))
                + ItemDetailLayout.previewSectionHeight(itemPreviewRows(detail)) - ItemDetailLayout.PREVIEW_HEIGHT,
            StatusEffectHudState.itemDetailScale(), owner.height);
    }

    private static Bounds contentBounds(Detail detail, Bounds viewport) {
        int height = ItemDetailLayout.contentHeight(actionRows(detail),
            detail.split ? 0 : informationHeight(detail, viewport.width), detail.split, itemPreviewRows(detail));
        return new Bounds(viewport.x, viewport.y, viewport.width, height);
    }

    private static ItemDetailLayout.Sections cardSections(Detail detail, Bounds viewport) {
        ItemDetailLayout.Sections sections = ItemDetailLayout.sections(viewport.height, actionRows(detail),
            detail.split ? 0 : informationHeight(detail, viewport.width), detail.split, itemPreviewRows(detail));
        detail.previewYScroll = ItemDetailLayout.clampScroll(detail.previewYScroll,
            sections.previewContent(), sections.previewHeight());
        detail.actionScroll = ItemDetailLayout.clampScroll(detail.actionScroll,
            sections.actionsContent(), sections.actionsHeight());
        detail.infoScroll = ItemDetailLayout.clampScroll(detail.infoScroll,
            sections.infoContent(), sections.infoHeight());
        return sections;
    }

    private static int actionRows(Detail detail) {
        if (detail != null && (detail.sourceOnly || detail.inspectable)) return 1;
        return 3 + (canReplaceCarrier(detail) ? 1 : 0)
            + (canEquipWeapon(detail) ? 1 : 0) + (hasUseAction(detail) ? 1 : 0)
            + (canDiscard(detail) ? 1 : 0);
    }

    private static String replaceActionKey(Screen owner, Detail detail) {
        if (detail != null && detail.stack.getItem() instanceof DeltaPackItem pack
            && !isPlayerCarrierEquipped(pack.slotIdentifier())) {
            return "item_detail.xero_delta.equip";
        }
        return "item_detail.xero_delta.replace";
    }

    private static boolean isPlayerCarrierEquipped(String identifier) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || identifier == null || identifier.isBlank()) return false;
        try {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(minecraft.player)
                .flatMap(handler -> handler.getStacksHandler(identifier))
                .filter(handler -> handler.getSlots() > 0)
                .map(handler -> !handler.getStacks().getStackInSlot(0).isEmpty())
                .orElse(false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean canUse(Detail detail) {
        Minecraft minecraft = Minecraft.getInstance();
        if (detail == null || minecraft.player == null
            || !(detail.stack.getItem() instanceof TimedUseItem)) return false;
        if (detail.stack.getItem() instanceof RepairKitItem repairKit) {
            return repairKit.canUse(minecraft.player);
        }
        return MedicalUseRules.canUseWheelItem(minecraft.player, detail.stack);
    }

    private static boolean hasUseAction(Detail detail) {
        return detail != null && detail.stack.getItem() instanceof TimedUseItem;
    }

    private static boolean canEquipWeapon(Detail detail) {
        return detail != null && !detail.sourceId.isBlank()
            && com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules.isTaczGun(detail.stack)
            && !com.xtdpotato.xero_delta.data.KnifeSkinRules.locked(detail.stack);
    }

    private static boolean canReplaceCarrier(Detail detail) {
        if (detail == null || detail.sourceId.isBlank()
            || !(detail.stack.getItem() instanceof DeltaPackItem pack)
            || (!"chest_rig".equals(pack.slotIdentifier())
                && !"backpack".equals(pack.slotIdentifier()))) return false;
        return !detail.sourceId.startsWith(
            "curio|" + pack.slotIdentifier() + "|0|");
    }

    private static boolean canDiscard(Detail detail) {
        return detail != null && !detail.sourceId.isBlank()
            && !com.xtdpotato.xero_delta.data.KnifeSkinRules.locked(detail.stack);
    }

    private static int adaptiveContentWidth(Detail detail) {
        Minecraft minecraft = Minecraft.getInstance();
        int minimum = detail == null ? 210 : Math.max(210, itemPreviewWidth(detail) + 16);
        String title = detail == null
            ? Component.translatable("status_effect_hud.xero_delta.item_detail_preview").getString()
            : detail.stack.getHoverName().getString();
        minimum = Math.max(minimum, minecraft.font.width(title) + 48);
        if (detail != null && detail.sourceOnly) {
            return Math.max(minimum, singleActionMinimum(
                Component.translatable("item_detail.xero_delta.source")));
        }
        if (detail != null && detail.inspectable) {
            return Math.max(minimum, singleActionMinimum(
                Component.translatable("safety_box.xero_delta.inspect")));
        }
        Component returnLabel = Component.translatable(detail != null
            && (detail.isWarehouseSource() || detail.isContainerSource())
            ? "item_detail.xero_delta.carry" : "item_detail.xero_delta.return");
        minimum = Math.max(minimum, pairedActionMinimum(
            Component.translatable("item_detail.xero_delta.sell"), returnLabel));
        minimum = Math.max(minimum, pairedActionMinimum(
            Component.translatable("item_detail.xero_delta.split"),
            Component.translatable("item_detail.xero_delta.source")));
        minimum = Math.max(minimum, singleActionMinimum(
            Component.translatable("market.xero_delta.uses")));
        if (canReplaceCarrier(detail)) {
            minimum = Math.max(minimum, singleActionMinimum(
                Component.translatable("item_detail.xero_delta.replace")));
        }
        if (canEquipWeapon(detail)) {
            minimum = Math.max(minimum, singleActionMinimum(
                Component.translatable("item_detail.xero_delta.equip_weapon")));
        }
        if (hasUseAction(detail)) {
            minimum = Math.max(minimum, singleActionMinimum(
                Component.translatable("item_detail.xero_delta.use")));
        }
        if (canDiscard(detail)) {
            minimum = Math.max(minimum, singleActionMinimum(
                Component.translatable("item_detail.xero_delta.discard")));
        }
        return minimum;
    }

    private static int pairedActionMinimum(Component first, Component second) {
        int button = Math.max(actionButtonMinimum(first), actionButtonMinimum(second));
        return button * 2 + 17;
    }

    private static int singleActionMinimum(Component label) {
        return actionButtonMinimum(label) + 12;
    }

    private static int actionButtonMinimum(Component label) {
        return Math.max(56, Minecraft.getInstance().font.width(label) + 32);
    }

    private static int actionHeight(Bounds p) { return ItemDetailLayout.ACTION_HEIGHT; }
    private static int half(Bounds p) { return Math.max(12, (p.width - 17) / 2); }
    private static Bounds closeBounds(Bounds p) {
        return new Bounds(p.right() - 13, p.y + ItemDetailLayout.HEADER_PADDING,
            ItemDetailLayout.HEADER_TEXT_HEIGHT, ItemDetailLayout.HEADER_TEXT_HEIGHT);
    }
    private static Bounds dragBounds(Bounds p) {
        return new Bounds(p.x, p.y, p.width - 16, ItemDetailLayout.HEADER_HEIGHT);
    }
    private static Bounds favoriteIconBounds(Bounds p) {
        int header = ItemDetailLayout.HEADER_HEIGHT;
        return new Bounds(p.right() - 18, p.y + header + 5, 12, 12);
    }
    /** Larger than the icon so scaled detail cards remain reliably clickable. */
    private static Bounds favoriteHitBounds(Bounds p) {
        Bounds icon = favoriteIconBounds(p);
        return new Bounds(icon.x - 4, icon.y - 4, 20, 20);
    }
    private static int actionRowY(Detail detail, Bounds p, int row) {
        return actionBase(detail, p) + row * ItemDetailLayout.ACTION_ROW_SPAN;
    }
    private static int actionBase(Detail detail, Bounds p) {
        return p.y + ItemDetailLayout.HEADER_HEIGHT
            + ItemDetailLayout.previewSectionHeight(itemPreviewRows(detail));
    }
    private static Bounds sellBounds(Detail detail, Bounds p) {
        return new Bounds(p.x + 6, actionRowY(detail, p, 0), half(p), actionHeight(p));
    }
    private static Bounds returnBounds(Detail detail, Bounds p) {
        Bounds sell = sellBounds(detail, p);
        return new Bounds(sell.right() + 5, sell.y, half(p), actionHeight(p));
    }
    private static Bounds splitBounds(Detail detail, Bounds p) {
        return new Bounds(p.x + 6, actionRowY(detail, p, 1), half(p), actionHeight(p));
    }
    private static Bounds sourceBounds(Detail detail, Bounds p) {
        Bounds split = splitBounds(detail, p);
        return new Bounds(split.right() + 5, split.y, half(p), actionHeight(p));
    }
    private static Bounds usesBounds(Detail detail, Bounds p) {
        return new Bounds(p.x + 6, actionRowY(detail, p, 2),
            p.width - 12, actionHeight(p));
    }
    private static Bounds replaceBounds(Detail detail, Bounds p) {
        return new Bounds(p.x + 6, actionRowY(detail, p, 3),
            p.width - 12, actionHeight(p));
    }
    private static Bounds equipWeaponBounds(Detail detail, Bounds p) {
        int row = 3 + (canReplaceCarrier(detail) ? 1 : 0);
        return new Bounds(p.x + 6, actionRowY(detail, p, row),
            p.width - 12, actionHeight(p));
    }
    private static Bounds useBounds(Detail detail, Bounds p) {
        int row = 3 + (canReplaceCarrier(detail) ? 1 : 0)
            + (canEquipWeapon(detail) ? 1 : 0) + (canDiscard(detail) ? 1 : 0);
        return new Bounds(p.x + 6, actionRowY(detail, p, row),
            p.width - 12, actionHeight(p));
    }
    private static Bounds discardBounds(Detail detail, Bounds p) {
        int row = 3 + (canReplaceCarrier(detail) ? 1 : 0)
            + (canEquipWeapon(detail) ? 1 : 0);
        return new Bounds(p.x + 6, actionRowY(detail, p, row),
            p.width - 12, actionHeight(p));
    }
    private static Bounds sourceOnlyBounds(Detail detail, Bounds p) { return new Bounds(p.x + 6, actionBase(detail, p), p.width - 12, actionHeight(p)); }
    private static Bounds inspectBounds(Detail detail, Bounds p) { return sourceOnlyBounds(detail, p); }
    private static Bounds minusBounds(Bounds p) { return new Bounds(p.x + 6, p.bottom() - 36, 20, 14); }
    private static Bounds plusBounds(Bounds p) { return new Bounds(p.right() - 26, p.bottom() - 36, 20, 14); }
    private static Bounds splitSliderBounds(Bounds p) { return new Bounds(p.x + 31, p.bottom() - 35, Math.max(1, p.width - 62), 12); }
    private static Bounds cancelBounds(Bounds p) { return new Bounds(p.x + 6, p.bottom() - 17, half(p), 13); }
    private static Bounds confirmBounds(Bounds p) { return new Bounds(cancelBounds(p).right() + 5, p.bottom() - 17, half(p), 13); }

    private static Bounds saleBounds(Screen owner) {
        int width = Math.max(260, Math.min(520, owner.width - 30));
        int height = Math.max(170, Math.min(250, owner.height - 36));
        return new Bounds((owner.width - width) / 2, (owner.height - height) / 2, width, height);
    }

    private static Bounds saleClose(Bounds p) { return new Bounds(p.right() - 22, p.y + 7, 14, 14); }

    private static Bounds sourcePanelBounds(Screen owner, boolean uses) {
        int guiWidth = guiWidth();
        int guiHeight = guiHeight();
        int width = Math.min(Math.max(1, guiWidth - 16),
            Math.min(360, Math.max(260, guiWidth - 48)));
        int desiredHeight = uses ? 318 : 330;
        int height = Math.min(Math.max(1, guiHeight - 16),
            Math.min(desiredHeight, Math.max(176, guiHeight - 48)));
        return new Bounds((guiWidth - width) / 2,
            (guiHeight - height) / 2, width, height);
    }

    private static int guiWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static int guiHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private static Bounds sourceCloseBounds(Bounds panel) {
        return new Bounds(panel.right() - 22, panel.y + 7, 14, 14);
    }

    private static Bounds sourceRecipeBounds(Bounds panel) {
        return new Bounds(panel.x + 10, panel.y + 46,
            Math.max(1, panel.width - 20), 24);
    }

    private static Bounds sourceMarketBounds(Bounds panel) {
        Bounds recipe = sourceRecipeBounds(panel);
        return new Bounds(recipe.x, recipe.bottom() + 6, recipe.width, recipe.height);
    }

    private static int sourceRewardListTop(Bounds panel) {
        return sourceMarketBounds(panel).bottom() + 61;
    }

    private static int sourceUsesListTop(Bounds panel) {
        return sourceRecipeBounds(panel).bottom() + 12;
    }

    private static Bounds sourceListRow(Bounds panel, int top, int index) {
        return new Bounds(panel.x + 10, top + index * 28,
            Math.max(1, panel.width - 20), 24);
    }

    private static int sourceBodyTop(Bounds panel) {
        return panel.y + 41;
    }

    private static int sourceBodyBottom(Bounds panel) {
        return sourceFooterBounds(panel).y - 7;
    }

    private static Bounds sourceFooterBounds(Bounds panel) {
        return new Bounds(panel.x + 10, panel.bottom() - SOURCE_FOOTER_HEIGHT,
            Math.max(1, panel.width - 20), SOURCE_FOOTER_HEIGHT - 8);
    }

    private static int sourceContentBottom(Detail detail, Bounds panel) {
        if (detail.usesPanel) {
            int top = sourceUsesListTop(panel);
            int count = FtbQuestIntegration.findMatches(detail.stack).size();
            return count == 0 ? top + 24 : sourceListRow(panel, top, count - 1).bottom();
        }
        int metadataBottom = sourceMarketBounds(panel).bottom() + 36;
        int top = sourceRewardListTop(panel);
        int count = FtbQuestIntegration.findRewardMatches(detail.stack).size();
        int rowsBottom = count == 0 ? top + 24 : sourceListRow(panel, top, count - 1).bottom();
        return Math.max(metadataBottom, rowsBottom);
    }

    private static double sourceMaxScroll(Detail detail, Bounds panel) {
        return Math.max(0.0D, sourceContentBottom(detail, panel) + 8.0D
            - sourceBodyBottom(panel));
    }

    private static void tickSourceScroll(Detail detail, Bounds panel) {
        double maximum = sourceMaxScroll(detail, panel);
        detail.sourceTargetScroll = Math.max(0.0D,
            Math.min(maximum, detail.sourceTargetScroll));
        detail.sourceScroll = Math.max(0.0D, Math.min(maximum,
            detail.sourceScroll + (detail.sourceTargetScroll - detail.sourceScroll) * 0.28D));
        if (Math.abs(detail.sourceTargetScroll - detail.sourceScroll) < 0.05D) {
            detail.sourceScroll = detail.sourceTargetScroll;
        }
    }

    private static void renderSourceScrollbar(GuiGraphics graphics, Detail detail,
                                               Bounds panel, float alpha) {
        double maximum = sourceMaxScroll(detail, panel);
        if (maximum <= 0.0D) return;
        int top = sourceBodyTop(panel);
        int bottom = sourceBodyBottom(panel);
        int trackHeight = Math.max(1, bottom - top);
        int contentHeight = trackHeight + (int) Math.ceil(maximum);
        int thumbHeight = Math.max(18, trackHeight * trackHeight / Math.max(1, contentHeight));
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + (int) Math.round(travel * detail.sourceScroll / maximum);
        int x = panel.right() - 5;
        graphics.fill(x, top, x + 2, bottom, color(0x663C4A4E, alpha));
        graphics.fill(x - 1, thumbY, x + 3, thumbY + thumbHeight,
            color(0xFF70BFA7, alpha));
    }

    private static int sourceListRowAt(Bounds panel, double mouseX, double mouseY,
                                       int top, int count) {
        for (int index = 0; index < count; index++) {
            if (sourceListRow(panel, top, index).contains(mouseX, mouseY)) return index;
        }
        return -1;
    }
    private static Bounds recycleBounds(Bounds p) {
        int divider = p.x + Math.max(74, p.width * 42 / 100);
        int available = Math.max(1, p.right() - divider - 20);
        int width = actionButtonWidth(
            Component.translatable("item_detail.xero_delta.sell"), available);
        return new Bounds(p.right() - width - 10, p.y + 75, width, 22);
    }
    private static Bounds listBounds(Bounds p) {
        int divider = p.x + Math.max(74, p.width * 42 / 100);
        int available = Math.max(1, p.right() - divider - 20);
        int width = actionButtonWidth(
            Component.translatable("item_detail.xero_delta.list"), available);
        return new Bounds(p.right() - width - 10, p.y + 128, width, 22);
    }

    private static int actionButtonWidth(Component label, int available) {
        int desired = Minecraft.getInstance().font.width(label) + 32;
        return Math.max(1, Math.min(available, Math.max(56, desired)));
    }

    private static boolean favorite(ItemStack stack) {
        return TradingClientState.INSTANCE.favorites().contains(ModDataStorage.getKey(stack));
    }

    private static String resolveSourceId(ItemStack stack) {
        if (stack != null) for (var source : TradingClientState.INSTANCE.sources())
            if (ItemStack.isSameItemSameComponents(stack, source.stack())) return source.id();
        return "";
    }

    private static boolean nearMarket() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        BlockPos center = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-5, -5, -5),
            center.offset(5, 5, 5)))
            if (mc.level.getBlockState(pos).is(ModBlocks.TRADING_MARKET.get())) return true;
        return false;
    }

    private static boolean externalContainerContext(Screen owner) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(owner instanceof AbstractContainerScreen<?>) || minecraft.player == null) {
            return false;
        }
        return minecraft.player.containerMenu != minecraft.player.inventoryMenu;
    }

    private static int qualityColor(String q) {
        return switch (q == null ? "gray" : q) {
            case "green" -> 0xFF48B96B; case "blue" -> 0xFF438DD8;
            case "purple" -> 0xFF9A62D5; case "gold" -> 0xFFE2B94F;
            case "red" -> 0xFFD94D4D; default -> 0xFF7A8585;
        };
    }

    private static int color(int color, float alpha) {
        return color & 0x00FFFFFF | Math.max(0, Math.min(255,
            Math.round((color >>> 24) * alpha))) << 24;
    }

    private static long multiply(long value, int count) {
        if (value <= 0 || count <= 0) return 0;
        return value > TradingRules.MAX_CURRENCY / count
            ? TradingRules.MAX_CURRENCY : value * count;
    }

    private static ResourceLocation icon(String path) {
        return ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, path);
    }

    private static final class Detail {
        final ItemStack stack;
        final boolean inspectable;
        final String sourceId;
        final boolean sourceOnly;
        final boolean safetyBoxGridSource;
        Bounds anchor;
        boolean anchorVisible = true, moved, split, splitDragging, sale, pending, usePending;
        boolean detailDismissed;
        boolean sourcePanel, usesPanel;
        int splitAmount = 1, popupX = Integer.MIN_VALUE, popupY = Integer.MIN_VALUE;
        double sourceScroll, sourceTargetScroll;
        double previewYScroll, actionScroll, infoScroll, previewScroll;
        boolean previewDragging;
        final long opened = System.nanoTime();
        long closing, saleOpened, saleClosing, sourceOpened, sourceClosing;
        long requestRevision, toastAt, closeAfter;
        Component toast;
        Detail(ItemStack stack, boolean inspectable, String sourceId,
               boolean sourceOnly, Bounds anchor, boolean safetyBoxGridSource) {
            this.stack = stack; this.inspectable = inspectable;
            this.sourceId = sourceId; this.sourceOnly = sourceOnly; this.anchor = anchor;
            this.safetyBoxGridSource = safetyBoxGridSource;
        }

        boolean isWarehouseSource() {
            return sourceId.startsWith("warehouse|");
        }

        boolean isContainerSource() {
            return sourceId.startsWith("container|");
        }

        int containerSlot() {
            if (!isContainerSource()) return -1;
            try {
                return Integer.parseInt(sourceId.substring("container|".length()));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
    }

    private record Drag(int mouseX, int mouseY, int popupX, int popupY) {}
    private record Bounds(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double x, double y) {
            return x >= this.x && x < right() && y >= this.y && y < bottom();
        }
    }
}
