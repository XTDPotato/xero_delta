package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.CommandWheelCatalog;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import com.xtdpotato.xero_delta.client.XeroDeltaClient;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paged command launcher with structured parameters and an editable full command.
 */
public final class CommandWheelScreen extends Screen {
    private static final int HEADER = 44;
    private static final int MARGIN = 18;
    private static final int ENTRY_W = 104;
    private static final int ENTRY_H = 38;
    private static final int CATEGORY_W = 46;
    private static final int CATEGORY_H = 22;
    private static final int WHEEL_PAGE_SIZE = 8;

    private final Screen parent;
    private final ScreenTransition transition = new ScreenTransition();
    private final Map<String, String> parameterValues = new HashMap<>();
    private final Map<String, EditBox> parameterBoxes = new HashMap<>();
    private EditBox commandBox;
    private EditBox searchBox;
    private String searchQuery = "";
    private String manualCommand = "";
    private CommandWheelCatalog.Category category = CommandWheelCatalog.Category.ALL;
    private List<CommandWheelCatalog.Entry> visible = List.of();
    private int selected;
    private int page;
    private int parameterOffset;
    private String openDropdown = "";
    private boolean commandEdited;
    private boolean updatingGeneratedCommand;

    public CommandWheelScreen(Screen parent) {
        super(Component.translatable("command_wheel.xero_delta.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        hideNativeCursor();
        searchBox = new Material3CompactEditBox(font, width / 2, 12,
            width / 2 - MARGIN, 20, Component.translatable("command_wheel.xero_delta.search"));
        searchBox.setMaxLength(128);
        searchBox.setValue(searchQuery);
        searchBox.setHint(Component.translatable("command_wheel.xero_delta.search"));
        searchBox.setResponder(value -> {
            searchQuery = value;
            page = 0;
            rebuildWheel(false);
            setFocused(searchBox);
            searchBox.setFocused(true);
        });
        if (visible.isEmpty()) rebuildWheel(false);
        else {
            page = Math.max(0, selected) / pageSize();
            parameterOffset = 0;
            openDropdown = "";
            rebuildInputBoxes();
        }
    }

    private void rebuildWheel(boolean preserveSelection) {
        String selectedId = preserveSelection && selectedEntry() != null
            ? selectedEntry().id() : "";
        var root = minecraft == null || minecraft.getConnection() == null
            ? null : minecraft.getConnection().getCommands().getRoot().getChild("xero");
        visible = CommandWheelCatalog.filter(
            root == null ? CommandWheelCatalog.entries() : CommandWheelCatalog.entries(root),
            category, "").stream().filter(entry -> matchesSearch(entry)).toList();
        selected = -1;
        for (int index = 0; index < visible.size(); index++) {
            if (visible.get(index).id().equals(selectedId)) {
                selected = index;
                page = index / pageSize();
                break;
            }
        }
        page = Math.max(0, Math.min(page, pageCount() - 1));
        if (selected < 0 && !visible.isEmpty()) selected = pageStart();
        if (selected >= 0) selectEntry(selected);
        else rebuildInputBoxes();
    }

    private void selectEntry(int index) {
        if (index < 0 || index >= visible.size()) return;
        selected = index;
        page = index / pageSize();
        parameterOffset = 0;
        openDropdown = "";
        parameterValues.clear();
        for (CommandWheelCatalog.Parameter parameter : selectedEntry().parameters()) {
            parameterValues.put(parameter.id(), parameter.defaultValue());
        }
        commandEdited = false;
        manualCommand = selectedEntry().previewCommand(parameterValues);
        rebuildInputBoxes();
    }

    private void rebuildInputBoxes() {
        clearWidgets();
        addRenderableWidget(searchBox);
        parameterBoxes.clear();
        commandBox = null;
        CommandWheelCatalog.Entry entry = selectedEntry();
        if (entry == null) return;
        int controlX = detailX() + 18;
        int controlW = Math.max(20, detailWidth() - 36);
        for (int index = parameterOffset; index < parameterEnd(); index++) {
            CommandWheelCatalog.Parameter parameter = entry.parameters().get(index);
            if (parameter.kind() != CommandWheelCatalog.ParameterKind.TEXT
                && parameter.kind() != CommandWheelCatalog.ParameterKind.NUMBER) continue;
            EditBox box = new Material3CompactEditBox(font, controlX, parameterY(index) + 13,
                controlW, 20, parameterLabel(parameter));
            box.setMaxLength(256);
            box.setValue(parameterValues.getOrDefault(
                parameter.id(), parameter.defaultValue()));
            if (parameter.kind() == CommandWheelCatalog.ParameterKind.NUMBER) {
                box.setFilter(value -> value.matches("-?\\d*(\\.\\d*)?"));
            }
            box.setHint(parameterLabel(parameter));
            box.setResponder(value -> {
                parameterValues.put(parameter.id(), value);
                refreshGeneratedCommand();
            });
            parameterBoxes.put(parameter.id(), addRenderableWidget(box));
        }
        int commandY = height - 52;
        commandBox = new Material3CompactEditBox(font, 10, commandY,
            Math.max(110, width - 20), 20,
            Component.translatable("command_wheel.xero_delta.command"));
        commandBox.setMaxLength(32767);
        commandBox.setValue(manualCommand);
        commandBox.setResponder(value -> {
            manualCommand = value;
            if (!updatingGeneratedCommand) commandEdited = true;
        });
        addRenderableWidget(commandBox);
    }

    private void refreshGeneratedCommand() {
        if (commandEdited || selectedEntry() == null) return;
        manualCommand = selectedEntry().previewCommand(parameterValues);
        if (commandBox == null) return;
        updatingGeneratedCommand = true;
        commandBox.setValue(manualCommand);
        updatingGeneratedCommand = false;
    }

    private void restoreGeneratedCommand() {
        commandEdited = false;
        refreshGeneratedCommand();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF5080D0F);
        transition.push(graphics);
        graphics.fill(0, 0, width, HEADER, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), width / 2 - MARGIN - 8),
            MARGIN, 16, 0xFFF2F5F4, false);
        drawWheel(graphics, mouseX, mouseY);
        drawDetails(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawOpenDropdown(graphics, mouseX, mouseY);
        if (selectedEntry() != null && inside(mouseX, mouseY,
            detailX() + detailWidth() - 108, height - 27, 22, 22)) {
            graphics.renderTooltip(font, Component.translatable(
                "command_wheel.xero_delta.restore"), mouseX, mouseY);
        }
        transition.pop(graphics);
        transition.drawFade(graphics, width, height);
    }

