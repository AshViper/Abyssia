package com.abyssia.advancement;

import com.abyssia.research.ScanTarget;
import com.abyssia.research.ScanTargetRegistry;
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

/** abyssia:scan, state based: looks at the whole scan database. */
public class ScanTrigger extends SimpleCriterionTrigger<ScanTrigger.TriggerInstance>
{
    @Override
    public Codec<TriggerInstance> codec() { return TriggerInstance.CODEC; }

    public void trigger(ServerPlayer player, Set<ResourceLocation> scanned)
    {
        trigger(player, i -> i.matches(scanned));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> target, Optional<String> group,
            MinMaxBounds.Ints count, boolean complete) implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ResourceLocation.CODEC.optionalFieldOf("target").forGetter(TriggerInstance::target),
                Codec.STRING.optionalFieldOf("group").forGetter(TriggerInstance::group),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::count),
                Codec.BOOL.optionalFieldOf("complete", false).forGetter(TriggerInstance::complete)
        ).apply(i, TriggerInstance::new));

        private static final String PREFIX = "scan_target/";

        private static boolean inGroup(ResourceLocation id, String g)
        {
            String path = id.getPath();
            if (path.startsWith(PREFIX)) path = path.substring(PREFIX.length());
            return path.startsWith(g);
        }

        boolean matches(Set<ResourceLocation> scanned)
        {
            if (target.isPresent() && !scanned.contains(target.get())) return false;
            if (group.isEmpty())
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
            String g = group.get();
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
    }
}
