package com.abyssia.habitat.build;

import com.abyssia.Abyssia;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** BT01 build menu tabs, in menu order. Lang key {@code habitat.abyssia.category.<id>}. */
public enum BuildCategory
{
    MODULE("module"),
    EQUIPMENT("equipment"),
    POWER("power"),
    CUSTOMIZE("customize"),
    UPGRADE("upgrade"),
    BREEDING("breeding");

    public final String id;

    BuildCategory(String id)
    {
        this.id = id;
    }

    public MutableComponent displayName()
    {
        return Component.translatable("habitat." + Abyssia.MODID + ".category." + id);
    }
}
