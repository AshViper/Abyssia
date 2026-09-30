package com.abyssia.entity;

import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * ラブカ / frilled shark (Chlamydoselachus anguineus). An eel-like, primitive shark of the outer shelf and upper slope
 * (usually 120-1280 m, Suruga and Sagami Bays among the best-known places), with six pairs of frilled gill slits and
 * rows of three-pronged teeth that hook soft prey. About 60 % of its diet is squid, which it is thought to take in a
 * snake-like lunge from a bent body; it may rise into shallower water at night. Neutral.
 */
public class FrilledShark extends DeepSeaShark
{
    private int snapTicks = -1;

    public FrilledShark(EntityType<? extends FrilledShark> type, Level level)
    {
        super(type, level, ModSounds.FRILLED_SHARK, 18, 4, 0.0035F);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0).add(Attributes.FOLLOW_RANGE, 14.0);
    }

    @Override
    protected double cruiseHeight()
    {
        return 2.0;
    }

    @Override
    protected double cruiseScore(BlockPos pos)
    {
        return super.cruiseScore(pos) * (this.isNightAbove() ? 0.5 : 1.0) + this.migration(pos, 0.25);
    }

    @Override
    protected TagKey<EntityType<?>> prey()
    {
        return ModTags.FRILLED_SHARK_PREY;
    }

    @Override
    protected double mouthForward()
    {
        return 0.55;
    }

    /** The lunge: jaws flung wide, then shut on the prey. */
    @Override
    protected void onAction(int action)
    {
        if (action != ACTION_BITE) return;
        this.animations().play("mouth_open", 0);
        this.snapTicks = 0;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide && this.snapTicks >= 0 && ++this.snapTicks == 5)
        {
            this.animations().stop("mouth_open");
            this.animations().play("mouth_close", 9);
            this.snapTicks = -1;
        }
    }
}
