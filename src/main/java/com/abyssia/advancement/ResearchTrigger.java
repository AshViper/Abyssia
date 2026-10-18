package com.abyssia.advancement;

import com.abyssia.research.Technology;
import com.abyssia.research.TechnologyRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** abyssia:research, state based: looks at all unlocked technologies. */
public class ResearchTrigger extends SimpleCriterionTrigger<ResearchTrigger.TriggerInstance>
{
    @Override
    public Codec<TriggerInstance> codec() { return TriggerInstance.CODEC; }

    public void trigger(ServerPlayer player, Set<ResourceLocation> unlocked)
    {
        trigger(player, i -> i.matches(unlocked));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> technology,
            MinMaxBounds.Ints count, boolean complete) implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ResourceLocation.CODEC.optionalFieldOf("technology").forGetter(TriggerInstance::technology),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::count),
                Codec.BOOL.optionalFieldOf("complete", false).forGetter(TriggerInstance::complete)
        ).apply(i, TriggerInstance::new));

        boolean matches(Set<ResourceLocation> unlocked)
        {
            if (technology.isPresent() && !unlocked.contains(technology.get())) return false;
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
    }
}
