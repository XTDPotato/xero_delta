package com.xtdpotato.xero_delta;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.UUID;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTRY =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, XeroDelta.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ItemStack>>> GRID_CONTENTS =
        REGISTRY.register("grid_contents",
            () -> DataComponentType.<List<ItemStack>>builder()
                .persistent(ItemStack.OPTIONAL_CODEC.listOf())
                .networkSynchronized(ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()))
                .build());

    private static final StreamCodec<RegistryFriendlyByteBuf, List<List<ItemStack>>> GRID_CONTAINERS_CODEC =
        StreamCodec.of((buf, containers) -> {
            buf.writeVarInt(containers.size());
            for (List<ItemStack> container : containers) {
                buf.writeVarInt(container.size());
                for (ItemStack stack : container) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
            }
        }, buf -> {
            int containerCount = buf.readVarInt();
            List<List<ItemStack>> containers = new java.util.ArrayList<>();
            for (int c = 0; c < containerCount; c++) {
                int size = buf.readVarInt();
                List<ItemStack> container = new java.util.ArrayList<>();
                for (int i = 0; i < size; i++) container.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                containers.add(container);
            }
            return containers;
        });

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<List<ItemStack>>>> GRID_CONTAINERS =
        REGISTRY.register("grid_containers",
            () -> DataComponentType.<List<List<ItemStack>>>builder()
                .persistent(ItemStack.OPTIONAL_CODEC.listOf().listOf())
                .networkSynchronized(GRID_CONTAINERS_CODEC)
                .build());

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> BOX_UUID =
        REGISTRY.register("box_uuid",
            () -> DataComponentType.<UUID>builder()
                .persistent(UUID_CODEC)
                .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> GRID_ROTATED =
        REGISTRY.register("grid_rotated",
            () -> DataComponentType.<Boolean>builder()
                .persistent(Codec.BOOL)
                .networkSynchronized(ByteBufCodecs.BOOL)
                .build());

    /** Per-stack binding flag. Bound stacks may be recycled, but never listed on the market. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ITEM_BOUND =
        REGISTRY.register("item_bound",
            () -> DataComponentType.<Boolean>builder()
                .persistent(Codec.BOOL)
                .networkSynchronized(ByteBufCodecs.BOOL)
                .build());

    /** UUID of the player this concrete stack was bound to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ITEM_BOUND_OWNER =
        REGISTRY.register("item_bound_owner",
            () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());

    /** Cosmetic selected for the ultimate safety box. */
    /** Marks a stack whose current contents have already been searched. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> LOOT_SEARCHED =
        REGISTRY.register("loot_searched",
            () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SAFETY_BOX_SKIN =
        REGISTRY.register("safety_box_skin",
            () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());

    public static void register(IEventBus bus) { REGISTRY.register(bus); }
}
