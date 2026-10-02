package com.abyssia.industry.blockentity;

import com.abyssia.industry.block.EnergyDeviceBlock;
import com.abyssia.industry.energy.EnergyLookup;
import com.abyssia.registry.ModIndustry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Energy device (battery): 1,000,000 FE, 256 FE/t in and out. Pushes into neighbouring consumers only (never into
 * another storage); cable networks fill it from generators and drain it into machines. CHARGE 0..3 follows the fill.
 */
public class EnergyDeviceBlockEntity extends IndustryBlockEntity
{
    public static final int CAPACITY = 1_000_000;
    public static final int TRANSFER = 256;

    public EnergyDeviceBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModIndustry.ENERGY_DEVICE_ENTITY.get(), pos, state, CAPACITY, TRANSFER, TRANSFER);
    }

    @Override
    protected void work()
    {
        EnergyLookup.pushToNeighbours(level, worldPosition, energy, TRANSFER, true);
        if ((level.getGameTime() + worldPosition.asLong()) % 10 == 0) updateCharge();
    }

    /** 0 = empty, 1 = below a third, 2 = below two thirds, 3 = above. */
    public static int chargeLevel(int stored, int capacity)
    {
        if (stored <= 0 || capacity <= 0) return 0;
        return Math.min(3, 1 + (int) (3L * stored / capacity));
    }

    private void updateCharge()
    {
        BlockState state = getBlockState();
        if (!state.hasProperty(EnergyDeviceBlock.CHARGE)) return;
        int charge = chargeLevel(energy.getEnergyStored(), energy.getMaxEnergyStored());
        if (state.getValue(EnergyDeviceBlock.CHARGE) != charge)
            level.setBlock(worldPosition, state.setValue(EnergyDeviceBlock.CHARGE, charge), 3);
    }
}
