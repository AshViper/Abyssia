package com.abyssia.industry.blockentity;

import com.abyssia.industry.MachineKind;
import com.abyssia.industry.VentHeat;
import com.abyssia.industry.energy.EnergyLookup;
import com.abyssia.registry.ModIndustry;
import com.abyssia.registry.ModPlants;
import com.abyssia.thermal.VentActivity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hydrothermal generator (80 FE/t x the most active neighbouring vent, 32,000 FE) and auxiliary generator
 * (40 FE/t from coal / charcoal 80,000 FE, bio oil 120,000, refined oil 200,000; 16,000 FE). Both push their energy
 * into neighbouring receivers and are pulled by cable networks; neither accepts energy.
 * Status of the hydrothermal generator: 0 = no vent next to it, else VentActivity ordinal + 1.
 */
public class GeneratorBlockEntity extends IndustryBlockEntity
{
    public static final int HYDRO_RATE = 80;
    public static final int AUX_RATE = 40;

    private int ventCheck;

    public GeneratorBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModIndustry.GENERATOR_ENTITY.get(), pos, state, capacity(state), 0, maxOutput(state));
    }

    private static boolean hydro(BlockState state)
    {
        return state.is(ModIndustry.HYDROTHERMAL_GENERATOR.get());
    }

    private static int capacity(BlockState state)
    {
        return hydro(state) ? 32_000 : 16_000;
    }

    private static int maxOutput(BlockState state)
    {
        return hydro(state) ? 4 * HYDRO_RATE : 4 * AUX_RATE;
    }

    /** FE a fuel item gives in the auxiliary generator, 0 if it is no fuel. */
    public static int fuelEnergy(ItemStack stack)
    {
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return 80_000;
        if (stack.is(ModPlants.BIO_OIL.get())) return 120_000;
        if (stack.is(ModPlants.REFINED_OIL.get())) return 200_000;
        return 0;
    }

    @Override
    protected void work()
    {
        boolean working = kind == MachineKind.HYDROTHERMAL_GENERATOR ? hydrothermal() : auxiliary();
        EnergyLookup.pushToNeighbours(level, worldPosition, energy, energy.maxExtract(), false);
        setWorking(working);
    }

    private boolean hydrothermal()
    {
        if (--ventCheck <= 0)
        {
            ventCheck = 10;
            VentActivity activity = VentHeat.adjacentActivity(level, worldPosition);
            status = activity == null ? 0 : activity.ordinal() + 1;
            rate = Math.round(HYDRO_RATE * VentHeat.multiplier(activity));
        }
        if (rate <= 0) return false;
        energy.generate(rate);
        return true;
    }

    private boolean auxiliary()
    {
        if (burn <= 0 && energy.room() >= AUX_RATE)
        {
            ItemStack fuel = items.getStackInSlot(0);
            int fe = fuelEnergy(fuel);
            if (fe > 0)
            {
                items.extractItem(0, 1, false);
                burn = burnMax = fe / AUX_RATE;
            }
        }
        // pause (keep the fuel burning state) while the buffer is full
        if (burn > 0 && energy.room() > 0)
        {
            energy.generate(AUX_RATE);
            burn--;
            if (burn == 0) burnMax = 0;
            rate = AUX_RATE;
            setChanged();
            return true;
        }
        rate = 0;
        return false;
    }
}
