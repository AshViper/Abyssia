package com.abyssia.client.entity;

import com.abyssia.Abyssia;
import com.abyssia.client.entity.model.AnglerfishAnimations;
import com.abyssia.client.entity.model.AtollaJellyAnimations;
import com.abyssia.client.entity.model.AtollaJellyGeometry;
import com.abyssia.client.entity.model.GiantPhantomJellyAnimations;
import com.abyssia.client.entity.model.GiantPhantomJellyGeometry;
import com.abyssia.client.entity.model.HelmetJellyAnimations;
import com.abyssia.client.entity.model.HelmetJellyGeometry;
import com.abyssia.client.entity.model.SilkyMedusaAnimations;
import com.abyssia.client.entity.model.SilkyMedusaGeometry;
import com.abyssia.client.entity.model.AnglerfishGeometry;
import com.abyssia.client.entity.model.BarreleyeGeometry;
import com.abyssia.client.entity.model.DeepSeaShrimpAnimations;
import com.abyssia.client.entity.model.DeepSeaShrimpGeometry;
import com.abyssia.client.entity.model.FrilledSharkAnimations;
import com.abyssia.client.entity.model.FrilledSharkGeometry;
import com.abyssia.client.entity.model.GiantIsopodAnimations;
import com.abyssia.client.entity.model.GiantIsopodGeometry;
import com.abyssia.client.entity.model.GiantSquidAnimations;
import com.abyssia.client.entity.model.GiantSquidGeometry;
import com.abyssia.client.entity.model.GoblinSharkAnimations;
import com.abyssia.client.entity.model.GoblinSharkGeometry;
import com.abyssia.client.entity.model.GoemonSquatLobsterAnimations;
import com.abyssia.client.entity.model.GoemonSquatLobsterGeometry;
import com.abyssia.client.entity.model.GulperEelGeometry;
import com.abyssia.client.entity.model.OharaShrimpAnimations;
import com.abyssia.client.entity.model.OharaShrimpGeometry;
import com.abyssia.client.entity.model.SatsumaTubewormAnimations;
import com.abyssia.client.entity.model.SatsumaTubewormGeometry;
import com.abyssia.client.entity.model.ScalyFootSnailAnimations;
import com.abyssia.client.entity.model.ScalyFootSnailGeometry;
import com.abyssia.client.entity.model.TubewormAnimations;
import com.abyssia.client.entity.model.TubewormGeometry;
import com.abyssia.client.entity.model.VentEelpoutAnimations;
import com.abyssia.client.entity.model.VentEelpoutGeometry;
import com.abyssia.client.entity.model.ViperfishAnimations;
import com.abyssia.client.entity.model.ViperfishGeometry;
import com.abyssia.client.entity.model.YumenamakoAnimations;
import com.abyssia.client.entity.model.YumenamakoGeometry;
import com.abyssia.client.entity.model.YunohanaCrabAnimations;
import com.abyssia.client.entity.model.YunohanaCrabGeometry;
import com.abyssia.entity.Anglerfish;
import com.abyssia.entity.AtollaJelly;
import com.abyssia.entity.GiantPhantomJelly;
import com.abyssia.entity.HelmetJelly;
import com.abyssia.entity.SilkyMedusa;
import com.abyssia.entity.Barreleye;
import com.abyssia.entity.DeepSeaShrimp;
import com.abyssia.entity.FrilledShark;
import com.abyssia.entity.GiantIsopod;
import com.abyssia.entity.GiantSquid;
import com.abyssia.entity.GoblinShark;
import com.abyssia.entity.GulperEel;
import com.abyssia.entity.ScalyFootSnail;
import com.abyssia.entity.SquatLobster;
import com.abyssia.entity.SwimmingSeaCucumber;
import com.abyssia.entity.Tubeworm;
import com.abyssia.entity.VentEelpout;
import com.abyssia.entity.VentShrimp;
import com.abyssia.entity.Viperfish;
import com.abyssia.entity.YunohanaCrab;
import com.abyssia.registry.ModEntities;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Model layers and renderers of the fauna. Geometry, clips and textures come from tools/bbmodel-generator (synced
 * by tools/gen_fauna.py); here each animal gets its clips, its size correction (the generator draws every animal at
 * a readable size, the scale restores the real size order: isopod < anglerfish < giant squid) and its light.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModEntityRenderers
{
    public static final ModelLayerLocation ANGLERFISH = layer("anglerfish");
    public static final ModelLayerLocation GIANT_ISOPOD = layer("giant_isopod");
    public static final ModelLayerLocation GULPER_EEL = layer("gulper_eel");
    public static final ModelLayerLocation VIPERFISH = layer("viperfish");
    public static final ModelLayerLocation GOBLIN_SHARK = layer("goblin_shark");
    public static final ModelLayerLocation BARRELEYE = layer("barreleye");
    public static final ModelLayerLocation YUMENAMAKO = layer("yumenamako");
    public static final ModelLayerLocation FRILLED_SHARK = layer("frilled_shark");
    public static final ModelLayerLocation GIANT_SQUID = layer("giant_squid");
    public static final ModelLayerLocation DEEP_SEA_SHRIMP = layer("deep_sea_shrimp");
    public static final ModelLayerLocation OHARA_SHRIMP = layer("ohara_shrimp");
    public static final ModelLayerLocation VENT_EELPOUT = layer("vent_eelpout");
    public static final ModelLayerLocation YUNOHANA_CRAB = layer("yunohana_crab");
    public static final ModelLayerLocation GOEMON_SQUAT_LOBSTER = layer("goemon_squat_lobster");
    public static final ModelLayerLocation SCALY_FOOT_SNAIL = layer("scaly_foot_snail");
    public static final ModelLayerLocation TUBEWORM = layer("tubeworm");
    public static final ModelLayerLocation SATSUMA_TUBEWORM = layer("satsuma_tubeworm");
    public static final ModelLayerLocation SILKY_MEDUSA = layer("silky_medusa");
    public static final ModelLayerLocation ATOLLA_JELLY = layer("atolla_jelly");
    public static final ModelLayerLocation HELMET_JELLY = layer("helmet_jelly");
    public static final ModelLayerLocation GIANT_PHANTOM_JELLY = layer("giant_phantom_jelly");

    private static final int EEL_FLASH_PERIOD = 170;
    private static final int EEL_FLASH_TICKS = 7;

    private ModEntityRenderers() {}

    private static ModelLayerLocation layer(String name)
    {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name), "main");
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event)
    {
        GeneratedFaunaRenderers.registerLayers(event);
        event.registerLayerDefinition(ANGLERFISH, AnglerfishGeometry::create);
        event.registerLayerDefinition(GIANT_ISOPOD, GiantIsopodGeometry::create);
        event.registerLayerDefinition(GULPER_EEL, GulperEelGeometry::create);
        event.registerLayerDefinition(VIPERFISH, ViperfishGeometry::create);
        event.registerLayerDefinition(GOBLIN_SHARK, GoblinSharkGeometry::create);
        event.registerLayerDefinition(BARRELEYE, BarreleyeGeometry::create);
        event.registerLayerDefinition(YUMENAMAKO, YumenamakoGeometry::create);
        event.registerLayerDefinition(FRILLED_SHARK, FrilledSharkGeometry::create);
        event.registerLayerDefinition(GIANT_SQUID, GiantSquidGeometry::create);
        event.registerLayerDefinition(DEEP_SEA_SHRIMP, DeepSeaShrimpGeometry::create);
        event.registerLayerDefinition(OHARA_SHRIMP, OharaShrimpGeometry::create);
        event.registerLayerDefinition(VENT_EELPOUT, VentEelpoutGeometry::create);
        event.registerLayerDefinition(YUNOHANA_CRAB, YunohanaCrabGeometry::create);
        event.registerLayerDefinition(GOEMON_SQUAT_LOBSTER, GoemonSquatLobsterGeometry::create);
        event.registerLayerDefinition(SCALY_FOOT_SNAIL, ScalyFootSnailGeometry::create);
        event.registerLayerDefinition(TUBEWORM, TubewormGeometry::create);
        event.registerLayerDefinition(SATSUMA_TUBEWORM, SatsumaTubewormGeometry::create);
        event.registerLayerDefinition(SILKY_MEDUSA, SilkyMedusaGeometry::create);
        event.registerLayerDefinition(ATOLLA_JELLY, AtollaJellyGeometry::create);
        event.registerLayerDefinition(HELMET_JELLY, HelmetJellyGeometry::create);
        event.registerLayerDefinition(GIANT_PHANTOM_JELLY, GiantPhantomJellyGeometry::create);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        // the esca: bacterial blue-green light that pulses, flickers in the threat display and goes dark when it flees
        event.registerEntityRenderer(ModEntities.ANGLERFISH.get(), FaunaRenderer.<Anglerfish>spec("anglerfish", ANGLERFISH,
                        root -> new FaunaModel<>(root, AnglerfishGeometry.ANGLERFISH, Map.of("idle", AnglerfishAnimations.IDLE,
                                "swim", AnglerfishAnimations.SWIM, "hurt", AnglerfishAnimations.HURT, "death", AnglerfishAnimations.DEATH,
                                "mouth_open", AnglerfishAnimations.MOUTH_OPEN, "mouth_close", AnglerfishAnimations.MOUTH_CLOSE,
                                "glow", AnglerfishAnimations.GLOW), Set.of(), true))
                .scale(0.8F, 0.3F).fish()
                .light(List.of(AnglerfishGeometry.ESCA), List.of(new FaunaGlowLayer.Halo(AnglerfishGeometry.ESCA, 0.4F, 0.45F, 0.95F, 1.0F)),
                        (fish, partial, age) -> {
                            float pulse = fish.mood() == Anglerfish.THREAT ? 0.72F + 0.28F * Mth.sin(age * 1.1F) : 0.9F + 0.1F * Mth.sin(age * 0.07F + fish.getId());
                            return new float[]{1.0F, 1.0F, 1.0F, Mth.clamp(fish.lure(partial) * pulse, 0.0F, 1.0F)};
                        })
                .provider());
        // no light of its own: its eyes' tapetum only shines back what light reaches it
        event.registerEntityRenderer(ModEntities.GIANT_ISOPOD.get(), FaunaRenderer.<GiantIsopod>spec("giant_isopod", GIANT_ISOPOD,
                        root -> new FaunaModel<>(root, GiantIsopodGeometry.GIANT_ISOPOD, Map.of("idle", GiantIsopodAnimations.IDLE,
                                "walk", GiantIsopodAnimations.WALK, "curl", GiantIsopodAnimations.CURL, "uncurl", GiantIsopodAnimations.UNCURL,
                                "hurt", GiantIsopodAnimations.HURT, "death", GiantIsopodAnimations.DEATH), Set.of(), false))
                .scale(0.42F, 0.3F)
                .eyeshine(0.8F, GiantIsopodGeometry.RIGHT_EYE, GiantIsopodGeometry.LEFT_EYE)
                .provider());
        // the tail organ glows pink and now and then flashes red
        event.registerEntityRenderer(ModEntities.GULPER_EEL.get(), FaunaRenderer.<GulperEel>spec("gulper_eel", GULPER_EEL, GulperEelModel::new)
                .scale(0.38F, 0.2F).fish()
                .light(List.of(GulperEelGeometry.TAIL_TIP), List.of(new FaunaGlowLayer.Halo(GulperEelGeometry.TAIL_TIP, 0.28F, 1.0F, 0.45F, 0.75F)),
                        (eel, partial, age) -> {
                            if (!eel.isInWater()) return new float[]{1.0F, 1.0F, 1.0F, 0.25F};
                            float cycle = (age + eel.getId() * 53) % EEL_FLASH_PERIOD;
                            if (cycle < EEL_FLASH_TICKS)
                            {
                                float t = Mth.sin(cycle / EEL_FLASH_TICKS * Mth.PI);
                                return new float[]{1.0F, 1.0F - 0.7F * t, 1.0F - 0.6F * t, 0.8F + 0.2F * t};
                            }
                            return new float[]{1.0F, 1.0F, 1.0F, 0.65F + 0.2F * Mth.sin(age * 0.11F + eel.getId())};
                        })
                .provider());
        registerPhase2(event);
        registerPhase3(event);
        registerMedusae(event);
        registerVentFauna(event);
        GeneratedFaunaRenderers.registerRenderers(event);
    }

    private static void registerPhase2(EntityRenderersEvent.RegisterRenderers event)
    {
        // the photophore at the tip of the dorsal ray; stressed, the lights flare for several seconds
        event.registerEntityRenderer(ModEntities.VIPERFISH.get(), FaunaRenderer.<Viperfish>spec("viperfish", VIPERFISH,
                        root -> new FaunaModel<>(root, ViperfishGeometry.VIPERFISH, Map.of("idle", ViperfishAnimations.IDLE,
                                "swim", ViperfishAnimations.SWIM, "hurt", ViperfishAnimations.HURT, "death", ViperfishAnimations.DEATH,
                                "mouth_open", ViperfishAnimations.MOUTH_OPEN, "mouth_close", ViperfishAnimations.MOUTH_CLOSE,
                                "bite", ViperfishAnimations.BITE, "glow", ViperfishAnimations.GLOW), Set.of(), true))
                .scale(0.43F, 0.2F).fish()
                .light(List.of(ViperfishGeometry.ESCA), List.of(new FaunaGlowLayer.Halo(ViperfishGeometry.ESCA, 0.22F, 0.55F, 0.75F, 1.0F)),
                        (fish, partial, age) -> {
                            if (!fish.isInWater()) return new float[]{1.0F, 1.0F, 1.0F, 0.2F};
                            float stress = fish.stress(partial);
                            float base = 0.75F + 0.15F * Mth.sin(age * 0.09F + fish.getId());
                            return new float[]{1.0F, 1.0F, 1.0F, Math.min(1.0F, base + stress * (0.25F + 0.2F * Mth.sin(age * 0.8F)))};
                        })
                .eyeshine(0.5F, ViperfishGeometry.RIGHT_EYE, ViperfishGeometry.LEFT_EYE)
                .provider());
        // sharks have a tapetum behind the retina; no light of their own
        event.registerEntityRenderer(ModEntities.GOBLIN_SHARK.get(), FaunaRenderer.<GoblinShark>spec("goblin_shark", GOBLIN_SHARK,
                        root -> new FaunaModel<>(root, GoblinSharkGeometry.GOBLIN_SHARK, Map.of("idle", GoblinSharkAnimations.IDLE,
                                "swim", GoblinSharkAnimations.SWIM, "hurt", GoblinSharkAnimations.HURT, "death", GoblinSharkAnimations.DEATH,
                                "mouth_open", GoblinSharkAnimations.MOUTH_OPEN, "mouth_close", GoblinSharkAnimations.MOUTH_CLOSE,
                                "bite", GoblinSharkAnimations.BITE), Set.of(), true))
                .scale(1.1F, 0.6F).fish()
                .eyeshine(0.8F, GoblinSharkGeometry.RIGHT_EYE, GoblinSharkGeometry.LEFT_EYE)
                .provider());
        // transparent head shield; the green of the eyes is pigment, it only catches light
        event.registerEntityRenderer(ModEntities.BARRELEYE.get(), FaunaRenderer.<Barreleye>spec("barreleye", BARRELEYE, BarreleyeModel::new)
                .scale(0.48F, 0.2F).fish()
                .eyeshine(0.6F, BarreleyeGeometry.RIGHT_EYE, BarreleyeGeometry.LEFT_EYE)
                .provider());
        // dark until disturbed: then the granules of the skin light up blue-green and fade
        event.registerEntityRenderer(ModEntities.YUMENAMAKO.get(), FaunaRenderer.<SwimmingSeaCucumber>spec("yumenamako", YUMENAMAKO,
                        root -> new FaunaModel<>(root, YumenamakoGeometry.YUMENAMAKO, Map.of("idle", YumenamakoAnimations.IDLE,
                                "swim", YumenamakoAnimations.SWIM, "hurt", YumenamakoAnimations.HURT, "death", YumenamakoAnimations.DEATH),
                                Set.of(), false))
                .scale(0.45F, 0.2F)
                .light(List.of(YumenamakoGeometry.YUMENAMAKO), List.of(new FaunaGlowLayer.Halo(YumenamakoGeometry.BODY, 0.45F, 0.4F, 1.0F, 0.85F)),
                        (cucumber, partial, age) -> new float[]{1.0F, 1.0F, 1.0F, cucumber.glow(partial) * (0.85F + 0.15F * Mth.sin(age * 0.6F))})
                .provider());
    }

    private static void registerPhase3(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.FRILLED_SHARK.get(), FaunaRenderer.<FrilledShark>spec("frilled_shark", FRILLED_SHARK,
                        root -> new FaunaModel<>(root, FrilledSharkGeometry.FRILLED_SHARK, Map.of("idle", FrilledSharkAnimations.IDLE,
                                "swim", FrilledSharkAnimations.SWIM, "hurt", FrilledSharkAnimations.HURT, "death", FrilledSharkAnimations.DEATH,
                                "mouth_open", FrilledSharkAnimations.MOUTH_OPEN, "mouth_close", FrilledSharkAnimations.MOUTH_CLOSE), Set.of(), true))
                .scale(0.58F, 0.35F).fish()
                .eyeshine(0.8F, FrilledSharkGeometry.RIGHT_EYE, FrilledSharkGeometry.LEFT_EYE)
                .provider());
        // no photophores in Architeuthis; the huge eyes only catch light
        event.registerEntityRenderer(ModEntities.GIANT_SQUID.get(), FaunaRenderer.<GiantSquid>spec("giant_squid", GIANT_SQUID,
                        root -> new FaunaModel<>(root, GiantSquidGeometry.GIANT_SQUID, Map.of("idle", GiantSquidAnimations.IDLE,
                                "swim", GiantSquidAnimations.SWIM, "tentacle_move", GiantSquidAnimations.TENTACLE_MOVE, "grab", GiantSquidAnimations.GRAB,
                                "hurt", GiantSquidAnimations.HURT, "death", GiantSquidAnimations.DEATH), Set.of(), true))
                .scale(2.0F, 1.2F)
                .eyeshine(0.6F, GiantSquidGeometry.RIGHT_EYE, GiantSquidGeometry.LEFT_EYE)
                .provider());
        // no photophores (the light of Acanthephyra is the spewed cloud); crustacean eyes shine back
        event.registerEntityRenderer(ModEntities.DEEP_SEA_SHRIMP.get(), FaunaRenderer.<DeepSeaShrimp>spec("deep_sea_shrimp", DEEP_SEA_SHRIMP,
                        root -> new FaunaModel<>(root, DeepSeaShrimpGeometry.DEEP_SEA_SHRIMP, Map.of("idle", DeepSeaShrimpAnimations.IDLE,
                                "swim", DeepSeaShrimpAnimations.SWIM, "flick", DeepSeaShrimpAnimations.FLICK, "walk", DeepSeaShrimpAnimations.WALK,
                                "hurt", DeepSeaShrimpAnimations.HURT, "death", DeepSeaShrimpAnimations.DEATH), Set.of(), true))
                .scale(0.25F, 0.1F)
                .eyeshine(0.7F, DeepSeaShrimpGeometry.RIGHT_EYE, DeepSeaShrimpGeometry.LEFT_EYE)
                .provider());
    }

    private static void registerVentFauna(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.OHARA_SHRIMP.get(), FaunaRenderer.<VentShrimp>spec("ohara_shrimp", OHARA_SHRIMP,
                        root -> new FaunaModel<>(root, OharaShrimpGeometry.OHARA_SHRIMP, Map.of("idle", OharaShrimpAnimations.IDLE,
                                "swim", OharaShrimpAnimations.SWIM, "flick", OharaShrimpAnimations.FLICK, "walk", OharaShrimpAnimations.WALK,
                                "hurt", OharaShrimpAnimations.HURT, "death", OharaShrimpAnimations.DEATH), Set.of(), true))
                .scale(0.26F, 0.1F)
                .provider());
        event.registerEntityRenderer(ModEntities.VENT_EELPOUT.get(), FaunaRenderer.<VentEelpout>spec("vent_eelpout", VENT_EELPOUT,
                        root -> new FaunaModel<>(root, VentEelpoutGeometry.VENT_EELPOUT, Map.of("idle", VentEelpoutAnimations.IDLE,
                                "swim", VentEelpoutAnimations.SWIM, "hurt", VentEelpoutAnimations.HURT, "death", VentEelpoutAnimations.DEATH,
                                "mouth_open", VentEelpoutAnimations.MOUTH_OPEN, "mouth_close", VentEelpoutAnimations.MOUTH_CLOSE), Set.of(), true))
                .scale(0.34F, 0.2F).fish()
                .provider());
        event.registerEntityRenderer(ModEntities.YUNOHANA_CRAB.get(), FaunaRenderer.<YunohanaCrab>spec("yunohana_crab", YUNOHANA_CRAB,
                        root -> new FaunaModel<>(root, YunohanaCrabGeometry.YUNOHANA_CRAB, Map.of("idle", YunohanaCrabAnimations.IDLE,
                                "walk", YunohanaCrabAnimations.WALK, "claw_snap", YunohanaCrabAnimations.CLAW_SNAP, "hurt", YunohanaCrabAnimations.HURT,
                                "death", YunohanaCrabAnimations.DEATH), Set.of(), false))
                .scale(0.3F, 0.25F)
                .provider());
        event.registerEntityRenderer(ModEntities.GOEMON_SQUAT_LOBSTER.get(), FaunaRenderer.<SquatLobster>spec("goemon_squat_lobster", GOEMON_SQUAT_LOBSTER,
                        root -> new FaunaModel<>(root, GoemonSquatLobsterGeometry.GOEMON_SQUAT_LOBSTER, Map.of("idle", GoemonSquatLobsterAnimations.IDLE,
                                "walk", GoemonSquatLobsterAnimations.WALK, "claw_snap", GoemonSquatLobsterAnimations.CLAW_SNAP,
                                "hurt", GoemonSquatLobsterAnimations.HURT, "death", GoemonSquatLobsterAnimations.DEATH), Set.of(), false))
                .scale(0.26F, 0.15F)
                .provider());
        event.registerEntityRenderer(ModEntities.SCALY_FOOT_SNAIL.get(), FaunaRenderer.<ScalyFootSnail>spec("scaly_foot_snail", SCALY_FOOT_SNAIL,
                        root -> new FaunaModel<>(root, ScalyFootSnailGeometry.SCALY_FOOT_SNAIL, Map.of("idle", ScalyFootSnailAnimations.IDLE,
                                "crawl", ScalyFootSnailAnimations.CRAWL, "retract", ScalyFootSnailAnimations.RETRACT, "hurt", ScalyFootSnailAnimations.HURT,
                                "death", ScalyFootSnailAnimations.DEATH), Set.of(), false))
                .scale(0.27F, 0.15F)
                .provider());
        event.registerEntityRenderer(ModEntities.TUBEWORM.get(), FaunaRenderer.<Tubeworm>spec("tubeworm", TUBEWORM,
                        root -> new FaunaModel<>(root, TubewormGeometry.TUBEWORM, Map.of("idle", TubewormAnimations.IDLE,
                                "sway", TubewormAnimations.SWAY, "retract", TubewormAnimations.RETRACT, "extend", TubewormAnimations.EXTEND,
                                "hurt", TubewormAnimations.HURT, "death", TubewormAnimations.DEATH), Set.of(), false))
                .scale(1.0F, 0.5F)
                .provider());
        event.registerEntityRenderer(ModEntities.SATSUMA_TUBEWORM.get(), FaunaRenderer.<Tubeworm>spec("satsuma_tubeworm", SATSUMA_TUBEWORM,
                        root -> new FaunaModel<>(root, SatsumaTubewormGeometry.SATSUMA_TUBEWORM, Map.of("idle", SatsumaTubewormAnimations.IDLE,
                                "sway", SatsumaTubewormAnimations.SWAY, "retract", SatsumaTubewormAnimations.RETRACT,
                                "extend", SatsumaTubewormAnimations.EXTEND, "hurt", SatsumaTubewormAnimations.HURT,
                                "death", SatsumaTubewormAnimations.DEATH), Set.of(), false))
                .scale(0.5F, 0.3F)
                .provider());
    }

    /**
     * Medusae: translucent bells (and lappets) over an opaque gut; light only from real light organs, as bright as the
     * animal's state (resting glimmer, alert display, flash, fading at death). Atolla's alarm runs around the eight
     * sectors of its coronal groove; the giant phantom jelly has no known light and is left dark.
     */
    private static void registerMedusae(EntityRenderersEvent.RegisterRenderers event)
    {
        // the bell flashes brilliant blue; the tentacles make no light
        event.registerEntityRenderer(ModEntities.SILKY_MEDUSA.get(), FaunaRenderer.<SilkyMedusa>spec("silky_medusa", SILKY_MEDUSA,
                        root -> new MedusaModel<>(root, SilkyMedusaGeometry.SILKY_MEDUSA, Map.of("idle", SilkyMedusaAnimations.IDLE,
                                "swim", SilkyMedusaAnimations.SWIM, "pulse", SilkyMedusaAnimations.PULSE, "spread", SilkyMedusaAnimations.SPREAD,
                                "hurt", SilkyMedusaAnimations.HURT, "death", SilkyMedusaAnimations.DEATH), Set.of(SilkyMedusaGeometry.BELL)))
                .scale(0.6F, 0.1F)
                .light(List.of(SilkyMedusaGeometry.BELL), List.of(new FaunaGlowLayer.Halo(SilkyMedusaGeometry.BELL, 0.35F, 0.45F, 0.7F, 1.0F)),
                        (jelly, partial, age) -> new float[]{1.0F, 1.0F, 1.0F, jelly.glow(partial)})
                .provider());
        // burglar alarm: a blue wave circling the coronal groove, plus a wide halo so it reads from far away
        FaunaRenderer.Spec<AtollaJelly> atolla = FaunaRenderer.<AtollaJelly>spec("atolla_jelly", ATOLLA_JELLY,
                        root -> new MedusaModel<>(root, AtollaJellyGeometry.ATOLLA_JELLY, Map.of("idle", AtollaJellyAnimations.IDLE,
                                "swim", AtollaJellyAnimations.SWIM, "pulse", AtollaJellyAnimations.PULSE, "spread", AtollaJellyAnimations.SPREAD,
                                "hurt", AtollaJellyAnimations.HURT, "death", AtollaJellyAnimations.DEATH),
                                Set.of(AtollaJellyGeometry.BELL, AtollaJellyGeometry.LAPPETS_FRONT, AtollaJellyGeometry.LAPPETS_RIGHT,
                                        AtollaJellyGeometry.LAPPETS_BACK, AtollaJellyGeometry.LAPPETS_LEFT)))
                .scale(0.75F, 0.2F);
        for (int i = 0; i < AtollaJelly.SECTORS; i++)
        {
            int sector = i;
            String bone = "groove_" + (i + 1);
            atolla.light(List.of(bone), List.of(new FaunaGlowLayer.Halo(bone, 0.22F, 0.35F, 0.55F, 1.0F)),
                    (jelly, partial, age) -> new float[]{1.0F, 1.0F, 1.0F, jelly.sectorGlow(sector, partial)});
        }
        atolla.light(List.of(), List.of(new FaunaGlowLayer.Halo(AtollaJellyGeometry.BELL, 1.1F, 0.25F, 0.4F, 1.0F)),
                (jelly, partial, age) -> new float[]{1.0F, 1.0F, 1.0F, 0.5F * Math.max(jelly.alertLevel(partial), jelly.flashLevel(partial))});
        event.registerEntityRenderer(ModEntities.ATOLLA_JELLY.get(), atolla.provider());
        // blue-green light of the coronal groove
        event.registerEntityRenderer(ModEntities.HELMET_JELLY.get(), FaunaRenderer.<HelmetJelly>spec("helmet_jelly", HELMET_JELLY,
                        root -> new MedusaModel<>(root, HelmetJellyGeometry.HELMET_JELLY, Map.of("idle", HelmetJellyAnimations.IDLE,
                                "swim", HelmetJellyAnimations.SWIM, "pulse", HelmetJellyAnimations.PULSE, "spread", HelmetJellyAnimations.SPREAD,
                                "hurt", HelmetJellyAnimations.HURT, "death", HelmetJellyAnimations.DEATH),
                                Set.of(HelmetJellyGeometry.BELL, HelmetJellyGeometry.LAPPETS_FRONT, HelmetJellyGeometry.LAPPETS_RIGHT,
                                        HelmetJellyGeometry.LAPPETS_BACK, HelmetJellyGeometry.LAPPETS_LEFT)))
                .scale(1.0F, 0.3F)
                .light(List.of(HelmetJellyGeometry.GROOVE_1, HelmetJellyGeometry.GROOVE_2, HelmetJellyGeometry.GROOVE_3, HelmetJellyGeometry.GROOVE_4,
                                HelmetJellyGeometry.GROOVE_5, HelmetJellyGeometry.GROOVE_6, HelmetJellyGeometry.GROOVE_7, HelmetJellyGeometry.GROOVE_8),
                        List.of(new FaunaGlowLayer.Halo(HelmetJellyGeometry.GROOVE_1, 0.4F, 0.3F, 0.9F, 0.75F),
                                new FaunaGlowLayer.Halo(HelmetJellyGeometry.GROOVE_5, 0.4F, 0.3F, 0.9F, 0.75F)),
                        (jelly, partial, age) -> new float[]{1.0F, 1.0F, 1.0F, jelly.glow(partial)})
                .provider());
        // no known bioluminescence: dark crimson, found only in the player's own light
        event.registerEntityRenderer(ModEntities.GIANT_PHANTOM_JELLY.get(), FaunaRenderer.<GiantPhantomJelly>spec("giant_phantom_jelly", GIANT_PHANTOM_JELLY,
                        root -> new MedusaModel<>(root, GiantPhantomJellyGeometry.GIANT_PHANTOM_JELLY, Map.of("idle", GiantPhantomJellyAnimations.IDLE,
                                "swim", GiantPhantomJellyAnimations.SWIM, "pulse", GiantPhantomJellyAnimations.PULSE,
                                "hurt", GiantPhantomJellyAnimations.HURT, "death", GiantPhantomJellyAnimations.DEATH),
                                Set.of(GiantPhantomJellyGeometry.BELL)))
                .scale(2.0F, 0.0F)
                .provider());
    }
}

