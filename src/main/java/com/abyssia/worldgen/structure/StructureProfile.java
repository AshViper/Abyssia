package com.abyssia.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

/**
 * Which seabed structures a group of biomes grows, and how often (a datapack registry,
 * {@code data/<ns>/abyssia/seabed_structure_profile/*.json}).
 * <p>
 * Each entry scatters its structure on its own jittered grid: cells of {@code max_distance} blocks, one candidate
 * per cell, kept {@code min_distance} blocks from its neighbours, realised with {@code chance} (times the profile's
 * {@code density} and the config multipliers). A candidate only stands where the biome at its centre belongs to
 * this profile and the structure's terrain conditions hold; larger tiers win overlaps.
 */
public record StructureProfile(HolderSet<Biome> biomes, float density, List<Entry> structures)
{
    public static final Codec<StructureProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(StructureProfile::biomes),
            Codec.floatRange(0, 16).optionalFieldOf("density", 1f).forGetter(StructureProfile::density),
            Entry.CODEC.listOf().fieldOf("structures").forGetter(StructureProfile::structures)
    ).apply(i, StructureProfile::new));

    public record Entry(ResourceLocation structure, float chance, int minDistance, int maxDistance)
    {
        private static final Codec<Entry> RAW = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("structure").forGetter(Entry::structure),
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Entry::chance),
                Codec.intRange(0, 4096).fieldOf("min_distance").forGetter(Entry::minDistance),
                Codec.intRange(16, 4096).fieldOf("max_distance").forGetter(Entry::maxDistance)
        ).apply(i, Entry::new));
        public static final Codec<Entry> CODEC = RAW.comapFlatMap(
                e -> e.minDistance < e.maxDistance ? DataResult.success(e)
                        : DataResult.error(() -> e.structure + ": min_distance must be below max_distance"),
                e -> e);
    }
}
