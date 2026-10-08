package com.abyssia.registry;

import com.abyssia.Abyssia;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * WRK01: the wreck core, the part of a seabed wreck (WreckFormation) the lidar scanner analyses. Unbreakable and
 * dropless in survival, faintly lit so it can be spotted from afar. Assets: tools/wreck_assets.py.
 */
public final class ModWrecks
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Abyssia.MODID);

    public static final DeferredBlock<Block> WRECK_CORE = BLOCKS.register("wreck_core", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE).strength(-1.0f, 3_600_000.0f).sound(SoundType.METAL).lightLevel(s -> 8)
            .noLootTable()));
    public static final DeferredItem<Item> WRECK_CORE_ITEM = ITEMS.register("wreck_core", () -> new BlockItem(WRECK_CORE.get(), new Item.Properties()));

    private ModWrecks() {}

    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) ->
        {
            if (e.getTabKey().equals(ModItems.TAB.getKey())) e.accept(WRECK_CORE_ITEM);
        });
    }
}
