package com.abyssia.fauna;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.entity.LureBearer;
import com.abyssia.entity.DeepSeaSwimmer;
import com.abyssia.registry.ModTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * The point of a lure: small fish (tag {@code abyssia:lured_by_light}) in dark water drift toward a lit lure nearby
 * (an anglerfish's esca, a viperfish's dorsal ray), where the predator strikes. Not every fish takes the bait, and one that gave up ignores lures for
 * a while, so a lure thins a school rather than emptying the sea.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class LureAttraction
{
    private LureAttraction() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event)
    {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof PathfinderMob fish) || !fish.getType().is(ModTags.LURED_BY_LIGHT)) return;
        fish.goalSelector.addGoal(3, new SwimToLureGoal(fish));
    }

    private static final class SwimToLureGoal extends Goal
    {
        private static final double RANGE = 10.0;
        private final PathfinderMob fish;
        @Nullable
        private LureBearer angler;
        private int cooldown;
        private int ticks;

        SwimToLureGoal(PathfinderMob fish)
        {
            this.fish = fish;
            this.cooldown = fish.getRandom().nextInt(200);
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public boolean canUse()
        {
            if (this.cooldown > 0)
            {
                --this.cooldown;
                return false;
            }
            if (!Config.LURE_ATTRACTION.get() || !this.fish.isInWater() || this.fish.getRandom().nextInt(10) != 0 || !DeepSeaSwimmer.isFairPrey(this.fish)) return false;
            // a lure only stands out in the dark
            if (this.fish.level().getMaxLocalRawBrightness(this.fish.blockPosition()) > 6) return false;
            List<Mob> near = this.fish.level().getEntitiesOfClass(Mob.class, this.fish.getBoundingBox().inflate(RANGE),
                    m -> m instanceof LureBearer lure && lure.isLureLit() && !lure.isDigesting());
            LureBearer best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Mob m : near)
            {
                double d = m.distanceToSqr(this.fish);
                if (d < bestDistance)
                {
                    best = (LureBearer) m;
                    bestDistance = d;
                }
            }
            if (best == null) return false;
            if (this.fish.getRandom().nextFloat() > 0.3F)
            {
                this.cooldown = 200 + this.fish.getRandom().nextInt(200);
                return false;
            }
            this.angler = best;
            return true;
        }

        @Override
        public void start()
        {
            this.ticks = 0;
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.angler != null && this.angler.isLureLit() && !this.angler.isDigesting() && this.ticks < 300;
        }

        @Override
        public void tick()
        {
            if (this.ticks++ % 10 != 0) return;
            Vec3 lure = this.angler.lurePosition();
            // hover just below the light, where the jaws are
            this.fish.getNavigation().moveTo(lure.x, lure.y - 0.3, lure.z, 1.0);
        }

        @Override
        public void stop()
        {
            this.angler = null;
            this.fish.getNavigation().stop();
            this.cooldown = 600 + this.fish.getRandom().nextInt(600);
        }
    }
}
