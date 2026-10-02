package com.abyssia.item.electric;

import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IForgeShearable;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Electric abyssal cutter: abyssal_cutter's sword damage, 250 FE per hit / block, and it acts as shears (fast on
 * leaves, wool, webs, vines, kelp, seagrass, coral). Shears drops come from the loot tables (Forge checks the
 * SHEARS_DIG tool action), so blocks break the normal way; FE is charged in mineBlock.
 */
public class ElectricCutterItem extends SwordItem implements ElectricTools.Electric
{
    public ElectricCutterItem(Tier tier, Properties props)
    {
        super(tier, 2, -1.8F, props);
    }

    @Override public int capacity() { return ElectricTools.CUTTER_CAPACITY; }

    private static boolean plantLike(BlockState state)
    {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL) || state.is(BlockTags.CORALS)
                || state.is(BlockTags.WALL_CORALS) || state.is(BlockTags.CORAL_BLOCKS)
                || state.getBlock() instanceof IForgeShearable || state.is(BlockTags.CAVE_VINES)
                || state.is(Blocks.COBWEB) || state.is(Blocks.VINE) || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS) || state.is(Blocks.HANGING_ROOTS);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state)
    {
        if (ElectricTools.getEnergy(stack) <= 0) return 0.0F;
        if (state.is(Blocks.COBWEB) || state.is(BlockTags.LEAVES)) return 15.0F;
        if (plantLike(state)) return state.is(BlockTags.WOOL) ? 5.0F : 15.0F;
        return super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(BlockState state)
    {
        return state.is(Blocks.COBWEB) || plantLike(state) || super.isCorrectToolForDrops(state);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action)
    {
        return ToolActions.DEFAULT_SHEARS_ACTIONS.contains(action) || ToolActions.DEFAULT_SWORD_ACTIONS.contains(action);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity)
    {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) > 0.0F)
            ElectricTools.consume(stack, ElectricTools.CUTTER_COST, entity);
        return true;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        if (!attacker.level().isClientSide) ElectricTools.consume(stack, ElectricTools.CUTTER_COST, attacker);
        return true;
    }

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
