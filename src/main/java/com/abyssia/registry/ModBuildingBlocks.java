package com.abyssia.registry;

import com.abyssia.block.StrippableLogBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.function.Supplier;

/**
 * Building blocks: a vanilla-style family for each main terrain rock, stairs / slabs / walls of vent and cave rock,
 * and a wood set from the ancient deep-sea plant's stem. Assets come from tools/building_assets.py (models, recipes,
 * tags) and tools/forge_textures.py (textures).
 */
public final class ModBuildingBlocks
{
    /** Stairs, slab and wall made of one block. */
    public record Shapes(DeferredBlock<Block> stairs, DeferredBlock<Block> slab, DeferredBlock<Block> wall) {}

    /** A rock's building family, like vanilla deepslate's: raw shapes, polished, bricks, cracked bricks and chiseled. */
    public record StoneFamily(Shapes shapes, DeferredBlock<Block> polished, Shapes polishedShapes, DeferredBlock<Block> bricks,
                              Shapes brickShapes, DeferredBlock<Block> crackedBricks, DeferredBlock<Block> chiseled) {}

    /** A metal crust's building family: polished and bricks with their shapes. */
    public record CrustFamily(DeferredBlock<Block> polished, Shapes polishedShapes, DeferredBlock<Block> bricks, Shapes brickShapes) {}

    // ---------- Stone families of the main terrain rocks ----------
    public static final StoneFamily DEEP_SEA_ROCK = family("deep_sea_rock", ModBlocks.DEEP_SEA_ROCK);
    public static final StoneFamily ABYSSAL_ROCK = family("abyssal_rock", ModBlocks.ABYSSAL_ROCK);
    public static final StoneFamily TRENCH_ROCK = family("trench_rock", ModBlocks.TRENCH_ROCK);
    public static final StoneFamily THERMAL_ROCK = family("thermal_rock", ModBlocks.THERMAL_ROCK);
    public static final StoneFamily VOLCANIC_ROCK = family("volcanic_rock", ModBlocks.VOLCANIC_ROCK);
    public static final StoneFamily CRYSTAL_ROCK = family("crystal_rock", ModBlocks.CRYSTAL_ROCK);
    public static final StoneFamily MINERAL_HOST_ROCK = family("mineral_host_rock", ModBlocks.MINERAL_HOST_ROCK);

    // ---------- Vent and cave rock: stairs, slabs and walls ----------
    public static final Shapes VENT_ROCK = shapes("vent_rock", ModBlocks.VENT_ROCK);
    public static final Shapes BLACK_VENT_ROCK = shapes("black_vent_rock", ModBlocks.BLACK_VENT_ROCK);
    public static final Shapes SULFUR_VENT_ROCK = shapes("sulfur_vent_rock", ModBlocks.SULFUR_VENT_ROCK);
    public static final Shapes MINERAL_VENT_ROCK = shapes("mineral_vent_rock", ModBlocks.MINERAL_VENT_ROCK);
    public static final Shapes ABYSSAL_CAVE_ROCK = shapes("abyssal_cave_rock", ModBlocks.ABYSSAL_CAVE_ROCK);
    public static final Shapes DARK_CAVE_ROCK = shapes("dark_cave_rock", ModBlocks.DARK_CAVE_ROCK);
    public static final Shapes WET_CAVE_ROCK = shapes("wet_cave_rock", ModBlocks.WET_CAVE_ROCK);
    public static final Shapes LAYERED_CAVE_ROCK = shapes("layered_cave_rock", ModBlocks.LAYERED_CAVE_ROCK);
    public static final Shapes MINERAL_CAVE_ROCK = shapes("mineral_cave_rock", ModBlocks.MINERAL_CAVE_ROCK);
    public static final Shapes ORGANIC_CAVE_ROCK = shapes("organic_cave_rock", ModBlocks.ORGANIC_CAVE_ROCK);
    public static final Shapes ERODED_CAVE_ROCK = shapes("eroded_cave_rock", ModBlocks.ERODED_CAVE_ROCK);

