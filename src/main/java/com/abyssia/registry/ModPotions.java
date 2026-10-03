package com.abyssia.registry;

import com.abyssia.Abyssia;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/** EN01 potions: deep_sight (jelly tentacle) and abyssal_current (oil sac), each with long and strong variants. */
public final class ModPotions
{
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(ForgeRegistries.POTIONS, Abyssia.MODID);

    public static final RegistryObject<Potion> DEEP_SIGHT = POTIONS.register("deep_sight",
            () -> new Potion("deep_sight", new MobEffectInstance(ModMobEffects.DEEP_SIGHT.get(), 3600)));
    public static final RegistryObject<Potion> LONG_DEEP_SIGHT = POTIONS.register("long_deep_sight",
            () -> new Potion("deep_sight", new MobEffectInstance(ModMobEffects.DEEP_SIGHT.get(), 9600)));
    public static final RegistryObject<Potion> STRONG_DEEP_SIGHT = POTIONS.register("strong_deep_sight",
            () -> new Potion("deep_sight", new MobEffectInstance(ModMobEffects.DEEP_SIGHT.get(), 1800, 1)));
    public static final RegistryObject<Potion> ABYSSAL_CURRENT = POTIONS.register("abyssal_current",
            () -> new Potion("abyssal_current", new MobEffectInstance(ModMobEffects.ABYSSAL_CURRENT.get(), 3600)));
    public static final RegistryObject<Potion> LONG_ABYSSAL_CURRENT = POTIONS.register("long_abyssal_current",
            () -> new Potion("abyssal_current", new MobEffectInstance(ModMobEffects.ABYSSAL_CURRENT.get(), 9600)));
    public static final RegistryObject<Potion> STRONG_ABYSSAL_CURRENT = POTIONS.register("strong_abyssal_current",
            () -> new Potion("abyssal_current", new MobEffectInstance(ModMobEffects.ABYSSAL_CURRENT.get(), 1800, 1)));

    private ModPotions() {}

    public static void register(IEventBus bus)
    {
        POTIONS.register(bus);
        bus.addListener(ModPotions::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event)
    {
        event.enqueueWork(() ->
        {
            mixes(DEEP_SIGHT, LONG_DEEP_SIGHT, STRONG_DEEP_SIGHT, ModItems.JELLY_TENTACLE::get);
            mixes(ABYSSAL_CURRENT, LONG_ABYSSAL_CURRENT, STRONG_ABYSSAL_CURRENT, ModItems.OIL_SAC::get);
        });
    }

    private static void mixes(RegistryObject<Potion> base, RegistryObject<Potion> longer, RegistryObject<Potion> strong, Supplier<Item> material)
    {
        BrewingRecipeRegistry.addRecipe(new Mix(() -> Potions.AWKWARD, material, base));
        BrewingRecipeRegistry.addRecipe(new Mix(base, () -> Items.REDSTONE, longer));
        BrewingRecipeRegistry.addRecipe(new Mix(base, () -> Items.GLOWSTONE_DUST, strong));
    }

    /** Vanilla-style potion mix: works on any potion container (bottle, splash, lingering), keeps the container. */
    private record Mix(Supplier<Potion> from, Supplier<Item> ingredient, Supplier<Potion> to) implements IBrewingRecipe
    {
        @Override
        public boolean isInput(ItemStack input)
        {
            return (input.is(Items.POTION) || input.is(Items.SPLASH_POTION) || input.is(Items.LINGERING_POTION))
                    && PotionUtils.getPotion(input) == from.get();
        }

        @Override
        public boolean isIngredient(ItemStack ingredient)
        {
            return ingredient.is(this.ingredient.get());
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack ingredient)
        {
            if (!isInput(input) || !isIngredient(ingredient)) return ItemStack.EMPTY;
            return PotionUtils.setPotion(new ItemStack(input.getItem()), to.get());
        }
    }
}
