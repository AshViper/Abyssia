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

/** abyssia:event, one-off events (pressure_damage, hazard_damage, ...). */
public class EventTrigger extends SimpleCriterionTrigger<EventTrigger.TriggerInstance>
{
    static final ResourceLocation ID = new ResourceLocation(Abyssia.MODID, "event");

    @Override
    public ResourceLocation getId() { return ID; }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext ctx)
    {
        return new TriggerInstance(player, json.has("event") ? GsonHelper.getAsString(json, "event") : null,
                json.has("key") ? GsonHelper.getAsString(json, "key") : null);
    }

    public void trigger(ServerPlayer player, String event, String key)
    {
        trigger(player, i -> (i.event == null || i.event.equals(event)) && (i.key == null || i.key.equals(key)));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance
    {
        private final String event;
        private final String key;

        TriggerInstance(ContextAwarePredicate player, String event, String key)
        {
            super(ID, player);
            this.event = event;
            this.key = key;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx)
        {
            JsonObject o = super.serializeToJson(ctx);
            if (event != null) o.addProperty("event", event);
            if (key != null) o.addProperty("key", key);
            return o;
        }
    }
}
