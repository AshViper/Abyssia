package com.abyssia.furniture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/**
 * The four cells of the large locker, seen from the front: bottom-left is the base (holds the block entity), the
 * others sit to its right (the viewer's right = facing counter-clockwise) and / or above it.
 */
public enum LockerPart implements StringRepresentable
{
    BL("bl", false, false),
    BR("br", true, false),
    TL("tl", false, true),
    TR("tr", true, true);

    private final String name;
    public final boolean right;
    public final boolean top;

    LockerPart(String name, boolean right, boolean top)
    {
        this.name = name;
        this.right = right;
        this.top = top;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }

    /** Position of this part for a locker whose base (bottom-left) is at base. */
    public BlockPos from(BlockPos base, Direction facing)
    {
        BlockPos pos = right ? base.relative(facing.getCounterClockWise()) : base;
        return top ? pos.above() : pos;
    }

    /** Position of the base for this part at pos. */
    public BlockPos base(BlockPos pos, Direction facing)
    {
        BlockPos base = right ? pos.relative(facing.getClockWise()) : pos;
        return top ? base.below() : base;
    }

    /** Left and right swapped (mirroring). */
    public LockerPart mirrored()
    {
        return switch (this)
        {
            case BL -> BR;
            case BR -> BL;
            case TL -> TR;
            case TR -> TL;
        };
    }
}
