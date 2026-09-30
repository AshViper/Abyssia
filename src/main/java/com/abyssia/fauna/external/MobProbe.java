package com.abyssia.fauna.external;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a throwaway instance of an entity type reveals (its class hierarchy, navigation, breathing, health...), which the
 * registry alone does not. The instance is created with {@link EntityType#create}, read and discarded; it is never
 * added to a level. Results are cached for the whole game session: the entity registry cannot change after startup.
 * A type whose constructor fails (client-only classes, mods expecting a real spawn) is recorded as {@link #failed}.
 */
public record MobProbe(boolean mob, @Nullable String exclusion, boolean waterBody, boolean breathesUnderwater,
                       Navigation navigation, float waterMalus, boolean bucketable, boolean ownable, boolean hostile,
                       float maxHealth, double attackDamage, boolean despawns, boolean failed)
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<EntityType<?>, MobProbe> CACHE = new ConcurrentHashMap<>();

    public enum Navigation
    {
        WATER, AMPHIBIOUS, GROUND, FLYING, OTHER
    }

    /** The cached probe of {@code type}, creating one instance in {@code level} the first time. */
    public static MobProbe of(EntityType<?> type, ServerLevel level)
    {
        return CACHE.computeIfAbsent(type, t -> probe(t, level));
    }

    private static MobProbe probe(EntityType<?> type, ServerLevel level)
    {
        Entity entity;
        try
        {
            entity = type.create(level);
        }
        catch (Exception | LinkageError e)
        {
            LOGGER.warn("External fauna: could not create {} to examine it ({}); treated as unknown", EntityType.getKey(type), e.toString());
            return unexamined();
        }
        if (entity == null) return unexamined();
        try
        {
            return read(entity);
        }
        catch (Exception | LinkageError e)
        {
            LOGGER.warn("External fauna: could not examine {} ({}); treated as unknown", EntityType.getKey(type), e.toString());
            return unexamined();
        }
        finally
        {
            try
            {
                entity.discard();
            }
            catch (Exception | LinkageError ignored)
            {
                // never added to a level: nothing to clean up that matters
            }
        }
    }

    private static MobProbe read(Entity entity)
    {
        String exclusion = null;
        if (entity instanceof Projectile) exclusion = "projectile";
        else if (entity instanceof Npc || entity instanceof Merchant) exclusion = "npc";
        else if (!entity.canChangeDimensions()) exclusion = "boss-like (cannot change dimensions)";
        if (!(entity instanceof Mob mob))
        {
            return new MobProbe(false, exclusion != null ? exclusion : "not a mob", false, false, Navigation.OTHER, 0, false, false, false, 0, 0, false, false);
        }
        float waterMalus = mob.getPathfindingMalus(BlockPathTypes.WATER);
        if (exclusion == null && waterMalus < 0) exclusion = "avoids water (pathfinding)";
        double attack = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0.0;
        return new MobProbe(true, exclusion,
                mob instanceof WaterAnimal || mob.getMobType() == MobType.WATER,
                mob.canBreatheUnderwater(),
                navigation(mob.getNavigation()),
                waterMalus,
                mob instanceof Bucketable,
                mob instanceof OwnableEntity,
                mob instanceof Enemy,
                mob.getMaxHealth(),
                attack,
                mob.removeWhenFarAway(128.0) && !mob.requiresCustomPersistence(),
                false);
    }

    private static Navigation navigation(PathNavigation navigation)
    {
        // AmphibiousPathNavigation and WaterBoundPathNavigation are unrelated classes; ground is checked last
        if (navigation instanceof WaterBoundPathNavigation) return Navigation.WATER;
        if (navigation instanceof AmphibiousPathNavigation) return Navigation.AMPHIBIOUS;
        if (navigation instanceof FlyingPathNavigation) return Navigation.FLYING;
        if (navigation instanceof GroundPathNavigation) return Navigation.GROUND;
        return Navigation.OTHER;
    }

    private static MobProbe unexamined()
    {
        return new MobProbe(false, null, false, false, Navigation.OTHER, 0, false, false, false, 0, 0, true, true);
    }

    /**
     * Needs air: drowns in the deep ocean. {@code canBreatheUnderwater} is what LivingEntity's drowning uses (dolphins
     * return false); the air supply is no guide, as water animals spend theirs out of water.
     */
    public boolean airBreather()
    {
        return this.mob && !this.breathesUnderwater;
    }
}
