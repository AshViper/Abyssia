package com.abyssia.entity;

import com.abyssia.registry.ModSounds;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A deep-sea shark configured by data (cruise height above the floor, prey tag, mouth position): the behaviour of
 * {@link DeepSeaShark} without a class per species. Voice kinds: ambient, bite, hurt, death, flop.
 */
public class GenericShark extends DeepSeaShark
{
    private final double cruiseHeight;
    private final TagKey<EntityType<?>> prey;
    private final double mouthForward;

    public GenericShark(EntityType<? extends GenericShark> type, Level level, ModSounds.Voice voice, int maxTurnPitch, int maxTurnYaw, float thrust,
                        double cruiseHeight, TagKey<EntityType<?>> prey, double mouthForward)
    {
        super(type, level, voice, maxTurnPitch, maxTurnYaw, thrust);
        this.cruiseHeight = cruiseHeight;
        this.prey = prey;
        this.mouthForward = mouthForward;
        // Mob's constructor calls registerGoals() before these fields are set: register DeepSeaShark's goals now
        if (!level.isClientSide) super.registerGoals();
    }

    public static AttributeSupplier.Builder attributes(double health, double attack, double armor)
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.ARMOR, armor).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.ATTACK_DAMAGE, attack).add(Attributes.FOLLOW_RANGE, 16.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void registerGoals()
    {
        // see the constructor
    }

    @Override
    protected double cruiseHeight()
    {
        return this.cruiseHeight;
    }

    @Override
    protected TagKey<EntityType<?>> prey()
    {
        return this.prey;
    }

    @Override
    protected double mouthForward()
    {
        return this.mouthForward;
    }

    @Override
    protected void onAction(int action)
    {
        if (action == ACTION_BITE) this.animations().play("bite", 15);
    }
}
