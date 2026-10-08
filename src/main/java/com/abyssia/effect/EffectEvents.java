package com.abyssia.effect;

import com.abyssia.Abyssia;
import com.abyssia.entity.AtollaJelly;
import com.abyssia.entity.GiantSquid;
import com.abyssia.entity.HelmetJelly;
import com.abyssia.entity.Anglerfish;
import com.abyssia.entity.DeepSeaShark;
import com.abyssia.registry.ModEnchantments;
import com.abyssia.registry.ModItems;
import com.abyssia.registry.ModMobEffects;
import com.abyssia.registry.ModTags;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * EN01 server logic: abyssal_focus, thermal_catch, debuffs from fauna attacks, cold_shock mining penalty. Fog is client side.
 * deep_swimmer's swim bonus is an attributes effect in data/abyssia/enchantment/deep_swimmer.json.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class EffectEvents
{
    /** Duration of the focus-granted Deep Sight; refreshed every second, so it vanishes within ~2 s of removing the helmet. */
    private static final int FOCUS_TICKS = 40;

    private static final Map<Supplier<? extends Item>, Supplier<? extends Item>> COOKED = Map.of(
            ModItems.ABYSSAL_FISH_FILLET, ModItems.COOKED_ABYSSAL_FISH,
            ModItems.VIPER_FLESH, ModItems.COOKED_VIPER_FLESH,
            ModItems.BLOBFISH_FLESH, ModItems.COOKED_BLOBFISH,
            ModItems.ANGLER_FLESH, ModItems.COOKED_ANGLER_FLESH,
            ModItems.EELPOUT_FLESH, ModItems.COOKED_EELPOUT_FLESH,
            ModItems.SHARK_FLESH, ModItems.COOKED_SHARK_FLESH);

    private EffectEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        // abyssal_focus: Deep Sight while the helmet is worn in the deep layer.
        if (player.tickCount % 20 == 0)
        {
            Holder<MobEffect> sight = ModMobEffects.DEEP_SIGHT;
            int lvl = ModEnchantments.level(player.level(), ModEnchantments.ABYSSAL_FOCUS, player.getItemBySlot(EquipmentSlot.HEAD));
            boolean active = lvl > 0 && DeepLayer.isDeep(player.level(), player.getY());
            MobEffectInstance cur = player.getEffect(sight);
            if (active)
            {
                // A stronger or longer real effect (potion, food) stays as it is.
                if (cur == null || cur.isAmbient() || cur.getAmplifier() < lvl - 1)
                    player.addEffect(new MobEffectInstance(sight, FOCUS_TICKS, lvl - 1, true, false, true));
            }
            else if (cur != null && cur.isAmbient() && cur.getDuration() <= FOCUS_TICKS)
                player.removeEffect(sight);
        }
    }

    /**
     * Fauna attacks inflict debuffs; the damage itself is untouched.
     * LivingDamageEvent.Post = once per hit that actually landed, like Forge's LivingHurtEvent (Incoming also fires on i-frame hits).
     */
    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post event)
    {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker != event.getSource().getDirectEntity()) return;
        LivingEntity victim = event.getEntity();
        if (attacker instanceof DeepSeaShark || attacker instanceof GiantSquid || attacker instanceof Anglerfish)
            victim.addEffect(new MobEffectInstance(ModMobEffects.PRESSURE_FATIGUE, 5 * 20));
        else if (attacker instanceof HelmetJelly)
            victim.addEffect(new MobEffectInstance(ModMobEffects.COLD_SHOCK, 4 * 20));
        else if (attacker instanceof AtollaJelly)
            victim.addEffect(new MobEffectInstance(ModMobEffects.MURK, 6 * 20));
    }

    /** thermal_catch: raw meat from Abyssia fish becomes cooked meat. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event)
    {
        if (!event.getEntity().getType().is(ModTags.FISH)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)) return;
        if (ModEnchantments.level(killer.level(), ModEnchantments.THERMAL_CATCH, killer.getMainHandItem()) <= 0) return;
        for (ItemEntity drop : event.getDrops())
        {
            ItemStack stack = drop.getItem();
            for (Map.Entry<Supplier<? extends Item>, Supplier<? extends Item>> e : COOKED.entrySet())
            {
                if (stack.is(e.getKey().get()))
                {
                    drop.setItem(new ItemStack(e.getValue().get(), stack.getCount()));
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        MobEffectInstance shock = event.getEntity().getEffect(ModMobEffects.COLD_SHOCK);
        if (shock != null) event.setNewSpeed(event.getNewSpeed() * Math.max(0f, 1f - 0.15f * (shock.getAmplifier() + 1)));
    }
}
