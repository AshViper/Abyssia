package com.abyssia.advancement;

import com.abyssia.Abyssia;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom advancement triggers (ACH01). */
public final class ModTriggers
{
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Abyssia.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, DiveTrigger> DIVE = TRIGGERS.register("dive", DiveTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, ScanTrigger> SCAN = TRIGGERS.register("scan", ScanTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, ResearchTrigger> RESEARCH = TRIGGERS.register("research", ResearchTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, BuildTrigger> BUILD = TRIGGERS.register("build", BuildTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, CaptureTrigger> CAPTURE = TRIGGERS.register("capture", CaptureTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, AquariumTrigger> AQUARIUM = TRIGGERS.register("aquarium", AquariumTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, EventTrigger> EVENT = TRIGGERS.register("event", EventTrigger::new);

    private ModTriggers() {}

    public static void register(IEventBus bus) { TRIGGERS.register(bus); }
}
