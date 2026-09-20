package com.xtdpotato.xero_delta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.xtdpotato.xero_delta.screen.CommandWheelValueDialog;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hold-to-open command launcher rendered above gameplay without a Screen. */
public final class CommandWheelClient {
    private static final long OPEN_NANOS = 160_000_000L;
    private static final long CLOSE_NANOS = 140_000_000L;
    private static final int PAGE_SIZE = 8;
    private static boolean keyWasDown;
    private static boolean active;
    private static boolean closing;
    private static int holdTicks;
    private static int selected = -1;
    private static int page;
    private static long animationStartNanos;
    private static long closingStartNanos;
    private static float closingOpacity;
    private static List<CommandWheelCatalog.Entry> entries = List.of();
    private static final Map<String, Map<String, String>> toggleValues = new HashMap<>();

    private CommandWheelClient() {
    }

    public static void tick(Minecraft minecraft, boolean keyDown) {
        if (minecraft.player == null || minecraft.screen != null || !minecraft.isWindowActive()
            || !minecraft.player.hasPermissions(2)) {
            reset(minecraft);
            return;
        }
        if (closing) {
            if (keyDown) {
                closing = false;
                active = false;
                holdTicks = 0;
                beginEntries(minecraft);
                WheelMouseController.open(minecraft, WheelMouseController.Owner.COMMAND);
            } else if (System.nanoTime() - closingStartNanos >= CLOSE_NANOS) {
                reset(minecraft);
            }
            return;
        }
        if (!keyDown) {
            if (active) {
                boolean openedParameterDialog = activateSelected(minecraft);
                active = false;
                keyWasDown = false;
                WheelMouseController.close(minecraft, WheelMouseController.Owner.COMMAND);
                if (openedParameterDialog) {
                    closing = false;
                    holdTicks = 0;
                    selected = -1;
                } else {
                    closing = true;
                    closingStartNanos = System.nanoTime();
                    closingOpacity = opacity(closingStartNanos);
                }
            } else {
                reset(minecraft);
            }
            return;
        }
        if (!keyWasDown) {
            beginEntries(minecraft);
            WheelMouseController.open(minecraft, WheelMouseController.Owner.COMMAND);
        }
        keyWasDown = true;
        WheelMouseController.keepHidden(minecraft);
        holdTicks++;
        if (!active && holdTicks >= 4 && !entries.isEmpty()) {
            active = true;
            animationStartNanos = System.nanoTime();
        }
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if ((!active && !closing) || entries.isEmpty() || minecraft.player == null) return;
        WheelMouseController.keepHidden(minecraft);
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int centerY = Math.max(72, height / 2);
        int radius = Math.min(146, Math.max(84, Math.min(width, height) / 3));
        int inner = Math.max(34, Math.round(radius * 0.42F));
        long now = System.nanoTime();
        float anim = active
            ? easeOut((now - animationStartNanos) / (float) OPEN_NANOS)
            : 1.0F - easeIn((now - closingStartNanos) / (float) CLOSE_NANOS);
        if (anim < 0.01F) return;
        List<CommandWheelCatalog.Entry> pageEntries = pageEntries();
        if (active) {
            WheelMouseController.confineToCircle(minecraft, centerX, centerY,
                Math.max(inner + 2.0F, radius - 5.0F));
            selected = selection(minecraft, centerX, centerY, pageEntries.size());
        }
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(0.92F + anim * 0.08F, 0.92F + anim * 0.08F, 1);
        graphics.pose().translate(-centerX, -centerY, 0);
        int accent = selected >= 0 ? 0xFF68D4AE : 0xFF80908D;
        RadialWheelRenderer.draw(graphics, centerX, centerY, inner, radius,
            pageEntries.size(), selected, accent, anim);
        for (int i = 0; i < pageEntries.size(); i++) {
            double angle = -Math.PI / 2 + Math.PI * 2 * i / pageEntries.size();
            int x = centerX + (int) Math.round(Math.cos(angle) * (inner + radius) * 0.52);
            int y = centerY + (int) Math.round(Math.sin(angle) * (inner + radius) * 0.52);
            int color = fade(i == selected ? 0xFFFFFFFF : 0xFFC1CAC8, anim);
            CommandWheelCatalog.Entry entry = pageEntries.get(i);
            drawCommandIcon(graphics, entry, x, y - 19, i == selected ? 0xFFB8F5DC : 0xFF9EAAA7, anim);
            Component title = entry.id().startsWith("tree:")
                ? Component.literal(entry.command())
                : Component.translatable(entry.titleKey());
            graphics.drawCenteredString(minecraft.font,
                minecraft.font.plainSubstrByWidth(title.getString(), 86), x, y + 1, color);
        }
        CommandWheelCatalog.Entry selectedEntry = selected >= 0 && selected < pageEntries.size()
            ? pageEntries.get(selected) : null;
        String center = selectedEntry == null
            ? Component.translatable("command_wheel.xero_delta.title").getString()
            : (selectedEntry.id().startsWith("tree:") ? selectedEntry.command()
                : Component.translatable(selectedEntry.titleKey()).getString());
        graphics.drawCenteredString(minecraft.font,
            minecraft.font.plainSubstrByWidth(center, 150), centerX, centerY - 13,
            fade(0xFFE8EEEC, anim));
        String action = selectedEntry == null
            ? Component.translatable("command_wheel.xero_delta.wheel_page_hint").getString()
            : actionHint(selectedEntry);
        graphics.drawCenteredString(minecraft.font,
            minecraft.font.plainSubstrByWidth(action, 170), centerX, centerY + 1,
            fade(0xFFB0BFBC, anim));
        graphics.drawCenteredString(minecraft.font,
            Component.literal((page + 1) + "/" + pageCount() + "  ·  "
                + Component.translatable("command_wheel.xero_delta.wheel_page_hint").getString()),
            centerX, centerY + 14, fade(0xFF96A5A2, anim));
        graphics.pose().popPose();
    }