    // ---------- Cobbled rocks: stairs, slabs and walls ----------
    public static final Shapes COBBLED_DEEP_SEA_ROCK = shapes("cobbled_deep_sea_rock", ModBlocks.COBBLED_DEEP_SEA_ROCK);
    public static final Shapes COBBLED_ABYSSAL_ROCK = shapes("cobbled_abyssal_rock", ModBlocks.COBBLED_ABYSSAL_ROCK);
    public static final Shapes COBBLED_TRENCH_ROCK = shapes("cobbled_trench_rock", ModBlocks.COBBLED_TRENCH_ROCK);
    public static final Shapes COBBLED_THERMAL_ROCK = shapes("cobbled_thermal_rock", ModBlocks.COBBLED_THERMAL_ROCK);
    public static final Shapes COBBLED_VOLCANIC_ROCK = shapes("cobbled_volcanic_rock", ModBlocks.COBBLED_VOLCANIC_ROCK);
    public static final Shapes COBBLED_CRYSTAL_ROCK = shapes("cobbled_crystal_rock", ModBlocks.COBBLED_CRYSTAL_ROCK);
    public static final Shapes COBBLED_MINERAL_HOST_ROCK = shapes("cobbled_mineral_host_rock", ModBlocks.COBBLED_MINERAL_HOST_ROCK);

    // ---------- Metal crusts: polished (reusing the crust art) and bricks, each with stairs / slab / wall ----------
    public static final CrustFamily MANGANESE_CRUST = crustFamily("manganese_crust", ModBlocks.MANGANESE_CRUST);
    public static final CrustFamily COBALT_CRUST = crustFamily("cobalt_crust", ModBlocks.COBALT_CRUST);
    public static final CrustFamily NICKEL_CRUST = crustFamily("nickel_crust", ModBlocks.NICKEL_CRUST);
    public static final CrustFamily IRON_CRUST = crustFamily("iron_crust", ModBlocks.IRON_CRUST);
    public static final CrustFamily COPPER_CRUST = crustFamily("copper_crust", ModBlocks.COPPER_CRUST);

