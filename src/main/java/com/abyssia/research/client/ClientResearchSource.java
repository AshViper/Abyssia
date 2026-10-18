package com.abyssia.research.client;

import com.abyssia.research.ClientResearch;
import com.abyssia.research.ScanTarget;
import com.abyssia.research.Technology;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.common.ForgeSpawnEggItem;

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
                    t.prerequisites(), unlocks, t.icon()));
        }
        return out;
    }

    public List<ResearchView.Target> targets()
    {
        List<ResearchView.Target> out = new ArrayList<>();
        for (ScanTarget t : ClientResearch.targets())
            out.add(new ResearchView.Target(t.id(), nz(t.category()), nz(t.nameKey()), t.nameKey() == null ? "" : t.nameKey() + ".desc", targetIcon(t)));
        return out;
    }

    private static ResourceLocation targetIcon(ScanTarget t)
    {
        try
        {
            ScanTarget.Match m = t.match();
            if (m == null || m.type() == null) return null;
            switch (m.type())
            {
                case BLOCK:
                {
                    Item item = BuiltInRegistries.BLOCK.get(m.value()).asItem();
                    return item == Items.AIR ? null : BuiltInRegistries.ITEM.getKey(item);
                }
                case ENTITY:
                {
                    var type = BuiltInRegistries.ENTITY_TYPE.getOptional(m.value()).orElse(null);
                    SpawnEggItem egg = type == null ? null : ForgeSpawnEggItem.fromEntityType(type);
                    return egg == null ? null : BuiltInRegistries.ITEM.getKey(egg);
                }
                case DEPOSIT_MINERAL:
                {
                    if (BuiltInRegistries.ITEM.containsKey(m.value())) return m.value();
                    Item item = BuiltInRegistries.BLOCK.get(m.value()).asItem();
                    return item == Items.AIR ? null : BuiltInRegistries.ITEM.getKey(item);
                }
                default:
                    return null;
            }
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
