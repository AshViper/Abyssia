package com.abyssia.worldgen.structure;

import com.abyssia.Abyssia;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Datapack registries for biome-specific seabed structures, loaded with the other worldgen registries when a world
 * loads (server-side only), so a datapack can retune, add or remove structures and profiles without code.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class StructureRegistries
{
    public static final ResourceKey<Registry<SeabedStructure>> STRUCTURES = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "seabed_structure"));
    public static final ResourceKey<Registry<StructureProfile>> PROFILES = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "seabed_structure_profile"));

    private StructureRegistries() {}

    @SubscribeEvent
    public static void register(DataPackRegistryEvent.NewRegistry event)
    {
        event.dataPackRegistry(STRUCTURES, SeabedStructure.CODEC);
        event.dataPackRegistry(PROFILES, StructureProfile.CODEC);
    }
}
