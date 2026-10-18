package com.abyssia.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/** abyssia:event, one-off events (pressure_damage, hazard_damage, ...). */
public class EventTrigger extends SimpleCriterionTrigger<EventTrigger.TriggerInstance>
{
    @Override
    public Codec<TriggerInstance> codec() { return TriggerInstance.CODEC; }

    public void trigger(ServerPlayer player, String event, String key)
    {
        trigger(player, i -> (i.event.isEmpty() || i.event.get().equals(event)) && (i.key.isEmpty() || i.key.get().equals(key)));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<String> event, Optional<String> key) implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.optionalFieldOf("event").forGetter(TriggerInstance::event),
                Codec.STRING.optionalFieldOf("key").forGetter(TriggerInstance::key)
        ).apply(i, TriggerInstance::new));
    }
}
