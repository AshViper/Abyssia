package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatConstructorItem;
import com.abyssia.habitat.HabitatDoorBlock;
import com.abyssia.habitat.HabitatHatchBlock;
import com.abyssia.habitat.HabitatSupportBlock;
import com.abyssia.habitat.HabitatWindowBlock;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Deep-sea habitat modules (feature H01, inbox/specs/H01-habitat-modules.md): the constructor item and the blocks it
 * places. The blocks have no block items and no loot tables (they drop nothing).
 * Blockstates, models, recipe, tags and names come from tools/habitat_assets.py.
 */
public final class ModHabitat
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);

    public static final DeferredBlock<Block> FLOOR = BLOCKS.register("habitat_floor", () -> new Block(metal(5.0f)));
    public static final DeferredBlock<Block> TRIM = BLOCKS.register("habitat_trim", () -> new Block(metal(6.0f)));
    public static final DeferredBlock<Block> WALL = BLOCKS.register("habitat_wall", () -> new Block(metal(6.0f)));
    public static final DeferredBlock<Block> CEILING = BLOCKS.register("habitat_ceiling", () -> new Block(metal(5.0f)));
    public static final DeferredBlock<Block> WINDOW = BLOCKS.register("habitat_window", () -> new HabitatWindowBlock(metal(3.0f)
            .noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).isValidSpawn((s, l, p, e) -> false)));
    public static final DeferredBlock<Block> LIGHT = BLOCKS.register("habitat_light", () -> new Block(metal(3.0f).lightLevel(s -> 15)));
    public static final DeferredBlock<Block> DOOR_FRAME = BLOCKS.register("habitat_door_frame", () -> new Block(metal(6.0f)));
    public static final DeferredBlock<Block> HATCH = BLOCKS.register("habitat_hatch", () -> new HabitatHatchBlock(metal(6.0f)));
    public static final DeferredBlock<Block> DOOR = BLOCKS.register("habitat_door", () -> new HabitatDoorBlock(metal(6.0f).noOcclusion()));
    /** H09 support legs under floating modules */
    public static final DeferredBlock<Block> SUPPORT = BLOCKS.register("habitat_support", () -> new HabitatSupportBlock(metal(5.0f).noOcclusion()));

    // H07 scan room console. TODO(NeoForge port, scan/): the real ScanConsoleBlock + block entity + menu need the
    // industry energy classes; until they are ported this is a plain placeholder block so the scan room still builds
    // and the minecraft:impermeable tag entry resolves. Swap in ScanConsoleBlock, SCAN_CONSOLE_ENTITY and
    // SCAN_CONSOLE_MENU (BLOCK_ENTITIES / MENUS registers) when scan/ is ported.
    public static final DeferredBlock<Block> SCAN_CONSOLE = BLOCKS.register("scan_console",
            () -> new Block(metal(5.0f).noOcclusion().lightLevel(s -> 7)));

    // NeoForge: the constructor's module / rotation live in data components (Forge 1.20 kept them in NBT "Mode" / "Rot";
    // 1.20 stacks are not migrated, a converted constructor falls back to the foundation and the player's facing).
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Abyssia.MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> HABITAT_MODE = DATA_COMPONENTS
            .registerComponentType("habitat_mode", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> HABITAT_ROT = DATA_COMPONENTS
            .registerComponentType("habitat_rot", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static DeferredItem<Item> CONSTRUCTOR;

    private ModHabitat() {}

    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
        DATA_COMPONENTS.register(modBus);
    }

    /** The constructor in the Abyssia tab (called from ModItems.register like ModTools). */
    public static void registerItems(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab)
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
