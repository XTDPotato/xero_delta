package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material2Button;
import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material2Icon;
import com.xtdpotato.xero_delta.trading.TradingHtmlThemeParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** Dedicated entity-model preview opened from the HTML corpse-rule editor. */
public final class CorpseEntityPreviewScreen extends Screen {
    private final Screen parent;
    private final String entityId;
    private final TradingHtmlThemeParser.Theme theme;
    private EntityModelPreview preview;
    private Material2Button resetButton;
    private Material2Button closeButton;

    public CorpseEntityPreviewScreen(Screen parent, String entityId) {
        super(Component.translatable("screen.xero_delta.corpse_rules.entity_preview"));
        this.parent = parent;
        this.entityId = entityId == null ? "" : entityId;
        this.theme = ConfigThemeCatalog.theme(
            com.xtdpotato.xero_delta.Config.INSTANCE.configTheme.get());
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(width - 32, 480);
        int panelHeight = Math.min(height - 32, 360);
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        preview = addRenderableWidget(new EntityModelPreview(x + 16, y + 52,
            panelWidth - 32, panelHeight - 68, entityType(entityId),
            theme.panelAlt(), theme.border(), theme.accent()));
        resetButton = addRenderableWidget(new Material2Button(
            x + panelWidth - 72, y + 14, 28,
            Component.translatable("screen.xero_delta.corpse_rules.reset_preview"),
            Material2Button.Variant.TEXT, () -> preview.resetView())
            .icon(Material2Icon.RESTART_ALT));
        closeButton = addRenderableWidget(new Material2Button(
            x + panelWidth - 40, y + 14, 28,
            Component.translatable("screen.xero_delta.config.close"),
            Material2Button.Variant.TEXT, this::onClose)
            .icon(Material2Icon.CLOSE));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC90A1013);
        int panelWidth = Math.min(width - 32, 480);
        int panelHeight = Math.min(height - 32, 360);
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        Material2Drawing.roundedRect(graphics, x, y, panelWidth, panelHeight, 8, theme.background());
        Material2Drawing.outlineRoundedRect(graphics, x, y, panelWidth, panelHeight, 8,
            1.0F, theme.border());
        Component heading = Component.translatable(
            "screen.xero_delta.corpse_rules.entity_preview_title", entityName(entityId));
        graphics.drawString(font, font.plainSubstrByWidth(heading.getString(), panelWidth - 116),
            x + 16, y + 16, theme.text(), false);
        graphics.drawString(font, font.plainSubstrByWidth(entityId, panelWidth - 116),
            x + 16, y + 30, theme.muted(), false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick) {
        // The popup already draws an opaque dim layer. Skipping the vanilla blur keeps
        // its title, one-pixel outline, model preview and controls at native resolution.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return preview != null && preview.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        return preview != null && preview.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (super.mouseReleased(mouseX, mouseY, button)) return true;
        return preview != null && preview.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return preview != null && preview.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private static EntityType<?> entityType(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    private static String entityName(String rawId) {
        EntityType<?> type = entityType(rawId);
        return type == null ? rawId : type.getDescription().getString();
    }
}

