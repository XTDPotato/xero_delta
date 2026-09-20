package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.client.CommandWheelCatalog;
import com.xtdpotato.xero_delta.screen.material.Material2Button;
import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Focused value-entry dialog opened only after a radial command needs editable
 * arguments. The command wheel itself remains a hold-to-open HUD surface.
 */
public final class CommandWheelValueDialog extends Screen {
    private static final int DIALOG_WIDTH = 338;
    private final CommandWheelCatalog.Entry entry;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, EditBox> textInputs = new LinkedHashMap<>();
    private Component validationMessage = Component.empty();
    private int dialogLeft;
    private int dialogTop;
    private int dialogHeight;

    public CommandWheelValueDialog(CommandWheelCatalog.Entry entry) {
        super(Component.translatable("command_wheel.xero_delta.set_parameters"));
        this.entry = entry;
        for (CommandWheelCatalog.Parameter parameter : entry.parameters()) {
            values.put(parameter.id(), parameter.defaultValue());
        }
    }

    @Override
    protected void init() {
        dialogHeight = 106 + entry.parameters().size() * 42;
        dialogHeight = Math.min(Math.max(150, dialogHeight), Math.max(150, height - 24));
        dialogLeft = (width - DIALOG_WIDTH) / 2;
        dialogTop = (height - dialogHeight) / 2;
        textInputs.clear();

        int rowY = dialogTop + 58;
        for (CommandWheelCatalog.Parameter parameter : entry.parameters()) {
            if (parameter.kind() == CommandWheelCatalog.ParameterKind.TEXT
                || parameter.kind() == CommandWheelCatalog.ParameterKind.NUMBER) {
                EditBox input = new Material3CompactEditBox(font, dialogLeft + 16, rowY + 13,
                    DIALOG_WIDTH - 32, 20, parameterLabel(parameter));
                input.setMaxLength(256);
                input.setValue(values.getOrDefault(parameter.id(), parameter.defaultValue()));
                if (parameter.kind() == CommandWheelCatalog.ParameterKind.NUMBER) {
                    input.setFilter(value -> value.matches("-?\\d*(\\.\\d*)?"));
                }
                input.setResponder(value -> values.put(parameter.id(), value));
                textInputs.put(parameter.id(), addRenderableWidget(input));
            } else {
                final Material2Button[] valueButton = new Material2Button[1];
                valueButton[0] = new Material2Button(dialogLeft + 16, rowY + 13,
                    DIALOG_WIDTH - 32, buttonMessage(parameter),
                    Material2Button.Variant.TONAL, () -> {
                    cycleValue(parameter);
                    valueButton[0].setMessage(buttonMessage(parameter));
                }).smooth(true);
                addRenderableWidget(valueButton[0]);
            }
            rowY += 42;
        }

        int buttonY = dialogTop + dialogHeight - 32;
        addRenderableWidget(new Material2Button(dialogLeft + 16, buttonY, 145,
            Component.translatable("command_wheel.xero_delta.cancel"),
            Material2Button.Variant.TEXT, this::onClose).smooth(true));
        addRenderableWidget(new Material2Button(dialogLeft + DIALOG_WIDTH - 161, buttonY, 145,
            Component.translatable("command_wheel.xero_delta.confirm"),
            Material2Button.Variant.FILLED, this::execute).smooth(true));
        if (!textInputs.isEmpty()) setInitialFocus(textInputs.values().iterator().next());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xBB05090A);
        Material2Drawing.smoothRoundedPanel(graphics, dialogLeft, dialogTop, DIALOG_WIDTH,
            dialogHeight, Material3Theme.RADIUS_LARGE, Material3Theme.OUTLINE_VARIANT,
            Material3Theme.SURFACE_CONTAINER);
        Material2Drawing.smoothRoundedRect(graphics, dialogLeft + 1, dialogTop + 1,
            DIALOG_WIDTH - 2, 36, Material3Theme.RADIUS_LARGE,
            Material3Theme.SURFACE_CONTAINER_HIGH);
        graphics.drawString(font, font.plainSubstrByWidth(entryTitle().getString(), DIALOG_WIDTH - 32),
            dialogLeft + 16, dialogTop + 12, 0xFFF0F5F3, false);
        int rowY = dialogTop + 58;
        for (CommandWheelCatalog.Parameter parameter : entry.parameters()) {
            graphics.drawString(font, font.plainSubstrByWidth(parameterLabel(parameter).getString(),
                DIALOG_WIDTH - 32), dialogLeft + 16, rowY,
                Material3Theme.TEXT_MUTED, false);
            rowY += 42;
        }
        if (!validationMessage.getString().isBlank()) {
            graphics.drawCenteredString(font, validationMessage, width / 2,
                dialogTop + dialogHeight - 49, 0xFFFF8B84);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return execute();
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean execute() {
        for (Map.Entry<String, EditBox> input : textInputs.entrySet()) {
            values.put(input.getKey(), input.getValue().getValue().trim());
        }
        for (CommandWheelCatalog.Parameter parameter : entry.parameters()) {
            if (parameter.required() && values.getOrDefault(parameter.id(), "").trim().isBlank()) {
                validationMessage = Component.translatable(
                    "command_wheel.xero_delta.parameter_required", parameterLabel(parameter));
                return false;
            }
        }
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null) return false;
        String command = entry.buildCommand(values);
        if (command.isBlank()) return false;
        if (command.startsWith("/")) command = command.substring(1);
        minecraft.player.connection.sendCommand(command);
        onClose();
        return true;
    }

    private void cycleValue(CommandWheelCatalog.Parameter parameter) {
        if (parameter.kind() == CommandWheelCatalog.ParameterKind.TOGGLE) {
            boolean enabled = Boolean.parseBoolean(values.getOrDefault(parameter.id(), parameter.defaultValue()));
            values.put(parameter.id(), Boolean.toString(!enabled));
            return;
        }
        if (parameter.options().isEmpty()) return;
        int current = parameter.options().indexOf(values.getOrDefault(parameter.id(), parameter.defaultValue()));
        values.put(parameter.id(), parameter.options().get(Math.floorMod(current + 1, parameter.options().size())));
    }

    private Component buttonMessage(CommandWheelCatalog.Parameter parameter) {
        String value = values.getOrDefault(parameter.id(), parameter.defaultValue());
        if (parameter.kind() == CommandWheelCatalog.ParameterKind.TOGGLE) {
            return Component.literal(Boolean.parseBoolean(value) ? "ON" : "OFF");
        }
        return Component.literal(value.isBlank() ? "—" : value);
    }

    private Component entryTitle() {
        return entry.id().startsWith("tree:") ? Component.literal(entry.command())
            : Component.translatable(entry.titleKey());
    }

    private static Component parameterLabel(CommandWheelCatalog.Parameter parameter) {
        return I18n.exists(parameter.labelKey()) ? Component.translatable(parameter.labelKey())
            : Component.literal(parameter.id());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}

