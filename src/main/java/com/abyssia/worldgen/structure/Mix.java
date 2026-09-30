package com.abyssia.worldgen.structure;

import com.abyssia.worldgen.cave.Palette;
import com.mojang.serialization.Codec;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A weighted block mix in structure data ({@code [{"data": <state>, "weight": n}, ...]}), flattened once at load
 * for hash-driven picks.
 */
public record Mix(SimpleWeightedRandomList<BlockState> list, Palette<BlockState> palette)
{
    public static final Codec<Mix> CODEC = SimpleWeightedRandomList.wrappedCodecAllowingEmpty(BlockState.CODEC)
            .xmap(l -> new Mix(l, Palette.of(l)), Mix::list);
    public static final Mix EMPTY = new Mix(SimpleWeightedRandomList.empty(), Palette.of(SimpleWeightedRandomList.empty()));

    public boolean isEmpty()
    {
        return palette.isEmpty();
    }

    public BlockState pick(double r)
    {
        return palette.pick(r);
    }
}
