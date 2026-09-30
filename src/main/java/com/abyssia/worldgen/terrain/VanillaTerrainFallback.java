package com.abyssia.worldgen.terrain;

import com.abyssia.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Used only when {@code custom_blocks_only} is off: swaps the deep ocean's custom terrain blocks for rough
 * vanilla equivalents right after the surface is built. Sections that cannot contain them are skipped.
 */
public final class VanillaTerrainFallback
{
    private static Map<Block, Block> replacements;

    private VanillaTerrainFallback() {}

    private static Map<Block, Block> replacements()
    {
        if (replacements == null)
        {
            Map<Block, Block> map = new IdentityHashMap<>();
            map.put(ModBlocks.DEEP_SEA_ROCK.get(), Blocks.STONE);
            map.put(ModBlocks.ABYSSAL_ROCK.get(), Blocks.DEEPSLATE);
            map.put(ModBlocks.TRENCH_ROCK.get(), Blocks.DEEPSLATE);
            map.put(ModBlocks.THERMAL_ROCK.get(), Blocks.BLACKSTONE);
            map.put(ModBlocks.VOLCANIC_ROCK.get(), Blocks.BASALT);
            map.put(ModBlocks.MOLTEN_VOLCANIC_ROCK.get(), Blocks.MAGMA_BLOCK);
            map.put(ModBlocks.VOLCANIC_GLASS.get(), Blocks.OBSIDIAN);
            map.put(ModBlocks.CRYSTAL_ROCK.get(), Blocks.CALCITE);
            map.put(ModBlocks.MINERAL_HOST_ROCK.get(), Blocks.ANDESITE);
            map.put(ModBlocks.DEEP_SEDIMENT.get(), Blocks.GRAVEL);
            map.put(ModBlocks.ABYSSAL_MUD.get(), Blocks.MUD);
            map.put(ModBlocks.DEEP_MUD.get(), Blocks.MUD);
            map.put(ModBlocks.MINERAL_SEDIMENT.get(), Blocks.TUFF);
            map.put(ModBlocks.CRYSTAL_SEDIMENT.get(), Blocks.SAND);
            map.put(ModBlocks.ORGANIC_SEDIMENT.get(), Blocks.MUD);
            map.put(ModBlocks.VOLCANIC_ASH.get(), Blocks.GRAVEL);
            map.put(ModBlocks.RUIN_GRAVEL.get(), Blocks.GRAVEL);
            map.put(ModBlocks.RUIN_SEDIMENT.get(), Blocks.SAND);
            map.put(ModBlocks.ANCIENT_MASONRY.get(), Blocks.STONE_BRICKS);
            map.put(ModBlocks.BONE_SEDIMENT.get(), Blocks.SAND);
            map.put(ModBlocks.FOSSIL_SILT.get(), Blocks.CLAY);
            map.put(ModBlocks.FOSSIL_ROCK.get(), Blocks.DIORITE);
            map.put(ModBlocks.SALT_CRUST.get(), Blocks.SAND);
            map.put(ModBlocks.BRINE_SILT.get(), Blocks.CLAY);
            map.put(ModBlocks.SALT_ROCK.get(), Blocks.CALCITE);
            map.put(ModBlocks.LUMEN_SAND.get(), Blocks.SAND);
            map.put(ModBlocks.GLOW_SILT.get(), Blocks.MUD);
            map.put(ModBlocks.LUMEN_ROCK.get(), Blocks.CALCITE);
            map.put(ModBlocks.FROST_SILT.get(), Blocks.SNOW_BLOCK);
            map.put(ModBlocks.ICY_SEDIMENT.get(), Blocks.PACKED_ICE);
            map.put(ModBlocks.FROZEN_ROCK.get(), Blocks.PACKED_ICE);
            map.put(ModBlocks.ABYSSAL_CAVE_ROCK.get(), Blocks.DEEPSLATE);
            map.put(ModBlocks.DARK_CAVE_ROCK.get(), Blocks.COBBLED_DEEPSLATE);
            map.put(ModBlocks.WET_CAVE_ROCK.get(), Blocks.STONE);
            map.put(ModBlocks.LAYERED_CAVE_ROCK.get(), Blocks.TUFF);
            map.put(ModBlocks.MINERAL_CAVE_ROCK.get(), Blocks.ANDESITE);
            map.put(ModBlocks.THERMAL_CAVE_ROCK.get(), Blocks.BLACKSTONE);
            map.put(ModBlocks.CRYSTAL_CAVE_ROCK.get(), Blocks.CALCITE);
            map.put(ModBlocks.ORGANIC_CAVE_ROCK.get(), Blocks.PACKED_MUD);
            map.put(ModBlocks.ERODED_CAVE_ROCK.get(), Blocks.SMOOTH_BASALT);
            map.put(ModBlocks.CAVE_SEDIMENT.get(), Blocks.GRAVEL);
            map.put(ModBlocks.CAVE_MUD.get(), Blocks.MUD);
            replacements = map;
        }
        return replacements;
    }

    public static void apply(ChunkAccess chunk)
    {
        Map<Block, Block> map = replacements();
        for (LevelChunkSection section : chunk.getSections())
        {
            if (section.hasOnlyAir() || !section.maybeHas(state -> map.containsKey(state.getBlock()))) continue;
            for (int y = 0; y < 16; y++)
            {
                for (int z = 0; z < 16; z++)
                {
                    for (int x = 0; x < 16; x++)
                    {
                        Block replacement = map.get(section.getBlockState(x, y, z).getBlock());
                        if (replacement != null) section.setBlockState(x, y, z, replacement.defaultBlockState(), false);
                    }
                }
            }
        }
    }
}
