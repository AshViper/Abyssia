package com.abyssia.registry;

import com.abyssia.Abyssia;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Sound events. The sounds themselves (and sounds.json) are synthesised by tools/gen_fauna.py: the real animals are
 * close to silent, so these are quiet underwater cues rather than calls.
 */
public final class ModSounds
{
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Abyssia.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ANGLERFISH_AMBIENT = sound("entity.anglerfish.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANGLERFISH_HURT = sound("entity.anglerfish.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANGLERFISH_DEATH = sound("entity.anglerfish.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANGLERFISH_THREAT = sound("entity.anglerfish.threat");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANGLERFISH_SNAP = sound("entity.anglerfish.snap");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANGLERFISH_FLOP = sound("entity.anglerfish.flop");

    public static final DeferredHolder<SoundEvent, SoundEvent> GIANT_ISOPOD_AMBIENT = sound("entity.giant_isopod.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> GIANT_ISOPOD_STEP = sound("entity.giant_isopod.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> GIANT_ISOPOD_HURT = sound("entity.giant_isopod.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GIANT_ISOPOD_DEATH = sound("entity.giant_isopod.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> GIANT_ISOPOD_CURL = sound("entity.giant_isopod.curl");
    public static final DeferredHolder<SoundEvent, SoundEvent> GIANT_ISOPOD_EAT = sound("entity.giant_isopod.eat");

    public static final DeferredHolder<SoundEvent, SoundEvent> GULPER_EEL_AMBIENT = sound("entity.gulper_eel.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULPER_EEL_GULP = sound("entity.gulper_eel.gulp");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULPER_EEL_INFLATE = sound("entity.gulper_eel.inflate");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULPER_EEL_DEFLATE = sound("entity.gulper_eel.deflate");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULPER_EEL_HURT = sound("entity.gulper_eel.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULPER_EEL_DEATH = sound("entity.gulper_eel.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> GULPER_EEL_FLOP = sound("entity.gulper_eel.flop");

    public static final Voice VIPERFISH = voice("viperfish", "ambient", "hurt", "death", "flop", "snap");
    public static final Voice GOBLIN_SHARK = voice("goblin_shark", "ambient", "hurt", "death", "flop", "bite");
    public static final Voice BARRELEYE = voice("barreleye", "ambient", "hurt", "death", "flop");
    public static final Voice YUMENAMAKO = voice("yumenamako", "ambient", "hurt", "death", "swim");
    public static final Voice FRILLED_SHARK = voice("frilled_shark", "ambient", "hurt", "death", "flop", "bite");
    public static final Voice GIANT_SQUID = voice("giant_squid", "ambient", "hurt", "death", "grab", "ink", "jet");
    public static final Voice DEEP_SEA_SHRIMP = voice("deep_sea_shrimp", "ambient", "hurt", "death", "flick", "spew");
    public static final Voice OHARA_SHRIMP = voice("ohara_shrimp", "ambient", "hurt", "death", "flick");
    public static final Voice VENT_EELPOUT = voice("vent_eelpout", "ambient", "hurt", "death", "flop");
    public static final Voice YUNOHANA_CRAB = voice("yunohana_crab", "ambient", "step", "hurt", "death", "snap");
    public static final Voice GOEMON_SQUAT_LOBSTER = voice("goemon_squat_lobster", "ambient", "step", "hurt", "death", "snap", "flick");
    public static final Voice SCALY_FOOT_SNAIL = voice("scaly_foot_snail", "step", "hurt", "death", "retract");
    public static final Voice TUBEWORM = voice("tubeworm", "retract", "hurt", "death");
    public static final Voice SATSUMA_TUBEWORM = voice("satsuma_tubeworm", "retract", "hurt", "death");
    public static final Voice SILKY_MEDUSA = voice("silky_medusa", "ambient", "pulse", "hurt", "death");
    public static final Voice ATOLLA_JELLY = voice("atolla_jelly", "ambient", "pulse", "hurt", "death");
    public static final Voice HELMET_JELLY = voice("helmet_jelly", "ambient", "pulse", "hurt", "death");
    public static final Voice GIANT_PHANTOM_JELLY = voice("giant_phantom_jelly", "ambient", "pulse", "hurt", "death");

    private ModSounds() {}

    /** The sound events of one animal by kind: entity.&lt;animal&gt;.&lt;kind&gt;. */
    public static final class Voice
    {
        private final Map<String, DeferredHolder<SoundEvent, SoundEvent>> events = new HashMap<>();

        public SoundEvent get(String kind)
        {
            return this.events.get(kind).get();
        }

        public boolean has(String kind)
        {
            return this.events.containsKey(kind);
        }
    }

    static Voice voice(String animal, String... kinds)
    {
        Voice voice = new Voice();
        for (String kind : kinds) voice.events.put(kind, sound("entity." + animal + "." + kind));
        return voice;
    }

    public static void register(IEventBus modBus)
    {
        SOUNDS.register(modBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name)
    {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name)));
    }
}
