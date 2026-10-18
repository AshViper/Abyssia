package com.abyssia.advancement;

import com.abyssia.Abyssia;
import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/** abyssia:aquarium, fired right after a successful release into an aquarium. */
public class AquariumTrigger extends SimpleCriterionTrigger<AquariumTrigger.TriggerInstance>
{
    static final ResourceLocation ID = new ResourceLocation(Abyssia.MODID, "aquarium");

    @Override
    public ResourceLocation getId() { return ID; }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext ctx)
    {
        return new TriggerInstance(player, json.has("species") ? GsonHelper.getAsString(json, "species") : null,
                MinMaxBounds.Ints.fromJson(json.get("count")), MinMaxBounds.Ints.fromJson(json.get("species_count")));
    }

    public void trigger(ServerPlayer player, String species, int count, int speciesCount)
    {
        trigger(player, i -> (i.species == null || i.species.equals(species)) && i.count.matches(count)
                && i.speciesCount.matches(speciesCount));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance
    {
        private final String species;
        private final MinMaxBounds.Ints count;
        private final MinMaxBounds.Ints speciesCount;

        TriggerInstance(ContextAwarePredicate player, String species, MinMaxBounds.Ints count, MinMaxBounds.Ints speciesCount)
        {
            super(ID, player);
            this.species = species;
            this.count = count;
            this.speciesCount = speciesCount;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx)
        {
            JsonObject o = super.serializeToJson(ctx);
            if (species != null) o.addProperty("species", species);
            if (!count.isAny()) o.add("count", count.serializeToJson());
            if (!speciesCount.isAny()) o.add("species_count", speciesCount.serializeToJson());
            return o;
        }
    }
}
