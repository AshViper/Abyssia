package com.abyssia.entity.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Steers a swimmer toward its navigation target by turning (limited degrees per tick, slower while turning hard)
 * and thrusting along its heading. Unlike vanilla's smooth swimming control the thrust is linear in speed:
 * {@code acceleration = speed modifier * movement speed * thrust}, which with {@link com.abyssia.entity.DeepSeaSwimmer}'s
 * water drag gives a top speed of {@code acceleration / (1 - drag)} - easy to tune for slow deep-sea animals.
 */
public class SwimMoveControl extends MoveControl
{
    private final int maxTurnPitch;
    private final int maxTurnYaw;
    private final float thrust;

    public SwimMoveControl(Mob mob, int maxTurnPitch, int maxTurnYaw, float thrust)
    {
        super(mob);
        this.maxTurnPitch = maxTurnPitch;
        this.maxTurnYaw = maxTurnYaw;
        this.thrust = thrust;
    }

    @Override
    public void tick()
    {
        if (this.operation != Operation.MOVE_TO || this.mob.getNavigation().isDone())
        {
            this.mob.setSpeed(0.0F);
            this.mob.setXxa(0.0F);
            this.mob.setYya(0.0F);
            this.mob.setZza(0.0F);
            return;
        }
        double dx = this.wantedX - this.mob.getX();
        double dy = this.wantedY - this.mob.getY();
        double dz = this.wantedZ - this.mob.getZ();
        if (dx * dx + dy * dy + dz * dz < 2.5E-7)
        {
            this.mob.setZza(0.0F);
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, this.maxTurnYaw));
        this.mob.yBodyRot = this.mob.getYRot();
        this.mob.yHeadRot = this.mob.getYRot();
        float speed = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)) * this.thrust;
        if (!this.mob.isInWater())
        {
            this.mob.setSpeed(speed * 0.1F);
            return;
        }
        float pitch = -(float) (Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        pitch = Mth.clamp(Mth.wrapDegrees(pitch), -this.maxTurnPitch, this.maxTurnPitch);
        this.mob.setXRot(this.rotlerp(this.mob.getXRot(), pitch, 4.0F));
        // arc through hard turns instead of skidding sideways
        float turning = Math.abs(Mth.wrapDegrees(this.mob.getYRot() - yaw));
        this.mob.setSpeed(speed * (1.0F - Mth.clamp((turning - 20.0F) / 90.0F, 0.0F, 0.8F)));
        // a unit input along the heading: the travel step accelerates by exactly the speed set above
        this.mob.zza = Mth.cos(this.mob.getXRot() * Mth.DEG_TO_RAD);
        this.mob.yya = -Mth.sin(this.mob.getXRot() * Mth.DEG_TO_RAD);
    }
}
