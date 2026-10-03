package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.effect.AbyssiaEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** EN01 effects. Fog/darkness of deep_sight and murk is applied client side in DeepOceanClientEffects. */
public final class ModMobEffects
{
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Abyssia.MODID);

    /** Cuts deep-sea fog and darkness (not night vision). */
    public static final RegistryObject<MobEffect> DEEP_SIGHT = EFFECTS.register("deep_sight",
            () -> new AbyssiaEffect(MobEffectCategory.BENEFICIAL, 0x39B8D8));
    /** Swim speed +15% per level (SWIM_SPEED only acts in water). */
    public static final RegistryObject<MobEffect> ABYSSAL_CURRENT = EFFECTS.register("abyssal_current", () ->
    {
        MobEffect e = new AbyssiaEffect(MobEffectCategory.BENEFICIAL, 0x2868C7);
        e.addAttributeModifier(ForgeMod.SWIM_SPEED.get(), "8f0b8e52-6a3c-4b1e-9d57-3a1c2e4b7a01", 0.15, Operation.MULTIPLY_TOTAL);
        return e;
    });
    /** Swim speed -10% per level. */
    public static final RegistryObject<MobEffect> PRESSURE_FATIGUE = EFFECTS.register("pressure_fatigue", () ->
    {
        MobEffect e = new AbyssiaEffect(MobEffectCategory.HARMFUL, 0x514A72);
        e.addAttributeModifier(ForgeMod.SWIM_SPEED.get(), "8f0b8e52-6a3c-4b1e-9d57-3a1c2e4b7a02", -0.10, Operation.MULTIPLY_TOTAL);
        return e;
    });
    /** Movement -15% per level (mining speed in EffectEvents). */
    public static final RegistryObject<MobEffect> COLD_SHOCK = EFFECTS.register("cold_shock", () ->
    {
        MobEffect e = new AbyssiaEffect(MobEffectCategory.HARMFUL, 0x8AB8D8);
        e.addAttributeModifier(Attributes.MOVEMENT_SPEED, "8f0b8e52-6a3c-4b1e-9d57-3a1c2e4b7a03", -0.15, Operation.MULTIPLY_TOTAL);
        return e;
    });
    /** Darker, thicker fog; halved by Deep Sight I, cancelled by II. */
    public static final RegistryObject<MobEffect> MURK = EFFECTS.register("murk",
            () -> new AbyssiaEffect(MobEffectCategory.HARMFUL, 0x26333D));

    private ModMobEffects() {}

    public static void register(IEventBus bus)
    {
        EFFECTS.register(bus);
    }
}
