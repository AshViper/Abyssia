package com.abyssia.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/** abyssia:build, fired when a habitat builder job completes. */
public class BuildTrigger extends SimpleCriterionTrigger<BuildTrigger.TriggerInstance>
{
    @Override
    public Codec<TriggerInstance> codec() { return TriggerInstance.CODEC; }

    public void trigger(ServerPlayer player, String entryId)
    {
        trigger(player, i -> i.build.isEmpty() || i.build.get().equals(entryId));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<String> build) implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.optionalFieldOf("build").forGetter(TriggerInstance::build)
        ).apply(i, TriggerInstance::new));
    }
}
