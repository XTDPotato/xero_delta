package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.TooltipFallbacks;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

@Mixin(ClientTooltipComponent.class)
public interface ClientTooltipComponentMixin {

    /**
     * Redirect FACTORIES.get() to return a no-op fallback instead of null,
     * preventing "Unknown TooltipComponent" crash from Connector/Fabric mods.
     */
    @Redirect(method = "create", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 0)
    private static Object redirectFactoryGet(Map<?, ?> map, Object key) {
        return TooltipFallbacks.getFactoryOrFallback(map, key);
    }
}
