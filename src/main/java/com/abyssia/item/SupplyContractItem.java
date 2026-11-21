package com.abyssia.item;

import com.abyssia.quest.SupplyContracts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Locale;

/** QOL01: right-click delivers today's request (see {@link SupplyContracts}); the paper itself carries no data and is kept. */
public class SupplyContractItem extends Item
{
    public SupplyContractItem(Properties properties)
    {
        super(properties);
    }

    /** "Bring 16 X for 4 Y" for the request of that game time */
    public static Component requestText(Player player, long gameTime)
    {
        SupplyContracts.Contract c = SupplyContracts.of(SupplyContracts.dayOf(gameTime), player.getUUID());
        return Component.translatable("message.abyssia.contract.request", c.count(), SupplyContracts.itemOf(c.item()).getDescription(),
                c.rewardCount(), SupplyContracts.itemOf(c.reward()).getDescription());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        SupplyContracts.Result r = SupplyContracts.redeem(sp);
        Component msg = r == SupplyContracts.Result.MISSING ? requestText(sp, level.getGameTime())
                : Component.translatable("message.abyssia.contract." + r.name().toLowerCase(Locale.ROOT));
        sp.displayClientMessage(msg, true);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
