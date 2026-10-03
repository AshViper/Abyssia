package com.abyssia.habitat.aquarium;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildChecks;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import com.abyssia.habitat.build.BuildStep;
import com.abyssia.habitat.build.BuiltUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BT01g build entry: the aquarium, an interior fixture standing on a module floor. SIZE x SIZE footprint, HEIGHT tall
 * (the room interior is 3 blocks: floor y0 .. ceiling y4). Local x -2..2 (right), z 0..4 (forward), controller at the
 * floor centre (0, 0, 2). Dismantle: creatures come back as filled canisters, blocks become air, 80 % refund.
 */
public final class AquariumEntry implements BuildEntry
{
    public static final String ID = "aquarium";
    public static final int SIZE = 5;
    /** interior height of a room (y1..y3); the spec's 4 does not fit inside a module */
    public static final int HEIGHT = 3;
    private static final double REFUND = 0.8;

    public record Placement(BlockPos origin, Direction forward) implements BuildPlacement
    {
        public BlockPos at(int x, int y, int z)
        {
            return origin.relative(forward, z).relative(forward.getClockWise(), x).above(y);
        }

        public BlockPos controller()
        {
            return at(0, 0, SIZE / 2);
        }

        @Override
        public AABB box()
        {
            int half = SIZE / 2;
            BlockPos a = at(-half, 0, 0), b = at(half, HEIGHT - 1, SIZE - 1);
            return new AABB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()) + 1, b.getY() + 1, Math.max(a.getZ(), b.getZ()) + 1);
        }

        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            return states();
        }

        /** every cell with its block, bottom-up */
        public Map<BlockPos, BlockState> states()
        {
            int half = SIZE / 2;
            Map<BlockPos, BlockState> out = new LinkedHashMap<>();
            for (int y = 0; y < HEIGHT; y++)
                for (int z = 0; z < SIZE; z++)
                    for (int x = -half; x <= half; x++) out.put(at(x, y, z), stateAt(x, y, z));
            return out;
        }

        private BlockState stateAt(int x, int y, int z)
        {
            int half = SIZE / 2;
            if (x == 0 && y == 0 && z == half) return AquariumContent.AQUARIUM.get().defaultBlockState();
            Direction right = forward.getClockWise();
            Direction outZ = z == 0 ? forward.getOpposite() : z == SIZE - 1 ? forward : null;
            Direction outX = x == -half ? right.getOpposite() : x == half ? right : null;
            AquariumPartBlock.Tier tier = y == 0 ? AquariumPartBlock.Tier.BOTTOM
                    : y == HEIGHT - 1 ? AquariumPartBlock.Tier.TOP : AquariumPartBlock.Tier.MIDDLE;
            AquariumPartBlock.Kind kind;
            Direction facing = forward;
            if (outZ != null && outX != null)
            {
                kind = AquariumPartBlock.Kind.CORNER;
                // the corner model's outer faces are FACING and FACING counter-clockwise
                facing = outX == outZ.getCounterClockWise() ? outZ : outX;
            }
            else if (outZ != null || outX != null)
            {
                kind = AquariumPartBlock.Kind.SIDE;
                facing = outZ != null ? outZ : outX;
            }
            else if (y == 0) kind = Math.floorMod(x + z, 2) == 0 ? AquariumPartBlock.Kind.FLOOR_CORAL : AquariumPartBlock.Kind.FLOOR;
            else kind = AquariumPartBlock.Kind.WATER;
            return AquariumContent.AQUARIUM_PART.get().defaultBlockState().setValue(AquariumPartBlock.KIND, kind)
                    .setValue(AquariumPartBlock.TIER, tier).setValue(HorizontalDirectionalBlock.FACING, facing);
        }
    }

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public BuildCategory category()
    {
        return BuildCategory.BREEDING;
    }

    @Override
    public Component detail()
    {
        return Component.translatable("screen." + Abyssia.MODID + ".habitat.size", SIZE, SIZE, HEIGHT);
    }

    @Override
    public List<ItemStack> cost()
    {
        return List.of(new ItemStack(Items.IRON_INGOT, 24), new ItemStack(Items.GLASS, 32), new ItemStack(Items.COPPER_INGOT, 8));
    }

    /** Centre of the tank floor = the aimed floor cell (pushed off walls), then the near edge towards the player. */
    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        Level level = player.level();
        Direction forward = HabitatPlan.facing(rot);
        int half = SIZE / 2;
        double reach = HabitatPlan.clampDistance(distance);
        HitResult hit = player.pick(reach, partialTick, false);
        BlockPos centre;
        if (hit instanceof BlockHitResult bhit && hit.getType() == HitResult.Type.BLOCK)
        {
            Direction face = bhit.getDirection();
            centre = bhit.getBlockPos().relative(face);
            if (face.getAxis().isHorizontal()) centre = centre.relative(face, half);
            else if (face == Direction.DOWN) centre = centre.below(HEIGHT - 1);
        }
        else
        {
            Vec3 end = player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(reach));
            centre = BlockPos.containing(end);
        }
        for (int i = 0; i <= HEIGHT && level.getBlockState(centre.below()).isAir(); i++) centre = centre.below();
        return new Placement(centre.relative(forward, -half), forward);
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        return BuildChecks.interior(level, player, p.states().keySet(), p.box());
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        List<BuildStep> steps = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (Map.Entry<BlockPos, BlockState> e : ((Placement) placement).states().entrySet())
            steps.add(new BuildStep(e.getKey(), e.getValue(), null, air));
        return BuildLayout.of(steps);
    }

    @Override
    public boolean canDismantle()
    {
        return true;
    }

    @Override
    public boolean dismantle(ServerLevel level, ServerPlayer player, BuiltUnits.Unit unit)
    {
        Placement p = new Placement(unit.origin(), HabitatPlan.facing(unit.rot()));
        List<ItemStack> give = new ArrayList<>();
        if (level.getBlockEntity(p.controller()) instanceof AquariumBlockEntity be) give.addAll(be.takeAllAsCanisters());
        for (BlockPos pos : p.states().keySet())
        {
            BlockState state = level.getBlockState(pos);
            if (state.is(AquariumContent.AQUARIUM.get()) || state.is(AquariumContent.AQUARIUM_PART.get()))
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (ItemStack paid : unit.paid())
        {
            int count = (int) Math.floor(paid.getCount() * REFUND);
            if (count > 0) give.add(paid.copyWithCount(count));
        }
        for (ItemStack stack : give)
            if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
        return true;
    }
}
