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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** EN01 server logic: enchantments, debuffs from fauna attacks, cold_shock mining penalty. Fog is client side. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EffectEvents
{
    private static final UUID DEEP_SWIMMER_ID = UUID.fromString("8f0b8e52-6a3c-4b1e-9d57-3a1c2e4b7a10");
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
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;

        // deep_swimmer: SWIM_SPEED only acts in water, so the modifier is harmless on land.
        AttributeInstance swim = player.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (swim != null)
        {
            int lvl = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.DEEP_SWIMMER.get(), player.getItemBySlot(EquipmentSlot.FEET));
            AttributeModifier cur = swim.getModifier(DEEP_SWIMMER_ID);
            double want = 0.10 * lvl;
            if (cur != null && (lvl == 0 || cur.getAmount() != want)) swim.removeModifier(DEEP_SWIMMER_ID);
            if (lvl > 0 && (cur == null || cur.getAmount() != want))
                swim.addTransientModifier(new AttributeModifier(DEEP_SWIMMER_ID, "Deep Swimmer", want, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }

        // abyssal_focus: Deep Sight while the helmet is worn in the deep layer.
        if (player.tickCount % 20 == 0)
        {
            MobEffect sight = ModMobEffects.DEEP_SIGHT.get();
            int lvl = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ABYSSAL_FOCUS.get(), player.getItemBySlot(EquipmentSlot.HEAD));
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

    /** Fauna attacks inflict debuffs; the damage itself is untouched. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event)
    {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker != event.getSource().getDirectEntity()) return;
        LivingEntity victim = event.getEntity();
        if (attacker instanceof DeepSeaShark || attacker instanceof GiantSquid || attacker instanceof Anglerfish)
            victim.addEffect(new MobEffectInstance(ModMobEffects.PRESSURE_FATIGUE.get(), 5 * 20));
        else if (attacker instanceof HelmetJelly)
            victim.addEffect(new MobEffectInstance(ModMobEffects.COLD_SHOCK.get(), 4 * 20));
        else if (attacker instanceof AtollaJelly)
            victim.addEffect(new MobEffectInstance(ModMobEffects.MURK.get(), 6 * 20));
    }

    /** thermal_catch: raw meat from Abyssia fish becomes cooked meat. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event)
    {
        if (!event.getEntity().getType().is(ModTags.FISH)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)) return;
        if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.THERMAL_CATCH.get(), killer.getMainHandItem()) <= 0) return;
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
        MobEffectInstance shock = event.getEntity().getEffect(ModMobEffects.COLD_SHOCK.get());
        if (shock != null) event.setNewSpeed(event.getNewSpeed() * Math.max(0f, 1f - 0.15f * (shock.getAmplifier() + 1)));
    }
}
