package com.xtdpotato.xero_delta;

import com.mojang.logging.LogUtils;
import com.xtdpotato.xero_delta.command.ModCommands;
import com.xtdpotato.xero_delta.block.ModBlocks;
import com.xtdpotato.xero_delta.item.ModCreativeModelTabs;
import com.xtdpotato.xero_delta.item.ModItems;
import com.xtdpotato.xero_delta.item.OfficialArmorMaterials;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.data.DeltaPacksConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(XeroDelta.MOD_ID)
public class XeroDelta {
    public static final String MOD_ID = "xero_delta";
    public static final Logger LOGGER = LogUtils.getLogger();

    public XeroDelta(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.register(modEventBus);
        ModEffects.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModNetwork::register);
        OfficialArmorMaterials.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeModelTabs.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC,
            DeltaPacksConfig.prepareConfigFile("xero_delta-common.toml"));
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("XeroDelta common setup complete");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("XeroDelta server starting!");
    }
}

