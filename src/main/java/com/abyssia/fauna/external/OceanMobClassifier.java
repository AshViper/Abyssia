package com.abyssia.fauna.external;

import com.abyssia.fauna.FaunaSpawnRule;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.neoforged.neoforge.common.Tags;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether an entity type is a sea animal, and what kind, from everything Forge 1.20.1 exposes without
 * reflection: its registry data (MobCategory, size, spawn placement), a probe instance ({@link MobProbe}: class,
 * navigation, breathing, attributes) and where it already spawns ({@link SpawnEvidence}).
 * <p>
 * Being able to enter water is not being aquatic: no single signal decides, each adds or subtracts points and only a
 * total of at least {@code min_score} (config, default 50) makes a candidate. Some things rule a type out whatever its
 * score (projectiles, NPCs, bosses, lava or water-avoiding mobs). Typical totals: cod/tropical fish/pufferfish 100,
 * squid 80, glow squid 75, dolphin 90 (then dropped as an air breather), guardian 70, drowned -20, turtle -25,
 * axolotl 30.
 */
public final class OceanMobClassifier
{
    // MobCategory
    static final int WATER_CREATURE = 30;
    static final int WATER_AMBIENT = 25;
    static final int UNDERGROUND_WATER = 25;
    static final int AXOLOTLS = 10;
    static final int LAND_CREATURE = -10;
    static final int AIR_AMBIENT = -20;
    static final int MISC = -30;
    // probe
    static final int WATER_BODY = 15;          // WaterAnimal or MobType.WATER
    static final int BREATHES_WATER = 10;
    static final int WATER_NAVIGATION = 20;    // WaterBoundPathNavigation
    static final int AMPHIBIOUS_NAVIGATION = 5;
    static final int WALKS_ON_LAND = -15;      // ground navigation on a body that is not aquatic
    static final int FLIES = -20;
    static final int BUCKETABLE = 5;
    static final int PET = -30;                // OwnableEntity: tamed animals, player companions
    // SpawnPlacements
    static final int PLACED_IN_WATER = 15;
    static final int PLACED_ON_GROUND = -15;
    // natural spawns
    static final int OCEAN_SPAWNS = 10;
    static final int INLAND_ONLY = -15;
    static final int LAND_SPAWNS = -15;

    /** Big or strong: size in blocks, max health, attack damage. */
    static final float LARGE_SIZE = 2.0F;
    static final float LARGE_HEALTH = 50.0F;
    static final double LARGE_ATTACK = 8.0;
    /** Small ambient fish. */
    static final float TINY_SIZE = 0.7F;
    static final float TINY_HEALTH = 6.0F;

    private OceanMobClassifier() {}

