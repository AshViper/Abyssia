package com.abyssia.item;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/** TRP01 Heart Tank: a diving tank (3D suit model) with an always-on enchantment glint (1.20.1 has no glint-override component). */
public final class HeartTankItem extends ArmorItem
{
    public HeartTankItem(ArmorMaterial material, Type type, Properties props) { super(material, type, props); }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer)
    { com.abyssia.client.armor.DivingSuitClient.init(consumer); }
}
