package com.abyssia.habitat.aquarium;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * BT01g: one cell of the aquarium other than the controller (no item, no loot). {@link Kind} + {@link Tier} pick the
 * model (frame column, glass wall, sand floor, invisible water volume), FACING turns it outwards. Right-click forwards
 * to the controller.
 */
public class AquariumPartBlock extends Block
{
    public enum Kind implements StringRepresentable
    {
        /** corner column (outer corner = FACING and FACING counter-clockwise) */
        CORNER("corner"),
        /** glass wall cell (outer face = FACING) */
        SIDE("side"),
        /** sand floor inside the walls */
        FLOOR("floor"),
        /** sand floor with a coral */
        FLOOR_CORAL("floor_coral"),
        /** water volume above the floor (drawn by the controller renderer) */
        WATER("water");

        private final String name;

        Kind(String name)
        {
            this.name = name;
        }

        @Override
        public String getSerializedName()
        {
            return name;
        }
    }

    public enum Tier implements StringRepresentable
    {
        BOTTOM("bottom"), MIDDLE("middle"), TOP("top");

        private final String name;

        Tier(String name)
        {
            this.name = name;
        }

        @Override
        public String getSerializedName()
        {
            return name;
        }
    }

    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final EnumProperty<Tier> TIER = EnumProperty.create("tier", Tier.class);

    public static final MapCodec<AquariumPartBlock> CODEC = simpleCodec(AquariumPartBlock::new);

    public AquariumPartBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, Kind.SIDE).setValue(TIER, Tier.BOTTOM)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec()
    {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(KIND, TIER, HorizontalDirectionalBlock.FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return state.getValue(KIND) == Kind.WATER ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.block();
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos)
    {
        return 1.0f;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos)
    {
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit)
    {
        BlockPos controller = findController(level, pos);
        if (controller == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return AquariumBlock.itemResult(stack, level, controller, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        BlockPos controller = findController(level, pos);
        if (controller == null) return InteractionResult.PASS;
        return AquariumBlock.interact(level, controller, player, InteractionHand.MAIN_HAND);
    }

    /** The controller whose tank contains pos (controller = floor centre), or null. */
    @Nullable
    public static BlockPos findController(BlockGetter level, BlockPos pos)
    {
        int half = AquariumEntry.SIZE / 2;
        for (int dy = 0; dy < AquariumEntry.HEIGHT; dy++)
            for (int dx = -half; dx <= half; dx++)
                for (int dz = -half; dz <= half; dz++)
                {
                    BlockPos p = pos.offset(dx, -dy, dz);
                    if (level.getBlockState(p).getBlock() instanceof AquariumBlock) return p;
                }
        return null;
    }
}
