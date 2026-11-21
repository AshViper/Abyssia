package com.abyssia.vehicle;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

/** Right-click places an exosuit on the ground / seabed block in reach, facing the player's way. */
public class ExosuitItem extends Item
{
    public ExosuitItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        boolean underwater = player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value());
        HitResult hit = getPlayerPOVHitResult(level, player, underwater ? ClipContext.Fluid.NONE : ClipContext.Fluid.ANY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        Vec3 at = hit.getLocation();

        Exosuit suit = VehicleContent.EXOSUIT.get().create(level);
        if (suit == null) return InteractionResultHolder.fail(stack);
        suit.setYRot(player.getYRot());
        suit.yRotO = player.getYRot();
        boolean placed = false;
        for (double up : new double[]{0.0, 1.0})
        {
            suit.setPos(at.add(0.0, up, 0.0));
            if (level.noCollision(suit, suit.getBoundingBox()))
            {
                placed = true;
                break;
            }
        }
        if (!placed) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide)
        {
            level.addFreshEntity(suit);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, suit.position());
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