    private void drawWheel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelRight = wheelPanelRight();
        graphics.fill(MARGIN, HEADER + 8, panelRight, height - 72, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE);
        int centerX = wheelCenterX();
        int centerY = wheelCenterY();
        int categoryRadius = Math.max(48, Math.min(66, height / 7));
        int entryRadius = Math.max(112, Math.min(158,
            Math.min(panelRight - MARGIN, height - HEADER) / 3));

        CommandWheelCatalog.Category[] categories = CommandWheelCatalog.Category.values();
        for (int index = 0; index < categories.length; index++) {
            Bounds bounds = categoryBounds(index, categoryRadius);
            int x = bounds.x;
            int y = bounds.y;
            boolean active = category == categories[index];
            boolean hover = bounds.contains(mouseX, mouseY);
            graphics.fill(x, y, bounds.right(), bounds.bottom(),
                active ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : hover ? com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_HIGH : com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
            Component label = Component.translatable("command_wheel.xero_delta.category."
                + categories[index].name().toLowerCase(Locale.ROOT));
            String compact = font.width(label) <= bounds.width - 6
                ? label.getString() : label.getString().substring(0, 1);
            graphics.drawCenteredString(font, compact, x + bounds.width / 2,
                y + 7, active ? 0xFF07110E : 0xFFD4DDDC);
        }

        List<CommandWheelCatalog.Entry> entries = pageEntries();
        for (int index = 0; index < entries.size(); index++) {
            Bounds bounds = entryBounds(index, entryRadius);
            boolean hover = bounds.contains(mouseX, mouseY);
            boolean active = pageStart() + index == selected;
            graphics.fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(),
                active ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY_CONTAINER : hover ? com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_HIGH : com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
            graphics.renderOutline(bounds.x, bounds.y, bounds.width, bounds.height,
                active ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : 0xFF354346);
            int iconX = bounds.x + 7;
            int iconY = bounds.y + (bounds.height - 18) / 2;
            graphics.fill(iconX, iconY, iconX + 24, iconY + 18,
                active ? 0xFF4DBA94 : 0xFF263336);
            graphics.drawCenteredString(font, symbol(entries.get(index)),
                iconX + 12, iconY + 5, 0xFFFFFFFF);
            Component title = entryTitle(entries.get(index));
            String clipped = font.plainSubstrByWidth(title.getString(), bounds.width - 39);
            graphics.drawString(font, clipped, bounds.x + 36, bounds.y + (bounds.height - 8) / 2,
                0xFFE4EAE9, false);
        }

        if (!compactLayout()) {
            graphics.fill(centerX - 34, centerY - 18, centerX + 34, centerY + 18,
                0xFF111A1C);
            graphics.renderOutline(centerX - 34, centerY - 18, 68, 36, Material3Theme.PRIMARY);
            graphics.drawCenteredString(font,
                Component.translatable("command_wheel.xero_delta.category."
                    + category.name().toLowerCase(Locale.ROOT)),
                centerX, centerY - 5, 0xFFF2F5F4);
        }
        graphics.drawCenteredString(font, (page + 1) + "/" + pageCount(),
            centerX, compactLayout() ? pageButtonY() + 5 : centerY + 7, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
        drawPageButton(graphics, mouseX, mouseY, previousPageX(), pageButtonY(), "<",
            page > 0);
        drawPageButton(graphics, mouseX, mouseY, nextPageX(), pageButtonY(), ">",
            page < pageCount() - 1);
    }

    private void drawPageButton(GuiGraphics graphics, int mouseX, int mouseY,
                                int x, int y, String label, boolean enabled) {
        boolean hover = enabled && inside(mouseX, mouseY, x, y, 22, 18);
        graphics.fill(x, y, x + 22, y + 18, !enabled ? 0xFF263033
            : hover ? com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_HIGH
            : com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        graphics.renderOutline(x, y, 22, 18, enabled
            ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : 0xFF354346);
        graphics.drawCenteredString(font, label, x + 11, y + 5,
            enabled ? 0xFFE4EAE9 : com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
    }

    private void drawDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = detailX();
        int y = HEADER + 8;
        int w = detailWidth();
        graphics.fill(x, y, x + w, height - 72, com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE);
        CommandWheelCatalog.Entry entry = selectedEntry();
        if (entry == null) {
            graphics.drawCenteredString(font,
                Component.translatable("command_wheel.xero_delta.empty"),
                x + w / 2, y + 34, com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED);
            return;
        }
        graphics.fill(x, y, x + 4, height - 72, com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
        graphics.drawString(font, font.plainSubstrByWidth(entryTitle(entry).getString(), w - 36),
            x + 18, y + 18, 0xFFF2F5F4, false);
        int textY = y + 42;
        Component description = entry.id().startsWith("tree:") ? Component.empty()
            : Component.translatable(entry.descriptionKey());
        for (var line : font.split(description, w - 36)) {
            if (height < 260) break;
            if (textY + font.lineHeight > HEADER + 74) break;
            graphics.drawString(font, line, x + 18, textY, 0xFFACB7B8, false);
            textY += font.lineHeight + 3;
        }

        if (entry.parameters().size() > parameterCapacity()) {
            drawPageButton(graphics, mouseX, mouseY, x + 18, parameterPageY(), "<", parameterOffset > 0);
            drawPageButton(graphics, mouseX, mouseY, x + w - 40, parameterPageY(), ">",
                parameterEnd() < entry.parameters().size());
            graphics.drawCenteredString(font, (parameterOffset / parameterCapacity() + 1) + "/"
                + ((entry.parameters().size() + parameterCapacity() - 1) / parameterCapacity()),
                x + w / 2, parameterPageY() + 5, Material3Theme.TEXT_MUTED);
        }
        for (int index = parameterOffset; index < parameterEnd(); index++) {
            CommandWheelCatalog.Parameter parameter = entry.parameters().get(index);
            int py = parameterY(index);
            graphics.drawString(font, font.plainSubstrByWidth(parameterLabel(parameter).getString(),
                parameter.kind() == CommandWheelCatalog.ParameterKind.TOGGLE ? w - 98 : w - 36),
                x + 18, py, 0xFFC4CFCE, false);
            if (parameter.kind() == CommandWheelCatalog.ParameterKind.TOGGLE) {
                drawToggle(graphics, mouseX, mouseY, parameter, x + w - 72, py - 3);
            } else if (parameter.kind() == CommandWheelCatalog.ParameterKind.DROPDOWN) {
                drawDropdown(graphics, mouseX, mouseY, parameter,
                    x + 18, py + 13, w - 36);
            }
        }

        boolean executable = canExecute();
        int previewY = height - 27;
        graphics.drawString(font, Component.translatable(
            "command_wheel.xero_delta.command"), 10, height - 67,
            com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED, false);
        int buttonX = x + w - 80;
        drawPageButton(graphics, mouseX, mouseY, buttonX - 28, previewY, "↶", true);
        boolean hover = inside(mouseX, mouseY, buttonX, previewY, 80, 22);
        graphics.fill(buttonX, previewY, buttonX + 80, previewY + 22,
            hover && executable ? 0xFF79E5BD : !executable ? 0xFF344043 : com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY);
        graphics.drawCenteredString(font,
            Component.translatable("command_wheel.xero_delta.execute"),
            buttonX + 40, previewY + 7, !executable ? com.xtdpotato.xero_delta.screen.material.Material3Theme.TEXT_MUTED : 0xFF07110E);
    }

    private void drawToggle(GuiGraphics graphics, int mouseX, int mouseY,
                            CommandWheelCatalog.Parameter parameter, int x, int y) {
        boolean enabled = Boolean.parseBoolean(parameterValues.getOrDefault(
            parameter.id(), parameter.defaultValue()));
        boolean hover = inside(mouseX, mouseY, x, y, 54, 18);
        graphics.fill(x, y, x + 54, y + 18,
            enabled ? (hover ? 0xFF79E5BD : com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY) : hover ? 0xFF3A4B4E : 0xFF283437);
        int knobX = enabled ? x + 38 : x + 2;
        graphics.fill(knobX, y + 2, knobX + 14, y + 16, 0xFFF2F5F4);
        graphics.drawString(font, enabled ? "ON" : "OFF",
            enabled ? x + 7 : x + 21, y + 5,
            enabled ? 0xFF07110E : 0xFFC2CCCB, false);
    }

    private void drawDropdown(GuiGraphics graphics, int mouseX, int mouseY,
                              CommandWheelCatalog.Parameter parameter, int x, int y, int width) {
        boolean open = parameter.id().equals(openDropdown);
        boolean hover = inside(mouseX, mouseY, x, y, width, 20);
        graphics.fill(x, y, x + width, y + 20, hover || open ? com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_HIGH : com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER);
        graphics.renderOutline(x, y, width, 20, open ? com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY : 0xFF405053);
        graphics.drawString(font, font.plainSubstrByWidth(parameterValues.getOrDefault(
            parameter.id(), parameter.defaultValue()), width - 26), x + 7, y + 6,
            0xFFE4EAE9, false);
        graphics.drawString(font, open ? "▲" : "▼", x + width - 14, y + 6,
            com.xtdpotato.xero_delta.screen.material.Material3Theme.PRIMARY, false);
    }

    private void drawOpenDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        if (openDropdown.isBlank()) return;
        CommandWheelCatalog.Parameter parameter = parameter(openDropdown);
        if (parameter == null) return;
        int index = selectedEntry().parameters().indexOf(parameter);
        int x = detailX() + 18;
        int y = dropdownY(index, parameter);
        int width = detailWidth() - 36;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 990.0F);
        for (int option = 0; option < parameter.options().size(); option++) {
            int oy = y + option * 20;
            boolean hover = inside(mouseX, mouseY, x, oy, width, 20);
            graphics.fill(x, oy, x + width, oy + 20, hover ? com.xtdpotato.xero_delta.screen.material.Material3Theme.SURFACE_CONTAINER_HIGH : 0xFF11191B);
            graphics.renderOutline(x, oy, width, 20, 0xFF405053);
            graphics.drawString(font, font.plainSubstrByWidth(parameter.options().get(option), width - 14),
                x + 7, oy + 6, 0xFFE4EAE9, false);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (!openDropdown.isBlank()) {
            CommandWheelCatalog.Parameter parameter = parameter(openDropdown);
            if (parameter != null) {
                int index = selectedEntry().parameters().indexOf(parameter);
                int x = detailX() + 18;
                int y = dropdownY(index, parameter);
                int option = (int) ((mouseY - y) / 20);
                if (inside(mouseX, mouseY, x, y, detailWidth() - 36,
                    parameter.options().size() * 20)
                    && option >= 0 && option < parameter.options().size()) {
                    parameterValues.put(parameter.id(), parameter.options().get(option));
                    refreshGeneratedCommand();
                    openDropdown = "";
                    return true;
                }
            }
            openDropdown = "";
        }

        if (inside(mouseX, mouseY, previousPageX(), pageButtonY(), 22, 18) && page > 0) {
            selectEntry((page - 1) * pageSize());
            return true;
        }
        if (inside(mouseX, mouseY, nextPageX(), pageButtonY(), 22, 18) && page < pageCount() - 1) {
            selectEntry((page + 1) * pageSize());
            return true;
        }
        int categoryRadius = Math.max(48, Math.min(66, height / 7));
        CommandWheelCatalog.Category[] categories = CommandWheelCatalog.Category.values();
        for (int index = 0; index < categories.length; index++) {
            if (categoryBounds(index, categoryRadius).contains(mouseX, mouseY)) {
                category = categories[index];
                page = 0;
                rebuildWheel(false);
                return true;
            }
        }

        int entryRadius = Math.max(112, Math.min(158,
            Math.min(wheelPanelRight() - MARGIN, height - HEADER) / 3));
        for (int index = 0; index < pageEntries().size(); index++) {
            if (!entryBounds(index, entryRadius).contains(mouseX, mouseY)) continue;
            selectEntry(pageStart() + index);
            return true;
        }

        CommandWheelCatalog.Entry entry = selectedEntry();
        if (entry != null) {
            if (entry.parameters().size() > parameterCapacity()) {
                if (inside(mouseX, mouseY, detailX() + 18, parameterPageY(), 22, 18)
                    && parameterOffset > 0) {
                    parameterOffset = Math.max(0, parameterOffset - parameterCapacity());
                    rebuildInputBoxes();
                    return true;
                }
                if (inside(mouseX, mouseY, detailX() + detailWidth() - 40, parameterPageY(), 22, 18)
                    && parameterEnd() < entry.parameters().size()) {
                    parameterOffset += parameterCapacity();
                    rebuildInputBoxes();
                    return true;
                }
            }
            for (int index = parameterOffset; index < parameterEnd(); index++) {
                CommandWheelCatalog.Parameter parameter = entry.parameters().get(index);
                int py = parameterY(index);
                if (parameter.kind() == CommandWheelCatalog.ParameterKind.TOGGLE
                    && inside(mouseX, mouseY, detailX() + detailWidth() - 72,
                        py - 3, 54, 18)) {
                    boolean enabled = Boolean.parseBoolean(parameterValues.getOrDefault(
                        parameter.id(), parameter.defaultValue()));
                    parameterValues.put(parameter.id(), Boolean.toString(!enabled));
                    refreshGeneratedCommand();
                    return true;
                }
                if (parameter.kind() == CommandWheelCatalog.ParameterKind.DROPDOWN
                    && inside(mouseX, mouseY, detailX() + 18, py + 13,
                        detailWidth() - 36, 20)) {
                    openDropdown = parameter.id().equals(openDropdown) ? "" : parameter.id();
                    return true;
                }
            }
            if (inside(mouseX, mouseY, detailX() + detailWidth() - 108,
                height - 27, 22, 18)) {
                restoreGeneratedCommand();
                return true;
            }
            if (inside(mouseX, mouseY, detailX() + detailWidth() - 80,
                height - 27, 80, 22)) return execute();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (searchBox != null && searchBox.isFocused()) {
                if (commandBox != null) setFocused(commandBox);
                return true;
            }
            return execute();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean execute() {
        if (minecraft == null || minecraft.player == null
            || minecraft.getConnection() == null || !canExecute()) return false;
        String value = commandBox == null ? selectedEntry().buildCommand(parameterValues)
            : commandBox.getValue().trim();
        if (value.startsWith("/")) value = value.substring(1);
        if (value.isBlank()) return false;
        minecraft.player.connection.sendCommand(value);
        onClose();
        return true;
    }

    private boolean canExecute() {
        return selectedEntry() != null && !manualCommand.isBlank()
            && (commandEdited || !selectedEntry().buildCommand(parameterValues).isBlank());
    }

    @Override
    public void onClose() {
        restoreNativeCursor();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && !XeroDeltaClient.COMMAND_WHEEL_KEY.isDown()) onClose();
    }

    private void hideNativeCursor() {
        if (minecraft == null || minecraft.getWindow() == null) return;
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    private void restoreNativeCursor() {
        if (minecraft == null || minecraft.getWindow() == null) return;
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    private CommandWheelCatalog.Parameter parameter(String id) {
        CommandWheelCatalog.Entry entry = selectedEntry();
        if (entry == null) return null;
        return entry.parameters().stream()
            .filter(parameter -> parameter.id().equals(id)).findFirst().orElse(null);
    }

    private CommandWheelCatalog.Entry selectedEntry() {
        return selected >= 0 && selected < visible.size() ? visible.get(selected) : null;
    }

    private Bounds entryBounds(int index, int radius) {
        if (compactLayout()) return new Bounds(MARGIN + 4, HEADER + 62 + index * 26,
            wheelPanelRight() - MARGIN - 8, 24);
        double angle = -Math.PI / 2.0D + Math.PI * 2.0D
            * index / Math.max(1, pageEntries().size());
        return new Bounds(
            wheelCenterX() + (int) Math.round(Math.cos(angle) * radius) - ENTRY_W / 2,
            wheelCenterY() + (int) Math.round(Math.sin(angle) * radius) - ENTRY_H / 2,
            ENTRY_W, ENTRY_H);
    }

    private int wheelPanelRight() {
        if (compactLayout()) return width * 46 / 100;
        return Math.max(360, Math.min(width * 58 / 100, 610));
    }

    private int wheelCenterX() {
        return (MARGIN + wheelPanelRight()) / 2;
    }

    private int wheelCenterY() {
        return HEADER + (height - HEADER - 72) / 2;
    }

    private int detailX() {
        return wheelPanelRight() + 10;
    }

    private int detailWidth() {
        return Math.max(80, width - detailX() - MARGIN);
    }

    private int parameterY(int index) {
        return parameterPageY() + 26 + (index - parameterOffset) * 43;
    }

    private boolean compactLayout() { return width < 900 || height < 580; }
    private int pageSize() {
        return compactLayout() ? Math.max(1, Math.min(WHEEL_PAGE_SIZE,
            (height - HEADER - 162) / 26)) : WHEEL_PAGE_SIZE;
    }
    private int pageCount() { return Math.max(1, (visible.size() + pageSize() - 1) / pageSize()); }
    private int pageStart() { return page * pageSize(); }
    private List<CommandWheelCatalog.Entry> pageEntries() {
        int start = pageStart();
        return start >= visible.size() ? List.of()
            : visible.subList(start, Math.min(visible.size(), start + pageSize()));
    }
    private int pageButtonY() { return compactLayout() ? height - 94 : wheelCenterY() + 27; }
    private int previousPageX() { return wheelCenterX() - (compactLayout() ? 55 : 31); }
    private int nextPageX() { return wheelCenterX() + (compactLayout() ? 33 : 9); }
    private int parameterPageY() { return HEADER + (height < 260 ? 55 : 79); }
    private int parameterCapacity() {
        return Math.max(1, 1 + (height - 72 - parameterPageY() - 26 - 33) / 43);
    }
    private int parameterEnd() {
        return selectedEntry() == null ? 0
            : Math.min(selectedEntry().parameters().size(), parameterOffset + parameterCapacity());
    }
    private int dropdownY(int index, CommandWheelCatalog.Parameter parameter) {
        return Math.max(HEADER + 8, Math.min(parameterY(index) + 33,
            height - 72 - parameter.options().size() * 20));
    }
    private Bounds categoryBounds(int index, int radius) {
        if (compactLayout()) {
            int cellWidth = (wheelPanelRight() - MARGIN - 8) / 4;
            return new Bounds(MARGIN + 4 + (index % 4) * cellWidth,
                HEADER + 10 + (index / 4) * 24, cellWidth - 2, CATEGORY_H);
        }
        double angle = -Math.PI / 2.0D + Math.PI * 2.0D * index
            / CommandWheelCatalog.Category.values().length;
        return new Bounds(wheelCenterX() + (int) Math.round(Math.cos(angle) * radius) - CATEGORY_W / 2,
            wheelCenterY() + (int) Math.round(Math.sin(angle) * radius) - CATEGORY_H / 2,
            CATEGORY_W, CATEGORY_H);
    }
    private static Component parameterLabel(CommandWheelCatalog.Parameter parameter) {
        return I18n.exists(parameter.labelKey()) ? Component.translatable(parameter.labelKey())
            : Component.literal(parameter.id());
    }

    private static String symbol(CommandWheelCatalog.Entry entry) {
        return switch (entry.category()) {
            case ALL -> "•";
            case GUI -> "▣";
            case PLAYER -> "@";
            case ITEM -> "◆";
            case TRADING -> "$";
            case SYSTEM -> "⚙";
            case MAIL -> "✉";
        };
    }

    private static Component entryTitle(CommandWheelCatalog.Entry entry) {
        if (entry.id().startsWith("tree:")) return Component.literal(entry.command());
        return Component.translatable(entry.titleKey());
    }

    private boolean matchesSearch(CommandWheelCatalog.Entry entry) {
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        return query.isEmpty() || entry.id().toLowerCase(Locale.ROOT).contains(query)
            || entry.command().toLowerCase(Locale.ROOT).contains(query)
            || entryTitle(entry).getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + height;
    }

    private record Bounds(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
    }
}
