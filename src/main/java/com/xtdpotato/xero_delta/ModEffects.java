package com.xtdpotato.xero_delta;

import com.xtdpotato.xero_delta.effect.InjuryEffect;
import com.xtdpotato.xero_delta.effect.StatusMarkerEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> REGISTRY =
        DeferredRegister.create(Registries.MOB_EFFECT, XeroDelta.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> HEAD_INJURY =
        REGISTRY.register("head_injury", () -> new InjuryEffect(0xD9544D));
    public static final DeferredHolder<MobEffect, MobEffect> CHEST_INJURY =
        REGISTRY.register("chest_injury", () -> new InjuryEffect(0xC76A4B));
    public static final DeferredHolder<MobEffect, MobEffect> ABDOMEN_INJURY =
        REGISTRY.register("abdomen_injury", () -> new InjuryEffect(0xBD6248));
    public static final DeferredHolder<MobEffect, MobEffect> LEFT_ARM_INJURY =
        REGISTRY.register("left_arm_injury", () -> new InjuryEffect(0xD38B48));
    public static final DeferredHolder<MobEffect, MobEffect> RIGHT_ARM_INJURY =
        REGISTRY.register("right_arm_injury", () -> new InjuryEffect(0xD38B48));
    public static final DeferredHolder<MobEffect, MobEffect> LEFT_LEG_INJURY =
        REGISTRY.register("left_leg_injury", () -> new InjuryEffect(0xB74F49));
    public static final DeferredHolder<MobEffect, MobEffect> RIGHT_LEG_INJURY =
        REGISTRY.register("right_leg_injury", () -> new InjuryEffect(0xB74F49));
    public static final DeferredHolder<MobEffect, MobEffect> WHOLE_BODY_INJURY =
        REGISTRY.register("whole_body_injury", () -> new InjuryEffect(0x8C3E4C));

    public static final DeferredHolder<MobEffect, MobEffect> HEAD_DIZZINESS =
        harmful("head_dizziness", 0xC9A34C);
    public static final DeferredHolder<MobEffect, MobEffect> HEAD_PAIN =
        harmful("head_pain", 0xD55A4F);
    public static final DeferredHolder<MobEffect, MobEffect> CHEST_PAIN =
        harmful("chest_pain", 0xC94C43);
    public static final DeferredHolder<MobEffect, MobEffect> ABDOMINAL_PAIN =
        harmful("abdominal_pain", 0xB95348);
    public static final DeferredHolder<MobEffect, MobEffect> ARM_FRACTURE =
        harmful("arm_fracture", 0xD17848);
    public static final DeferredHolder<MobEffect, MobEffect> LEG_FRACTURE =
        harmful("leg_fracture", 0xB96743);
    public static final DeferredHolder<MobEffect, MobEffect> LEG_FISSURE =
        harmful("leg_fissure", 0xA94B40);
    public static final DeferredHolder<MobEffect, MobEffect> BLEEDING =
        harmful("bleeding", 0xB51F2F);

    public static final DeferredHolder<MobEffect, MobEffect> PAIN_RELIEF =
        beneficial("pain_relief", 0x65D69A);
    public static final DeferredHolder<MobEffect, MobEffect> WEIGHT_BOOST =
        beneficial("weight_boost", 0x4EC8B0);
    public static final DeferredHolder<MobEffect, MobEffect> HEARING_BOOST =
        beneficial("hearing_boost", 0x69AADB);
    public static final DeferredHolder<MobEffect, MobEffect> STAMINA_ATTRIBUTE_BOOST =
        beneficial("stamina_attribute_boost", 0xE26F86);
    public static final DeferredHolder<MobEffect, MobEffect> STAMINA_CAPACITY_BOOST =
        beneficial("stamina_capacity_boost", 0x4DBD9A);

    private ModEffects() {
    }

    private static DeferredHolder<MobEffect, MobEffect> harmful(String id, int color) {
        return REGISTRY.register(id, () -> new StatusMarkerEffect(false, color));
    }

    private static DeferredHolder<MobEffect, MobEffect> beneficial(String id, int color) {
        return REGISTRY.register(id, () -> new StatusMarkerEffect(true, color));
    }

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}
