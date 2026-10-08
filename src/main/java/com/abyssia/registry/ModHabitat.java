package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatConnectedBlock;
import com.abyssia.habitat.HabitatConstructorItem;
import com.abyssia.habitat.HabitatDoorBlock;
import com.abyssia.habitat.HabitatHatchBlock;
import com.abyssia.habitat.HabitatLightBlock;
import com.abyssia.habitat.HabitatSupportBlock;
import com.abyssia.habitat.HabitatWindowBlock;
import com.abyssia.habitat.scan.ScanConsoleBlock;
import com.abyssia.habitat.scan.ScanConsoleBlockEntity;
import com.abyssia.habitat.scan.ScanConsoleMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Deep-sea habitat modules (feature H01, inbox/specs/H01-habitat-modules.md): the constructor item and the blocks it
 * places. The blocks have no block items and no loot tables (they drop nothing).
 * Blockstates, models, recipe, tags and names come from tools/habitat_assets.py.
 */
public final class ModHabitat
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyssia.MODID);

    public static final RegistryObject<Block> FLOOR = BLOCKS.register("habitat_floor", () -> new HabitatConnectedBlock(metal(5.0f)));
    public static final RegistryObject<Block> TRIM = BLOCKS.register("habitat_trim", () -> new HabitatConnectedBlock(metal(6.0f)));
    public static final RegistryObject<Block> WALL = BLOCKS.register("habitat_wall", () -> new HabitatConnectedBlock(metal(6.0f)));
    public static final RegistryObject<Block> CEILING = BLOCKS.register("habitat_ceiling", () -> new HabitatConnectedBlock(metal(5.0f)));
    public static final RegistryObject<Block> WINDOW = BLOCKS.register("habitat_window", () -> new HabitatWindowBlock(metal(3.0f)
            .noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).isValidSpawn((s, l, p, e) -> false)));
    public static final RegistryObject<Block> LIGHT = BLOCKS.register("habitat_light", () -> new HabitatLightBlock(metal(3.0f).lightLevel(s -> s.getValue(HabitatLightBlock.LIT) ? 15 : 0)));
    public static final RegistryObject<Block> DOOR_FRAME = BLOCKS.register("habitat_door_frame", () -> new Block(metal(6.0f)));
    public static final RegistryObject<Block> HATCH = BLOCKS.register("habitat_hatch", () -> new HabitatHatchBlock(metal(6.0f)));
    public static final RegistryObject<Block> DOOR = BLOCKS.register("habitat_door", () -> new HabitatDoorBlock(metal(6.0f).noOcclusion()));
    /** H09 support legs under floating modules */
    public static final RegistryObject<Block> SUPPORT = BLOCKS.register("habitat_support", () -> new HabitatSupportBlock(metal(5.0f).noOcclusion()));

    // H07 scan room console (block entity + terminal menu; still no block item / loot)
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Abyssia.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Abyssia.MODID);
    public static final RegistryObject<Block> SCAN_CONSOLE = BLOCKS.register("scan_console",
            () -> new ScanConsoleBlock(metal(5.0f).noOcclusion().lightLevel(s -> 7)));
    public static final RegistryObject<BlockEntityType<ScanConsoleBlockEntity>> SCAN_CONSOLE_ENTITY = BLOCK_ENTITIES.register("scan_console",
            () -> BlockEntityType.Builder.of(ScanConsoleBlockEntity::new, SCAN_CONSOLE.get()).build(null));
    public static final RegistryObject<MenuType<ScanConsoleMenu>> SCAN_CONSOLE_MENU = MENUS.register("scan_console",
            () -> IForgeMenuType.create(ScanConsoleMenu::new));

    public static RegistryObject<Item> CONSTRUCTOR;

    private ModHabitat() {}

    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }

    /** The constructor in the Abyssia tab (called from ModItems.register like ModIndustry). */
    public static void registerItems(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab)
    {
        CONSTRUCTOR = items.register("habitat_constructor", () -> new HabitatConstructorItem(new Item.Properties().stacksTo(1)));
        tab.add(CONSTRUCTOR);
    }

    private static BlockBehaviour.Properties metal(float hardness)
    {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(hardness, 6.0f)
                .requiresCorrectToolForDrops().sound(SoundType.METAL);
    }
}
