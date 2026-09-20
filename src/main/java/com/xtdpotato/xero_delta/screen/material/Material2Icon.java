package com.xtdpotato.xero_delta.screen.material;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Compatibility enum backed by rasterized Material Symbols Rounded textures. */
public enum Material2Icon {
    INFO("info"), MAIL("mail"), SECURITY("security"), GRID_VIEW("grid_view"),
    PALETTE("palette"), TOOLTIP("tooltip"), DASHBOARD("dashboard"),
    RESTART_ALT("restart_alt"), SAVE("save"), ADD("add"),
    CLOSE("close"), OPEN_IN_NEW("open_in_new"), EXTENSION("extension"),
    PAYMENTS("payments"), MOVE("move"), INVENTORY("inventory"),
    TUNE("tune"), SETTINGS("settings"), CHECK("check"),
    BACKPACK("backpack"), CUBE("cube"), TEXT_FIELDS("text_fields"),
    BORDER_STYLE("border_style"), FORMAT_SIZE("format_size"), ALIGN_TOP("align_top"),
    ALIGN_BOTTOM("align_bottom"), ALIGN_LEFT("align_left"),
    ALIGN_RIGHT("align_right"), AUTO_AWESOME("auto_awesome"),
    RULE("rule"), SHIELD("shield"), LINK("link"),
    HANDYMAN("handyman"), WIDGETS("widgets"), CATEGORY("category"),
    COLORIZE("colorize"), CHEVRON_RIGHT("chevron_right"),
    TOGGLE_ON("toggle_on");

    private final String textureName;
    private final ResourceLocation texture;

    Material2Icon(String textureName) {
        this.textureName = textureName;
        this.texture = ResourceLocation.fromNamespaceAndPath(
            "xero_delta", "textures/gui/"
                + ("info".equals(textureName) || "close".equals(textureName) ? "action/" : "material3/")
                + textureName + ".png");
    }

    public static Material2Icon fromName(String name) {
        if (name != null) for (Material2Icon icon : values()) {
            if (icon.textureName.equalsIgnoreCase(name.trim())) return icon;
        }
        return SETTINGS;
    }

    public void render(GuiGraphics graphics, int centerX, int centerY, int color) {
        float alpha = (color >>> 24 & 0xFF) / 255.0F;
        float red = (color >>> 16 & 0xFF) / 255.0F;
        float green = (color >>> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        RenderSystem.setShaderColor(red, green, blue, alpha);
        try {
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            graphics.blit(texture, centerX - 6, centerY - 6,
                12, 12, 0, 0, 16, 16, 16, 16);
        } finally {
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}


