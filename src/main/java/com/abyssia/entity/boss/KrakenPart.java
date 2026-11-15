package com.abyssia.entity.boss;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;

/** A kraken hitbox: a body part (tentacle -1, width 6) or one of the 4 near the tip of a tentacle (width 4). A severed tentacle's parts switch off. */
public class KrakenPart extends BossPart
{
    private final int tentacle;
    private final EntityDimensions size;
    boolean active = true;

    public KrakenPart(AbyssBoss parent, int index, int tentacle, float width)
    {
        super(parent, index);
        this.tentacle = tentacle;
        this.size = EntityDimensions.scalable(width, width);
        this.refreshDimensions();
    }

    /** Tentacle number 0..7, or -1 for a body part. */
    public int tentacle() { return tentacle; }

    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        return size != null ? size : super.getDimensions(pose);
    }

    @Override
    public boolean isPickable() { return active; }
}
