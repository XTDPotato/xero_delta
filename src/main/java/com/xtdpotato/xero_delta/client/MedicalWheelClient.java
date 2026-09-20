package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.compat.DeltaSpotWheelLayoutCompat;
import com.xtdpotato.xero_delta.data.MedicalShortcutRules;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.MedicalItem;
import com.xtdpotato.xero_delta.item.MedicalUseRules;
import com.xtdpotato.xero_delta.network.MedicalWheelUsePacket;
import com.xtdpotato.xero_delta.network.MedicalUseActionPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.trading.TradingInventorySources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import com.xtdpotato.xero_delta.data.ItemSize;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import com.xtdpotato.xero_delta.client.WheelLayout;

import java.util.ArrayList;
import java.util.List;

/** Client-only hold-to-open medical wheel that never replaces the gameplay screen. */
public final class MedicalWheelClient {
    private static final int MAX_ENTRIES = 8;
    private static final int ACCENT = 0xFF68D4AE;
    private static final int TEXT = 0xFFE8EEEC;
    private static final int MUTED = 0xFF96A5A2;
    private static boolean keyWasDown;
    private static boolean active;
    private static int holdTicks;
    private static int selected = -1;
    private static List<Entry> entries = List.of();
    private static float pointerX;
    private static float pointerY;

    private MedicalWheelClient() {
    }

    public static void tick(Minecraft minecraft, boolean keyDown) {
        if (minecraft.player == null || minecraft.screen != null || !minecraft.isWindowActive()
            || DownedClientState.INSTANCE.interactionLocked()) {
            reset(minecraft);
            return;
        }
        if (!keyDown) {
            if (active) {
                release();
            } else if (keyWasDown && !entries.isEmpty()) {
                ModNetwork.sendToServer(new MedicalUseActionPacket(
                    MedicalUseActionPacket.START_AUTO));
            }
            reset(minecraft);
            return;
        }
        if (!keyWasDown) {
            holdTicks = 0;
            selected = -1;
            entries = collect(minecraft.player);
            if (!entries.isEmpty()) {
                WheelMouseController.open(minecraft, WheelMouseController.Owner.MEDICAL);
            }
        }
        keyWasDown = true;
        WheelMouseController.keepHidden(minecraft);
        holdTicks++;
        if (!active && holdTicks >= MedicalShortcutRules.holdTicks(
                Config.INSTANCE.medicalWheelHoldSeconds.get()) && !entries.isEmpty()) {
            active = true;
        }
    }

    public static boolean active() {
        return active;
    }

    /** Returns true when the key should be consumed by the medical wheel. */
    public static boolean canOpen(Player player) {
        return player != null && !collect(player).isEmpty();
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (!active || minecraft.player == null || entries.isEmpty()) return;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int radius = wheelRadius(width, height);
        int centerX = wheelCenterX(radius, width);
        int centerY = wheelCenterY(radius, height);
        int innerRadius = Math.max(34, Math.round(radius * 0.43F));
        WheelMouseController.keepHidden(minecraft);
        WheelMouseController.confineToCircle(minecraft, centerX, centerY,
            Math.max(innerRadius + 2.0F, radius - 5.0F));
        updateSelectionFromLook(minecraft, radius);
        graphics.pose().pushPose();
        float animation = 1.0F;
        RadialWheelRenderer.draw(graphics, centerX, centerY, innerRadius, radius,
            entries.size(), selected, ACCENT, animation);

        for (int index = 0; index < entries.size(); index++) {
            double angle = -Math.PI / 2.0D
                + Math.PI * 2.0D * index / entries.size();
            double itemRadius = (innerRadius + radius) * 0.52D;
            int x = centerX + (int) Math.round(Math.cos(angle) * itemRadius);
            int y = centerY + (int) Math.round(Math.sin(angle) * itemRadius);
            boolean chosen = index == selected;
            ItemStack stack = entries.get(index).stack();
            renderSizedMedicalItem(graphics, minecraft, stack, x, y - 8);
            graphics.drawCenteredString(minecraft.font,
                minecraft.font.plainSubstrByWidth(stack.getHoverName().getString(), 72),
                x, y + 5, chosen ? TEXT : 0xFFC1CAC8);
            graphics.drawCenteredString(minecraft.font, itemStatus(stack),
                x, y + 16, chosen ? ACCENT : MUTED);
        }

        if (selected >= 0) {
            ItemStack stack = entries.get(selected).stack();
            graphics.drawCenteredString(minecraft.font,
                minecraft.font.plainSubstrByWidth(stack.getHoverName().getString(), 82),
                centerX, centerY - 8, TEXT);
            graphics.drawCenteredString(minecraft.font,
                MedicalUseRules.canUseWheelItem(minecraft.player, stack)
                    ? itemStatus(stack)
                    : Component.translatable("medical_wheel.xero_delta.unavailable").getString(),
                centerX, centerY + 8, selectedEnabled(minecraft) ? ACCENT : 0xFFE4877B);
        } else {
            graphics.drawCenteredString(minecraft.font,
                Component.translatable("medical_wheel.xero_delta.title"), centerX, centerY - 4, TEXT);
            graphics.drawCenteredString(minecraft.font,
                Component.translatable("medical_wheel.xero_delta.select"), centerX, centerY + 9, MUTED);
        }
        graphics.pose().popPose();
    }

    private static boolean selectedEnabled(Minecraft minecraft) {
        return selected >= 0 && selected < entries.size()
            && MedicalUseRules.canUseWheelItem(minecraft.player, entries.get(selected).stack());
    }

