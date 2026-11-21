package com.abyssia.habitat.upkeep;

import com.abyssia.Abyssia;
import com.abyssia.habitat.power.HabitatBases;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** BAS01: sneak + use on a habitat module reinforces its shell for good (halves wear and quake damage); once per module. */
public class HullReinforcementItem extends Item
{
    public HullReinforcementItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        // the client does not know the modules: leave it to the server result
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.PASS;
        int module = HabitatBases.get(level).moduleAt(context.getClickedPos());
        if (module < 0) return InteractionResult.PASS;
        var mod = HabitatBases.get(level).module(module);
        if (mod == null || !HabitatUpkeep.wearable(mod.box())) return InteractionResult.PASS;
        String key = "message." + Abyssia.MODID + ".habitat.upkeep";
        if (!HabitatUpkeep.get(level).reinforce(module))
        {
            player.displayClientMessage(Component.translatable(key + ".already_reinforced").append(HabitatUpkeep.status(level, module)), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        level.playSound(null, context.getClickedPos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8f, 0.8f);
        player.displayClientMessage(Component.translatable(key + ".reinforce_done").append(HabitatUpkeep.status(level, module)), true);
        return InteractionResult.CONSUME;
    }
}
