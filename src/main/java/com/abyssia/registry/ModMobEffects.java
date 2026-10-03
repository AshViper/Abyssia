package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.effect.AbyssiaEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** EN01 effects. Fog/darkness of deep_sight and murk is applied client side in DeepOceanClientEffects. */
public final class ModMobEffects
{
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Abyssia.MODID);

    /** Cuts deep-sea fog and darkness (not night vision). */
    public static final DeferredHolder<MobEffect, MobEffect> DEEP_SIGHT = EFFECTS.register("deep_sight",
            () -> new AbyssiaEffect(MobEffectCategory.BENEFICIAL, 0x39B8D8));
    /** Swim speed +15% per level (SWIM_SPEED only acts in water). */
    public static final DeferredHolder<MobEffect, MobEffect> ABYSSAL_CURRENT = EFFECTS.register("abyssal_current",
            () -> new AbyssiaEffect(MobEffectCategory.BENEFICIAL, 0x2868C7)
                    .addAttributeModifier(NeoForgeMod.SWIM_SPEED, id("effect.abyssal_current"), 0.15, Operation.ADD_MULTIPLIED_TOTAL));
    /** Swim speed -10% per level. */
    public static final DeferredHolder<MobEffect, MobEffect> PRESSURE_FATIGUE = EFFECTS.register("pressure_fatigue",
            () -> new AbyssiaEffect(MobEffectCategory.HARMFUL, 0x514A72)
                    .addAttributeModifier(NeoForgeMod.SWIM_SPEED, id("effect.pressure_fatigue"), -0.10, Operation.ADD_MULTIPLIED_TOTAL));
    /** Movement -15% per level (mining speed in EffectEvents). */
    public static final DeferredHolder<MobEffect, MobEffect> COLD_SHOCK = EFFECTS.register("cold_shock",
            () -> new AbyssiaEffect(MobEffectCategory.HARMFUL, 0x8AB8D8)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("effect.cold_shock"), -0.15, Operation.ADD_MULTIPLIED_TOTAL));
    /** Darker, thicker fog; halved by Deep Sight I, cancelled by II. */
    public static final DeferredHolder<MobEffect, MobEffect> MURK = EFFECTS.register("murk",
            () -> new AbyssiaEffect(MobEffectCategory.HARMFUL, 0x26333D));

    private ModMobEffects() {}

    private static ResourceLocation id(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, path);
    }

    public static void register(IEventBus bus)
    {
        EFFECTS.register(bus);
    }
}
