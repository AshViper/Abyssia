package com.abyssia.industry.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Industrial valve: an inline pipe segment along FACING's axis (pipes connect to both ends) with a housing and a
 * handwheel. The shape is the north model's boxes turned like the blockstate turns the model (east y90, south
 * y180, west y270, up x270, down x90), so with facing up the wheel points south and with facing down north.
 */
public class ValveBlock extends FacingDecorBlock
{
    /** north model: pipe 4..12 along z, housing 3..13 x 3..13 x 5..11, stem + handwheel up to y 14 */
    private static final double[][] NORTH_BOXES = {
            {4, 4, 0, 12, 12, 16},
            {3, 3, 5, 13, 13, 11},
            {3, 12, 3, 13, 14, 13},
    };

    public ValveBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected VoxelShape shapeFor(Direction facing)
    {
        VoxelShape shape = Shapes.empty();
        for (double[] box : NORTH_BOXES)
        {
            double[] a = turn(facing, box[0], box[1], box[2]);
            double[] b = turn(facing, box[3], box[4], box[5]);
            shape = Shapes.or(shape, Block.box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                    Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])));
        }
        return shape.optimize();
    }

    /** A point (16 units) of the north model turned to facing, as the blockstate x / y rotations do. */
    private static double[] turn(Direction facing, double x, double y, double z)
    {
        return switch (facing)
        {
            case NORTH -> new double[] {x, y, z};
            case EAST -> new double[] {16 - z, y, x};
            case SOUTH -> new double[] {16 - x, y, 16 - z};
            case WEST -> new double[] {z, y, 16 - x};
            case UP -> new double[] {x, 16 - z, y};
            case DOWN -> new double[] {x, z, 16 - y};
        };
    }
}
