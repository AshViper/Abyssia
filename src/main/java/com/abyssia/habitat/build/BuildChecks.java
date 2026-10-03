package com.abyssia.habitat.build;

import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.power.HabitatBases;
import com.abyssia.habitat.power.HabitatPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** BT01a shared placement rules for entries (the modules themselves use HabitatPlan.check). */
public final class BuildChecks
{
    /** client fallback: how far to look for the hull on each side of an interior cell */
    private static final int HULL_SEARCH = 13;

    private BuildChecks() {}

    /**
     * Interior fixture rule (ladder, locker, workbench, charging station, aquarium): every cell is air inside a
     * registered module box (HabitatBases, strictly inside the shell) and the player may build there; no living
     * entity in {@code box} (null = skip). The client has no HabitatBases, so it approximates "inside" with hull
     * blocks on all six sides; the server decides.
     */
    public static BuildCheck interior(Level level, Player player, Iterable<BlockPos> cells, @Nullable AABB box)
    {
        ItemStack stack = player.getMainHandItem();
        for (BlockPos pos : cells)
        {
            if (!level.getBlockState(pos).isAir() || !insideModule(level, pos)) return BuildCheck.NOT_INTERIOR;
            if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, stack)) return BuildCheck.PERMISSION;
        }
        if (box != null && !level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator()).isEmpty())
            return BuildCheck.ENTITY;
        return BuildCheck.OK;
    }

    /** The registered module box strictly containing pos (shell excluded), or null (always null on the client). */
    @Nullable
    public static BoundingBox moduleInterior(Level level, BlockPos pos)
    {
        if (!(level instanceof ServerLevel server)) return null;
        for (HabitatBases.Module m : HabitatBases.get(server).modules())
        {
            BoundingBox b = m.box();
            if (pos.getX() > b.minX() && pos.getX() < b.maxX() && pos.getY() > b.minY() && pos.getY() < b.maxY()
                    && pos.getZ() > b.minZ() && pos.getZ() < b.maxZ()) return b;
        }
        return null;
    }

    /** Server: inside a registered module box. Client: hull (HabitatPower.isShell) within reach on all six sides. */
    public static boolean insideModule(Level level, BlockPos pos)
    {
        if (level instanceof ServerLevel) return moduleInterior(level, pos) != null;
        for (Direction dir : Direction.values())
        {
            boolean hit = false;
            BlockPos.MutableBlockPos p = pos.mutable();
            for (int i = 0; i < HULL_SEARCH && !hit; i++)
                if (HabitatPower.isShell(level.getBlockState(p.move(dir)))) hit = true;
            if (!hit) return false;
        }
        return true;
    }

    /** The modules' water-footprint rule for arbitrary cells (outdoor builds such as generators). */
    public static BuildCheck water(Level level, Player player, Iterable<BlockPos> cells, @Nullable AABB box)
    {
        ItemStack stack = player.getMainHandItem();
        for (BlockPos pos : cells)
        {
            if (!HabitatPlan.replaceable(level.getBlockState(pos))) return BuildCheck.NOT_WATER;
            if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, stack)) return BuildCheck.PERMISSION;
        }
        if (box != null && !level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive).isEmpty()) return BuildCheck.ENTITY;
        return BuildCheck.OK;
    }
}
