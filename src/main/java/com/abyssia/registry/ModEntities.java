package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.abyssia.entity.Anglerfish;
import com.abyssia.entity.AtollaJelly;
import com.abyssia.entity.Barreleye;
import com.abyssia.entity.DeepSeaShrimp;
import com.abyssia.entity.FrilledShark;
import com.abyssia.entity.GiantIsopod;
import com.abyssia.entity.GiantPhantomJelly;
import com.abyssia.entity.GiantSquid;
import com.abyssia.entity.GoblinShark;
import com.abyssia.entity.GulperEel;
import com.abyssia.entity.HelmetJelly;
import com.abyssia.entity.ScalyFootSnail;
import com.abyssia.entity.SilkyMedusa;
import com.abyssia.entity.SquatLobster;
import com.abyssia.entity.SwimmingSeaCucumber;
import com.abyssia.entity.Tubeworm;
import com.abyssia.entity.VentEelpout;
import com.abyssia.entity.VentShrimp;
import com.abyssia.entity.Viperfish;
import com.abyssia.entity.YunohanaCrab;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Deep-sea animals modelled on real species (research notes in tools/fauna/&lt;id&gt;.py). Sizes keep their real order
 * (shrimp &lt; isopod &lt; anglerfish &lt; frilled shark &lt; goblin shark &lt; giant squid), deformed to read in game. Where they spawn is datapack data (fauna_spawns), placed
 * by {@link com.abyssia.fauna.FaunaSpawner}; they are not in any biome's spawner list.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Abyssia.MODID);

    public static final RegistryObject<EntityType<Anglerfish>> ANGLERFISH = ENTITIES.register("anglerfish",
            () -> EntityType.Builder.of(Anglerfish::new, MobCategory.WATER_CREATURE).sized(0.55F, 0.5F).clientTrackingRange(8).build("anglerfish"));
    public static final RegistryObject<EntityType<GiantIsopod>> GIANT_ISOPOD = ENTITIES.register("giant_isopod",
            () -> EntityType.Builder.of(GiantIsopod::new, MobCategory.WATER_CREATURE).sized(0.5F, 0.25F).clientTrackingRange(6).build("giant_isopod"));
    public static final RegistryObject<EntityType<GulperEel>> GULPER_EEL = ENTITIES.register("gulper_eel",
            () -> EntityType.Builder.of(GulperEel::new, MobCategory.WATER_CREATURE).sized(0.45F, 0.25F).clientTrackingRange(8).build("gulper_eel"));

    public static final RegistryObject<EntityType<Viperfish>> VIPERFISH = animal("viperfish", Viperfish::new, 0.35F, 0.3F, 8);
    public static final RegistryObject<EntityType<GoblinShark>> GOBLIN_SHARK = animal("goblin_shark", GoblinShark::new, 0.9F, 0.6F, 10);
    public static final RegistryObject<EntityType<Barreleye>> BARRELEYE = animal("barreleye", Barreleye::new, 0.4F, 0.35F, 8);
    public static final RegistryObject<EntityType<SwimmingSeaCucumber>> YUMENAMAKO = animal("yumenamako", SwimmingSeaCucumber::new, 0.35F, 0.3F, 6);
    public static final RegistryObject<EntityType<FrilledShark>> FRILLED_SHARK = animal("frilled_shark", FrilledShark::new, 0.6F, 0.4F, 10);
    public static final RegistryObject<EntityType<GiantSquid>> GIANT_SQUID = animal("giant_squid", GiantSquid::new, 1.6F, 1.4F, 12);
    public static final RegistryObject<EntityType<DeepSeaShrimp>> DEEP_SEA_SHRIMP = animal("deep_sea_shrimp", DeepSeaShrimp::new, 0.3F, 0.2F, 6);
    public static final RegistryObject<EntityType<VentShrimp>> OHARA_SHRIMP = animal("ohara_shrimp", VentShrimp::new, 0.3F, 0.2F, 6);
    public static final RegistryObject<EntityType<VentEelpout>> VENT_EELPOUT = animal("vent_eelpout", VentEelpout::new, 0.35F, 0.25F, 6);
    public static final RegistryObject<EntityType<YunohanaCrab>> YUNOHANA_CRAB = animal("yunohana_crab", YunohanaCrab::new, 0.45F, 0.25F, 6);
    public static final RegistryObject<EntityType<SquatLobster>> GOEMON_SQUAT_LOBSTER = animal("goemon_squat_lobster", SquatLobster::new, 0.35F, 0.2F, 6);
    public static final RegistryObject<EntityType<ScalyFootSnail>> SCALY_FOOT_SNAIL = animal("scaly_foot_snail", ScalyFootSnail::new, 0.3F, 0.3F, 6);
    public static final RegistryObject<EntityType<Tubeworm>> TUBEWORM = animal("tubeworm",
            (EntityType<Tubeworm> type, Level level) -> new Tubeworm(type, level, ModSounds.TUBEWORM), 0.8F, 1.6F, 8);
    public static final RegistryObject<EntityType<Tubeworm>> SATSUMA_TUBEWORM = animal("satsuma_tubeworm",
            (EntityType<Tubeworm> type, Level level) -> new Tubeworm(type, level, ModSounds.SATSUMA_TUBEWORM), 0.5F, 1.1F, 8);
    // Medusae: the hitbox is the bell only (tentacles and arms hang below it and never collide). Atolla is tracked far
    // so its alarm display can be seen from a distance.
    public static final RegistryObject<EntityType<SilkyMedusa>> SILKY_MEDUSA = animal("silky_medusa", SilkyMedusa::new, 0.3F, 0.3F, 6);
    public static final RegistryObject<EntityType<AtollaJelly>> ATOLLA_JELLY = animal("atolla_jelly", AtollaJelly::new, 0.55F, 0.3F, 10);
    public static final RegistryObject<EntityType<HelmetJelly>> HELMET_JELLY = animal("helmet_jelly", HelmetJelly::new, 0.6F, 0.75F, 8);
    public static final RegistryObject<EntityType<GiantPhantomJelly>> GIANT_PHANTOM_JELLY = animal("giant_phantom_jelly", GiantPhantomJelly::new, 2.8F, 1.5F, 12);

    private ModEntities() {}

    private static <T extends Mob> RegistryObject<EntityType<T>> animal(String name, EntityType.EntityFactory<T> factory, float width, float height,
                                                                         int trackingRange)
    {
        return ENTITIES.register(name, () -> EntityType.Builder.of(factory, MobCategory.WATER_CREATURE).sized(width, height)
                .clientTrackingRange(trackingRange).build(name));
    }

    public static void register(IEventBus modBus)
    {
        ENTITIES.register(modBus);
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event)
    {
        event.put(ANGLERFISH.get(), Anglerfish.createAttributes().build());
        event.put(GIANT_ISOPOD.get(), GiantIsopod.createAttributes().build());
        event.put(GULPER_EEL.get(), GulperEel.createAttributes().build());
        event.put(VIPERFISH.get(), Viperfish.createAttributes().build());
        event.put(GOBLIN_SHARK.get(), GoblinShark.createAttributes().build());
        event.put(BARRELEYE.get(), Barreleye.createAttributes().build());
        event.put(YUMENAMAKO.get(), SwimmingSeaCucumber.createAttributes().build());
        event.put(FRILLED_SHARK.get(), FrilledShark.createAttributes().build());
        event.put(GIANT_SQUID.get(), GiantSquid.createAttributes().build());
        event.put(DEEP_SEA_SHRIMP.get(), DeepSeaShrimp.createAttributes().build());
        event.put(OHARA_SHRIMP.get(), DeepSeaShrimp.createAttributes().build());
        event.put(VENT_EELPOUT.get(), VentEelpout.createAttributes().build());
        event.put(YUNOHANA_CRAB.get(), YunohanaCrab.createAttributes().build());
        event.put(GOEMON_SQUAT_LOBSTER.get(), SquatLobster.createAttributes().build());
        event.put(SCALY_FOOT_SNAIL.get(), ScalyFootSnail.createAttributes().build());
        event.put(TUBEWORM.get(), Tubeworm.createAttributes().build());
        event.put(SATSUMA_TUBEWORM.get(), Tubeworm.createAttributes().build());
        event.put(SILKY_MEDUSA.get(), SilkyMedusa.createAttributes().build());
        event.put(ATOLLA_JELLY.get(), AtollaJelly.createAttributes().build());
        event.put(HELMET_JELLY.get(), HelmetJelly.createAttributes().build());
        event.put(GIANT_PHANTOM_JELLY.get(), GiantPhantomJelly.createAttributes().build());
        GeneratedFauna.attributes(event);
    }

    @SubscribeEvent
    public static void spawnPlacements(SpawnPlacementRegisterEvent event)
    {
        // Only the basic "in water" check: depth, habitat and caps are the fauna spawner's job.
        SpawnPlacementRegisterEvent.Operation replace = SpawnPlacementRegisterEvent.Operation.REPLACE;
        for (RegistryObject<? extends EntityType<?>> type : List.of(ANGLERFISH, GIANT_ISOPOD, GULPER_EEL, VIPERFISH, GOBLIN_SHARK, BARRELEYE, YUMENAMAKO,
                FRILLED_SHARK, GIANT_SQUID, DEEP_SEA_SHRIMP, OHARA_SHRIMP, VENT_EELPOUT, YUNOHANA_CRAB, GOEMON_SQUAT_LOBSTER, SCALY_FOOT_SNAIL,
                TUBEWORM, SATSUMA_TUBEWORM, SILKY_MEDUSA, ATOLLA_JELLY, HELMET_JELLY, GIANT_PHANTOM_JELLY))
        {
            register(event, type.get(), replace);
        }
        for (RegistryObject<? extends EntityType<?>> type : GeneratedFauna.types()) register(event, type.get(), replace);
    }

    private static <T extends Entity> void register(SpawnPlacementRegisterEvent event, EntityType<T> type, SpawnPlacementRegisterEvent.Operation operation)
    {
        event.register(type, SpawnPlacements.Type.IN_WATER, Heightmap.Types.OCEAN_FLOOR, ModEntities::inWater, operation);
    }

    private static boolean inWater(EntityType<?> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random)
    {
        return level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.above()).is(FluidTags.WATER);
    }
}
