package com.abyssia.registry;

import com.abyssia.Abyssia;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** EN01 potions: deep_sight (jelly tentacle) and abyssal_current (oil sac), each with long and strong variants. */
public final class ModPotions
{
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, Abyssia.MODID);

    public static final DeferredHolder<Potion, Potion> DEEP_SIGHT = POTIONS.register("deep_sight",
            () -> new Potion("deep_sight", new MobEffectInstance(ModMobEffects.DEEP_SIGHT, 3600)));
    public static final DeferredHolder<Potion, Potion> LONG_DEEP_SIGHT = POTIONS.register("long_deep_sight",
            () -> new Potion("deep_sight", new MobEffectInstance(ModMobEffects.DEEP_SIGHT, 9600)));
    public static final DeferredHolder<Potion, Potion> STRONG_DEEP_SIGHT = POTIONS.register("strong_deep_sight",
            () -> new Potion("deep_sight", new MobEffectInstance(ModMobEffects.DEEP_SIGHT, 1800, 1)));
    public static final DeferredHolder<Potion, Potion> ABYSSAL_CURRENT = POTIONS.register("abyssal_current",
            () -> new Potion("abyssal_current", new MobEffectInstance(ModMobEffects.ABYSSAL_CURRENT, 3600)));
    public static final DeferredHolder<Potion, Potion> LONG_ABYSSAL_CURRENT = POTIONS.register("long_abyssal_current",
            () -> new Potion("abyssal_current", new MobEffectInstance(ModMobEffects.ABYSSAL_CURRENT, 9600)));
    public static final DeferredHolder<Potion, Potion> STRONG_ABYSSAL_CURRENT = POTIONS.register("strong_abyssal_current",
            () -> new Potion("abyssal_current", new MobEffectInstance(ModMobEffects.ABYSSAL_CURRENT, 1800, 1)));

    private ModPotions() {}

    public static void register(IEventBus bus)
    {
        POTIONS.register(bus);
        NeoForge.EVENT_BUS.addListener(ModPotions::registerBrewing);
    }

    /** Awkward + material, then Redstone (long) / Glowstone (strong); splash and lingering come from the vanilla container mixes. */
    private static void registerBrewing(RegisterBrewingRecipesEvent event)
    {
        PotionBrewing.Builder builder = event.getBuilder();
        mixes(builder, DEEP_SIGHT, LONG_DEEP_SIGHT, STRONG_DEEP_SIGHT, ModItems.JELLY_TENTACLE.get());
        mixes(builder, ABYSSAL_CURRENT, LONG_ABYSSAL_CURRENT, STRONG_ABYSSAL_CURRENT, ModItems.OIL_SAC.get());
    }

    private static void mixes(PotionBrewing.Builder builder, Holder<Potion> base, Holder<Potion> longer, Holder<Potion> strong, Item material)
    {
        builder.addMix(Potions.AWKWARD, material, base);
        builder.addMix(base, Items.REDSTONE, longer);
        builder.addMix(base, Items.GLOWSTONE_DUST, strong);
    }
}
