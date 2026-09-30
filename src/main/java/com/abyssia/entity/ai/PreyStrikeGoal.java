package com.abyssia.entity.ai;

import com.abyssia.entity.DeepSeaSwimmer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An ambush strike: small prey (an entity type tag) within reach and in front of the mouth is struck in one short
 * lunge and swallowed whole - it leaves nothing behind, no drops, no scent. The strike itself (sound, the animation
 * event) and what happens after a meal are the predator's.
 */
public class PreyStrikeGoal extends Goal
{
    private final Mob predator;
    private final TagKey<EntityType<?>> prey;
    private final double reach;
    private final double lunge;
    private final BooleanSupplier ready;
    private final Supplier<Vec3> mouth;
    private final Runnable onStrike;
    private final Consumer<Mob> onEat;
    private final boolean pull;
    @Nullable
    private Mob target;
    private int ticks;

    /**
     * @param reach    how far from the mouth prey can be struck (blocks)
     * @param lunge    lunge speed toward the prey (blocks/tick)
     * @param ready    whether the predator hunts right now (not sated, not fleeing...)
     * @param onStrike plays the strike (sound, entity event for the jaw animation)
     * @param onEat    after the prey is swallowed (heal, digest...)
     */
    public PreyStrikeGoal(Mob predator, TagKey<EntityType<?>> prey, double reach, double lunge, BooleanSupplier ready, Supplier<Vec3> mouth,
                          Runnable onStrike, Consumer<Mob> onEat)
    {
        this(predator, prey, reach, lunge, false, ready, mouth, onStrike, onEat);
    }

    /**
     * @param pull the prey is seized at reach and hauled to the mouth (a squid's tentacles) instead of the predator
     *             lunging at it; {@code lunge} is then the haul speed
     */
    public PreyStrikeGoal(Mob predator, TagKey<EntityType<?>> prey, double reach, double lunge, boolean pull, BooleanSupplier ready,
                          Supplier<Vec3> mouth, Runnable onStrike, Consumer<Mob> onEat)
    {
        this.pull = pull;
        this.predator = predator;
        this.prey = prey;
        this.reach = reach;
        this.lunge = lunge;
        this.ready = ready;
        this.mouth = mouth;
        this.onStrike = onStrike;
        this.onEat = onEat;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public boolean canUse()
    {
        if (!this.predator.isInWater() || this.predator.getRandom().nextInt(2) != 0 || !this.ready.getAsBoolean()) return false;
        Vec3 m = this.mouth.get();
        Vec3 look = this.predator.getLookAngle();
        List<Mob> near = this.predator.level().getEntitiesOfClass(Mob.class, this.predator.getBoundingBox().inflate(this.reach),
                e -> e != this.predator && e.getType().is(this.prey) && DeepSeaSwimmer.isFairPrey(e));
        Mob best = null;
        double bestDistance = this.reach * this.reach;
        for (Mob e : near)
        {
            Vec3 to = centre(e).subtract(m);
            double d = to.lengthSqr();
            if (d < bestDistance && to.normalize().dot(look) > 0.2)
            {
                best = e;
                bestDistance = d;
            }
        }
        this.target = best;
        return best != null;
    }

    private static Vec3 centre(Mob e)
    {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    @Override
    public void start()
    {
        this.ticks = 0;
        this.predator.getNavigation().stop();
        this.onStrike.run();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.ticks < (this.pull ? 16 : 8) && this.target != null && this.target.isAlive();
    }

    @Override
    public void tick()
    {
        // ticked between canContinueToUse checks as well: the prey may already be swallowed
        if (this.target == null) return;
        ++this.ticks;
        Vec3 t = centre(this.target);
        this.predator.getLookControl().setLookAt(t.x, t.y, t.z, 60.0F, 60.0F);
        Vec3 to = t.subtract(this.mouth.get());
        if (this.pull) this.target.setDeltaMovement(to.normalize().scale(-this.lunge));
        else if (this.ticks <= 3) this.predator.setDeltaMovement(to.normalize().scale(this.lunge));
        if (to.lengthSqr() < 0.8 * 0.8)
        {
            if (this.predator.level() instanceof ServerLevel server)
            {
                server.sendParticles(ParticleTypes.BUBBLE, t.x, t.y, t.z, 8, 0.15, 0.15, 0.15, 0.02);
            }
            Mob eaten = this.target;
            this.target = null;
            eaten.discard();
            this.predator.gameEvent(GameEvent.EAT);
            this.onEat.accept(eaten);
        }
    }

    @Override
    public void stop()
    {
        this.target = null;
    }
}
