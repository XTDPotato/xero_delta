package com.xtdpotato.xero_delta.block;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(XeroDelta.MOD_ID);

    public static final DeferredBlock<Block> TRADING_MARKET = BLOCKS.register("trading_market",
        () -> new TradingMarketBlock(BlockBehaviour.Properties.of()
            .strength(4.0F, 8.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<Block> PERSONAL_WAREHOUSE = BLOCKS.register("personal_warehouse",
        () -> new PersonalWarehouseBlock(BlockBehaviour.Properties.of()
            .strength(4.0F, 8.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<Block> RECYCLING_STATION = BLOCKS.register("recycling_station",
        () -> new RecyclingStationBlock(BlockBehaviour.Properties.of()
            .strength(4.0F, 8.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
            .noOcclusion()));

    private ModBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
