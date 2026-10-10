package com.abyssia.item;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/** Plain diving armor piece (helmet / tank) that shows the 3D diving-suit model; the tier follows the material. */
public final class DivingArmorItem extends ArmorItem
{
    public DivingArmorItem(ArmorMaterial material, Type type, Properties props) { super(material, type, props); }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer)
    { com.abyssia.client.armor.DivingSuitClient.init(consumer); }
}
