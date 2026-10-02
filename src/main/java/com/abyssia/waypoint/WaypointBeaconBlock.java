package com.abyssia.waypoint;

import com.abyssia.industry.block.IndustrialLightBlock;
import com.abyssia.waypoint.client.WaypointClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * W01 waypoint beacon (inbox/specs/W01-waypoint-beacon.md): the work light's shape and light, with COLOR (0..15,
 * {@link WaypointColors}) tinting the lamp through a BlockColor. Empty-handed right-click opens the name / colour
 * screen; placing and breaking update the dimension's {@link WaypointRegistry}, which drives every client's HUD markers.
 */
public class WaypointBeaconBlock extends IndustrialLightBlock implements EntityBlock
{
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, WaypointColors.COUNT - 1);

    public WaypointBeaconBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(COLOR, WaypointColors.DEFAULT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new WaypointBeaconBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (level.isClientSide)
        {
            String name = level.getBlockEntity(pos) instanceof WaypointBeaconBlockEntity be ? be.getName() : "";
            int color = state.getValue(COLOR);
            WaypointClient.openScreen(pos, name, color);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server)) return;
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof WaypointBeaconBlockEntity be)
            be.setOwner(player.getUUID());
        WaypointRegistry.refresh(server, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved)
    {
        super.onPlace(state, level, pos, oldState, moved);
        // colour changes (same block) are registered by WaypointSavePacket
        if (level instanceof ServerLevel server && !oldState.is(this)) WaypointRegistry.refresh(server, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (level instanceof ServerLevel server && !newState.is(this)) WaypointRegistry.remove(server, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    /** A faint dust of the beacon's colour from the lamp now and then (animateTick reaches a block every few seconds). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        int rgb = WaypointColors.rgb(state.getValue(COLOR));
        Vector3f color = new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        Direction facing = state.getValue(FACING);
        // lamp centre: 4/16 out from the mounting face
        double x = pos.getX() + 0.5 - facing.getStepX() * 0.25;
        double y = pos.getY() + 0.5 - facing.getStepY() * 0.25;
        double z = pos.getZ() + 0.5 - facing.getStepZ() * 0.25;
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++)
            level.addParticle(new DustParticleOptions(color, 0.6f), x + (random.nextDouble() - 0.5) * 0.3,
                    y + (random.nextDouble() - 0.5) * 0.3, z + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.02, 0.0);
    }
}
