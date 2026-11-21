package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.entity.giant.GiantSpec;
import com.abyssia.entity.giant.GiantSpecies;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * GNT01: the giant wild animals (30+ blocks, see com.abyssia.entity.giant). Where each lives is datapack data
 * (data/abyssia/giant_spawns), placed by {@link com.abyssia.fauna.GiantSpawner}. Adding a species: a {@link GiantSpec} in
 * {@link GiantSpecies#SPECS}, a renderer model ({@code GiantRenderer.registerModel}), lang and a spawn rule.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModGiants
{
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Abyssia.MODID);

    /** One giant species: its id, entity type and constants. */
    public record Species(String id, Supplier<? extends EntityType<? extends GiantFauna>> type, GiantSpec spec) {}

    private static final Map<String, Species> SPECIES = new LinkedHashMap<>();

    static
    {
        for (GiantSpec spec : GiantSpecies.SPECS) ModGiants.<GiantSpecies>giant(spec, (type, level) -> new GiantSpecies(type, level, spec));
    }

    private ModGiants() {}

    private static <T extends GiantFauna> RegistryObject<EntityType<T>> giant(GiantSpec spec, EntityType.EntityFactory<T> factory)
    {
        float size = (float) (spec.radius() * 2);
        RegistryObject<EntityType<T>> holder = ENTITIES.register(spec.id(),
                () -> EntityType.Builder.of(factory, MobCategory.MISC).sized(size, size).fireImmune()
                        .clientTrackingRange(8).updateInterval(1).build(spec.id()));
        SPECIES.put(spec.id(), new Species(spec.id(), holder, spec));
        return holder;
    }

    public static void register(IEventBus modBus)
    {
        com.abyssia.entity.giant.GiantNetwork.register();
        ENTITIES.register(modBus);
    }

    public static Collection<Species> all()
    {
        return SPECIES.values();
    }

    public static Collection<String> ids()
    {
        return SPECIES.keySet();
    }

    @Nullable
    public static Species byId(String id)
    {
        return SPECIES.get(id);
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event)
    {
        for (Species s : SPECIES.values()) event.put(s.type().get(), GiantFauna.createAttributes(s.spec()).build());
    }
}
