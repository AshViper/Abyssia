package com.abyssia.entity.ai;

import com.abyssia.worldgen.DeepLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import java.util.Optional;

/**
 * The layer a mod animal belongs to, decided once from where it first is (spawn, or load for older saves) and saved,
 * so a deep animal pushed above the bedrock band into a rift shaft keeps to the deep layer instead of swimming up into
 * the ocean world (and the other way round). Mobs hold one and expose it through {@link Bound}.
 */
public final class HomeLayer
{
    /** NBT key of the saved layer (true = deep layer). */
    public static final String KEY = "HomeDeep";

    private boolean decided;
    private boolean deep;

    /** Implemented by the mod's animals that wander with {@link ScoredSwimGoal} or {@link SeabedRandomPos}. */
    public interface Bound
    {
        HomeLayer homeLayer();
    }

    /** True if the animal's home is the deep layer; decided at the first call from its current position. */
    public boolean isDeep(Entity mob)
    {
        if (!this.decided)
        {
            this.deep = DeepLayer.isDeep(mob.level(), mob.getY());
            this.decided = true;
        }
        return this.deep;
    }

    public void save(CompoundTag tag)
    {
        if (this.decided) tag.putBoolean(KEY, this.deep);
    }

    public void load(CompoundTag tag)
    {
        if (tag.contains(KEY))
        {
            this.deep = tag.getBoolean(KEY);
            this.decided = true;
        }
    }

    /** The layer of a mob: its saved home, or (not one of ours) the layer of its current Y. */
    public static boolean of(Entity mob)
    {
        return mob instanceof Bound bound ? bound.homeLayer().isDeep(mob) : DeepLayer.isDeep(mob.level(), mob.getY());
    }

    /** The saved "Home" position: the 1.21 int-array form, or the older {X,Y,Z} compound of 1.20 saves. */
    public static Optional<BlockPos> readHome(CompoundTag tag)
    {
        if (tag.contains("Home", Tag.TAG_COMPOUND))
        {
            CompoundTag c = tag.getCompound("Home");
            return Optional.of(new BlockPos(c.getInt("X"), c.getInt("Y"), c.getInt("Z")));
        }
        return NbtUtils.readBlockPos(tag, "Home");
    }
}
