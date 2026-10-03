package com.abyssia.worldgen.cave;

import com.abyssia.block.CaveMossBlock;
import com.abyssia.block.HangingPlantBlock;
import com.abyssia.block.SeafloorCarpetBlock;
import com.abyssia.block.SpeleothemBlock;
import com.abyssia.block.StackingPlantBlock;
import com.abyssia.block.UnderwaterPlantBlock;
import com.abyssia.block.WallPlantBlock;
import com.abyssia.registry.ModTags;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;

/**
 * Places one plant, crystal or speleothem the way its block attaches: stacking plants grow up in columns, hanging
 * plants down, wall plants face out of their wall, moss covers every touching face, clusters point away from their
 * support. Columns stop at the first block that is not open water, so nothing cuts into rock or pokes out of a lake.
 * Survival rules are checked against the chunk directly (support faces, plant-inhibiting minerals).
 */
final class CavePlacer
{
    private CavePlacer() {}

    /**
     * Places {@code entry} at an open block whose support lies in direction {@code support} (DOWN for a floor, UP
     * for a ceiling, a horizontal direction for a wall). {@code roll} (0..1) picks the column height.
     * Returns false if this kind of plant cannot go there.
     */
    static boolean plant(CaveChunk ctx, int lx, int y, int lz, CaveEnvironment.PlantEntry entry, Direction support, double roll, int heightCap)
    {
        if (y > entry.maxY()) return false;
        BlockState state = entry.state();
        Block block = state.getBlock();
        boolean water = ctx.water(lx, y, lz);
        if (block instanceof AmethystClusterBlock) return cluster(ctx, lx, y, lz, state, support);
        if (!water) return false;
        BlockState ground = ctx.get(lx + support.getStepX(), y + support.getStepY(), lz + support.getStepZ());
        int height = Math.min(heightCap, entry.minHeight() + Mth.floor(roll * (entry.maxHeight() - entry.minHeight() + 1)));
        if (block instanceof StackingPlantBlock stacking)
        {
            if (support != Direction.DOWN || !ctx.sturdy(lx, y - 1, lz, Direction.UP)) return false;
            if (!stacking.growsOnAnything() && ground.is(ModTags.INHIBITS_PLANTS)) return false;
            column(ctx, lx, y, lz, state, Direction.UP, height, StackingPlantBlock.TOP);
            return true;
        }
        if (block instanceof HangingPlantBlock)
        {
            if (support != Direction.UP || !ctx.sturdy(lx, y + 1, lz, Direction.DOWN) || ground.is(ModTags.INHIBITS_PLANTS)) return false;
            column(ctx, lx, y, lz, state, Direction.DOWN, height, HangingPlantBlock.TIP);
            return true;
        }
        if (block instanceof WallPlantBlock)
        {
            if (support.getAxis().isVertical() || ground.is(ModTags.INHIBITS_PLANTS)) return false;
            if (!ctx.sturdy(lx + support.getStepX(), y, lz + support.getStepZ(), support.getOpposite())) return false;
            ctx.set(lx, y, lz, state.setValue(WallPlantBlock.FACING, support.getOpposite()));
            return true;
        }
        if (block instanceof CaveMossBlock moss) return moss(ctx, lx, y, lz, moss);
        if (support != Direction.DOWN || !ctx.sturdy(lx, y - 1, lz, Direction.UP)) return false;
        if (block instanceof SeafloorCarpetBlock carpet && !carpet.growsOnAnything() && ground.is(ModTags.INHIBITS_PLANTS)) return false;
        if (block instanceof UnderwaterPlantBlock && ground.is(ModTags.INHIBITS_PLANTS)) return false;
        ctx.set(lx, y, lz, state);
        return true;
    }

    /** A column of {@code height} blocks from (lx, y, lz) toward {@code grow}; {@code end} marks its last block. */
    private static void column(CaveChunk ctx, int lx, int y, int lz, BlockState state, Direction grow,
                               int height, net.minecraft.world.level.block.state.properties.BooleanProperty end)
    {
        int n = 0;
        while (n < height && ctx.water(lx, y + grow.getStepY() * n, lz) && ctx.inField(lx, y + grow.getStepY() * n, lz)) n++;
        for (int i = 0; i < n; i++) ctx.set(lx, y + grow.getStepY() * i, lz, state.setValue(end, i == n - 1));
    }

    /** Moss film on every sturdy face touching this block (floor, walls and ceiling at once). */
    static boolean moss(CaveChunk ctx, int lx, int y, int lz, CaveMossBlock moss)
    {
        if (!ctx.water(lx, y, lz)) return false;
        BlockState state = moss.defaultBlockState();
        boolean any = false;
        for (Direction d : Direction.values())
        {
            int nx = lx + d.getStepX(), ny = y + d.getStepY(), nz = lz + d.getStepZ();
            if (ctx.sturdy(nx, ny, nz, d.getOpposite()) && !ctx.get(nx, ny, nz).is(ModTags.INHIBITS_PLANTS))
            {
                state = state.setValue(MultifaceBlock.getFaceProperty(d), true);
                any = true;
            }
        }
        if (any) ctx.set(lx, y, lz, state);
        return any;
    }

    /** A crystal cluster pointing away from its support; waterlogged unless it grows in a lake's gas pocket. */
    static boolean cluster(CaveChunk ctx, int lx, int y, int lz, BlockState state, Direction support)
    {
        if (!ctx.open(lx, y, lz)) return false;
        if (!ctx.sturdy(lx + support.getStepX(), y + support.getStepY(), lz + support.getStepZ(), support.getOpposite())) return false;
        if (state.hasProperty(AmethystClusterBlock.FACING)) state = state.setValue(AmethystClusterBlock.FACING, support.getOpposite());
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) state = state.setValue(BlockStateProperties.WATERLOGGED, ctx.water(lx, y, lz));
        else if (!ctx.water(lx, y, lz)) return false;
        ctx.set(lx, y, lz, state);
        return true;
    }

    /**
     * A stalactite (tip DOWN, hanging from the block above) or stalagmite (tip UP) of {@code length} segments from
     * (lx, y, lz), thickest at its root. With {@code merge} its tip is a merged tip meeting another one.
     */
    static void speleothem(CaveChunk ctx, int lx, int y, int lz, BlockState material, Direction tip, int length, boolean merge)
    {
        if (length <= 0) return;
        int step = tip.getStepY();
        for (int i = 0; i < length; i++)
        {
            int py = y + step * i;
            if (!ctx.open(lx, py, lz)) return;
            BlockState state = material;
            if (material.getBlock() instanceof SpeleothemBlock speleothem)
            {
                int fromTip = length - 1 - i;
                DripstoneThickness thickness = fromTip == 0 ? (merge ? DripstoneThickness.TIP_MERGE : DripstoneThickness.TIP)
                        : fromTip == 1 ? DripstoneThickness.FRUSTUM : i == 0 ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
                state = speleothem.segment(tip, thickness, ctx.water(lx, py, lz));
            }
            else if (!ctx.water(lx, py, lz) && !material.getFluidState().isEmpty())
            {
                return;
            }
            ctx.set(lx, py, lz, state);
        }
    }
}
