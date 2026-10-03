package com.abyssia.habitat.generator;

import com.abyssia.registry.ModItems;
import com.abyssia.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * BT01d: the three multiblock generators. Local frame: x = right (forward.getClockWise()), y = up, z = forward;
 * local (0, 0, 0) is the corner {@code origin}; z = 0 is the back row that touches the habitat wall when snapped.
 * Solid cells hold a part block (or the controller); the other footprint cells stay water. The element models are
 * written by tools/bt01/generator_assets.py from the same cell layout (keep both in sync).
 */
public enum GeneratorKind
{
    CURRENT_TURBINE("current_turbine", 5, 5, 5, 2, 2, 1, 48, 32, 16),
    GEOTHERMAL("geothermal_generator", 3, 4, 3, 1, 0, 1, 48, 24, 8),
    BIOFUEL("biofuel_generator", 3, 3, 3, 1, 0, 1, 32, 32, 8);

    public final String id;
    /** x (right), y (up), z (forward) */
    public final int width, height, depth;
    /** controller cell, local */
    public final int cx, cy, cz;
    private final int iron, copper, glass;

    GeneratorKind(String id, int width, int height, int depth, int cx, int cy, int cz, int iron, int copper, int glass)
    {
        this.id = id;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.iron = iron;
        this.copper = copper;
        this.glass = glass;
    }

    /** build cost: the bulk metal + glass, 8 machine frames each, plus the kind's working parts (user 2026-10-04) */
    public List<ItemStack> cost()
    {
        List<ItemStack> out = new ArrayList<>(List.of(new ItemStack(Items.IRON_INGOT, iron), new ItemStack(Items.COPPER_INGOT, copper),
                new ItemStack(Items.GLASS, glass), new ItemStack(ModItems.MACHINE_FRAME.get(), 8)));
        switch (this)
        {
            case CURRENT_TURBINE -> out.add(new ItemStack(ModItems.CONDUCTIVE_COMPONENT.get(), 4));
            case GEOTHERMAL ->
            {
                out.add(new ItemStack(Items.GOLD_INGOT, 8));
                out.add(new ItemStack(ModItems.THERMAL_COMPONENT.get(), 2));
            }
            case BIOFUEL -> out.add(new ItemStack(ModItems.PRESSURE_VALVE.get(), 2));
        }
        return out;
    }

    /** the loop played while the generator works */
    public SoundEvent runningSound()
    {
        return switch (this)
        {
            case CURRENT_TURBINE -> ModSounds.MACHINE_TURBINE.get();
            case GEOTHERMAL -> ModSounds.MACHINE_GEOTHERMAL.get();
            case BIOFUEL -> ModSounds.MACHINE_BIOFUEL.get();
        };
    }

    public Block controller()
    {
        return switch (this)
        {
            case CURRENT_TURBINE -> ModGenerators.CURRENT_TURBINE.get();
            case GEOTHERMAL -> ModGenerators.GEOTHERMAL.get();
            case BIOFUEL -> ModGenerators.BIOFUEL.get();
        };
    }

    /** whether local cell (x, y, z) holds a block (part or controller) */
    public boolean solid(int x, int y, int z)
    {
        return switch (this)
        {
            // mount posts + 2 crossbeams on the wall row, nacelle, pylon, hub (rotor blades sweep water cells)
            case CURRENT_TURBINE -> (z == 0 && (x == 0 || x == 4))
                    || (z == 0 && (y == 2 || y == 4))
                    || (x == 2 && y == 2)
                    || (x == 2 && z == 2 && y <= 1);
            // 3 x 3 furnace base + heat exchanger slab, 1 x 1 tower above
            case GEOTHERMAL -> y <= 1 || (x == 1 && z == 1);
            // housing 3 x 3 x 2, then the 2 x 2 tank (left front) and the stack (right back)
            case BIOFUEL -> y <= 1 || (x <= 1 && z >= 1) || (x == 2 && z == 0);
        };
    }

    // ---------------------------------------------------------------- geometry

    public static BlockPos at(BlockPos origin, Direction forward, int x, int y, int z)
    {
        return origin.relative(forward, z).relative(forward.getClockWise(), x).above(y);
    }

    public BlockPos controllerPos(BlockPos origin, Direction forward)
    {
        return at(origin, forward, cx, cy, cz);
    }

    /** the corner origin of a generator whose controller is at {@code controller} */
    public BlockPos origin(BlockPos controller, Direction forward)
    {
        return controller.relative(forward, -cz).relative(forward.getClockWise(), -cx).below(cy);
    }

    /** every footprint cell, bottom-up */
    public List<BlockPos> cells(BlockPos origin, Direction forward)
    {
        List<BlockPos> out = new ArrayList<>(width * height * depth);
        for (int y = 0; y < height; y++)
            for (int z = 0; z < depth; z++)
                for (int x = 0; x < width; x++) out.add(at(origin, forward, x, y, z));
        return out;
    }

    /** solid cells, bottom-up */
    public List<BlockPos> solidCells(BlockPos origin, Direction forward)
    {
        List<BlockPos> out = new ArrayList<>();
        for (int y = 0; y < height; y++)
            for (int z = 0; z < depth; z++)
                for (int x = 0; x < width; x++)
                    if (solid(x, y, z)) out.add(at(origin, forward, x, y, z));
        return out;
    }

    public BoundingBox blockBox(BlockPos origin, Direction forward)
    {
        return BoundingBox.fromCorners(origin, at(origin, forward, width - 1, height - 1, depth - 1));
    }

    public AABB box(BlockPos origin, Direction forward)
    {
        BoundingBox b = blockBox(origin, forward);
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
    }

    /** the cell whose centre is sampled for the natural current (turbine rotor hub) */
    public BlockPos rotorCell(BlockPos origin, Direction forward)
    {
        return at(origin, forward, 2, 2, 4);
    }
}
