package com.abyssia.entity.giant.ability;

import com.abyssia.entity.giant.GiantBrain;
import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.vehicle.Submarine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Lantern grazer (friendly): a submarine with a player aboard within 48 blocks gets an escort: the animal swims 12 to 20
 * blocks beside it for up to 2400 ticks, then keeps away for 2400 ticks. While escorting it drops a lantern baleen every 1200 to
 * 2400 ticks (nothing while the item is not registered) and the crew keep night vision (there is no point light in the client
 * light code that a submarine could use, so the light is this effect).
 */
final class LanternGrazerAbility extends GiantAbility
{
    static final int RADIUS = 48, MAX = 2400, REST = 2400;
    @Nullable
    private UUID escort;
    private long start, restUntil, nextDrop;
    private double side = 1, gap = 16;

    LanternGrazerAbility(GiantFauna g) { super(g); }

    @Override
    public void tick(long now, GiantBrain.State state, @Nullable Entity target)
    {
        if (escort == null)
        {
            if (now < restUntil || g.tickCount % 10 != 0) return;
            Vec3 h = g.headPos();
            Submarine best = null;
            double bd = RADIUS * RADIUS;
            for (Submarine s : g.level().getEntitiesOfClass(Submarine.class, g.getBoundingBox().inflate(RADIUS), s -> s.isAlive() && s.getControllingPassenger() instanceof Player))
            {
                double d = s.position().distanceToSqr(h);
                if (d < bd) { bd = d; best = s; }
            }
            if (best == null) return;
            escort = best.getUUID();
            start = now;
            side = g.getRandom().nextBoolean() ? 1 : -1;
            gap = 12 + g.getRandom().nextInt(9);
            nextDrop = now + 1200 + g.getRandom().nextInt(1201);
            return;
        }
        Submarine s = sub(escort);
        if (s == null || !(s.getControllingPassenger() instanceof Player) || now - start >= MAX || s.position().distanceToSqr(g.headPos()) > 96 * 96)
        {
            escort = null;
            restUntil = now + REST;
            return;
        }
        if (g.tickCount % 100 == 0 && s.getControllingPassenger() != null)
            for (Entity e : s.getPassengers())
                if (e instanceof Player p) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
        if (now >= nextDrop)
        {
            nextDrop = now + 1200 + g.getRandom().nextInt(1201);
            Item baleen = item("lantern_baleen");
            if (baleen != null)
            {
                Vec3 h = g.headPos();
                g.level().addFreshEntity(new ItemEntity(g.level(), h.x, h.y, h.z, new ItemStack(baleen)));
            }
        }
    }

    @Nullable
    private Vec3 station()
    {
        Submarine s = escort != null ? sub(escort) : null;
        return s == null ? null : center(s).add(left(s).scale(side * gap));
    }

    @Override
    public Vec3 steer(GiantBrain.State state, Vec3 head, @Nullable Entity target, Vec3 dir)
    {
        Vec3 st = station();
        if (st == null) return dir;
        Vec3 to = st.subtract(head);
        return to.lengthSqr() > 1.0E-4 ? to.normalize() : dir;
    }

    @Override
    public double speedMul(GiantBrain.State state, int stateTicks, @Nullable Entity target)
    {
        Vec3 st = station();
        Submarine s = escort != null ? sub(escort) : null;
        if (st == null || s == null) return 1.0;
        // match the hull's pace, faster when behind the station
        double want = s.getDeltaMovement().length() + g.headPos().distanceTo(st) * 0.02;
        return Math.max(0.3, Math.min(4.0, want / g.spec().speed()));
    }

    @Override
    public double turnRate(GiantBrain.State state, double def)
    {
        return escort != null ? 0.08 : def;
    }

    @Override
    public void save(CompoundTag tag)
    {
        tag.putLong("EscortRestUntil", restUntil);
    }

    @Override
    public void load(CompoundTag tag)
    {
        restUntil = tag.getLong("EscortRestUntil");
    }
}
