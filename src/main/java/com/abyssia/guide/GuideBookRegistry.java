package com.abyssia.guide;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** GB01 guide book: own DeferredRegister so ModItems stays untouched; the item joins the Abyssia tab by event. */
public final class GuideBookRegistry
{
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Abyssia.MODID);

    public static final RegistryObject<Item> ABYSS_GUIDE_BOOK = ITEMS.register("abyss_guide_book",
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
