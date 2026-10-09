package com.abyssia.hazard;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.habitat.power.HabitatBases;
import com.abyssia.registry.ModBlocks;
import com.abyssia.registry.ModMobEffects;
import com.abyssia.vehicle.Submarine;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AB02 environment hazards of the deep crust (Y &lt; -376), see inbox/specs/AB02-hazards.md. Biome tags
 * {@code abyssia:hazard/toxic|heat|cold} (written by tools/gen_worldgen.py) pick the hazard, the depth band the intensity 1..4.
 * <ul>
 * <li>toxic: gas at the eye; only a sealed rig (helmet tag + chest tag) protects, the tank wears out faster inside it.</li>
 * <li>heat: fire damage; each worn {@code abyssia:hazard/heat_proof} piece takes 25 %, Fire Resistance takes all.</li>
 * <li>cold: cumulative freezing + cold_shock; each worn {@code abyssia:hazard/cold_proof} piece takes 25 %,
 * a heat source within {@link #HEAT_SOURCE_RADIUS} blocks suspends it.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class HazardZone
{
    public enum Type
    {
        NONE, TOXIC, HEAT, COLD;
    }

    public static final TagKey<Biome> TOXIC_BIOMES = biomeTag("hazard/toxic");
    public static final TagKey<Biome> HEAT_BIOMES = biomeTag("hazard/heat");
    public static final TagKey<Biome> COLD_BIOMES = biomeTag("hazard/cold");
    public static final TagKey<Item> TOXIC_PROOF_HEAD = itemTag("hazard/toxic_proof_head");
    public static final TagKey<Item> TOXIC_PROOF_CHEST = itemTag("hazard/toxic_proof_chest");
    public static final TagKey<Item> HEAT_PROOF = itemTag("hazard/heat_proof");
    public static final TagKey<Item> COLD_PROOF = itemTag("hazard/cold_proof");

    /** a thermal vent or molten rock this close suspends the cold */
    public static final int HEAT_SOURCE_RADIUS = 6;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** ticks between two warnings of the same kind for one player */
    private static final long WARN_COOLDOWN = 200;
    /** the tank loses 1 durability on every this many pulses spent in toxic gas */
    private static final int TANK_WEAR_PULSES = 3;
    /** cold pulses in a row that still add to the freezing */
    private static final int MAX_COLD_STREAK = 4;

    private static final class State
    {
        Type last = Type.NONE;
        long warnedAt = Long.MIN_VALUE;
        int streak;
        int pulses;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private HazardZone() {}

    private static TagKey<Biome> biomeTag(String path)
    {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, path));
    }

    private static TagKey<Item> itemTag(String path)
    {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, path));
    }

    /** Intensity 1..4 at a Y (B' = 1, C = 2, D = 3, E = 4), or the config override. */
    public static int intensityAt(double y)
    {
        int override = Config.HAZARD_INTENSITY_OVERRIDE.get();
        if (override > 0) return override;
        if (y >= DeepLayer.BAND_C_TOP_Y) return 1;
        if (y >= DeepLayer.BAND_D_TOP_Y) return 2;
        if (y >= DeepLayer.BAND_E_TOP_Y) return 3;
        return 4;
    }

    /** The hazard at a player's position: toxic from the eye, heat / cold from the feet. */
    public static Type typeAt(ServerLevel level, Player player)
    {
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        if (level.getBiome(eye).is(TOXIC_BIOMES)) return Type.TOXIC;
        BlockPos feet = player.blockPosition();
        if (level.getBiome(feet).is(HEAT_BIOMES)) return Type.HEAT;
        if (level.getBiome(feet).is(COLD_BIOMES)) return Type.COLD;
        return Type.NONE;
    }

    /** True when the helmet and the chest piece together are a sealed breathing rig. */
    public static boolean sealedRig(Player player)
    {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(TOXIC_PROOF_HEAD)
                && player.getItemBySlot(EquipmentSlot.CHEST).is(TOXIC_PROOF_CHEST);
    }

    /** Number of worn armor pieces (0..4) in an item tag. */
    public static int worn(Player player, TagKey<Item> tag)
    {
        int n = 0;
        for (EquipmentSlot slot : ARMOR)
            if (player.getItemBySlot(slot).is(tag)) n++;
        return n;
    }

    /** True when a thermal vent, molten rock or magma block is within {@link #HEAT_SOURCE_RADIUS} blocks of the pos. */
    public static boolean nearHeatSource(Level level, BlockPos pos)
    {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = HEAT_SOURCE_RADIUS;
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                {
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    Block b = level.getBlockState(p).getBlock();
                    if (b == ModBlocks.THERMAL_VENT.get() || b == ModBlocks.MOLTEN_VOLCANIC_ROCK.get() || b == Blocks.MAGMA_BLOCK)
                        return true;
                }
        return false;
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event)
    {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) return;
        int pulse = Config.HAZARD_PULSE_TICKS.get();
        if (player.tickCount % pulse != 0) return;
        if (!Config.HAZARD_ENABLED.get() || level.dimension() != Level.OVERWORLD
                || !DeepLayer.isAbyss(player.getY()) || !DeepLayer.hasDeepLayer(level.dimensionType().effectsLocation())
                || player.isCreative() || player.isSpectator() || !player.isAlive()
                || player.getVehicle() instanceof Submarine
                || HabitatBases.get(level).moduleAt(player.blockPosition().above()) >= 0)
        {
            STATES.remove(player.getUUID());
            return;
        }
        State st = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        Type type = typeAt(level, player);
        if (type == Type.NONE)
        {
            st.last = Type.NONE;
            st.streak = 0;
            st.pulses = 0;
            return;
        }
        int level4 = intensityAt(player.getY());
        float perSecond = pulse / 20.0F;
        boolean protectedNow = switch (type)
        {
            case TOXIC -> toxic(sp, st, level4, perSecond, pulse);
            case HEAT -> heat(sp, level4, perSecond);
            case COLD -> cold(sp, st, level4, perSecond, pulse);
            default -> true;
        };
        if (type != Type.COLD) st.streak = 0;
        warn(sp, st, type, protectedNow, level.getGameTime());
        st.last = type;
    }

    /** @return true when the player is protected */
    private static boolean toxic(ServerPlayer player, State st, int intensity, float perSecond, int pulse)
    {
        if (sealedRig(player))
        {
            if (++st.pulses % TANK_WEAR_PULSES == 0)
                player.getItemBySlot(EquipmentSlot.CHEST).hurtAndBreak(1, player, EquipmentSlot.CHEST);
            return true;
        }
        st.pulses = 0;
        player.addEffect(new MobEffectInstance(MobEffects.POISON, pulse + 40, intensity - 1, false, true));
        if (intensity >= 3)
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, pulse + 40, 0, false, true));
        hurt(player, player.damageSources().magic(), Config.HAZARD_TOXIC_DAMAGE_PER_LEVEL.get() * intensity * perSecond);
        return false;
    }

    private static boolean heat(ServerPlayer player, int intensity, float perSecond)
    {
        if (player.hasEffect(MobEffects.FIRE_RESISTANCE)) return true;
        double factor = 1.0 - 0.25 * worn(player, HEAT_PROOF);
        if (factor <= 0.0) return true;
        hurt(player, player.damageSources().onFire(), Config.HAZARD_HEAT_DAMAGE_PER_LEVEL.get() * intensity * factor * perSecond);
        return false;
    }

    private static boolean cold(ServerPlayer player, State st, int intensity, float perSecond, int pulse)
    {
        if (nearHeatSource(player.level(), player.blockPosition()))
        {
            st.streak = 0;
            return true;
        }
        double factor = 1.0 - 0.25 * worn(player, COLD_PROOF);
        if (factor <= 0.0)
        {
            st.streak = 0;
            return true;
        }
        st.streak = Math.min(MAX_COLD_STREAK, st.streak + 1);
        double cumulative = 1.0 + 0.25 * (st.streak - 1);
        player.addEffect(new MobEffectInstance(ModMobEffects.COLD_SHOCK, pulse + 40, intensity - 1, false, true));
        hurt(player, player.damageSources().freeze(),
                Config.HAZARD_COLD_DAMAGE_PER_LEVEL.get() * intensity * factor * cumulative * perSecond);
        return false;
    }

    private static void hurt(ServerPlayer player, DamageSource source, double amount)
    {
        if (amount > 0.0) player.hurt(source, (float) amount);
    }

    private static void warn(ServerPlayer player, State st, Type type, boolean protectedNow, long now)
    {
        // on entry into a hazard type, then again after the cooldown while the player stays unprotected
        boolean entered = st.last != type;
        if (!entered && protectedNow) return;
        if (now - st.warnedAt < WARN_COOLDOWN) return;
        st.warnedAt = now;
        String key = "message." + Abyssia.MODID + ".hazard." + type.name().toLowerCase(java.util.Locale.ROOT) + (protectedNow ? "_protected" : "");
        player.displayClientMessage(Component.translatable(key), true);
    }

    @SubscribeEvent
    public static void playerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)
    {
        STATES.remove(event.getEntity().getUUID());
    }
}
