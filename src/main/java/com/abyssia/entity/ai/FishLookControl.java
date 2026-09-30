package com.abyssia.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;

/**
 * A fish has no neck: looking at something turns the whole body (yaw and pitch) at a limited rate. When nothing
 * is being looked at and the fish is not swimming anywhere, it slowly levels out.
 */
public class FishLookControl extends LookControl
{
    private final float turnSpeed;

    public FishLookControl(Mob mob, float turnSpeed)
    {
        super(mob);
        this.turnSpeed = turnSpeed;
    }

    @Override
    public void tick()
    {
        if (this.lookAtCooldown > 0)
        {
            --this.lookAtCooldown;
            this.getYRotD().ifPresent(yaw -> {
                float rot = this.rotateTowards(this.mob.getYRot(), yaw, this.turnSpeed);
                this.mob.setYRot(rot);
                this.mob.yBodyRot = rot;
                this.mob.yHeadRot = rot;
            });
            this.getXRotD().ifPresent(pitch -> this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), pitch, this.turnSpeed * 0.5F)));
        }
        else
        {
            if (this.mob.getNavigation().isDone()) this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), 0.0F, 1.5F));
            this.mob.yHeadRot = this.mob.yBodyRot;
        }
    }
}
