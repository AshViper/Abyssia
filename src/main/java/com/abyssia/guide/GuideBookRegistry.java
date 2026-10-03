package com.abyssia.guide;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** GB01 guide book: own DeferredRegister so ModItems stays untouched; the item joins the Abyssia tab by event. */
public final class GuideBookRegistry
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Abyssia.MODID);

    public static final DeferredItem<Item> ABYSS_GUIDE_BOOK = ITEMS.register("abyss_guide_book",
            () -> new GuideBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private GuideBookRegistry() {}

    public static void register(IEventBus modBus)
    {
        ITEMS.register(modBus);
        modBus.addListener(GuideBookRegistry::onTabContents);
    }

    private static void onTabContents(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) event.accept(ABYSS_GUIDE_BOOK.get());
    }
}
