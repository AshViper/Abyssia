package com.abyssia.industry;

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
import com.abyssia.habitat.dismantle.Dismantler;
import com.abyssia.industry.blockentity.ExcavatorPartBlockEntity;
import com.abyssia.industry.block.IndustryEntityBlock;
import com.abyssia.registry.ModHabitat;
import com.abyssia.registry.ModIndustry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ORE01 abyssal excavator Mk1 / Mk2 as an EQUIPMENT build entry (no item, no crafting recipe, no drops - like the
 * submarine dock). A multiblock like the habitat generators ({@link ExcavatorStructure}): the master block sits on the
 * aimed cell (centre-bottom of a 3 x 3 x 3 volume), part blocks fill the other collision cells, and the master
 * renderer (industry/client/ExcavatorRenderer) draws the machine; the whole volume must be free water. R rotates it.
 * Dismantle: master + parts back to water (the inventory drops), 80 % of the paid materials back.
 */
public final class ExcavatorEntry implements BuildEntry
{
    public final ExcavatorTier tier;
    private final String id;

    public ExcavatorEntry(ExcavatorTier tier)
    {
        this.tier = tier;
        this.id = tier == ExcavatorTier.MK2 ? "abyssal_excavator_mk2" : "abyssal_excavator";
    }

    public record Placement(BlockPos pos, Direction facing, Block block) implements BuildPlacement
    {
        @Override
        public BlockPos origin()
        {
            return pos;
        }

        @Override
        public Direction forward()
        {
            return facing;
        }

        /** the 3 x 3 x 3 volume the model fills */
        @Override
        public AABB box()
        {
            return new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1, pos.getX() + 2, pos.getY() + 3, pos.getZ() + 2);
        }

        public List<BlockPos> cells()
        {
            return ExcavatorStructure.cells(pos);
        }

        /** the solid cells as plain trim blocks (the master renderer draws the real model once built) */
        @Override
        public Map<BlockPos, BlockState> ghost(BlockGetter level)
        {
            Map<BlockPos, BlockState> out = new LinkedHashMap<>();
            BlockState ghost = ModHabitat.TRIM.get().defaultBlockState();
            out.put(pos, ghost);
            for (BlockPos part : ExcavatorStructure.partCells(pos)) out.put(part, ghost);
            return out;
        }
    }

    private static BlockState state(Block block, Direction facing)
    {
        return block.defaultBlockState().setValue(IndustryEntityBlock.FACING, facing).setValue(BlockStateProperties.WATERLOGGED, true);
    }

    private Block block()
    {
        return (tier == ExcavatorTier.MK2 ? ModIndustry.ABYSSAL_EXCAVATOR_MK2 : ModIndustry.ABYSSAL_EXCAVATOR).get();
    }

    @Override
    public String id()
    {
        return id;
    }

    @Override
    public BuildCategory category()
    {
        return BuildCategory.EQUIPMENT;
    }

    @Override
    public Component detail()
    {
        return Component.translatable("habitat." + Abyssia.MODID + "." + id + ".detail");
    }

    /** the menu uses the generated item icon (the block has no item) */
    @Override
    public ResourceLocation icon()
    {
        return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/item/" + id + ".png");
    }

    /** the removed crafting recipes' materials (Mk2 = the Mk1 materials + its own ring, the Mk1 item is no longer a part) */
    @Override
    public List<ItemStack> cost()
    {
        if (tier == ExcavatorTier.MK2)
            return List.of(mod("iron_plate", 4), mod("iron_gear", 2), mod("copper_wire", 4), mod("machine_frame", 2),
                    mod("iron_rod", 2), mod("manganese_ingot", 1), mod("nickel_ingot", 2));
        return List.of(mod("iron_plate", 2), mod("iron_gear", 1), mod("copper_wire", 2), mod("machine_frame", 1),
                mod("iron_rod", 2), mod("manganese_ingot", 1));
    }

    private static ItemStack mod(String name, int count)
    {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name)), count);
    }

    @Nullable
    @Override
    public Placement plan(Player player, int rot, int distance, float partialTick)
    {
        int reach = HabitatPlan.clampDistance(distance);
        HitResult hit = player.pick(reach, partialTick, false);
        BlockPos aimed = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK
                ? b.getBlockPos().relative(b.getDirection())
                : BlockPos.containing(player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(reach)));
        return new Placement(aimed.immutable(), HabitatPlan.facing(rot), block());
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        return BuildChecks.water(level, player, p.cells(), p.box());
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        BlockState part = ModIndustry.EXCAVATOR_PART.get().defaultBlockState();
        List<BuildStep> steps = new ArrayList<>();
        steps.add(new BuildStep(p.pos, state(p.block, p.facing), null));
        for (BlockPos pos : ExcavatorStructure.partCells(p.pos)) steps.add(new BuildStep(pos, part, null));
        return BuildLayout.of(steps);
    }

    @Override
    public Completion complete(ServerLevel level, ServerPlayer player, BuildPlacement placement)
    {
        Placement p = (Placement) placement;
        for (BlockPos pos : ExcavatorStructure.partCells(p.pos))
            if (level.getBlockEntity(pos) instanceof ExcavatorPartBlockEntity part) part.setController(p.pos);
        return Completion.NONE;
    }

    @Override
    public boolean canDismantle()
    {
        return true;
    }

    @Override
    public boolean dismantle(ServerLevel level, ServerPlayer player, BuiltUnits.Unit unit)
    {
        BlockPos pos = unit.origin();
        ExcavatorStructure.remove(level, pos);
        Dismantler.refund(level, player, unit.paid(), pos);
        return true;
    }
}
