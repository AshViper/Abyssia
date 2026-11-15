package com.abyssia.entity.boss;

import com.abyssia.Abyssia;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * BOSS01 abyssal altar. Right-click with an abyss serpent lure in a deep-layer hall to start the summon; the block entity
 * ({@link AbyssalAltarBlockEntity}) holds the boss and takes it back when everyone has left.
 * <p>
 * Activation: the biome is in {@code #abyssia:deep_layer}, 40 blocks above are open (water or air) and 30 blocks are open
 * to each of the four horizontal sides (a stand-in for "inside an AbyssCavity hall", whose site data is package-private).
 */
public class AbyssalAltarBlock extends BaseEntityBlock
{
    private static final TagKey<Biome> DEEP_LAYER = TagKey.create(Registries.BIOME, new ResourceLocation(Abyssia.MODID, "deep_layer"));
    private static final int OPEN_UP = 40, OPEN_SIDE = 30;

    public AbyssalAltarBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new AbyssalAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide ? null : createTickerHelper(type, BossContent.ALTAR_ENTITY.get(), AbyssalAltarBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        ItemStack stack = player.getItemInHand(hand);
        int kind = BossContent.lureKind(stack);
        if (kind >= 0)
        {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof AbyssalAltarBlockEntity be)
            {
                String problem = be.busy() ? "occupied" : inactiveReason(sl, pos);
                if (problem != null)
                {
                    player.displayClientMessage(Component.translatable("message.abyssia.altar." + problem), true);
                    return InteractionResult.CONSUME;
                }
                if (!player.getAbilities().instabuild) stack.shrink(1);
                be.startSummon(sl, kind);
            }
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof AbyssalAltarBlockEntity be)
        {
            if (be.giveStoredLures(player)) return InteractionResult.CONSUME;
            String problem = be.busy() ? "occupied" : inactiveReason(sl, pos);
            player.displayClientMessage(Component.translatable(problem == null ? "message.abyssia.altar.ready" : "message.abyssia.altar." + problem), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof AbyssalAltarBlockEntity be) be.retreat(sl, true);
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Null when the altar can be started here, else the lang key suffix of the reason. */
    @Nullable
    static String inactiveReason(ServerLevel level, BlockPos pos)
    {
        if (!level.getBiome(pos).is(DEEP_LAYER)) return "wrong_place";
        for (int dy = 1; dy <= OPEN_UP; dy++) if (!open(level, pos.above(dy))) return "no_space";
        // Halls are full of pillars and spires: ask for mostly open water around the altar, not a clear cross.
        int samples = 0, openCount = 0;
        for (Direction d : Direction.Plane.HORIZONTAL)
            for (int i = 2; i <= OPEN_SIDE; i += 2) { samples++; if (open(level, pos.relative(d, i).above(2))) openCount++; }
        return openCount * 100 >= samples * 85 ? null : "no_space";
    }

    private static boolean open(ServerLevel level, BlockPos p)
    {
        if (!level.hasChunkAt(p)) return false;
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }
}
