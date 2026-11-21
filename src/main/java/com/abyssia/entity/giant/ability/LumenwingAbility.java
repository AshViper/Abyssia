package com.abyssia.entity.giant.ability;

import com.abyssia.entity.giant.GiantFauna;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;

/**
 * Lumenwing (friendly): a player with shears who uses them on the body or a part harvests 1 to 2 lumen filaments, once per 6000
 * ticks per animal (saved). Nothing happens while the item is not registered. A kill drops nothing of the sort.
 */
final class LumenwingAbility extends GiantAbility
{
    static final int WAIT = 6000;
    private long readyAt;

    LumenwingAbility(GiantFauna g) { super(g); }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand)
    {
        ItemStack tool = player.getItemInHand(hand);
        if (!(tool.getItem() instanceof ShearsItem)) return InteractionResult.PASS;
        if (g.level().isClientSide) return InteractionResult.SUCCESS;
        Item filament = item("lumen_filament");
        if (filament == null) return InteractionResult.PASS;
        long now = g.level().getGameTime();
        if (now < readyAt)
        {
            player.displayClientMessage(Component.translatable("message.abyssia.giant.shear_wait", (readyAt - now) / 20), true);
            return InteractionResult.CONSUME;
        }
        readyAt = now + WAIT;
        tool.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        int n = 1 + g.getRandom().nextInt(2);
        ItemEntity drop = new ItemEntity(g.level(), player.getX(), player.getEyeY(), player.getZ(), new ItemStack(filament, n));
        drop.setDefaultPickUpDelay();
        g.level().addFreshEntity(drop);
        g.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
        return InteractionResult.CONSUME;
    }

    @Override
    public void save(CompoundTag tag)
    {
        tag.putLong("ShearReadyAt", readyAt);
    }

    @Override
    public void load(CompoundTag tag)
    {
        readyAt = tag.getLong("ShearReadyAt");
    }
}
