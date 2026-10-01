package com.abyssia.worldgen.cave;

import com.abyssia.Abyssia;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Datapack registries for the cave network. Loaded with the other worldgen registries when a world loads, so they
 * are never pinned into level.dat and a datapack can retune or extend caves (new biome profiles, new environments).
 */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class CaveRegistries
{
    public static final ResourceKey<Registry<CaveProfile>> PROFILES = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "cave_profile"));
    public static final ResourceKey<Registry<CaveEnvironment>> ENVIRONMENTS = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "cave_environment"));
    public static final ResourceKey<Registry<CavernTemplate>> CAVERN_TEMPLATES = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "cavern_template"));

    private CaveRegistries() {}

    @SubscribeEvent
    public static void register(DataPackRegistryEvent.NewRegistry event)
    {
        // Server-side only: clients never need them.
        event.dataPackRegistry(PROFILES, CaveProfile.CODEC);
        event.dataPackRegistry(ENVIRONMENTS, CaveEnvironment.CODEC);
        event.dataPackRegistry(CAVERN_TEMPLATES, CavernTemplate.CODEC);
    }
}
