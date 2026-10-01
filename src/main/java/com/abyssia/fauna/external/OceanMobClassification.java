package com.abyssia.fauna.external;

import com.abyssia.fauna.FaunaSpawnRule;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.SpawnPlacementType;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The verdict on one entity type ({@link OceanMobClassifier}): its score with the reasons, what rules it out (if
 * anything), its deep-sea category and the traits the spawn profile needs.
 *
 * @param exclusion  why it can never be a candidate whatever its score (null: none)
 * @param placement  its registered spawn placement (NO_RESTRICTIONS also when none is registered)
 * @param where      where it goes in the water column: open water, or the seabed for bottom walkers
 * @param extent     largest of its width and height, in blocks
 * @param examined   a probe instance could be created and read
 */
public record OceanMobClassification(EntityType<?> type, ResourceLocation id, int score, List<String> reasons,
                                     @Nullable String exclusion, DeepSeaSpawnCategory category,
                                     SpawnPlacementType placement, FaunaSpawnRule.Placement where,
                                     boolean airBreather, boolean hostile, boolean despawns, float extent,
                                     float maxHealth, SpawnEvidence evidence, boolean examined)
{
    /** A sea animal by automatic detection alone. */
    public boolean detected(int minScore)
    {
        return this.exclusion == null && this.examined && this.score >= minScore;
    }

    public String describe()
    {
        return String.format("%s score %d [%s]%s", this.id, this.score, String.join(", ", this.reasons),
                this.exclusion != null ? " excluded: " + this.exclusion : "");
    }
}
