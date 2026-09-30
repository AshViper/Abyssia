package com.abyssia.fauna;

import com.abyssia.Abyssia;
import com.abyssia.entity.GiantIsopod;
import com.abyssia.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Food falls: an animal that dies in the water leaves a scent of carrion on the seabed below for a few minutes, and
 * scavengers (giant isopods find food by chemoreception) come from well beyond sight to feed on it. Carrion items
 * (tag {@code abyssia:isopod_food}) sink to the seabed instead of floating up the whole water column as vanilla items
 * do. Server-side and transient: nothing is saved, a restart simply clears the tables.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public final class CarrionScent
{
    private static final int LIFETIME = 20 * 60 * 4;
    private static final int MAX_PER_LEVEL = 64;
    private static final int FLOOR_SCAN = 96;
    private static final double SINK_SPEED = 0.04;
    private static final int MAX_SINKING = 256;
    private static final Map<ResourceKey<Level>, List<Scent>> SCENTS = new HashMap<>();
    private static final Map<ResourceKey<Level>, List<ItemEntity>> SINKING = new HashMap<>();

    public static final class Scent
    {
        public final BlockPos pos;
        private final long expires;
        private int portions;

        Scent(BlockPos pos, long expires, int portions)
        {
            this.pos = pos;
            this.expires = expires;
            this.portions = portions;
        }

        public boolean isLeft()
        {
            return this.portions > 0;
        }

        /** One scavenger's meal from it. */
        public void consume()
        {
            --this.portions;
        }
    }

    private CarrionScent() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || dead instanceof Player || dead instanceof GiantIsopod || !dead.isInWater()) return;
        if (!FaunaSpawner.isFaunaLevel(level)) return;
        List<Scent> list = SCENTS.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        if (list.size() >= MAX_PER_LEVEL) list.remove(0);
        // bigger carcasses feed more scavengers; the carcass settles on the seabed below
        int portions = Math.max(1, Math.min(6, (int) Math.ceil(dead.getBbWidth() * dead.getBbHeight() * 6)));
        list.add(new Scent(seabedBelow(level, dead.blockPosition()), level.getGameTime() + LIFETIME, portions));
    }

    private static BlockPos seabedBelow(ServerLevel level, BlockPos from)
    {
        BlockPos.MutableBlockPos p = from.mutable();
        for (int i = 0; i < FLOOR_SCAN && p.getY() > level.getMinBuildHeight(); i++)
        {
            if (!SpawnSite.isWater(level, p.move(Direction.DOWN))) return p.above();
        }
        return from;
    }

    @SubscribeEvent
    public static void onItemJoin(EntityJoinLevelEvent event)
    {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ItemEntity item && item.getItem().is(ModTags.ISOPOD_FOOD)
                && FaunaSpawner.isFaunaLevel(level))
        {
            List<ItemEntity> list = SINKING.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
            if (list.size() < MAX_SINKING) list.add(item);
        }
    }

    /** Runs after the entities have ticked: overrides the items' buoyancy with a slow sink. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        List<ItemEntity> list = SINKING.get(level.dimension());
        if (list == null || list.isEmpty()) return;
        for (Iterator<ItemEntity> it = list.iterator(); it.hasNext(); )
        {
            ItemEntity item = it.next();
            if (item.isRemoved())
            {
                it.remove();
                continue;
            }
            if (item.isInWater() && !item.onGround())
            {
                Vec3 v = item.getDeltaMovement();
                item.setDeltaMovement(v.x * 0.9, Math.max(Math.min(v.y, 0.0) - 0.01, -SINK_SPEED), v.z * 0.9);
            }
        }
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event)
    {
        if (event.getLevel() instanceof ServerLevel level)
        {
            SCENTS.remove(level.dimension());
            SINKING.remove(level.dimension());
        }
    }

    /** The nearest carrion scent within {@code range} blocks that still has food left. */
    @Nullable
    public static Scent nearest(ServerLevel level, BlockPos from, double range)
    {
        List<Scent> list = SCENTS.get(level.dimension());
        if (list == null) return null;
        long now = level.getGameTime();
        Scent best = null;
        double bestDistance = range * range;
        for (Iterator<Scent> it = list.iterator(); it.hasNext(); )
        {
            Scent s = it.next();
            if (s.expires < now || !s.isLeft())
            {
                it.remove();
                continue;
            }
            double d = s.pos.distSqr(from);
            if (d < bestDistance)
            {
                best = s;
                bestDistance = d;
            }
        }
        return best;
    }
}
