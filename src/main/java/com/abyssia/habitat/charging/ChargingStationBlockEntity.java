package com.abyssia.habitat.charging;

import com.abyssia.furniture.ChargeSlotHandler;
import com.abyssia.industry.energy.CableNetworkManager;
import com.abyssia.industry.energy.IndustryEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * BT01e charging station: a 20,000 FE buffer that only receives (Capabilities.EnergyStorage.BLOCK on every face,
 * registered in {@link ChargingContent}, so HabitatPower's wireless distribution and cables treat it as a consumer)
 * and gives at most 200 FE/t in total to the FE items ({@link ChargeSlotHandler#chargeable}, abyssia:energy component
 * via Capabilities.EnergyStorage.ITEM) in the inventories of players within 3 blocks. The share is split evenly, the
 * first item rotating every tick (round-robin), and the rest of the budget goes to whoever still takes it.
 */
public class ChargingStationBlockEntity extends BlockEntity
{
    public static final int CAPACITY = 20_000;
    public static final int MAX_RECEIVE = 2_000;
    public static final int CHARGE_RATE = 200;
    public static final double RANGE = 3.0;

    private final IndustryEnergyStorage energy = new IndustryEnergyStorage(CAPACITY, MAX_RECEIVE, 0, this::setChanged);
    private int cursor;

    public ChargingStationBlockEntity(BlockPos pos, BlockState state)
    {
        super(ChargingContent.STATION_ENTITY.get(), pos, state);
    }

    /** the buffer (capability provider, tests / tooling) */
    public IEnergyStorage energy()
    {
        return energy;
    }

    public void serverTick()
    {
        if (level == null) return;
        if ((level.getGameTime() + worldPosition.asLong()) % 10 == 0) CableNetworkManager.touchAround(level, worldPosition);
        if (energy.getEnergyStored() <= 0) return;
        List<IEnergyStorage> targets = targets();
        if (targets.isEmpty()) return;
        int budget = Math.min(CHARGE_RATE, energy.getEnergyStored());
        int n = targets.size();
        int start = Math.floorMod(cursor, n);
        cursor = start + 1;
        int share = Math.max(1, budget / n);
        int spent = 0;
        for (int k = 0; k < n && spent < budget; k++)
            spent += Math.max(0, targets.get((start + k) % n).receiveEnergy(Math.min(share, budget - spent), false));
        for (int k = 0; k < n && spent < budget; k++)
            spent += Math.max(0, targets.get((start + k) % n).receiveEnergy(budget - spent, false));
        if (spent > 0) energy.consume(Math.min(spent, budget));
    }

    /** chargeable FE items that still take energy, of every player within RANGE */
    private List<IEnergyStorage> targets()
    {
        List<IEnergyStorage> out = new ArrayList<>();
        Vec3 centre = Vec3.atCenterOf(worldPosition);
        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(RANGE + 1),
                p -> p.isAlive() && !p.isSpectator() && p.position().distanceToSqr(centre) <= RANGE * RANGE + 1.0e-3))
        {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++)
            {
                ItemStack stack = inv.getItem(i);
                if (!ChargeSlotHandler.chargeable(stack)) continue;
                IEnergyStorage target = stack.getCapability(Capabilities.EnergyStorage.ITEM);
                if (target != null && target.receiveEnergy(CHARGE_RATE, true) > 0) out.add(target);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("Energy"));
    }
}
