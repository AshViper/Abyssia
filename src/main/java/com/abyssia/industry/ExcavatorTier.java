package com.abyssia.industry;

/**
 * The numbers of each Abyssal Excavator tier, in one place. Mk1 and Mk2 (which minerals each can mine: ExcavatorMinerals); a new tier is a new constant
 * here plus its block registration (the block entity reads everything from the tier of its block).
 * Energy buffer / receive rate follow the processing machines (MachineKind) like every other industrial block.
 */
public enum ExcavatorTier
{
    //   tier  energyCapacity                  energyReceive                 FE/t  cycle ore  radius
    MK1(1, MachineKind.MACHINE_CAPACITY, MachineKind.MACHINE_RECEIVE, 20, 40, 1, 32),
    MK2(2, MachineKind.MACHINE_CAPACITY, MachineKind.MACHINE_RECEIVE, 40, 30, 2, 48);

    public final int tier;
    public final int energyCapacity;
    public final int energyReceive;
    /** FE used on every tick that is spent mining */
    public final int energyPerTick;
    /** ticks of mining per cycle */
    public final int cycleTicks;
    /** ore taken from the deposit per finished cycle */
    public final int orePerCycle;
    /** horizontal radius (blocks) in which a deposit is looked for (OreDepositManager#findNearby) */
    public final int workingRadius;

    ExcavatorTier(int tier, int energyCapacity, int energyReceive, int energyPerTick, int cycleTicks, int orePerCycle, int workingRadius)
    {
        this.tier = tier;
        this.energyCapacity = energyCapacity;
        this.energyReceive = energyReceive;
        this.energyPerTick = energyPerTick;
        this.cycleTicks = cycleTicks;
        this.orePerCycle = orePerCycle;
        this.workingRadius = workingRadius;
    }

    /** FE for one finished cycle (Mk1: 800, Mk2: 1200). */
    public int energyPerCycle()
    {
        return energyPerTick * cycleTicks;
    }
}
