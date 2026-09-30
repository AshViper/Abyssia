package com.abyssia.entity;

import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * ミツクリザメ / goblin shark (Mitsukurina owstoni). A flabby, slow, pinkish shark of the upper continental slope
 * (usually 270-960 m) that cruises close to the bottom, sensing prey with the electroreceptors of its blade-like
 * snout. Its jaws shoot forward like a slingshot (filmed at 3.1 m/s) to take rattails, dragonfishes, squid and
 * crustaceans. Neutral.
 */
public class GoblinShark extends DeepSeaShark
{
    public GoblinShark(EntityType<? extends GoblinShark> type, Level level)
    {
        super(type, level, ModSounds.GOBLIN_SHARK, 12, 3, 0.003F);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ARMOR, 2.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0).add(Attributes.FOLLOW_RANGE, 16.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected double cruiseHeight()
    {
        return 3.0;
    }

    @Override
    protected TagKey<EntityType<?>> prey()
    {
        return ModTags.GOBLIN_SHARK_PREY;
    }

    @Override
    protected double mouthForward()
    {
        return 0.9;
    }

    /** The slingshot: the jaws fly forward out from under the snout and snap. */
    @Override
    protected void onAction(int action)
    {
        if (action == ACTION_BITE) this.animations().play("bite", 15);
    }
}
