package com.abyssia.habitat.generator;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * BT01d multiblock generators: controller blocks (block entity + renderer), the shared invisible part block, and the
 * three POWER build entries. No block items, no loot (removed only by dismantling; unbreakable in survival).
 * Assets: tools/bt01/generator_assets.py.
 */
public final class ModGenerators
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Abyssia.MODID);

    public static final DeferredBlock<Block> PART = BLOCKS.register("generator_part", () -> new GeneratorPartBlock(props()));
    public static final DeferredBlock<Block> CURRENT_TURBINE = BLOCKS.register(GeneratorKind.CURRENT_TURBINE.id,
            () -> new GeneratorBlock(GeneratorKind.CURRENT_TURBINE, props()));
    public static final DeferredBlock<Block> GEOTHERMAL = BLOCKS.register(GeneratorKind.GEOTHERMAL.id,
            () -> new GeneratorBlock(GeneratorKind.GEOTHERMAL, props().lightLevel(s -> 9)));
    public static final DeferredBlock<Block> BIOFUEL = BLOCKS.register(GeneratorKind.BIOFUEL.id,
            () -> new GeneratorBlock(GeneratorKind.BIOFUEL, props().lightLevel(s -> 6)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorBlockEntity>> GENERATOR_ENTITY = BLOCK_ENTITIES.register("generator",
            () -> BlockEntityType.Builder.of(GeneratorBlockEntity::new, CURRENT_TURBINE.get(), GEOTHERMAL.get(), BIOFUEL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorPartBlockEntity>> PART_ENTITY = BLOCK_ENTITIES.register("generator_part",
            () -> BlockEntityType.Builder.of(GeneratorPartBlockEntity::new, PART.get()).build(null));

    private ModGenerators() {}

    /** BuildContent anchor BT01d */
    public static void register(IEventBus bus)
    {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(ModGenerators::registerCapabilities);
        for (GeneratorKind kind : GeneratorKind.values()) BuildRegistry.register(new GeneratorEntry(kind));
    }

    /** NeoForge block capability (Forge: BlockEntity#getCapability): biofuel fuel slot on the controller and every part (hoppers / pipes). */
    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, GENERATOR_ENTITY.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PART_ENTITY.get(), (be, side) -> be.itemHandler());
    }

    private static BlockBehaviour.Properties props()
    {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0f, 3_600_000.0f).noLootTable()
                .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)
                .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).isValidSpawn((s, l, p, e) -> false);
    }
}
