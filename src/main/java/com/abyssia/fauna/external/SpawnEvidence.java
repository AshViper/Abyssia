package com.abyssia.fauna.external;

import com.abyssia.Abyssia;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.Tags;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Where an entity already spawns naturally in the loaded worlds: every biome's spawn lists (as modified by Forge biome
 * modifiers, i.e. including other mods' additions) and every structure's spawn overrides (ocean monument guardians).
 * Abyssia's own biomes are left out so the deep ocean's lists do not vouch for themselves.
 * <p>
 * Spawning in an ocean (under a water category, or from an ocean structure) is strong evidence for a sea animal;
 * spawning only inland (rivers, lakes, caves) or under a land category counts against it. No listing at all says nothing: mods may spawn their animals their own way.
 */
public record SpawnEvidence(int oceanListings, int deepOceanListings, int inlandWaterListings, int landListings,
                            boolean undergroundWater, @Nullable MobSpawnSettings.SpawnerData sample)
{
    public static final SpawnEvidence NONE = new SpawnEvidence(0, 0, 0, 0, false, null);

    public boolean listed()
    {
        return this.oceanListings + this.inlandWaterListings + this.landListings > 0;
    }

    /** Listed somewhere, but never in an ocean. */
    public boolean inlandOnly()
    {
        return this.listed() && this.oceanListings == 0;
    }

    /** Its ocean listings are all deep oceans (deep-sea evidence). */
    public boolean deepOceanOnly()
    {
        return this.oceanListings > 0 && this.deepOceanListings == this.oceanListings;
    }

    public String describe()
    {
        if (!this.listed()) return "no natural spawn listing";
        return String.format("listed in %d ocean (%d deep), %d inland water, %d land spawn lists%s", this.oceanListings, this.deepOceanListings,
                this.inlandWaterListings, this.landListings, this.undergroundWater ? ", underground water" : "");
    }

    /** Scans every biome and structure once (at server start or after a datapack reload, when tags are bound). */
    public static Map<EntityType<?>, SpawnEvidence> scan(RegistryAccess access)
    {
        Map<EntityType<?>, Builder> found = new HashMap<>();
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        biomes.holders().forEach(biome -> {
            if (biome.key().location().getNamespace().equals(Abyssia.MODID)) return;
            Habitat habitat = Habitat.of(biome);
            MobSpawnSettings settings = biome.value().getMobSettings();
            for (MobCategory category : MobCategory.values())
            {
                for (MobSpawnSettings.SpawnerData data : settings.getMobs(category).unwrap())
                {
                    found.computeIfAbsent(data.type, t -> new Builder()).add(habitat, category, data, false);
                }
            }
        });
        Registry<Structure> structures = access.registryOrThrow(Registries.STRUCTURE);
        structures.holders().forEach(structure -> {
            if (structure.value().spawnOverrides().isEmpty()) return;
            Habitat habitat = Habitat.of(structure.value().biomes());
            for (Map.Entry<MobCategory, net.minecraft.world.level.levelgen.structure.StructureSpawnOverride> e : structure.value().spawnOverrides().entrySet())
            {
                for (MobSpawnSettings.SpawnerData data : e.getValue().spawns().unwrap())
                {
                    found.computeIfAbsent(data.type, t -> new Builder()).add(habitat, e.getKey(), data, true);
                }
            }
        });
        Map<EntityType<?>, SpawnEvidence> result = new HashMap<>();
        found.forEach((type, b) -> result.put(type, b.build()));
        return result;
    }

    static boolean isWaterCategory(MobCategory category)
    {
        return category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS;
    }

    /** What kind of place a biome (or a structure's set of biomes) is. */
    private record Habitat(boolean ocean, boolean deepOcean, boolean water)
    {
        static Habitat of(Holder<Biome> biome)
        {
            boolean deep = biome.is(BiomeTags.IS_DEEP_OCEAN);
            boolean ocean = deep || biome.is(BiomeTags.IS_OCEAN);
            return new Habitat(ocean, deep, ocean || biome.is(BiomeTags.IS_RIVER) || biome.is(Tags.Biomes.IS_AQUATIC));
        }

        /** A structure's biomes: oceanic when any is, deep when every oceanic one is deep. */
        static Habitat of(Iterable<Holder<Biome>> biomes)
        {
            boolean ocean = false;
            boolean allDeep = true;
            boolean water = false;
            for (Holder<Biome> biome : biomes)
            {
                Habitat h = of(biome);
                ocean |= h.ocean;
                water |= h.water;
                if (h.ocean && !h.deepOcean) allDeep = false;
            }
            return new Habitat(ocean, ocean && allDeep, water);
        }
    }

    private static final class Builder
    {
        int ocean;
        int deep;
        int inland;
        int land;
        boolean underground;
        MobSpawnSettings.SpawnerData sample;
        boolean sampleFromOcean;

        /**
         * An ocean listing needs a water category or a structure (monument guardians are monsters): ocean biomes
         * also list land monsters for their islands and shores, which says nothing about the sea.
         */
        void add(Habitat habitat, MobCategory category, MobSpawnSettings.SpawnerData data, boolean structure)
        {
            boolean waterCategory = isWaterCategory(category);
            if (habitat.ocean() && (waterCategory || structure))
            {
                this.ocean++;
                if (habitat.deepOcean()) this.deep++;
            }
            else if (waterCategory || (habitat.water() && !habitat.ocean()))
            {
                // rivers, and water-category lists of dry biomes (their caves' pools and lakes)
                this.inland++;
            }
            else
            {
                this.land++;
            }
            if (category == MobCategory.UNDERGROUND_WATER_CREATURE) this.underground = true;
            boolean fromOcean = habitat.ocean() && (waterCategory || structure);
            if (this.sample == null || (fromOcean && !this.sampleFromOcean))
            {
                this.sample = data;
                this.sampleFromOcean = fromOcean;
            }
        }

        SpawnEvidence build()
        {
            return new SpawnEvidence(this.ocean, this.deep, this.inland, this.land, this.underground, this.sample);
        }
    }
}
