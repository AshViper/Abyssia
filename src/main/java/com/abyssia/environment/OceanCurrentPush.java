package com.abyssia.environment;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.entity.DriftingMedusa;
import com.abyssia.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lets the {@link OceanCurrentManager} field carry what is in the water. Vanilla water drag pulls an entity's
 * velocity toward zero by a factor {@code f} per tick; adding {@code current * (1 - f)} each tick shifts that pull
 * toward the moving water instead, so a passive entity settles at exactly the current's speed while a swimmer keeps
 * its own propulsion on top of the flow.
 * <p>
 * Whoever simulates an entity pushes it: the server for mobs, items, XP orbs and unsteered boats, the client
 * ({@code client.OceanCurrentClient}) for the local player and the boat it steers. Only ocean water is carried
 * (ocean biomes, this mod's biomes and the deep layer), so rivers, lakes and pools stay still.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public final class OceanCurrentPush
{
    /** 1 - water drag per tick, per kind of entity (living 0.8, sprint-swimming 0.9, boats 0.9, items/XP ~0.97). */
    private static final double LIVING_DRAG = 0.2, SPRINT_SWIM_DRAG = 0.1, BOAT_DRAG = 0.1, LOOSE_DRAG = 0.03;

    private OceanCurrentPush() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START || !(event.level instanceof ServerLevel level)) return;
        if (!Config.CURRENT_PUSH_ENTITIES.get() || (Config.CURRENT_PUSH_SCALE.get() <= 0.0 && !Config.NATURAL_CURRENTS.get())) return;
        for (Entity entity : level.getAllEntities())
        {
            // Players and what they steer are simulated by their client, which pushes them itself.
            if (entity instanceof Player || entity.getControllingPassenger() instanceof Player) continue;
            push(entity);
        }
    }

    /**
     * Pushes one entity along the current if it is a kind the water carries and it is in ocean water: the gentle field
     * everywhere, plus a {@link NaturalCurrents} stream where there is one.
     */
    public static void push(Entity entity)
    {
        double drag = drag(entity);
        if (drag <= 0.0 || entity.isRemoved() || entity.isPassenger() || entity.noPhysics || !entity.isInWater()) return;
        if (entity.getType().is(ModTags.IGNORES_OCEAN_CURRENT)) return;
        double exposure = entity instanceof CurrentResistant r ? 1.0 - Math.min(1f, Math.max(0f, r.getCurrentResistance())) : 1.0;
        if (exposure <= 0.0) return;
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        if (!isOceanWater(level, pos)) return;

        Vec3 push = Config.CURRENT_PUSH_SCALE.get() > 0.0 ? fieldPush(entity, level, pos, drag) : Vec3.ZERO;
        push = push.add(naturalPush(entity, level, drag));
        if (push == Vec3.ZERO) return;
        push = push.scale(exposure);
        // Boats float on the surface: up/downwelling would only fight their buoyancy.
        double vy = entity instanceof Boat ? 0.0 : push.y;
        entity.setDeltaMovement(entity.getDeltaMovement().add(push.x, vy, push.z));
    }

    /** The {@link OceanCurrentManager} field's share of this tick's push. */
    private static Vec3 fieldPush(Entity entity, Level level, BlockPos pos, double drag)
    {
        Vec3 current = OceanCurrentManager.getCurrent(level, pos);
        // The client knows the viewer's cave surroundings (the same shelter and passage stream the particles feel);
        // CaveAmbience is client-thread state, so the integrated server must not read it.
        if (level.isClientSide && Config.CAVE_CURRENT_EFFECTS.get())
        {
            current = current.scale(CaveAmbience.currentShelter()).add(CaveAmbience.passageFlow(entity.getX(), entity.getY(), entity.getZ()));
        }
        return current.scale(Config.CURRENT_PUSH_SCALE.get() * drag);
    }

    /**
     * A natural stream's share: the same drag-compensating push toward the stream's speed (strength x falloff x
     * max_speed), but never beyond it, so nothing is flung faster than the stream however long it rides it. Swimming
     * against it still works: the push is added to the swimmer's own propulsion.
     */
    private static Vec3 naturalPush(Entity entity, Level level, double drag)
    {
        CurrentData data = NaturalCurrents.getCurrentAt(level, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
        if (!data.isPresent()) return Vec3.ZERO;
        double target = data.getLocalStrength() * NaturalCurrents.maxSpeed(level);
        double along = entity.getDeltaMovement().dot(data.getDirection());
        if (along >= target) return Vec3.ZERO;
        return data.getDirection().scale(Math.min(target * drag, target - along));
    }

    private static double drag(Entity entity)
    {
        if (entity instanceof Player player)
        {
            if (player.isSpectator() || player.getAbilities().flying) return 0.0;
            return player.isSprinting() ? SPRINT_SWIM_DRAG : LIVING_DRAG;
        }
        // Drifting medusae ride their own regional current.
        if (entity instanceof DriftingMedusa) return 0.0;
        if (entity instanceof LivingEntity living) return living.isSprinting() ? SPRINT_SWIM_DRAG : LIVING_DRAG;
        if (entity instanceof Boat) return BOAT_DRAG;
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) return LOOSE_DRAG;
        return 0.0;
    }

    private static boolean isOceanWater(Level level, BlockPos pos)
    {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.is(BiomeTags.IS_OCEAN)
                || biome.unwrapKey().map(k -> k.location().getNamespace().equals(Abyssia.MODID)).orElse(false);
    }
}
