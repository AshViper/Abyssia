package com.abyssia.habitat.upkeep;

import com.abyssia.Abyssia;
import com.abyssia.registry.AbyssiaTab;
import com.abyssia.registry.ModItems;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** BAS01 habitat upkeep: the hull patch kit and the hull reinforcement plate. Assets: tools/habitat_upkeep_assets.py. */
public final class UpkeepContent
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Abyssia.MODID);

    public static final DeferredItem<Item> HULL_PATCH_KIT = ITEMS.register("hull_patch_kit",
            () -> new HullPatchKitItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> HULL_REINFORCEMENT = ITEMS.register("hull_reinforcement",
            () -> new HullReinforcementItem(new Item.Properties().stacksTo(16)));

    private UpkeepContent() {}

    public static void register(IEventBus bus)
    {
        ITEMS.register(bus);
        bus.addListener(UpkeepContent::onTabContents);
    }

    private static void onTabContents(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey().equals(ModItems.tabKey(AbyssiaTab.EQUIPMENT)))
        {
            event.accept(HULL_PATCH_KIT.get());
            event.accept(HULL_REINFORCEMENT.get());
        }
    }
}
