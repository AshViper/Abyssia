package com.abyssia.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/** abyssia:aquarium, fired right after a successful release into an aquarium. */
public class AquariumTrigger extends SimpleCriterionTrigger<AquariumTrigger.TriggerInstance>
{
    @Override
    public Codec<TriggerInstance> codec() { return TriggerInstance.CODEC; }

    public void trigger(ServerPlayer player, String species, int count, int speciesCount)
    {
        trigger(player, i -> (i.species.isEmpty() || i.species.get().equals(species)) && i.count.matches(count)
                && i.speciesCount.matches(speciesCount));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<String> species, MinMaxBounds.Ints count,
            MinMaxBounds.Ints speciesCount) implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.optionalFieldOf("species").forGetter(TriggerInstance::species),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::count),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("species_count", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::speciesCount)
        ).apply(i, TriggerInstance::new));
    }
}