    /**
     * @param probe {@code null} when the type was ruled out before being instantiated (see {@code preExclusion})
     */
    public static OceanMobClassification classify(EntityType<?> type, ResourceLocation id, @Nullable MobProbe probe,
                                                  @Nullable String preExclusion, SpawnEvidence evidence)
    {
        List<String> reasons = new ArrayList<>();
        int score = 0;
        MobCategory mobCategory = type.getCategory();
        score += add(reasons, switch (mobCategory)
        {
            case WATER_CREATURE -> WATER_CREATURE;
            case WATER_AMBIENT -> WATER_AMBIENT;
            case UNDERGROUND_WATER_CREATURE -> UNDERGROUND_WATER;
            case AXOLOTLS -> AXOLOTLS;
            case CREATURE -> LAND_CREATURE;
            case AMBIENT -> AIR_AMBIENT;
            case MISC -> MISC;
            default -> 0; // MONSTER: says nothing either way (guardians, drowned, zombies)
        }, "category " + mobCategory.getName());

        SpawnPlacementType placement = SpawnPlacements.getPlacementType(type);
        if (placement == SpawnPlacementTypes.IN_WATER) score += add(reasons, PLACED_IN_WATER, "spawn placement in water");
        else if (placement == SpawnPlacementTypes.ON_GROUND) score += add(reasons, PLACED_ON_GROUND, "spawn placement on ground");

        if (evidence.oceanListings() > 0) score += add(reasons, OCEAN_SPAWNS, "spawns in oceans");
        if (evidence.inlandOnly()) score += add(reasons, INLAND_ONLY, "spawns only inland");
        if (evidence.landListings() > 0) score += add(reasons, LAND_SPAWNS, "land spawn lists");

        String exclusion = preExclusion;
        if (exclusion == null && type.is(Tags.EntityTypes.BOSSES)) exclusion = "boss (#c:bosses)";
        if (exclusion == null && placement == SpawnPlacementTypes.IN_LAVA) exclusion = "lava placement";

        boolean hostile = mobCategory == MobCategory.MONSTER;
        boolean airBreather = false;
        boolean despawns = true;
        float health = 0;
        double attack = 0;
        FaunaSpawnRule.Placement where = FaunaSpawnRule.Placement.OPEN_WATER;
        if (probe != null && !probe.failed())
        {
            if (exclusion == null) exclusion = probe.exclusion();
            if (probe.waterBody()) score += add(reasons, WATER_BODY, "aquatic body (WaterAnimal / MobType.WATER)");
            if (probe.breathesUnderwater()) score += add(reasons, BREATHES_WATER, "breathes underwater");
            switch (probe.navigation())
            {
                case WATER -> score += add(reasons, WATER_NAVIGATION, "swims (water-bound navigation)");
                case AMPHIBIOUS -> score += add(reasons, AMPHIBIOUS_NAVIGATION, "amphibious navigation");
                case GROUND -> {
                    // water animals without swimming pathfinding (squid) keep the default ground navigation
                    if (!probe.waterBody()) score += add(reasons, WALKS_ON_LAND, "walks (ground navigation)");
                }
                case FLYING -> score += add(reasons, FLIES, "flies");
                default -> {}
            }
            if (probe.bucketable()) score += add(reasons, BUCKETABLE, "bucketable");
            if (probe.ownable()) score += add(reasons, PET, "tameable / owned");
            hostile |= probe.hostile();
            airBreather = probe.airBreather();
            despawns = probe.despawns();
            health = probe.maxHealth();
            attack = probe.attackDamage();
            // bottom dwellers: placed on the ground, or walking without a swimming placement
            boolean walks = probe.navigation() == MobProbe.Navigation.GROUND || probe.navigation() == MobProbe.Navigation.AMPHIBIOUS;
            if (placement == SpawnPlacementTypes.ON_GROUND || (walks && placement != SpawnPlacementTypes.IN_WATER)) where = FaunaSpawnRule.Placement.SEABED;
        }

        EntityDimensions size = type.getDimensions();
        float extent = Math.max(size.width(), size.height());
        DeepSeaSpawnCategory category = categorise(probe, mobCategory, hostile, extent, health, attack, evidence);
        return new OceanMobClassification(type, id, score, List.copyOf(reasons), exclusion, category, placement, where,
                airBreather, hostile, despawns, extent, health, evidence, probe != null && !probe.failed());
    }

    private static DeepSeaSpawnCategory categorise(@Nullable MobProbe probe, MobCategory mobCategory, boolean hostile, float extent,
                                                   float health, double attack, SpawnEvidence evidence)
    {
        if (probe == null || probe.failed()) return DeepSeaSpawnCategory.UNKNOWN;
        if (extent >= LARGE_SIZE || health >= LARGE_HEALTH || attack >= LARGE_ATTACK) return DeepSeaSpawnCategory.LARGE_CREATURE;
        if (hostile || (attack > 0 && mobCategory != MobCategory.WATER_AMBIENT)) return DeepSeaSpawnCategory.PREDATOR;
        if (evidence.undergroundWater() || evidence.deepOceanOnly()) return DeepSeaSpawnCategory.DEEP_SEA;
        if (mobCategory == MobCategory.WATER_AMBIENT || (extent <= TINY_SIZE && health <= TINY_HEALTH)) return DeepSeaSpawnCategory.AMBIENT;
        return DeepSeaSpawnCategory.SMALL_CREATURE;
    }

    private static int add(List<String> reasons, int points, String why)
    {
        if (points != 0) reasons.add((points > 0 ? "+" : "") + points + " " + why);
        return points;
    }
}
