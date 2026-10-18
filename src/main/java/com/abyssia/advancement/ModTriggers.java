package com.abyssia.advancement;

import net.minecraft.advancements.CriteriaTriggers;

/** Custom advancement triggers (ACH01). */
public final class ModTriggers
{
    public static final DiveTrigger DIVE = new DiveTrigger();
    public static final ScanTrigger SCAN = new ScanTrigger();
    public static final ResearchTrigger RESEARCH = new ResearchTrigger();
    public static final BuildTrigger BUILD = new BuildTrigger();
    public static final CaptureTrigger CAPTURE = new CaptureTrigger();
    public static final AquariumTrigger AQUARIUM = new AquariumTrigger();
    public static final EventTrigger EVENT = new EventTrigger();

    private ModTriggers() {}

    /** Call once during mod construction. */
    public static void init()
    {
        CriteriaTriggers.register(DIVE);
        CriteriaTriggers.register(SCAN);
        CriteriaTriggers.register(RESEARCH);
        CriteriaTriggers.register(BUILD);
        CriteriaTriggers.register(CAPTURE);
        CriteriaTriggers.register(AQUARIUM);
        CriteriaTriggers.register(EVENT);
    }
}
