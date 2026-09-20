package com.xtdpotato.xero_delta.compat;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Lets TAcz own a shared interaction key while its interaction prompt is visible. */
public final class TaczInteractionPriority {
    private static boolean initialized;
    private static KeyMapping interactKey;
    private static Method mainHandHoldGun;
    private static Method canInteractBlock;
    private static Method canInteractEntity;
    private static Object disableInteractHudText;
    private static Method configValueGet;

    private TaczInteractionPriority() {}

    public static boolean ownsInteraction(Minecraft minecraft, KeyMapping xeroKey) {
        initialize();
        if (interactKey == null || !interactKey.same(xeroKey)
            || minecraft.player == null || minecraft.player.isSpectator()
            || minecraft.screen != null || minecraft.hitResult == null) return false;
        try {
            if (disableInteractHudText != null && configValueGet != null
                && Boolean.TRUE.equals(configValueGet.invoke(disableInteractHudText))) {
                return false;
            }
            if (!(boolean) mainHandHoldGun.invoke(null, minecraft.player)) return false;
            if (minecraft.hitResult instanceof BlockHitResult hit && minecraft.level != null) {
                return (boolean) canInteractBlock.invoke(null,
                    minecraft.level.getBlockState(hit.getBlockPos()));
            }
            if (minecraft.hitResult instanceof EntityHitResult hit) {
                return (boolean) canInteractEntity.invoke(null, hit.getEntity());
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("tacz")) return;
        try {
            Class<?> interactKeyType = Class.forName("com.tacz.guns.client.input.InteractKey");
            Field interactKeyField = interactKeyType.getField("INTERACT_KEY");
            interactKey = (KeyMapping) interactKeyField.get(null);

            Class<?> gunType = Class.forName("com.tacz.guns.api.item.IGun");
            mainHandHoldGun = gunType.getMethod("mainHandHoldGun",
                net.minecraft.world.entity.LivingEntity.class);

            Class<?> configReadType = Class.forName(
                "com.tacz.guns.config.util.InteractKeyConfigRead");
            canInteractBlock = configReadType.getMethod("canInteractBlock",
                net.minecraft.world.level.block.state.BlockState.class);
            canInteractEntity = configReadType.getMethod("canInteractEntity",
                net.minecraft.world.entity.Entity.class);

            Class<?> renderConfigType = Class.forName("com.tacz.guns.config.client.RenderConfig");
            disableInteractHudText = renderConfigType.getField("DISABLE_INTERACT_HUD_TEXT")
                .get(null);
            if (disableInteractHudText != null) {
                configValueGet = disableInteractHudText.getClass().getMethod("get");
            }
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            interactKey = null;
            mainHandHoldGun = null;
            canInteractBlock = null;
            canInteractEntity = null;
            disableInteractHudText = null;
            configValueGet = null;
        }
    }
}
