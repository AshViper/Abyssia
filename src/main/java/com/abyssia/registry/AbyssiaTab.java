package com.abyssia.registry;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;

/** The Abyssia creative tabs; each registration site adds its items to one of them (registration order = tab order). */
public enum AbyssiaTab
{
    NATURE("nature"),
    BUILDING("building"),
    MATERIALS("materials"),
    FOOD("food"),
    EQUIPMENT("equipment"),
    MACHINES("machines");

    public final String id;
    private final List<DeferredItem<? extends Item>> items = new ArrayList<>();

    AbyssiaTab(String id)
    {
        this.id = id;
    }

    public List<DeferredItem<? extends Item>> items()
    {
        return items;
    }
}
