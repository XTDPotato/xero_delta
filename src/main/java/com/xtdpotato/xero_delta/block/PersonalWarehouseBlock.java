package com.xtdpotato.xero_delta.block;

import com.xtdpotato.xero_delta.data.PersonalWarehouseData;
import com.xtdpotato.xero_delta.data.WarehouseCategory;
import com.xtdpotato.xero_delta.grid.ContainerGridNormalizer;
import com.xtdpotato.xero_delta.menu.PersonalWarehouseContainer;
import com.xtdpotato.xero_delta.menu.PersonalWarehouseMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class PersonalWarehouseBlock extends Block {
    public PersonalWarehouseBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        open(serverPlayer);
        return InteractionResult.CONSUME;
    }

    public static void open(ServerPlayer player) {
        open(player, WarehouseCategory.MAIN);
    }

    public static boolean isNearby(Player player) {
        if (player == null) return false;
        return isNearby(player.blockPosition(), pos -> player.level().hasChunkAt(pos)
            && player.level().getBlockState(pos).is(ModBlocks.PERSONAL_WAREHOUSE.get()));
    }

    static boolean isNearby(BlockPos center, java.util.function.Predicate<BlockPos> warehouseAt) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-5, -5, -5), center.offset(5, 5, 5))) {
            if (warehouseAt.test(pos)) return true;
        }
        return false;
    }

    public static void open(ServerPlayer player, WarehouseCategory requestedCategory) {
        PersonalWarehouseData data = PersonalWarehouseData.get(player.server);
        PersonalWarehouseData.Warehouse warehouse = data.warehouse(player.getUUID());
        WarehouseCategory category = requestedCategory == null
            ? WarehouseCategory.MAIN : requestedCategory;
        PersonalWarehouseData.Bin bin = warehouse.bin(category);
        int[] used = new int[WarehouseCategory.values().length];
        int[] total = new int[WarehouseCategory.values().length];
        for (WarehouseCategory value : WarehouseCategory.values()) {
            used[value.ordinal()] = warehouse.usedCells(value);
            total[value.ordinal()] = warehouse.bin(value).capacity();
        }
        String customName = warehouse.name();
        Component title = customName.isBlank()
            ? Component.translatable("screen.xero_delta.personal_warehouse")
            : Component.literal(customName);
        MenuProvider provider = new SimpleMenuProvider(
            (id, inventory, ignored) -> new PersonalWarehouseMenu(id, inventory,
                new PersonalWarehouseContainer(data, bin, category), bin.rows(),
                category, customName, used, total),
            title);
        player.openMenu(provider, buffer -> {
            buffer.writeUtf(category.id(), 32);
            buffer.writeVarInt(bin.rows());
            buffer.writeUtf(customName, PersonalWarehouseData.MAX_NAME_LENGTH * 4);
            for (WarehouseCategory value : WarehouseCategory.values()) {
                buffer.writeVarInt(used[value.ordinal()]);
                buffer.writeVarInt(total[value.ordinal()]);
            }
        });
        if (player.containerMenu instanceof PersonalWarehouseMenu) {
            ContainerGridNormalizer.normalize(player, player.containerMenu);
        }
    }
}
