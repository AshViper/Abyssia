package com.abyssia.habitat.build;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Block work of a timed build: {@code steps} spread over the duration (bottom-up), then on the last tick {@code air}
 * cells become air and {@code water} cells water sources. Air / water cells must stay replaceable (water) until then.
 */
public record BuildLayout(List<BuildStep> steps, List<BlockPos> air, List<BlockPos> water)
{
    public static BuildLayout of(List<BuildStep> steps)
    {
        return new BuildLayout(steps, List.of(), List.of());
    }
}
