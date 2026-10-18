package com.abyssia.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** abyssia:capture, fired right after a capture canister catches an entity. */
public class CaptureTrigger extends SimpleCriterionTrigger<CaptureTrigger.TriggerInstance>
{
    @Override
    public Codec<TriggerInstance> codec() { return TriggerInstance.CODEC; }

    public void trigger(ServerPlayer player, ResourceLocation entityType)
    {
        trigger(player, i -> i.entity.isEmpty() || i.entity.get().equals(entityType));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> entity) implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ResourceLocation.CODEC.optionalFieldOf("entity").forGetter(TriggerInstance::entity)
        ).apply(i, TriggerInstance::new));
    }
}
