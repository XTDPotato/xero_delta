package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.compat.BetterLootingClientPriority;
import com.xtdpotato.xero_delta.compat.TaczInteractionPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Compact crosshair-adjacent F/H context prompt. */
public final class ContextInteractionHudRenderer {
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;

    private ContextInteractionHudRenderer() {
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.player == null || minecraft.screen != null
            || DownedClientState.INSTANCE.downed()) return;
        if (BetterLootingClientPriority.hasPickupTarget()) return;
        Prompt prompt = DownedClientState.INSTANCE.carrying() ? null : resolve(minecraft);
        if (prompt == null && !DownedClientState.INSTANCE.carrying()) return;
        boolean taczOwnsInteraction = TaczInteractionPriority.ownsInteraction(minecraft,
            XeroDeltaClient.RESCUE_KEY);
        boolean suppressInteraction = taczOwnsInteraction && prompt != null;
        if (suppressInteraction && !prompt.carryable()) return;
        renderConfigured(graphics, minecraft, prompt, false, suppressInteraction);
    }

    public static StatusEffectHudRenderer.Bounds renderPreview(GuiGraphics graphics,
                                                                 Minecraft minecraft) {
        Prompt prompt = new Prompt(Component.translatable("interaction.xero_delta.open"),
            Component.translatable("status_effect_hud.xero_delta.preview_context_name"), true);
        return renderConfigured(graphics, minecraft, prompt, true, false);
    }

    private static StatusEffectHudRenderer.Bounds renderConfigured(GuiGraphics graphics,
                                                                     Minecraft minecraft,
                                                                     Prompt prompt,
                                                                     boolean preview,
                                                                     boolean suppressInteraction) {
        boolean carryingOnly = prompt == null;
        boolean carryTargetOnly = suppressInteraction && prompt != null && prompt.carryable();
        boolean secondRow = !suppressInteraction && prompt != null && prompt.carryable();
        int firstWidth = carryTargetOnly
            ? rowWidth(minecraft, Component.translatable("interaction.xero_delta.carry"),
                Component.empty())
            : carryingOnly
            ? rowWidth(minecraft, Component.translatable("interaction.xero_delta.drop"),
                Component.empty())
            : rowWidth(minecraft, prompt.action(), prompt.name());
        int secondWidth = secondRow
            ? rowWidth(minecraft, Component.translatable("interaction.xero_delta.carry"),
                Component.empty()) : 0;
        int contentWidth = Math.max(firstWidth, secondWidth);
        int contentHeight = secondRow ? ROW_HEIGHT * 2 + ROW_GAP : ROW_HEIGHT;
        float scale = StatusEffectHudState.contextScale();
        int scaledWidth = Math.round(contentWidth * scale);
        int scaledHeight = Math.round(contentHeight * scale);
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int[] position = StatusEffectHudState.contextPosition(screenWidth / 2 + 38,
            screenHeight / 2 + 12, screenWidth, screenHeight, scaledWidth, scaledHeight);

        graphics.pose().pushPose();
        graphics.pose().translate(position[0], position[1], 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        float opacity = StatusEffectHudState.contextOpacity();
        if (carryTargetOnly) {
            drawRow(graphics, minecraft, 0, 0, "H",
                Component.translatable("interaction.xero_delta.carry"), Component.empty(), opacity);
        } else if (prompt != null) {
            drawRow(graphics, minecraft, 0, 0, "F", prompt.action(), prompt.name(), opacity);
            if (prompt.carryable()) drawRow(graphics, minecraft, 0, ROW_HEIGHT + ROW_GAP, "H",
                Component.translatable("interaction.xero_delta.carry"), Component.empty(), opacity);
        } else {
            drawRow(graphics, minecraft, 0, 0, "H",
                Component.translatable("interaction.xero_delta.drop"), Component.empty(), opacity);
        }
        graphics.pose().popPose();
        return new StatusEffectHudRenderer.Bounds(position[0], position[1],
            scaledWidth, scaledHeight);
    }

    private static Prompt resolve(Minecraft minecraft) {
        if (minecraft.hitResult instanceof EntityHitResult hit) {
            Entity entity = hit.getEntity();
            if (ContextInteractionClient.isCarriedTarget(minecraft, entity)) return null;
            if (ContextInteractionClient.isRescueTarget(minecraft, entity)) {
                return new Prompt(Component.translatable("interaction.xero_delta.rescue"),
                    entity.getDisplayName(), true);
            }
            if (entity instanceof CorpseEntity corpse) {
                return new Prompt(Component.translatable("interaction.xero_delta.search"),
                    corpse.getDisplayName(), true);
            }
            if (entity instanceof AbstractVillager) {
                return new Prompt(Component.translatable("interaction.xero_delta.trade"),
                    entity.getDisplayName(), false);
            }
            if (entity instanceof MenuProvider) {
                return new Prompt(Component.translatable("interaction.xero_delta.open"),
                    entity.getDisplayName(), false);
            }
        } else if (minecraft.hitResult instanceof BlockHitResult hit && minecraft.level != null) {
            var state = minecraft.level.getBlockState(hit.getBlockPos());
            if (state.getMenuProvider(minecraft.level, hit.getBlockPos()) != null) {
                return new Prompt(Component.translatable("interaction.xero_delta.open"),
                    state.getBlock() instanceof ChestBlock
                        ? Component.translatable("block.minecraft.chest")
                        : state.getBlock().getName(), false);
            }
            if (isDirectlyInteractable(state.getBlock())) {
                return new Prompt(Component.translatable("interaction.xero_delta.interact"),
                    state.getBlock().getName(), false);
            }
        }
        return null;
    }

    private static boolean isDirectlyInteractable(net.minecraft.world.level.block.Block block) {
        return block instanceof DoorBlock || block instanceof TrapDoorBlock
            || block instanceof FenceGateBlock || block instanceof ButtonBlock
            || block instanceof LeverBlock || block instanceof BellBlock
            || block instanceof NoteBlock || block instanceof BedBlock
            || block instanceof RespawnAnchorBlock || block instanceof CakeBlock
            || block instanceof ComposterBlock || block instanceof DaylightDetectorBlock
            || block instanceof RepeaterBlock || block instanceof ComparatorBlock;
    }

    private static int rowWidth(Minecraft minecraft, Component action, Component name) {
        return Math.max(92, 36 + minecraft.font.width(action) + minecraft.font.width(name));
    }

    private static void drawRow(GuiGraphics graphics, Minecraft minecraft, int x, int y,
                                 String key, Component action, Component name, float opacity) {
        int width = rowWidth(minecraft, action, name);
        graphics.fill(x, y, x + width, y + ROW_HEIGHT, alpha(0xB51B2325, opacity));
        graphics.fill(x + 4, y + 3, x + 22, y + 17, alpha(0xFFE5E9E8, opacity));
        graphics.renderOutline(x + 4, y + 3, 18, 14, alpha(0xFF687275, opacity));
        graphics.drawString(minecraft.font, key,
            x + 13 - minecraft.font.width(key) / 2, y + 6,
            alpha(0xFF172023, opacity), false);
        graphics.drawString(minecraft.font, action, x + 28, y + 6,
            alpha(0xFFF1F3F2, opacity), false);
        if (!name.getString().isBlank()) {
            graphics.drawString(minecraft.font, name,
                x + 32 + minecraft.font.width(action), y + 6,
                alpha(0xFF5ECDB2, opacity), false);
        }
    }

    private static int alpha(int color, float opacity) {
        int source = color >>> 24;
        int adjusted = Math.max(0, Math.min(255, Math.round(source * opacity)));
        return (adjusted << 24) | (color & 0x00FFFFFF);
    }

    private record Prompt(Component action, Component name, boolean carryable) {}
}
