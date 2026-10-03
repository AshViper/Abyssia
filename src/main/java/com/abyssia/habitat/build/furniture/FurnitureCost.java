package com.abyssia.habitat.build.furniture;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.registries.BuiltInRegistries;

/** BT01h helpers shared by the locker / workbench entries. */
final class FurnitureCost
{
    private FurnitureCost() {}

    static ItemStack mod(String name, int count)
    {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name));
        return new ItemStack(item, count);
    }

    static ItemStack vanilla(String name, int count)
    {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(name)), count);
    }

    /** the aimed cell (face-adjacent when a block is hit, else eye + look x reach), dropped onto the floor */
    static BlockPos aimedFloorCell(Player player, int distance, float partialTick, int maxDrop)
    {
        Level level = player.level();
        int reach = HabitatPlan.clampDistance(distance);
        HitResult hit = player.pick(reach, partialTick, false);
        BlockPos pos = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK
                ? b.getBlockPos().relative(b.getDirection())
                : BlockPos.containing(player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(reach)));
        for (int i = 0; i < maxDrop && level.getBlockState(pos.below()).isAir(); i++) pos = pos.below();
        return pos.immutable();
    }
}
