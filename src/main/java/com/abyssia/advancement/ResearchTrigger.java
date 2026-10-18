package com.abyssia.advancement;

import com.abyssia.Abyssia;
import com.abyssia.research.Technology;
import com.abyssia.research.TechnologyRegistry;
import com.google.gson.JsonObject;
import java.util.Set;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/** abyssia:research, state based: looks at all unlocked technologies. */
public class ResearchTrigger extends SimpleCriterionTrigger<ResearchTrigger.TriggerInstance>
{
    static final ResourceLocation ID = new ResourceLocation(Abyssia.MODID, "research");

    @Override
    public ResourceLocation getId() { return ID; }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext ctx)
    {
        return new TriggerInstance(player,
                json.has("technology") ? new ResourceLocation(GsonHelper.getAsString(json, "technology")) : null,
                MinMaxBounds.Ints.fromJson(json.get("count")),
                GsonHelper.getAsBoolean(json, "complete", false));
    }

    public void trigger(ServerPlayer player, Set<ResourceLocation> unlocked)
    {
        trigger(player, i -> i.matches(unlocked));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance
    {
        private final ResourceLocation technology;
        private final MinMaxBounds.Ints count;
        private final boolean complete;

        TriggerInstance(ContextAwarePredicate player, ResourceLocation technology, MinMaxBounds.Ints count, boolean complete)
        {
            super(ID, player);
            this.technology = technology;
            this.count = count;
            this.complete = complete;
        }

        boolean matches(Set<ResourceLocation> unlocked)
        {
            if (technology != null && !unlocked.contains(technology)) return false;
            int have = 0, total = 0;
            for (Technology t : TechnologyRegistry.all())
            {
                total++;
                if (unlocked.contains(t.id())) have++;
            }
            if (!count.matches(have)) return false;
            if (complete && (total == 0 || have < total)) return false;
            return true;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx)
        {
            JsonObject o = super.serializeToJson(ctx);
            if (technology != null) o.addProperty("technology", technology.toString());
            if (!count.isAny()) o.add("count", count.serializeToJson());
            if (complete) o.addProperty("complete", true);
            return o;
        }
    }
}
