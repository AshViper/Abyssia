package com.abyssia.advancement;

import com.abyssia.Abyssia;
import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/** abyssia:build, fired when a habitat builder job completes. */
public class BuildTrigger extends SimpleCriterionTrigger<BuildTrigger.TriggerInstance>
{
    static final ResourceLocation ID = new ResourceLocation(Abyssia.MODID, "build");

    @Override
    public ResourceLocation getId() { return ID; }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext ctx)
    {
        return new TriggerInstance(player, json.has("build") ? GsonHelper.getAsString(json, "build") : null);
    }

    public void trigger(ServerPlayer player, String entryId)
    {
        trigger(player, i -> i.build == null || i.build.equals(entryId));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance
    {
        private final String build;

        TriggerInstance(ContextAwarePredicate player, String build)
        {
            super(ID, player);
            this.build = build;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx)
        {
            JsonObject o = super.serializeToJson(ctx);
            if (build != null) o.addProperty("build", build);
            return o;
        }
    }
}
