package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.environment.CurrentData;
import com.abyssia.environment.NaturalCurrents;
import com.abyssia.environment.OceanCurrentManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SUB03 deep-sea sonar (server, every sonar_interval ticks while someone is aboard): sea life within sonar_range
 * (count, nearest distance, a bubble ping for each newly detected one), the current at the hull (the existing
 * OceanCurrentManager / NaturalCurrents values, the stronger of the two) and the nearest submarine dock within
 * sonar_dock_range (block entities of the chunks in reach only). Arrows are relative to the hull's heading
 * (up = ahead). No block / ore scan.
 */
public final class SubmarineSonar
{
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String KEY = "message." + Abyssia.MODID + ".submarine.sonar";

    private SubmarineSonar() {}

    /** the HUD suffix; {@code seen} keeps the creatures already reported (pinged once per entry into range) */
    public static Component scan(Submarine sub, Set<UUID> seen)
    {
        ServerLevel level = (ServerLevel) sub.level();
        Vec3 c = sub.getBoundingBox().getCenter();
        double range = Config.SUB_SONAR_RANGE.get();

        // sea life
        List<LivingEntity> life = level.getEntitiesOfClass(LivingEntity.class, sub.getBoundingBox().inflate(range),
                e -> e.isAlive() && !(e instanceof Player) && isSeaLife(e) && e.distanceToSqr(c) <= range * range);
        Set<UUID> now = new HashSet<>();
        double nearest = Double.MAX_VALUE;
        boolean ping = false;
        for (LivingEntity e : life)
        {
            now.add(e.getUUID());
            if (!seen.contains(e.getUUID())) ping = true;
            nearest = Math.min(nearest, Math.sqrt(e.distanceToSqr(c)));
        }
        seen.retainAll(now);
        seen.addAll(now);
        if (ping)
        {
            level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 8, 1.6, 0.8, 1.6, 0.02);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 0.3f, 1.6f);
        }
        MutableComponent line = Component.translatable(KEY + ".prefix");
        String count = life.size() >= 10 ? "10+" : Integer.toString(life.size());
        line.append(life.isEmpty() ? Component.translatable(KEY + ".life_none")
                : Component.translatable(KEY + ".life", count, Math.round(nearest)));

        // current
        Vec3 flow = current(level, c);
        double speed = Math.sqrt(flow.x * flow.x + flow.z * flow.z);
        if (speed < 1.0e-3) line.append(Component.translatable(KEY + ".current_none"));
        else
        {
            String arrow = arrow(sub.getYRot(), flow.x, flow.z);
            String tier = speed >= 0.20 ? "strong" : speed >= 0.10 ? "normal" : "weak";
            String arrows = arrow.repeat(speed >= 0.20 ? 3 : speed >= 0.10 ? 2 : 1);
            line.append(Component.translatable(KEY + ".current", Component.translatable(KEY + "." + tier), arrows));
        }

        // dock
        if (sub.getDock().isEmpty())
        {
            BlockPos dock = nearestDock(level, c, Config.SUB_SONAR_DOCK_RANGE.get());
            if (dock != null)
            {
                double dx = dock.getX() + 0.5 - c.x, dz = dock.getZ() + 0.5 - c.z;
                line.append(Component.translatable(KEY + ".dock", arrow(sub.getYRot(), dx, dz),
                        Math.round(Math.sqrt(dock.distToCenterSqr(c)))));
            }
        }
        return line;
    }

    /** vanilla water animals and Abyssia fauna (mobs of this mod) */
    private static boolean isSeaLife(LivingEntity e)
    {
        return e instanceof WaterAnimal || e instanceof Mob && Abyssia.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
    }

    /** flow in blocks per tick: the stronger (horizontally) of the ocean current field and a natural stream */
    private static Vec3 current(ServerLevel level, Vec3 c)
    {
        Vec3 ocean = OceanCurrentManager.getCurrent(level, c.x, c.y, c.z);
        CurrentData natural = NaturalCurrents.getCurrentAt(level, c.x, c.y, c.z);
        Vec3 stream = natural.isPresent() ? natural.getDirection().scale(natural.getLocalStrength() * NaturalCurrents.maxSpeed(level)) : Vec3.ZERO;
        return stream.horizontalDistanceSqr() > ocean.horizontalDistanceSqr() ? stream : ocean;
    }

    /** 8-way arrow of the world direction (dx, dz) seen from a hull with this yaw (up = ahead) */
    static String arrow(float yawDeg, double dx, double dz)
    {
        double world = Math.toDegrees(Math.atan2(-dx, dz));   // Minecraft yaw of the direction
        double rel = Mth.wrapDegrees(world - yawDeg);
        int i = Math.floorMod((int) Math.round(rel / 45.0), 8);
        return ARROWS[i];
    }

    private static BlockPos nearestDock(ServerLevel level, Vec3 c, int range)
    {
        BlockPos best = null;
        double bestSq = (double) range * range;
        int minX = Mth.floor(c.x - range) >> 4, maxX = Mth.floor(c.x + range) >> 4;
        int minZ = Mth.floor(c.z - range) >> 4, maxZ = Mth.floor(c.z + range) >> 4;
        for (int cx = minX; cx <= maxX; cx++)
            for (int cz = minZ; cz <= maxZ; cz++)
            {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values())
                {
                    if (!(be instanceof SubmarineDockBlockEntity)) continue;
                    double d = be.getBlockPos().distToCenterSqr(c);
                    if (d <= bestSq)
                    {
                        bestSq = d;
                        best = be.getBlockPos();
                    }
                }
            }
        return best;
    }
}
