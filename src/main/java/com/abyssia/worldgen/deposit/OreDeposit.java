package com.abyssia.worldgen.deposit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Optional;
import java.util.UUID;

/**
 * Represents a single ore deposit (mineral vein) generated in the world.
 * Contains metadata about the vein for future scanner, mining platform, and map systems.
 */
public record OreDeposit(
        UUID depositId,
        ResourceLocation mineralId,
        BlockPos center,
        AABB bounds,
        com.abyssia.worldgen.OreVeinFeature.Size size,
        com.abyssia.worldgen.OreVeinFeature.Shape shape,
        int totalOre,
        int minedAmount
) {
    public CompoundTag save(CompoundTag tag) {
        tag.putUUID("deposit_id", depositId);
        tag.putString("mineral", mineralId.toString());
        tag.putLong("center", center.asLong());
        tag.putDouble("min_x", bounds.minX);
        tag.putDouble("min_y", bounds.minY);
        tag.putDouble("min_z", bounds.minZ);
        tag.putDouble("max_x", bounds.maxX);
        tag.putDouble("max_y", bounds.maxY);
        tag.putDouble("max_z", bounds.maxZ);
        tag.putString("size", size.getSerializedName());
        tag.putString("shape", shape.name());
        tag.putInt("total_ore", totalOre);
        tag.putInt("mined_amount", minedAmount);
        return tag;
    }

    public static OreDeposit load(CompoundTag tag) {
        UUID id = tag.getUUID("deposit_id");
        ResourceLocation mineral = ResourceLocation.parse(tag.getString("mineral"));
        BlockPos center = BlockPos.of(tag.getLong("center"));
        AABB bounds = new AABB(
                tag.getDouble("min_x"),
                tag.getDouble("min_y"),
                tag.getDouble("min_z"),
                tag.getDouble("max_x"),
                tag.getDouble("max_y"),
                tag.getDouble("max_z")
        );
        com.abyssia.worldgen.OreVeinFeature.Size size = com.abyssia.worldgen.OreVeinFeature.Size.valueOf(tag.getString("size").toUpperCase());
        com.abyssia.worldgen.OreVeinFeature.Shape shape = com.abyssia.worldgen.OreVeinFeature.Shape.valueOf(tag.getString("shape"));
        int totalOre = tag.getInt("total_ore");
        int minedAmount = tag.getInt("mined_amount");
        return new OreDeposit(id, mineral, center, bounds, size, shape, totalOre, minedAmount);
    }

    public OreDeposit withMinedAmount(int additional) {
        return new OreDeposit(depositId, mineralId, center, bounds, size, shape, totalOre, minedAmount + additional);
    }

    public int remainingOre() {
        return Math.max(0, totalOre - minedAmount);
    }

    public float depletionRatio() {
        return totalOre > 0 ? (float) minedAmount / totalOre : 0f;
    }

    public boolean contains(BlockPos pos) {
        return bounds.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public Optional<BlockState> mineralState(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(Registries.BLOCK).get(
                ResourceKey.create(Registries.BLOCK, mineralId)
        ).map(holder -> holder.value().defaultBlockState());
    }

    public ResourceKey<Level> dimension() {
        return Level.OVERWORLD;
    }
}