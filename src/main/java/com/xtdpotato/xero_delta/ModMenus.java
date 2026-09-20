package com.xtdpotato.xero_delta;

import com.xtdpotato.xero_delta.grid.SafetyBoxMenu;
import com.xtdpotato.xero_delta.trading.TradingMenu;
import com.xtdpotato.xero_delta.trading.RecyclingMenu;
import com.xtdpotato.xero_delta.trading.TradingOperatorMenu;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import com.xtdpotato.xero_delta.menu.PersonalWarehouseMenu;
import com.xtdpotato.xero_delta.menu.GroundPackMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTRY =
        DeferredRegister.create(Registries.MENU, XeroDelta.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<SafetyBoxMenu>> SAFETY_BOX =
        REGISTRY.register("safety_box_menu",
            () -> new MenuType<>(SafetyBoxMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<TradingMenu>> TRADING_MARKET =
        REGISTRY.register("trading_market",
            () -> new MenuType<>(TradingMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<RecyclingMenu>> RECYCLING_STATION =
        REGISTRY.register("recycling_station",
            () -> new MenuType<>(RecyclingMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<TradingOperatorMenu>> TRADING_OPERATOR =
        REGISTRY.register("trading_operator",
            () -> new MenuType<>(TradingOperatorMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<PersonalWarehouseMenu>> PERSONAL_WAREHOUSE =
        REGISTRY.register("personal_warehouse", () -> IMenuTypeExtension.create(PersonalWarehouseMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CorpseMenu>> CORPSE =
        REGISTRY.register("corpse", () -> IMenuTypeExtension.create(CorpseMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<GroundPackMenu>> GROUND_PACK =
        REGISTRY.register("ground_pack", () -> IMenuTypeExtension.create(GroundPackMenu::new));

    public static void register(IEventBus bus) { REGISTRY.register(bus); }
}
