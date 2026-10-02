package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.furniture.LargeLockerBlock;
import com.abyssia.furniture.LargeLockerBlockEntity;
import com.abyssia.furniture.LargeLockerMenu;
import com.abyssia.furniture.LockerPartBlockEntity;
import com.abyssia.furniture.WallWorkbenchBlock;
import com.abyssia.furniture.WallWorkbenchBlockEntity;
import com.abyssia.furniture.WallWorkbenchMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Base furniture (features H04 / H05, inbox/specs/H04-base-equipment.md): the large locker (2x1x2 multiblock, 81
 * slots) and the wall-mounted workbench (crafting + FE charge slot).
 * Blockstates, models, loot, recipes, tags and names come from tools/furniture_assets.py.
 */
public final class ModFurniture
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Abyssia.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Abyssia.MODID);

    /** No requiresCorrectToolForDrops: breaking any cell must drop the locker the same way (no voided lockers). */
    public static final RegistryObject<LargeLockerBlock> LARGE_LOCKER = BLOCKS.register("large_locker", () -> new LargeLockerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.5f, 6.0f).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<WallWorkbenchBlock> WALL_WORKBENCH = BLOCKS.register("wall_workbench", () -> new WallWorkbenchBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)
                    .lightLevel(s -> s.getValue(WallWorkbenchBlock.POWERED) ? 5 : 0)));

    public static final RegistryObject<BlockEntityType<LargeLockerBlockEntity>> LARGE_LOCKER_ENTITY = BLOCK_ENTITIES.register("large_locker",
            () -> BlockEntityType.Builder.of(LargeLockerBlockEntity::new, LARGE_LOCKER.get()).build(null));
    public static final RegistryObject<BlockEntityType<LockerPartBlockEntity>> LOCKER_PART_ENTITY = BLOCK_ENTITIES.register("large_locker_part",
            () -> BlockEntityType.Builder.of(LockerPartBlockEntity::new, LARGE_LOCKER.get()).build(null));
    public static final RegistryObject<BlockEntityType<WallWorkbenchBlockEntity>> WALL_WORKBENCH_ENTITY = BLOCK_ENTITIES.register("wall_workbench",
            () -> BlockEntityType.Builder.of(WallWorkbenchBlockEntity::new, WALL_WORKBENCH.get()).build(null));
    public static final RegistryObject<MenuType<WallWorkbenchMenu>> WALL_WORKBENCH_MENU = MENUS.register("wall_workbench",
            () -> IForgeMenuType.create(WallWorkbenchMenu::new));
    public static final RegistryObject<MenuType<LargeLockerMenu>> LARGE_LOCKER_MENU = MENUS.register("large_locker",
            () -> IForgeMenuType.create(LargeLockerMenu::new));

    private ModFurniture() {}

    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }

    /** Block items in the Abyssia tab (called from ModItems.register like ModIndustry). */
    public static void registerItems(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab)
    {
        tab.add(items.register("large_locker", () -> new BlockItem(LARGE_LOCKER.get(), new Item.Properties())));
        tab.add(items.register("wall_workbench", () -> new BlockItem(WALL_WORKBENCH.get(), new Item.Properties())));
    }
}
