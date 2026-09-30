package com.abyssia.entity;

import com.abyssia.entity.ai.ScoredSwimGoal;
import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * オハラエビ / Alvinocaris longirostris, a vent shrimp of the Okinawa Trough (and of seeps in Sagami Bay), 930-1736 m.
 * Pale and nearly eyeless, it swarms over chimneys and vent rock, grazing on the chemosynthetic bacteria there, and
 * flicks away from anything that comes close. It stays with its vent field. No light of its own.
 */
public class VentShrimp extends DeepSeaShrimp
{
    public VentShrimp(EntityType<? extends VentShrimp> type, Level level)
    {
        super(type, level);
    }

    @Override
    protected ModSounds.Voice voice()
    {
        return ModSounds.OHARA_SHRIMP;
    }

    @Override
    protected boolean spews()
    {
        return false;
    }

    @Override
    protected int homeRadius()
    {
        return 8;
    }

    /** Close over vent rock, where the bacteria grow. */
    @Override
    protected double spotScore(BlockPos pos)
    {
        int floor = ScoredSwimGoal.floorDistance(this.level(), pos, 6);
        boolean vent = this.level().getBlockState(pos.below(floor + 1)).is(ModTags.FAUNA_VENT);
        return -floor * 0.5 + (vent ? 3.0 : 0.0);
    }
}
