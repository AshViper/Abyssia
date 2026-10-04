package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.environment.CurrentData;
import com.abyssia.environment.NaturalCurrents;
import com.abyssia.environment.OceanCurrentManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SUB03 Deep-Sea Sonar (server): creatures in sonar_range (WaterAnimal + Abyssia mobs, no players), the current at the
 * hull (the existing OceanCurrentManager field / natural current values, whichever is faster) and the nearest
 * submarine dock within sonar_dock_range (block entities of the loaded chunks in range only). Arrows are relative to
 * the hull's heading (up = ahead). Newly detected creatures get a bubble pop + a soft note.
 */
public final class SubmarineSonar
{
    /** 8 directions, clockwise from straight ahead */
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String KEY = "message." + Abyssia.MODID + ".submarine.sonar.";

    private SubmarineSonar() {}

    /** scans around {@code sub}, updates {@code seen} (UUIDs detected last scan) and returns the HUD suffix */
    public static Component scan(Submarine sub, Set<UUID> seen)
    {
        ServerLevel level = (ServerLevel) sub.level();
        Vec3 c = sub.getBoundingBox().getCenter();
        double range = Config.SUBMARINE_SONAR_RANGE.get();
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, sub.getBoundingBox().inflate(range),
                e -> e.isAlive() && !(e instanceof Player) && aquatic(e) && e.distanceToSqr(c) <= range * range);
        Set<UUID> now = new HashSet<>();
        double nearest = Double.MAX_VALUE;
        boolean fresh = false;
        for (LivingEntity e : found)
        {
            now.add(e.getUUID());
            nearest = Math.min(nearest, Math.sqrt(e.distanceToSqr(c)));
            if (!seen.contains(e.getUUID())) fresh = true;
        }
        seen.clear();
        seen.addAll(now);
        if (fresh)
        {
            level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 6, sub.getBbWidth() * 0.6, 0.6, sub.getBbWidth() * 0.6, 0.02);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 0.3f, 1.8f);
        }

        MutableComponent line = Component.translatable(KEY + "prefix");
        int count = found.size();
        String shown = count >= 10 ? "10+" : Integer.toString(count);
        line.append(count == 0 ? Component.translatable(KEY + "life_none")
                : Component.translatable(KEY + "life", shown, Math.round(nearest)));

        Vec3 current = current(level, c);
        double speed = Math.sqrt(current.x * current.x + current.z * current.z);
        if (speed < 1.0E-3) line.append(Component.translatable(KEY + "current_none"));
        else
        {
            String tier = speed >= 0.20 ? "strong" : speed >= 0.10 ? "normal" : "weak";
            String arrow = arrow(sub, current.x, current.z);
            line.append(Component.translatable(KEY + "current", Component.translatable(KEY + tier), arrow.repeat(tier.equals("strong") ? 3 : tier.equals("normal") ? 2 : 1)));
        }

        if (sub.getDock().isEmpty())
        {
            BlockPos dock = nearestDock(level, c, Config.SUBMARINE_SONAR_DOCK_RANGE.get());
            if (dock != null)
            {
                double dx = dock.getX() + 0.5 - c.x, dz = dock.getZ() + 0.5 - c.z;
                line.append(Component.translatable(KEY + "dock", arrow(sub, dx, dz), Math.round(Math.sqrt(dock.distToCenterSqr(c)))));
            }
        }
        return line;
    }

    private static boolean aquatic(LivingEntity e)
    {
        if (e instanceof WaterAnimal) return true;
        var key = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return e instanceof Mob && key != null && Abyssia.MODID.equals(key.getNamespace());
    }

    /** the faster of the OceanCurrentManager field and the natural current / stream at {@code at} (blocks per tick) */
    private static Vec3 current(ServerLevel level, Vec3 at)
    {
        Vec3 field = OceanCurrentManager.getCurrent(level, at.x, at.y, at.z);
        CurrentData data = NaturalCurrents.getCurrentAt(level, at.x, at.y, at.z);
        Vec3 natural = data.isPresent() ? data.getDirection().scale(data.getLocalStrength() * NaturalCurrents.maxSpeed(level)) : Vec3.ZERO;
        return natural.horizontalDistanceSqr() > field.horizontalDistanceSqr() ? natural : field;
    }

    /** 8-way arrow of the horizontal vector (dx, dz) relative to the hull's heading */
    private static String arrow(Submarine sub, double dx, double dz)
    {
        // world yaw of the vector (Minecraft convention: 0 = +Z, 90 = -X), minus the hull yaw; clockwise is positive
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = Mth.wrapDegrees(yaw - sub.getYRot());
        int i = Math.floorMod((int) Math.round(rel / 45.0), 8);
        return ARROWS[i];
    }

    @Nullable
    private static BlockPos nearestDock(ServerLevel level, Vec3 c, int range)
    {
        BlockPos best = null;
        double bestSq = (double) range * range;
        int minX = Mth.floor(c.x - range) >> 4, maxX = Mth.floor(c.x + range) >> 4;
        int minZ = Mth.floor(c.z - range) >> 4, maxZ = Mth.floor(c.z + range) >> 4;
        for (int cx = minX; cx <= maxX; cx++)
            for (int cz = minZ; cz <= maxZ; cz++)
            {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
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
