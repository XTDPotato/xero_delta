package com.xtdpotato.xero_delta;

import com.xtdpotato.xero_delta.entity.CorpseEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTRY =
        DeferredRegister.create(Registries.ENTITY_TYPE, XeroDelta.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<CorpseEntity>> CORPSE =
        REGISTRY.register("corpse", () -> EntityType.Builder
            .<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
            .sized(1.65F, 0.5F)
            .clientTrackingRange(10)
            .updateInterval(3)
            .build("corpse"));
    public static final DeferredHolder<EntityType<?>, EntityType<CorpseEntity>> LOOT_BOX =
        REGISTRY.register("loot_box", () -> EntityType.Builder
            .<CorpseEntity>of((type, level) -> new CorpseEntity(type, level, true), MobCategory.MISC)
            .sized(1.65F, 0.5F)
            .clientTrackingRange(10)
            .updateInterval(3)
            .build("loot_box"));

    private ModEntities() {}

    public static void register(IEventBus bus) { REGISTRY.register(bus); }
}