    // ---------- Ancient deep-sea wood (the stem itself is ModBlocks.ANCIENT_STEM) ----------
    // Like the nether's stems it does not burn; it sounds like nether wood and reuses the crimson door / gate sounds.
    public static final DeferredBlock<Block> STRIPPED_ANCIENT_STEM = ModBlocks.block("stripped_ancient_stem",
            () -> new RotatedPillarBlock(stem()));
    public static final DeferredBlock<Block> STRIPPED_ANCIENT_WOOD = ModBlocks.block("stripped_ancient_wood",
            () -> new RotatedPillarBlock(stem()));
    public static final DeferredBlock<Block> ANCIENT_WOOD = ModBlocks.block("ancient_wood",
            () -> new StrippableLogBlock(stem(), STRIPPED_ANCIENT_WOOD));
    public static final DeferredBlock<Block> ANCIENT_PLANKS = ModBlocks.block("ancient_planks",
            () -> new Block(wood().strength(2.0f, 3.0f).sound(SoundType.NETHER_WOOD)));
    public static final DeferredBlock<Block> ANCIENT_STAIRS = ModBlocks.block("ancient_stairs",
            () -> new StairBlock(ANCIENT_PLANKS.get().defaultBlockState(), copy(ANCIENT_PLANKS)));
    public static final DeferredBlock<Block> ANCIENT_SLAB = ModBlocks.block("ancient_slab", () -> new SlabBlock(copy(ANCIENT_PLANKS)));
    public static final DeferredBlock<Block> ANCIENT_FENCE = ModBlocks.block("ancient_fence",
            () -> new FenceBlock(copy(ANCIENT_PLANKS).forceSolidOn()));
    public static final DeferredBlock<Block> ANCIENT_FENCE_GATE = ModBlocks.block("ancient_fence_gate",
            () -> new FenceGateBlock(WoodType.CRIMSON, copy(ANCIENT_PLANKS).forceSolidOn()));
    public static final DeferredBlock<Block> ANCIENT_DOOR = door("ancient_door",
            () -> new DoorBlock(BlockSetType.CRIMSON, wood().strength(3.0f).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<Block> ANCIENT_TRAPDOOR = ModBlocks.block("ancient_trapdoor",
            () -> new TrapDoorBlock(BlockSetType.CRIMSON,
                    wood().strength(3.0f).noOcclusion().isValidSpawn((state, level, pos, type) -> false)));
    public static final DeferredBlock<Block> ANCIENT_PRESSURE_PLATE = ModBlocks.block("ancient_pressure_plate",
            () -> new PressurePlateBlock(BlockSetType.CRIMSON, wood().forceSolidOn().noCollission()
                    .strength(0.5f).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<Block> ANCIENT_BUTTON = ModBlocks.block("ancient_button",
            () -> new ButtonBlock(BlockSetType.CRIMSON, 30,
                    BlockBehaviour.Properties.of().noCollission().strength(0.5f).pushReaction(PushReaction.DESTROY)));

    private ModBuildingBlocks() {}

    /** Loads the class, which registers everything above; called before the block registry fires. */
    static void init() {}

    private static StoneFamily family(String rock, DeferredBlock<Block> base)
    {
        Shapes shapes = shapes(rock, base);
        DeferredBlock<Block> polished = ModBlocks.block("polished_" + rock, () -> new Block(copy(base)));
        Shapes polishedShapes = shapes("polished_" + rock, polished);
        DeferredBlock<Block> bricks = ModBlocks.block(rock + "_bricks", () -> new Block(copy(base)));
        Shapes brickShapes = shapes(rock + "_brick", bricks);
        DeferredBlock<Block> cracked = ModBlocks.block("cracked_" + rock + "_bricks", () -> new Block(copy(base)));
        DeferredBlock<Block> chiseled = ModBlocks.block("chiseled_" + rock, () -> new Block(copy(base)));
        return new StoneFamily(shapes, polished, polishedShapes, bricks, brickShapes, cracked, chiseled);
    }

    private static CrustFamily crustFamily(String crust, DeferredBlock<Block> base)
    {
        DeferredBlock<Block> polished = ModBlocks.block("polished_" + crust, () -> new Block(copy(base)));
        DeferredBlock<Block> bricks = ModBlocks.block(crust + "_bricks", () -> new Block(copy(base)));
        return new CrustFamily(polished, shapes("polished_" + crust, polished), bricks, shapes(crust + "_brick", bricks));
    }

    private static Shapes shapes(String name, DeferredBlock<Block> base)
    {
        return new Shapes(
                ModBlocks.block(name + "_stairs", () -> new StairBlock(base.get().defaultBlockState(), copy(base))),
                ModBlocks.block(name + "_slab", () -> new SlabBlock(copy(base))),
                ModBlocks.block(name + "_wall", () -> new WallBlock(copy(base).forceSolidOn())));
    }

    private static BlockBehaviour.Properties copy(DeferredBlock<Block> block)
    {
        return BlockBehaviour.Properties.ofFullCopy(block.get());
    }

    private static BlockBehaviour.Properties wood()
    {
        return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_CYAN);
    }

    private static BlockBehaviour.Properties stem()
    {
        return wood().strength(2.5f).sound(SoundType.STEM);
    }

    private static DeferredBlock<Block> door(String name, Supplier<Block> factory)
    {
        DeferredBlock<Block> block = ModBlocks.BLOCKS.register(name, factory);
        ModItems.doubleHighBlockItem(name, block);
        return block;
    }
}
