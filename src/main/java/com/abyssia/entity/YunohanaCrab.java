package com.abyssia.entity;

import com.abyssia.entity.ai.ClawWarningGoal;
import com.abyssia.entity.ai.RetaliateGoal;
import com.abyssia.entity.ai.ScavengeItemsGoal;
import com.abyssia.entity.ai.SeabedStrollGoal;
import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * ユノハナガニ / Gandalfus yunohana, the vent crab of the Izu-Ogasawara arc (Myojin Knoll, Suiyo, Kaikata and Nikko
 * seamounts, 420-1400 m). Pale and nearly white, with reduced eyes, a hairy squarish carapace and long legs. Like its
 * relatives (Bythograeidae) it roams the vent field as scavenger and opportunistic predator (game simplification:
 * it picks at the bacterial film on vent rock and scavenges carrion). It warns intruders with raised claws and
 * pinches whoever crowds or attacks it - neutral.
 */
public class YunohanaCrab extends BenthicWalker
{
    private static final int ACTION_SNAP = 0;
    private int fedCooldown;

    public YunohanaCrab(EntityType<? extends YunohanaCrab> type, Level level)
    {
        super(type, level, "walk");
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.ARMOR, 4.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.FOLLOW_RANGE, 12.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new RetaliateGoal(this, 1.3, 0.5, 200, target -> {
            this.snap();
            this.pinch(target);
        }));
        this.goalSelector.addGoal(1, new ClawWarningGoal(this, 2.5, this::snap, player -> {
            this.snap();
            if (!player.getAbilities().invulnerable) this.pinch(player);
        }));
        this.goalSelector.addGoal(2, new ScavengeItemsGoal(this, ModTags.ISOPOD_FOOD, 10.0, 1.2, () -> this.fedCooldown <= 0, item -> {
            this.snap();
            this.heal(2.0F);
            this.fedCooldown = 1200 + this.random.nextInt(1200);
        }));
        // wanders the vent field, keeping to the warm rock where the bacteria grow
        this.goalSelector.addGoal(4, new SeabedStrollGoal(this, 60, 1.0, 6, 3, () -> true,
                pos -> this.level().getBlockState(pos.below()).is(ModTags.FAUNA_VENT) ? 2.0 : 0.0));
    }

    @Override
    protected int homeRadius()
    {
        return 12;
    }

    private void snap()
    {
        this.broadcastAction(ACTION_SNAP);
        this.playSound(ModSounds.YUNOHANA_CRAB.get("snap"), 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
    }

    @Override
    protected void onAction(int action)
    {
        if (action == ACTION_SNAP) this.animations().play("claw_snap", 12);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!this.level().isClientSide && this.fedCooldown > 0) --this.fedCooldown;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.YUNOHANA_CRAB.get("ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 400;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.YUNOHANA_CRAB.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.YUNOHANA_CRAB.get("death");
    }

    @Override
    protected SoundEvent getSwimSound()
    {
        return ModSounds.YUNOHANA_CRAB.get("step");
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.45F;
    }
}
