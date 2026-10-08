package com.abyssia.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/**
 * {@code {"type": "abyssia:abyss_floor"}}: moves a placement to the first open block above the abyss layer's seabed
 * ({@link DeepLayer#abyssFloorY}), the abyss counterpart of {@code abyssia:deep_floor}. Drops the position where the
 * column has no abyss floor.
 */
public class AbyssFloorPlacement extends PlacementModifier
{
    public static final AbyssFloorPlacement INSTANCE = new AbyssFloorPlacement();
    public static final Codec<AbyssFloorPlacement> CODEC = Codec.unit(() -> INSTANCE);

    private AbyssFloorPlacement() {}

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos)
    {
        int y = DeepLayer.abyssFloorY(context.getLevel(), pos.getX(), pos.getZ());
        return y > DeepLayer.MIN_Y + 5 ? Stream.of(new BlockPos(pos.getX(), y + 1, pos.getZ())) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type()
    {
        return ModWorldgen.ABYSS_FLOOR.get();
    }
}
