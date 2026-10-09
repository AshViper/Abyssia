package com.abyssia.research.client;

import com.abyssia.research.ClientResearch;
import com.abyssia.research.ScanTarget;
import com.abyssia.research.Technology;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** {@link ResearchView.Source} over R's {@link ClientResearch}: the only place that knows R's record accessors. */
final class ClientResearchSource implements ResearchView.Source
{
    public boolean isUnlocked(ResourceLocation tech) { return ClientResearch.isUnlocked(tech); }
    public boolean isScanned(ResourceLocation target) { return ClientResearch.isScanned(target); }
    public int fragments(ResourceLocation target) { return ClientResearch.fragments(target); }

    public List<ResearchView.Tech> technologies()
    {
        List<ResearchView.Tech> out = new ArrayList<>();
        for (Technology t : ClientResearch.technologies())
        {
            List<ResearchView.Req> reqs = new ArrayList<>();
            for (Technology.Requirement r : t.requirements()) reqs.add(new ResearchView.Req(r.target(), r.count()));
            List<String> unlocks = new ArrayList<>();
            for (ResourceLocation u : t.unlocks()) unlocks.add(u.getPath());
            out.add(new ResearchView.Tech(t.id(), Component.translatable(t.titleKey()), nz(t.tier()), nz(t.depthBand()), reqs,
                    t.prerequisites(), unlocks));
        }
        return out;
    }

    public List<ResearchView.Target> targets()
    {
        List<ResearchView.Target> out = new ArrayList<>();
        for (ScanTarget t : ClientResearch.targets())
            out.add(new ResearchView.Target(t.id(), nz(t.category()), nz(t.nameKey()), t.nameKey() == null ? "" : t.nameKey() + ".desc"));
        return out;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
