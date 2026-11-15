package com.abyssia.entity.boss;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;

/** An abyss mother hitbox: a bell part (tentacle -1, width 8; {@code core} = the weak point under the bell) or one of 3 near the tip of a tentacle (width 3). */
public class MotherPart extends BossPart
{
    private final int tentacle;
    private final boolean core;
    private final EntityDimensions size;

    public MotherPart(AbyssBoss parent, int index, int tentacle, boolean core, float width)
    {
        super(parent, index);
        this.tentacle = tentacle;
        this.core = core;
        this.size = EntityDimensions.scalable(width, width);
        this.refreshDimensions();
    }

    /** Tentacle number 0..11, or -1 for a bell part. */
    public int tentacle() { return tentacle; }

    public boolean core() { return core; }

    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        return size != null ? size : super.getDimensions(pose);
    }
}
