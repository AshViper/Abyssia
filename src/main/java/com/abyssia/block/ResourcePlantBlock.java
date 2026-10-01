package com.abyssia.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;

/**
 * A resource plant (tools/plant_defs.py): right-clicking a ripe one harvests its material from
 * {@code loot_tables/harvest/<id>.json} (shears add a bonus there) and leaves the plant standing; it ripens again on
 * random ticks. Breaking it gives the plant, plus the material only while ripe. Worldgen places it ripe (the default
 * state); a placed plant starts unripe, so harvesting, breaking and replanting cannot loop.
 */
public class ResourcePlantBlock extends UnderwaterPlantBlock
{
    public static final BooleanProperty RIPE = BooleanProperty.create("ripe");

    private final float growthChance;

    public ResourcePlantBlock(Properties properties, SporeEmitter spores, float growthChance)
    {
        super(properties, spores);
        this.growthChance = growthChance;
        registerDefaultState(stateDefinition.any().setValue(RIPE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(RIPE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(RIPE, false);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state)
    {
        return !state.getValue(RIPE) && growthChance > 0;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (random.nextFloat() < growthChance) level.setBlock(pos, state.setValue(RIPE, true), 2);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!state.getValue(RIPE)) return InteractionResult.PASS;
        if (level instanceof ServerLevel server)
        {
            ItemStack tool = player.getItemInHand(hand);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(this);
            LootParams params = new LootParams.Builder(server)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, tool)
                    .withParameter(LootContextParams.BLOCK_STATE, state)
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                    .create(LootContextParamSets.BLOCK);
            server.getServer().getLootData().getLootTable(id.withPrefix("harvest/")).getRandomItems(params)
                    .forEach(stack -> popResource(level, pos, stack));
            if (tool.is(Tags.Items.TOOLS_SHEAR)) tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            level.setBlock(pos, state.setValue(RIPE, false), 2);
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 0.8f + level.random.nextFloat() * 0.4f);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
