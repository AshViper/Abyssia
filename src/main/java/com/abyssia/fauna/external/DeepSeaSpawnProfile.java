package com.abyssia.fauna.external;

import com.abyssia.fauna.FaunaSpawnRule;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

/**
 * How one external animal spawns in the deep ocean: its category defaults, adjusted by what is known about it (natural
 * group size, body size) and then by the data rules that match it. Per biome it becomes a {@link FaunaSpawnRule}
 * ({@link #toRule}) evaluated by the fauna spawner like any native species.
 *
 * @param weight            relative weight before biome multipliers (native rules' scale)
 * @param clearance         open water needed around it (large bodies)
 * @param minPlayerDistance never closer than this to a player (large animals)
 * @param biomes            only these biome ids / #tags (empty: wherever the biome rules allow its category)
 * @param source            detected | whitelist | data
 */
public record DeepSeaSpawnProfile(EntityType<?> entityType, ResourceLocation id, int score, DeepSeaSpawnCategory category,
                                  double weight, int minCount, int maxCount, FaunaSpawnRule.Depth depth, String depthLabel,
                                  FaunaSpawnRule.Placement placement, float caveFactor, int capCount, int capRadius,
                                  int clearance, int minPlayerDistance, boolean hostile,
                                  SpawnPlacementType spawnPlacement, List<String> biomes, String source)
{
    private static final int MAX_CLEARANCE = 6;

    static DeepSeaSpawnProfile of(OceanMobClassification c, List<ExternalSpawnRules.EntityRule> rules, String source,
                                  double weightMultiplier, int largeMinDistance)
    {
        DeepSeaSpawnCategory category = c.category();
        for (ExternalSpawnRules.EntityRule r : rules) category = r.category().orElse(category);

        FaunaSpawnRule.Depth depth = category.depth.depth();
        String depthLabel = category.depth.getSerializedName();
        double weight = category.weight * weightMultiplier;
        int min = category.groupMin;
        int max = category.groupMax;
        // an animal that already spawns in groups keeps its own group size, within the category's bounds
        if (c.evidence().sample() != null)
        {
            min = Mth.clamp(c.evidence().sample().minCount, 1, category.groupMax);
            max = Mth.clamp(c.evidence().sample().maxCount, min, category.groupMax);
        }
        FaunaSpawnRule.Placement placement = c.where();
        float caveFactor = category.caveFactor;
        int capCount = category.capCount;
        int capRadius = category.capRadius;
        boolean large = category == DeepSeaSpawnCategory.LARGE_CREATURE;
        int clearance = large ? Mth.clamp(Mth.ceil(c.extent() / 2.0F) + 1, 2, MAX_CLEARANCE) : 0;
        int minDistance = large ? largeMinDistance : 0;
        List<String> biomes = List.of();

        for (ExternalSpawnRules.EntityRule r : rules)
        {
            if (r.depthBand().isPresent())
            {
                depth = r.depthBand().get();
                depthLabel = r.depthMetres().isPresent() ? "custom" : r.depth().get().getSerializedName();
            }
            weight *= r.weightMultiplier();
            if (r.group().isPresent())
            {
                min = r.group().get().min();
                max = Math.max(min, r.group().get().max());
            }
            if (r.cap().isPresent())
            {
                capCount = r.cap().get().count();
                capRadius = r.cap().get().radius();
            }
            placement = r.placement().orElse(placement);
            if (!r.biomes().isEmpty()) biomes = r.biomes();
            minDistance = r.minPlayerDistance().orElse(minDistance);
        }
        if (!c.despawns())
        {
            // never despawns: keep it to a rare one per wide area
            capCount = 1;
            capRadius = Math.max(capRadius, 128);
            weight *= 0.25;
        }
        return new DeepSeaSpawnProfile(c.type(), c.id(), c.score(), category, weight, min, max, depth, depthLabel, placement,
                caveFactor, capCount, capRadius, clearance, minDistance, c.hostile(), c.placement(), biomes, source);
    }

    /** Whether the profile's own biome restriction admits {@code biome}. */
    public boolean allows(Holder<Biome> biome)
    {
        return this.biomes.isEmpty() || ExternalSpawnRules.matches(biome, this.biomes);
    }

    /**
     * The spawn rule in one biome ({@code multiplier} = biome x category multipliers). Weights are whole numbers like
     * the native ones; anything allowed there keeps at least 1.
     */
    public FaunaSpawnRule toRule(double multiplier)
    {
        int w = (int) Math.max(1, Math.round(this.weight * multiplier));
        return new FaunaSpawnRule(this.entityType, this.category.getSerializedName(), w, new FaunaSpawnRule.Range(this.minCount, this.maxCount),
                this.depth, this.placement, this.caveFactor, 1.0F, 15, List.of(), List.of(), List.of(), this.clearance,
                new FaunaSpawnRule.Cap(this.capCount, this.capRadius));
    }

    public String describe()
    {
        return String.format("%s: %s, score %d, depth %s (%.0f-%.0f m), %s, weight %.1f, group %d-%d, cap %d/%d%s%s [%s]",
                this.id, this.category.getSerializedName(), this.score, this.depthLabel, this.depth.min(), this.depth.max(),
                this.placement.getSerializedName(), this.weight, this.minCount, this.maxCount, this.capCount, this.capRadius,
                this.minPlayerDistance > 0 ? ", >= " + this.minPlayerDistance + " blocks from players" : "",
                this.hostile ? ", hostile" : "", this.source);
    }
}
