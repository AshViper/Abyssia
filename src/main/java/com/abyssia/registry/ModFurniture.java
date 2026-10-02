package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.furniture.LargeLockerBlock;
import com.abyssia.furniture.LargeLockerBlockEntity;
import com.abyssia.furniture.LargeLockerMenu;
import com.abyssia.furniture.LockerPartBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Base furniture (feature H04, inbox/specs/H04-base-equipment.md): the large locker (2x1x2 multiblock, 81 slots).
 * The wall workbench (H05) joins once the industry energy API is ported.
 * Blockstates, models, loot, recipes, tags and names come from tools/furniture_assets.py.
 */
public final class ModFurniture
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Abyssia.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Abyssia.MODID);

    /** No requiresCorrectToolForDrops: breaking any cell must drop the locker the same way (no voided lockers). */
    public static final DeferredBlock<LargeLockerBlock> LARGE_LOCKER = BLOCKS.register("large_locker", () -> new LargeLockerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.5f, 6.0f).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeLockerBlockEntity>> LARGE_LOCKER_ENTITY = BLOCK_ENTITIES.register("large_locker",
            () -> BlockEntityType.Builder.of(LargeLockerBlockEntity::new, LARGE_LOCKER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LockerPartBlockEntity>> LOCKER_PART_ENTITY = BLOCK_ENTITIES.register("large_locker_part",
            () -> BlockEntityType.Builder.of(LockerPartBlockEntity::new, LARGE_LOCKER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<LargeLockerMenu>> LARGE_LOCKER_MENU = MENUS.register("large_locker",
            () -> IMenuTypeExtension.create(LargeLockerMenu::new));

    private ModFurniture() {}

    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(ModFurniture::registerCapabilities);
    }

    /** Block items in the Abyssia tab (called from ModItems.register). */
    public static void registerItems(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab)
    {
        tab.add(items.register("large_locker", () -> new BlockItem(LARGE_LOCKER.get(), new Item.Properties())));
    }

    /** The locker's item handler: the base cell serves it directly, the other cells forward to the base. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LARGE_LOCKER_ENTITY.get(), (be, side) -> be.itemHandler(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LOCKER_PART_ENTITY.get(), (be, side) -> be.itemHandler(side));
    }
}
