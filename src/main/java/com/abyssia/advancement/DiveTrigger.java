package com.abyssia.advancement;

import com.abyssia.Abyssia;
import com.abyssia.item.PressureGear;
import com.abyssia.vehicle.Submarine;
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
import net.minecraft.world.entity.EquipmentSlot;

/** abyssia:dive, fired every 20 ticks per player. */
public class DiveTrigger extends SimpleCriterionTrigger<DiveTrigger.TriggerInstance>
{
    static final ResourceLocation ID = new ResourceLocation(Abyssia.MODID, "dive");

    @Override
    public ResourceLocation getId() { return ID; }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext ctx)
    {
        return new TriggerInstance(player, MinMaxBounds.Doubles.fromJson(json.get("y")),
                json.has("submerged") ? Boolean.valueOf(GsonHelper.getAsBoolean(json, "submerged")) : null,
                json.has("in_submarine") ? Boolean.valueOf(GsonHelper.getAsBoolean(json, "in_submarine")) : null,
                MinMaxBounds.Ints.fromJson(json.get("submarine_upgrades")), MinMaxBounds.Ints.fromJson(json.get("suit_tier")),
                MinMaxBounds.Ints.fromJson(json.get("suit_pieces")), MinMaxBounds.Doubles.fromJson(json.get("health")));
    }

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

    public static class TriggerInstance extends AbstractCriterionTriggerInstance
    {
        private final MinMaxBounds.Doubles y;
        private final Boolean submerged;
        private final Boolean inSubmarine;
        private final MinMaxBounds.Ints submarineUpgrades;
        private final MinMaxBounds.Ints suitTier;
        private final MinMaxBounds.Ints suitPieces;
        private final MinMaxBounds.Doubles health;

        TriggerInstance(ContextAwarePredicate player, MinMaxBounds.Doubles y, Boolean submerged, Boolean inSubmarine,
                MinMaxBounds.Ints submarineUpgrades, MinMaxBounds.Ints suitTier, MinMaxBounds.Ints suitPieces, MinMaxBounds.Doubles health)
        {
            super(ID, player);
            this.y = y;
            this.submerged = submerged;
            this.inSubmarine = inSubmarine;
            this.submarineUpgrades = submarineUpgrades;
            this.suitTier = suitTier;
            this.suitPieces = suitPieces;
            this.health = health;
        }

        boolean matches(double py, boolean isSubmerged, boolean riding, int upgrades, int tier, int pieces, double hp)
        {
            if (!y.matches(py)) return false;
            if (submerged != null && submerged != isSubmerged) return false;
            if (inSubmarine != null && inSubmarine != riding) return false;
            if (!submarineUpgrades.isAny() && (!riding || !submarineUpgrades.matches(upgrades))) return false;
            return suitTier.matches(tier) && suitPieces.matches(pieces) && health.matches(hp);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx)
        {
            JsonObject o = super.serializeToJson(ctx);
            if (!y.isAny()) o.add("y", y.serializeToJson());
            if (submerged != null) o.addProperty("submerged", submerged);
            if (inSubmarine != null) o.addProperty("in_submarine", inSubmarine);
            if (!submarineUpgrades.isAny()) o.add("submarine_upgrades", submarineUpgrades.serializeToJson());
            if (!suitTier.isAny()) o.add("suit_tier", suitTier.serializeToJson());
            if (!suitPieces.isAny()) o.add("suit_pieces", suitPieces.serializeToJson());
            if (!health.isAny()) o.add("health", health.serializeToJson());
            return o;
        }
    }
}