    private static void release() {
        if (selected < 0 || selected >= entries.size()) return;
        Entry entry = entries.get(selected);
        ModNetwork.sendToServer(new MedicalWheelUsePacket(
            entry.id().toString(), entry.sourceId()));
    }

    private static void updateSelectionFromLook(Minecraft minecraft, int radius) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int wheelRadius = radius;
        float centerX = wheelCenterX(wheelRadius, width);
        float centerY = wheelCenterY(wheelRadius, height);
        pointerX = WheelMouseController.guiX(minecraft) - centerX;
        pointerY = WheelMouseController.guiY(minecraft) - centerY;
        float distance = (float) Math.sqrt(pointerX * pointerX + pointerY * pointerY);
        if (distance < WheelLayout.deadZone(radius) || entries.isEmpty()) {
            selected = -1;
            return;
        }
        double angle = Math.atan2(pointerY, pointerX) + Math.PI / 2.0D;
        double sector = Math.PI * 2.0D / entries.size();
        double normalized = (angle + Math.PI * 2.0D) % (Math.PI * 2.0D);
        selected = (int) Math.floor((normalized + sector / 2.0D) / sector)
            % entries.size();
    }

    private static void renderSizedMedicalItem(GuiGraphics graphics, Minecraft minecraft,
                                               ItemStack stack, int centerX, int centerY) {
        ItemSize size = ClientDataCache.INSTANCE.getSize(stack);
        int cell = Math.max(7, Math.min(12, 32 / Math.max(size.width(), size.height())));
        int width = size.width() * cell;
        int height = size.height() * cell;
        int x = centerX - width / 2;
        int y = centerY - height / 2;
        QualityItemBackground.pushSuppress();
        try {
            GridItemRenderer.renderSizedItem(graphics, minecraft.font, stack, x, y, width, height,
                false, ClientDataCache.INSTANCE.shouldRotateTexture(stack),
                ClientDataCache.INSTANCE.shouldStretchTexture(stack),
                ClientDataCache.INSTANCE.proportionalTextureScale(stack));
        } finally {
            QualityItemBackground.popSuppress();
        }
    }

    private static int wheelRadius(int width, int height) {
        return WheelLayout.radius(width, height, DeltaSpotWheelLayoutCompat.scalePercent());
    }

    private static int wheelCenterX(int radius, int width) {
        return WheelLayout.center(width, radius, DeltaSpotWheelLayoutCompat.centerXPercent());
    }

    private static int wheelCenterY(int radius, int height) {
        return WheelLayout.center(height, radius, DeltaSpotWheelLayoutCompat.centerYPercent());
    }

    private static List<Entry> collect(Player player) {
        List<Entry> found = new ArrayList<>();
        collectCarrier(player, "chest_rig", found, true);
        for (int slot = 4; slot <= 8; slot++) {
            appendMedical(found, player.getInventory().getItem(slot), "player|" + slot, true);
        }
        return List.copyOf(found.subList(0, Math.min(MAX_ENTRIES, found.size())));
    }

    /** Inventory shortcuts use the same accessible sources as the wheel. */
    public static List<Entry> inventoryEntries(Player player) {
        List<Entry> found = new ArrayList<>();
        if (player == null) return List.of();
        collectCarrier(player, "chest_rig", found, false);
        for (int slot = 4; slot <= 8; slot++) {
            appendMedical(found, player.getInventory().getItem(slot), "player|" + slot, false);
        }
        return List.copyOf(found);
    }

    private static void collectCarrier(Player player, String identifier,
                                       List<Entry> found, boolean expandCount) {
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            var handler = curios.getStacksHandler(identifier).orElse(null);
            if (handler == null || handler.getSlots() <= 0
                || !curios.isSlotActive(identifier, 0)) return;
            ItemStack carrier = handler.getStacks().getStackInSlot(0);
            if (!(carrier.getItem() instanceof DeltaPackItem pack)
                || !identifier.equals(pack.slotIdentifier())) return;
            GridBackingStore store = new GridBackingStore(carrier,
                pack.gridWidth(), pack.gridHeight(), 0,
                stack -> GridBackingStore.isBlockedInEquippedStorage(identifier, stack));
            for (int index = 0; index < store.getSize(); index++) {
                ItemStack stack = store.getItemRaw(index % store.getWidth(),
                    index / store.getWidth());
                String sourceId = TradingInventorySources.sourceIdForCurio(
                    identifier, 0, index, stack);
                appendMedical(found, stack, sourceId, expandCount);
            }
        });
    }

    private static void appendMedical(List<Entry> found, ItemStack stack,
                                      String sourceId, boolean expandCount) {
        if (!(stack.getItem() instanceof MedicalItem)) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || sourceId == null || sourceId.isBlank()) return;
        if (!expandCount) {
            found.add(new Entry(id, stack.copy(), sourceId));
            return;
        }
        for (int count = 0; count < stack.getCount() && found.size() < MAX_ENTRIES; count++) {
            found.add(new Entry(id, stack.copyWithCount(1), sourceId));
        }
    }

    private static String itemStatus(ItemStack stack) {
        if (!stack.isDamageableItem()) return "x" + stack.getCount();
        int maximum = Math.max(1, stack.getMaxDamage());
        return Math.max(0, maximum - stack.getDamageValue()) + "/" + maximum;
    }

    private static void reset(Minecraft minecraft) {
        WheelMouseController.close(minecraft, WheelMouseController.Owner.MEDICAL);
        keyWasDown = false;
        active = false;
        holdTicks = 0;
        selected = -1;
        entries = List.of();
        pointerX = 0.0F;
        pointerY = 0.0F;
    }

    public record Entry(ResourceLocation id, ItemStack stack, String sourceId) {
    }
}

