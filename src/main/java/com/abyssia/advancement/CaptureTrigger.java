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

/** abyssia:capture, fired right after a capture canister catches an entity. */
public class CaptureTrigger extends SimpleCriterionTrigger<CaptureTrigger.TriggerInstance>
{
    static final ResourceLocation ID = new ResourceLocation(Abyssia.MODID, "capture");

    @Override
    public ResourceLocation getId() { return ID; }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext ctx)
    {
        return new TriggerInstance(player, json.has("entity") ? new ResourceLocation(GsonHelper.getAsString(json, "entity")) : null);
    }

    public void trigger(ServerPlayer player, ResourceLocation entityType)
    {
        trigger(player, i -> i.entity == null || i.entity.equals(entityType));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance
    {
        private final ResourceLocation entity;

        TriggerInstance(ContextAwarePredicate player, ResourceLocation entity)
        {
            super(ID, player);
            this.entity = entity;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx)
        {
            JsonObject o = super.serializeToJson(ctx);
            if (entity != null) o.addProperty("entity", entity.toString());
            return o;
        }
    }
}