    private static void beginEntries(Minecraft minecraft) {
        var root = minecraft.getConnection() == null ? null
            : minecraft.getConnection().getCommands().getRoot().getChild("xero");
        entries = CommandWheelCatalog.filter(
            root == null ? CommandWheelCatalog.entries() : CommandWheelCatalog.entries(root),
            CommandWheelCatalog.Category.ALL, "").stream()
            .filter(CommandWheelClient::hasUsableDefaults).toList();
        page = Math.max(0, Math.min(page, pageCount() - 1));
        selected = -1;
    }

    private static boolean hasUsableDefaults(CommandWheelCatalog.Entry entry) {
        return entry.parameters().stream().noneMatch(parameter ->
            parameter.required() && parameter.defaultValue().trim().isBlank());
    }

    /**
     * Executes no-argument and toggle-only presets immediately. Commands with
     * editable values deliberately hand off to a focused dialog after key release.
     */
    private static boolean activateSelected(Minecraft minecraft) {
        List<CommandWheelCatalog.Entry> pageEntries = pageEntries();
        if (selected < 0 || selected >= pageEntries.size() || minecraft.getConnection() == null) return false;
        CommandWheelCatalog.Entry entry = pageEntries.get(selected);
        if (requiresValueDialog(entry)) {
            minecraft.setScreen(new CommandWheelValueDialog(entry));
            return true;
        }
        Map<String, String> values = currentToggleValues(entry);
        String command = entry.buildCommand(values);
        if (!command.isBlank()) {
            if (command.startsWith("/")) command = command.substring(1);
            minecraft.player.connection.sendCommand(command);
            advanceToggleValues(entry, values);
        }
        return false;
    }

    /** Called from the global mouse-scroll hook while this HUD wheel owns pointer input. */
    public static boolean mouseScrolled(Minecraft minecraft, double delta) {
        if (!active || entries.isEmpty() || delta == 0.0D || pageCount() <= 1) return false;
        page = Math.floorMod(page + (delta < 0.0D ? 1 : -1), pageCount());
        selected = -1;
        WheelMouseController.keepHidden(minecraft);
        return true;
    }

    private static boolean requiresValueDialog(CommandWheelCatalog.Entry entry) {
        return entry.parameters().stream().anyMatch(parameter ->
            parameter.kind() != CommandWheelCatalog.ParameterKind.TOGGLE);
    }

    private static Map<String, String> currentToggleValues(CommandWheelCatalog.Entry entry) {
        Map<String, String> stored = toggleValues.computeIfAbsent(entry.id(), ignored -> new HashMap<>());
        Map<String, String> values = new HashMap<>();
        for (CommandWheelCatalog.Parameter parameter : entry.parameters()) {
            String value = stored.computeIfAbsent(parameter.id(), ignored -> parameter.defaultValue());
            values.put(parameter.id(), value);
        }
        return values;
    }

    private static void advanceToggleValues(CommandWheelCatalog.Entry entry, Map<String, String> values) {
        Map<String, String> stored = toggleValues.computeIfAbsent(entry.id(), ignored -> new HashMap<>());
        for (CommandWheelCatalog.Parameter parameter : entry.parameters()) {
            boolean enabled = Boolean.parseBoolean(values.getOrDefault(parameter.id(), parameter.defaultValue()));
            stored.put(parameter.id(), Boolean.toString(!enabled));
        }
    }

