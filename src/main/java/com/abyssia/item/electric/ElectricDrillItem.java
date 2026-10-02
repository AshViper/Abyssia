package com.abyssia.item.electric;

import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Electric abyssal drill: pickaxe + shovel at the abyssal drill tier, 500 FE per block (hardness > 0). Sneaking mines
 * the 3x3 plane facing the player (each block fires BlockEvent.BreakEvent through the game mode, costs 500 FE).
 */
public class ElectricDrillItem extends PickaxeItem implements ElectricTools.Electric
{
    /** True while the 3x3 neighbours are being broken (those breaks come back into this item and must not recurse). */
    private static boolean areaBreaking;

    private final Tier tier;

    public ElectricDrillItem(Tier tier, Properties props)
    {
        super(tier, 1, -3.0F, props);
        this.tier = tier;
    }

    @Override public int capacity() { return ElectricTools.DRILL_CAPACITY; }

    private static boolean digs(BlockState state)
    {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state)
    {
        if (ElectricTools.getEnergy(stack) <= 0) return 0.0F;
        return digs(state) ? tier.getSpeed() : 1.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(BlockState state)
    {
        return digs(state) && TierSortingRegistry.isCorrectTierForDrops(tier, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) { return isCorrectToolForDrops(state); }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action)
    {
        return ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(action) || ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action);
    }

    /** Server side, before the center block is removed (its BreakEvent has already passed): break the 3x3 neighbours. */
    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player)
    {
        if (areaBreaking || !(player instanceof ServerPlayer sp) || !player.isShiftKeyDown()
                || ElectricTools.getEnergy(stack) <= 0) return false;
        Level level = player.level();
        BlockState center = level.getBlockState(pos);
        if (!digs(center)) return false;
        HitResult hit = player.pick(player.getAttributeValue(ForgeMod.BLOCK_REACH.get()) + 1.0, 1.0F, false);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) return false;
        Direction.Axis axis = bhr.getDirection().getAxis();
        float centerHardness = center.getDestroySpeed(level, pos);
        areaBreaking = true;
        try
        {
            for (int a = -1; a <= 1; a++)
            {
                for (int b = -1; b <= 1; b++)
                {
                    if (a == 0 && b == 0) continue;
                    // keep the center block's own 500 FE in reserve (charged in mineBlock)
                    if (!sp.getAbilities().instabuild && ElectricTools.getEnergy(stack) < 2 * ElectricTools.DRILL_COST) return false;
                    BlockPos p = switch (axis)
                    {
                        case X -> pos.offset(0, a, b);
                        case Y -> pos.offset(a, 0, b);
                        case Z -> pos.offset(a, b, 0);
                    };
                    BlockState state = level.getBlockState(p);
                    float hardness = state.getDestroySpeed(level, p);
                    if (state.isAir() || hardness <= 0.0F || hardness > centerHardness || !isCorrectToolForDrops(state)) continue;
                    if (sp.gameMode.destroyBlock(p)) ElectricTools.consume(stack, ElectricTools.DRILL_COST, sp);
                }
            }
        }
        finally { areaBreaking = false; }
        return false;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity)
    {
        if (!level.isClientSide && !areaBreaking && state.getDestroySpeed(level, pos) > 0.0F)
            ElectricTools.consume(stack, ElectricTools.DRILL_COST, entity);
        return true;
    }

    @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) { return true; }
    @Override public boolean isRepairable(ItemStack stack) { return false; }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return ElectricTools.barWidth(stack); }
    @Override public int getBarColor(ItemStack stack) { return ElectricTools.BAR_COLOR; }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack)
    {
        return ElectricTools.attackModifiers(slot, stack, super.getAttributeModifiers(slot, stack));
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity)
    {
        if (ElectricTools.getEnergy(stack) <= 0) ElectricTools.emptyMessage(entity);
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged)
    {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack)
    {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag)
    {
        ElectricTools.tooltip(stack, lines);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) { return new StackEnergy(stack); }
}
