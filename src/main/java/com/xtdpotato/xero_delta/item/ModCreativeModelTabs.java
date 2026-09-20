package com.xtdpotato.xero_delta.item;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class ModCreativeModelTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, XeroDelta.MOD_ID);

    public static final Supplier<CreativeModeTab> TAB = CREATIVE_MODE_TAB.register("xero_delta",
        () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack(ModItems.SAFETY_BOX_3X3.get()))
            .title(Component.translatable("itemGroup.xero_delta"))
            .displayItems((p, output) -> {
                output.accept(ModItems.SAFETY_BOX_2X1.get());
                output.accept(ModItems.SAFETY_BOX_2X2.get());
                output.accept(ModItems.SAFETY_BOX_3X2.get());
                output.accept(ModItems.SAFETY_BOX_3X3.get());
                output.accept(ModItems.SAFETY_BOX_4X2.get());
                output.accept(ModItems.TRADING_MARKET.get());
                output.accept(ModItems.PERSONAL_WAREHOUSE.get());
                output.accept(ModItems.RECYCLING_STATION.get());
                for (var item : ModItems.EXTRA_ITEMS) output.accept(item.get());
                for (var item : ModItems.OFFICIAL_ITEMS) output.accept(item.get());
            }).build());

    public static void register(IEventBus eventBus) { CREATIVE_MODE_TAB.register(eventBus); }
}