    private static String actionHint(CommandWheelCatalog.Entry entry) {
        if (entry.parameters().isEmpty()) {
            return Component.translatable("command_wheel.xero_delta.release").getString();
        }
        if (requiresValueDialog(entry)) {
            return Component.translatable("command_wheel.xero_delta.configure").getString();
        }
        CommandWheelCatalog.Parameter parameter = entry.parameters().getFirst();
        String value = currentToggleValues(entry).getOrDefault(parameter.id(), parameter.defaultValue());
        Component label = net.minecraft.client.resources.language.I18n.exists(parameter.labelKey())
            ? Component.translatable(parameter.labelKey()) : Component.literal(parameter.id());
        return Component.translatable(Boolean.parseBoolean(value)
                ? "command_wheel.xero_delta.toggle_enable"
                : "command_wheel.xero_delta.toggle_disable",
            label).getString();
    }

    private static void drawCommandIcon(GuiGraphics graphics, CommandWheelCatalog.Entry entry,
                                        int centerX, int centerY, int color, float opacity) {
        int tint = fade(color, opacity);
        int left = centerX - 7;
        int top = centerY - 7;
        switch (entry.category()) {
            case GUI -> {
                graphics.renderOutline(left, top, 15, 12, tint);
                graphics.fill(left + 2, top + 3, left + 13, top + 5, tint);
                graphics.fill(left + 5, top + 14, left + 10, top + 16, tint);
            }
            case PLAYER -> {
                graphics.fill(left + 5, top, left + 10, top + 5, tint);
                graphics.fill(left + 3, top + 6, left + 12, top + 14, tint);
                graphics.fill(left + 1, top + 14, left + 14, top + 16, tint);
            }
            case ITEM -> {
                graphics.fill(left + 5, top, left + 10, top + 3, tint);
                graphics.fill(left + 2, top + 3, left + 13, top + 13, tint);
                graphics.fill(left + 5, top + 13, left + 10, top + 16, tint);
            }
            case TRADING -> {
                graphics.fill(left + 2, top + 2, left + 13, top + 14, tint);
                graphics.fill(left + 4, top, left + 11, top + 16, tint);
                graphics.drawCenteredString(Minecraft.getInstance().font, "$", centerX, top + 4, 0xFF0B1110);
            }
            case SYSTEM -> {
                graphics.fill(left + 5, top, left + 10, top + 16, tint);
                graphics.fill(left, top + 5, left + 15, top + 10, tint);
                graphics.fill(left + 3, top + 3, left + 12, top + 12, tint);
                graphics.fill(left + 6, top + 6, left + 9, top + 9, 0xFF101719);
            }
            case MAIL -> {
                graphics.renderOutline(left, top + 2, 15, 11, tint);
                graphics.fill(left + 2, top + 4, left + 7, top + 7, tint);
                graphics.fill(left + 8, top + 4, left + 13, top + 7, tint);
            }
            case ALL -> graphics.fill(left + 4, top + 4, left + 11, top + 11, tint);
        }
    }

    private static List<CommandWheelCatalog.Entry> pageEntries() {
        int start = page * PAGE_SIZE;
        return start >= entries.size() ? List.of() : entries.subList(start, Math.min(entries.size(), start + PAGE_SIZE));
    }

    private static int pageCount() { return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE); }

    private static int selection(Minecraft minecraft, int cx, int cy, int count) {
        if (count == 0) return -1;
        float dx = WheelMouseController.guiX(minecraft) - cx;
        float dy = WheelMouseController.guiY(minecraft) - cy;
        if (Math.hypot(dx, dy) < 22) return -1;
        double sector = Math.PI * 2 / count;
        double normalized = (Math.atan2(dy, dx) + Math.PI / 2 + Math.PI * 2) % (Math.PI * 2);
        return (int) Math.floor((normalized + sector / 2) / sector) % count;
    }

    private static void reset(Minecraft minecraft) {
        WheelMouseController.close(minecraft, WheelMouseController.Owner.COMMAND);
        keyWasDown = false; active = false; closing = false; holdTicks = 0; selected = -1;
    }

    private static float opacity(long now) {
        float p = Math.max(0, Math.min(1, (now - (closing ? closingStartNanos : animationStartNanos))
            / (float) (closing ? CLOSE_NANOS : OPEN_NANOS)));
        return closing ? closingOpacity * (1 - p * p * p) : 1 - (float) Math.pow(1 - p, 3);
    }
    private static float easeOut(float v) { v = Math.max(0, Math.min(1, v)); return 1 - (float) Math.pow(1 - v, 3); }
    private static float easeIn(float v) { v = Math.max(0, Math.min(1, v)); return v * v * v; }
    private static int fade(int color, float opacity) { return Math.max(4, Math.round((color >>> 24) * opacity)) << 24 | color & 0xFFFFFF; }
}
