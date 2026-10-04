package com.abyssia.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * SUB04 invisible helper block under the dock gangway (the gangway itself is drawn by DockRenderer): only the collision
 * the player walks on. Placed / removed by {@link SubmarineDockBlockEntity} (NBT "Gangway"); no item, no drop.
 * FACING = the dock frame's +x (outward from the pool); PART = the cell, all in the floor layer (y -32..-16 px, deck top
 * -24 px = 8 px high): 0 = x 24..40 px (deck from 28), 1 = x 40..56 (deck), 2 = x 56..72 mount only (58..66, always there while the dock exists), 3 = same + deck end (56..58) while the gangway is down.
 */
public class SubmarineDockGangwayBlock extends Block
{
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);

    /** boxes {x0, z0, x1, z1, height} per part, for FACING = EAST; block px (x outward, z across) */
    private static final int[][][] BOXES = {
            {{4, 4, 16, 12, 8}},
            {{0, 4, 16, 12, 8}},
            {{2, 0, 10, 16, 8}},
            {{0, 4, 2, 12, 8}, {2, 0, 10, 16, 8}},
    };
    private static final Map<Direction, VoxelShape[]> SHAPES = new EnumMap<>(Direction.class);

    static
    {
        for (Direction d : Direction.Plane.HORIZONTAL)
        {
            VoxelShape[] parts = new VoxelShape[BOXES.length];
            for (int part = 0; part < BOXES.length; part++)
            {
                VoxelShape shape = Shapes.empty();
                for (int[] b : BOXES[part])
                {
                    double x0 = b[0], z0 = b[1], x1 = b[2], z1 = b[3], x0r, x1r, z0r, z1r;
                    switch (d)
                    {
                        case SOUTH -> { x0r = 16 - z1; x1r = 16 - z0; z0r = x0; z1r = x1; }
                        case WEST -> { x0r = 16 - x1; x1r = 16 - x0; z0r = 16 - z1; z1r = 16 - z0; }
                        case NORTH -> { x0r = z0; x1r = z1; z0r = 16 - x1; z1r = 16 - x0; }
                        default -> { x0r = x0; x1r = x1; z0r = z0; z1r = z1; }
                    }
                    shape = Shapes.or(shape, Block.box(x0r, 0, z0r, x1r, b[4], z1r));
                }
                parts[part] = shape;
            }
            SHAPES.put(d, parts);
        }
    }

    public SubmarineDockGangwayBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST).setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, PART);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPES.get(state.getValue(FACING))[state.getValue(PART)];
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }
}
