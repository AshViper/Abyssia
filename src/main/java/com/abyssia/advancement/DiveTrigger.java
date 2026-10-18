package com.abyssia.advancement;

import com.abyssia.item.PressureGear;
import com.abyssia.vehicle.Submarine;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

/** abyssia:dive, fired every 20 ticks per player. */
public class DiveTrigger extends SimpleCriterionTrigger<DiveTrigger.TriggerInstance>
{
    @Override
    public Codec<TriggerInstance> codec() { return TriggerInstance.CODEC; }

    public void trigger(ServerPlayer player)
    {
        double y = player.getY();
        boolean submerged = player.isUnderWater();
        Submarine sub = player.getVehicle() instanceof Submarine s ? s : null;
        int upgrades = sub == null ? -1 : Integer.bitCount(sub.upgradeMask() & ((1 << com.abyssia.vehicle.SubmarineUpgrades.SLOTS) - 1));
        int tier = PressureGear.wornTier(player);
        int pieces = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            if (PressureGear.pieceTier(player.getItemBySlot(slot)) > 0) pieces++;
        double health = player.getHealth();
        final int fPieces = pieces;
        trigger(player, i -> i.matches(y, submerged, sub != null, upgrades, tier, fPieces, health));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, MinMaxBounds.Doubles y, Optional<Boolean> submerged,
            Optional<Boolean> inSubmarine, MinMaxBounds.Ints submarineUpgrades, MinMaxBounds.Ints suitTier,
            MinMaxBounds.Ints suitPieces, MinMaxBounds.Doubles health) implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("y", MinMaxBounds.Doubles.ANY).forGetter(TriggerInstance::y),
                Codec.BOOL.optionalFieldOf("submerged").forGetter(TriggerInstance::submerged),
                Codec.BOOL.optionalFieldOf("in_submarine").forGetter(TriggerInstance::inSubmarine),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("submarine_upgrades", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::submarineUpgrades),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("suit_tier", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::suitTier),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("suit_pieces", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::suitPieces),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("health", MinMaxBounds.Doubles.ANY).forGetter(TriggerInstance::health)
        ).apply(i, TriggerInstance::new));

        boolean matches(double py, boolean isSubmerged, boolean riding, int upgrades, int tier, int pieces, double hp)
        {
            if (!y.matches(py)) return false;
            if (submerged.isPresent() && submerged.get() != isSubmerged) return false;
            if (inSubmarine.isPresent() && inSubmarine.get() != riding) return false;
            if (!submarineUpgrades.isAny() && (!riding || !submarineUpgrades.matches(upgrades))) return false;
            return suitTier.matches(tier) && suitPieces.matches(pieces) && health.matches(hp);
        }
    }
}
