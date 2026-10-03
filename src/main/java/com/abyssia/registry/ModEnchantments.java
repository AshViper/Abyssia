package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.enchantment.AbyssalFocusEnchantment;
import com.abyssia.enchantment.DeepSwimmerEnchantment;
import com.abyssia.enchantment.ThermalCatchEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** EN01 enchantments. */
public final class ModEnchantments
{
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Abyssia.MODID);

    public static final RegistryObject<Enchantment> DEEP_SWIMMER = ENCHANTMENTS.register("deep_swimmer", DeepSwimmerEnchantment::new);
    public static final RegistryObject<Enchantment> THERMAL_CATCH = ENCHANTMENTS.register("thermal_catch", ThermalCatchEnchantment::new);
    public static final RegistryObject<Enchantment> ABYSSAL_FOCUS = ENCHANTMENTS.register("abyssal_focus", AbyssalFocusEnchantment::new);

    private ModEnchantments() {}

    public static void register(IEventBus bus)
    {
        ENCHANTMENTS.register(bus);
    }
}
