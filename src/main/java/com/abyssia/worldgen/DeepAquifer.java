package com.abyssia.worldgen;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * PERF06: vanilla's aquifer with a short cut through the deep layer. Under the deep layer's rock ceiling the fluid is
 * fixed (floodedness 1.0 there: every aquifer holds the global fluid, the generator's picker), yet vanilla still looks
 * up the three nearest aquifer centres for every open block, and the deep sea is almost all open blocks: 7.5% of the
 * default world's generation. Below {@link #TOP} the answer is taken from the picker at once, as a disabled aquifer
 * gives it; above it vanilla's aquifer is asked as before. TOP keeps two aquifer cells (12 blocks each) of margin
 * under the ceiling, so no block whose nearest centres could lie above the deep layer is short-cut.
 */
final class DeepAquifer implements Aquifer
{
    private static final int TOP = DeepLayer.CEILING_BOTTOM_Y - 24;
    @Nullable
    private static final Field FIELD = find();

    private final Aquifer vanilla;
    private final Aquifer.FluidPicker picker;
    private boolean deep;

    private DeepAquifer(Aquifer vanilla, Aquifer.FluidPicker picker)
    {
        this.vanilla = vanilla;
        this.picker = picker;
    }

    /** NoiseChunk's aquifer field, found by its type (its name differs between loaders and mappings). */
    @Nullable
    private static Field find()
    {
        try
        {
            for (Field f : NoiseChunk.class.getDeclaredFields())
            {
                if (f.getType() != Aquifer.class) continue;
                f.setAccessible(true);
                return f;
            }
        }
        catch (RuntimeException e)
        {
            // no access: generation runs on vanilla's aquifer alone
        }
        return null;
    }

    /** Puts the short cut in front of the noise chunk's aquifer (once; a no-op where the field cannot be reached). */
    static void install(NoiseChunk noiseChunk, Aquifer.FluidPicker picker)
    {
        if (FIELD == null) return;
        try
        {
            Aquifer current = (Aquifer) FIELD.get(noiseChunk);
            if (!(current instanceof DeepAquifer)) FIELD.set(noiseChunk, new DeepAquifer(current, picker));
        }
        catch (IllegalAccessException | RuntimeException e)
        {
            // left as it was
        }
    }

    @Nullable
    @Override
    public BlockState computeSubstance(DensityFunction.FunctionContext context, double substance)
    {
        int y = context.blockY();
        deep = y < TOP;
        if (!deep) return vanilla.computeSubstance(context, substance);
        return substance > 0.0 ? null : picker.computeFluid(context.blockX(), y, context.blockZ()).at(y);
    }

    @Override
    public boolean shouldScheduleFluidUpdate()
    {
        // deep water is one still body: no fluid ticks (fillFromNoise drops the deep marks anyway)
        return !deep && vanilla.shouldScheduleFluidUpdate();
    }
}
