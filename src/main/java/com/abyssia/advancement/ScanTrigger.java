package com.abyssia.advancement;

import com.abyssia.Abyssia;
import com.abyssia.research.ScanTarget;
import com.abyssia.research.ScanTargetRegistry;
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

/** abyssia:scan, state based: looks at the whole scan database. */
public class ScanTrigger extends SimpleCriterionTrigger<ScanTrigger.TriggerInstance>
{
    static final ResourceLocation ID = new ResourceLocation(Abyssia.MODID, "scan");

    @Override
    public ResourceLocation getId() { return ID; }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext ctx)
    {
        return new TriggerInstance(player,
                json.has("target") ? new ResourceLocation(GsonHelper.getAsString(json, "target")) : null,
                json.has("group") ? GsonHelper.getAsString(json, "group") : null,
                MinMaxBounds.Ints.fromJson(json.get("count")),
                GsonHelper.getAsBoolean(json, "complete", false));
    }

    public void trigger(ServerPlayer player, Set<ResourceLocation> scanned)
    {
        trigger(player, i -> i.matches(scanned));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance
    {
        private static final String PREFIX = "scan_target/";

        private final ResourceLocation target;
        private final String group;
        private final MinMaxBounds.Ints count;
        private final boolean complete;

        TriggerInstance(ContextAwarePredicate player, ResourceLocation target, String group, MinMaxBounds.Ints count, boolean complete)
        {
            super(ID, player);
            this.target = target;
            this.group = group;
            this.count = count;
            this.complete = complete;
        }

        private static boolean inGroup(ResourceLocation id, String g)
        {
            String path = id.getPath();
            if (path.startsWith(PREFIX)) path = path.substring(PREFIX.length());
            return path.startsWith(g);
        }

        boolean matches(Set<ResourceLocation> scanned)
        {
            if (target != null && !scanned.contains(target)) return false;
            if (group == null)
            {
                int have0 = 0, total0 = 0;
                for (ScanTarget t : ScanTargetRegistry.all())
                {
                    total0++;
                    if (scanned.contains(t.id())) have0++;
                }
                if (!count.matches(have0)) return false;
                if (complete && (total0 == 0 || have0 < total0)) return false;
                return true;
            }
            String g = group;
            int have = 0;
            for (ResourceLocation id : scanned) if (inGroup(id, g)) have++;
            if (!count.matches(have)) return false;
            if (complete)
            {
                int total = 0;
                for (ScanTarget t : ScanTargetRegistry.all())
                    if (inGroup(t.id(), g))
                    {
                        total++;
                        if (!scanned.contains(t.id())) return false;
                    }
                if (total == 0) return false;
            }
            return true;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx)
        {
            JsonObject o = super.serializeToJson(ctx);
            if (target != null) o.addProperty("target", target.toString());
            if (group != null) o.addProperty("group", group);
            if (!count.isAny()) o.add("count", count.serializeToJson());
            if (complete) o.addProperty("complete", true);
            return o;
        }
    }
}
