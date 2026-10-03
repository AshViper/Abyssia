package com.abyssia.registry;

import com.abyssia.Abyssia;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * EN01 enchantments. In 1.21 enchantments are data-driven: definitions live in data/abyssia/enchantment/*.json
 * (deep_swimmer's swim bonus is an attributes effect there); this class only holds the keys the Java logic reads.
 */
public final class ModEnchantments
{
    public static final ResourceKey<Enchantment> DEEP_SWIMMER = key("deep_swimmer");
    public static final ResourceKey<Enchantment> THERMAL_CATCH = key("thermal_catch");
    public static final ResourceKey<Enchantment> ABYSSAL_FOCUS = key("abyssal_focus");

    private ModEnchantments() {}

    private static ResourceKey<Enchantment> key(String name)
    {
        return ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name));
    }

    /** Level of an Abyssia enchantment on a stack; 0 when absent (or a datapack removed the enchantment). */
    public static int level(Level level, ResourceKey<Enchantment> key, ItemStack stack)
    {
        if (stack.isEmpty()) return 0;
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(key)
                .map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack))
                .orElse(0);
    }
}
