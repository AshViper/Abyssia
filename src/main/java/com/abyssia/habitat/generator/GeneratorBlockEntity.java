package com.abyssia.habitat.generator;

import com.abyssia.Abyssia;
import com.abyssia.block.ThermalVentBlock;
import com.abyssia.environment.NaturalCurrents;
import com.abyssia.habitat.power.HabitatPower;
import com.abyssia.industry.VentHeat;
import com.abyssia.registry.ModPlants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * BT01d generator controller. No energy capability (no double counting with the base scan): every server tick it
 * pushes its output straight into the base through {@link HabitatPower#externalReceiver} of a habitat shell block next
 * to its footprint, so a generator that touches no registered shell makes 0 FE.
 * <ul>
 *   <li>current turbine: 120 x felt current strength at the rotor hub, below 0.1 nothing, cap 240 FE/t</li>
 *   <li>geothermal: 80 x VentHeat.multiplier(activity of the vent right under the core) = 0/40/80/120/160 FE/t</li>
 *   <li>biofuel: bio_oil 100,000 FE, refined_oil 200,000 FE, burnt at 100 FE/t only as far as the base accepts it</li>
 * </ul>
 * Debug: {@link #lastOutput()} = FE actually delivered last tick, {@link #potential()} = what it could make.
 */
public class GeneratorBlockEntity extends BlockEntity
{
    public static final int TURBINE_BASE = 120, TURBINE_CAP = 240, GEOTHERMAL_BASE = 80, BURN = 100;
    public static final int BIO_OIL_FE = 100_000, REFINED_OIL_FE = 200_000;
    public static final float MIN_CURRENT = 0.1f;
    private static final int RESCAN_TICKS = 40, SAMPLE_TICKS = 20;
    /** rotor degrees per tick at current strength 1 */
    private static final float ROTOR_DEG = 12.0f;

    /** a shell block taking FE, with the base receiver resolved at the last rescan (one outlet per receiver) */
    private record Outlet(BlockPos shell, Direction side, IEnergyStorage receiver) {}

    private final ItemStackHandler fuelSlot = new ItemStackHandler(1)
    {
        @Override
        public boolean isItemValid(int slot, ItemStack stack)
        {
            return kind() == GeneratorKind.BIOFUEL && fuelValue(stack) > 0;
        }

        @Override
        protected void onContentsChanged(int slot)
        {
            setChanged();
        }
    };

    /** FE left from burnt items */
    private int fuel;
    private int lastOutput, potential, sampled;
    private float sampledStrength;
    private long nextScan, nextSample;
    private List<Outlet> outlets = List.of();

    // client: rotor animation
    private float rotor, prevRotor, rotorSpeed;
    private long nextClientSample;

    public GeneratorBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModGenerators.GENERATOR_ENTITY.get(), pos, state);
    }

    public GeneratorKind kind()
    {
        return getBlockState().getBlock() instanceof GeneratorBlock b ? b.kind : GeneratorKind.BIOFUEL;
    }

    public Direction forward()
    {
        BlockState state = getBlockState();
        return state.hasProperty(GeneratorBlock.FACING) ? state.getValue(GeneratorBlock.FACING) : Direction.SOUTH;
    }

    public BlockPos origin()
    {
        return kind().origin(worldPosition, forward());
    }

    /** FE delivered to the base on the last server tick */
    public int lastOutput()
    {
        return lastOutput;
    }

    /** FE/t the generator could make right now (before the shell / base-full limit) */
    public int potential()
    {
        return potential;
    }

    public boolean connected()
    {
        return !outlets.isEmpty();
    }

    public int fuel()
    {
        return fuel;
    }

    public ItemStackHandler fuelSlot()
    {
        return fuelSlot;
    }

    public static int fuelValue(ItemStack stack)
    {
        if (stack.isEmpty()) return 0;
        if (stack.is(ModPlants.BIO_OIL.get())) return BIO_OIL_FE;
        if (stack.is(ModPlants.REFINED_OIL.get())) return REFINED_OIL_FE;
        return 0;
    }

    // ---------------------------------------------------------------- server

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity be)
    {
        if (level instanceof ServerLevel server) be.tickServer(server);
    }

    private void tickServer(ServerLevel level)
    {
        GeneratorKind kind = kind();
        long now = level.getGameTime();
        if (now >= nextScan)
        {
            outlets = findOutlets(level, kind);
            nextScan = now + RESCAN_TICKS;
        }
        if (now >= nextSample)
        {
            sampled = sample(level, kind);
            nextSample = now + SAMPLE_TICKS;
        }
        if (kind == GeneratorKind.BIOFUEL)
        {
            if (fuel < BURN && !outlets.isEmpty())
            {
                int value = fuelValue(fuelSlot.getStackInSlot(0));
                if (value > 0)
                {
                    fuelSlot.extractItem(0, 1, false);
                    fuel += value;
                    setChanged();
                }
            }
            potential = Math.min(BURN, fuel);
        }
        else potential = sampled;

        int delivered = potential <= 0 || outlets.isEmpty() ? 0 : push(level, potential);
        if (kind == GeneratorKind.BIOFUEL && delivered > 0)
        {
            fuel -= delivered;
            setChanged();
        }
        lastOutput = delivered;
    }

    /** FE/t from the environment (turbine / geothermal), refreshed every second */
    private int sample(ServerLevel level, GeneratorKind kind)
    {
        BlockPos origin = origin();
        Direction forward = forward();
        switch (kind)
        {
            case CURRENT_TURBINE ->
            {
                BlockPos hub = kind.rotorCell(origin, forward);
                sampledStrength = level.isLoaded(hub) ? NaturalCurrents.getCurrentAt(level, hub).getLocalStrength() : 0f;
                return turbineOutput(sampledStrength);
            }
            case GEOTHERMAL ->
            {
                BlockPos vent = worldPosition.below();
                if (!level.isLoaded(vent)) return 0;
                BlockState state = level.getBlockState(vent);
                if (!(state.getBlock() instanceof ThermalVentBlock)) return 0;
                return Math.round(GEOTHERMAL_BASE * VentHeat.multiplier(state.getValue(ThermalVentBlock.ACTIVITY)));
            }
            default ->
            {
                return 0;
            }
        }
    }

    public static int turbineOutput(float strength)
    {
        if (strength < MIN_CURRENT) return 0;
        return Math.min(TURBINE_CAP, Math.round(TURBINE_BASE * strength));
    }

    /** habitat shell blocks next to the footprint that accept FE (registered base, generator side outside modules) */
    private List<Outlet> findOutlets(ServerLevel level, GeneratorKind kind)
    {
        BlockPos origin = origin();
        Direction forward = forward();
        BoundingBox box = kind.blockBox(origin, forward);
        List<Outlet> out = new ArrayList<>();
        Set<IEnergyStorage> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockPos cell : kind.cells(origin, forward))
            for (Direction dir : Direction.values())
            {
                BlockPos n = cell.relative(dir);
                if (box.isInside(n) || !level.isLoaded(n)) continue;
                IEnergyStorage receiver = HabitatPower.externalReceiver(level, n, dir.getOpposite());
                if (receiver != null && seen.add(receiver)) out.add(new Outlet(n, dir.getOpposite(), receiver));
            }
        return out.isEmpty() ? List.of() : out;
    }

    private int push(ServerLevel level, int amount)
    {
        // receivers cached at the rescan (every RESCAN_TICKS); a base that takes nothing (full / input cap) ends the push
        int remaining = amount;
        for (Outlet o : outlets)
        {
            int accepted = Math.max(0, o.receiver().receiveEnergy(remaining, false));
            if (accepted <= 0) break;
            remaining -= accepted;
            if (remaining <= 0) break;
        }
        return amount - Math.max(0, remaining);
    }

    /** chat lines for an empty-hand right-click (and for the test harness) */
    public List<Component> status()
    {
        String key = "message." + Abyssia.MODID + ".generator.";
        GeneratorKind kind = kind();
        Component name = getBlockState().getBlock().getName();
        List<Component> lines = new ArrayList<>();
        lines.add(connected() ? Component.translatable(key + "output", name, lastOutput, potential)
                : Component.translatable(key + "disconnected", name));
        switch (kind)
        {
            case CURRENT_TURBINE -> lines.add(Component.translatable(key + "current", String.format(Locale.ROOT, "%.2f", sampledStrength)));
            case GEOTHERMAL -> lines.add(Component.translatable(key + "vent", String.format(Locale.ROOT, "%.1f", sampled / (float) GEOTHERMAL_BASE)));
            case BIOFUEL ->
            {
                ItemStack slot = fuelSlot.getStackInSlot(0);
                long total = fuel + (long) slot.getCount() * fuelValue(slot);
                lines.add(Component.translatable(key + "fuel", fuel, slot.getCount(), total / BURN / 20));
            }
        }
        return lines;
    }

    public void sendStatus(Player player)
    {
        for (Component line : status()) player.displayClientMessage(line, false);
    }

    // ---------------------------------------------------------------- client

    public static void clientTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity be)
    {
        long now = level.getGameTime();
        if (now >= be.nextClientSample)
        {
            be.nextClientSample = now + SAMPLE_TICKS;
            float s = NaturalCurrents.getCurrentAt(level, be.kind().rotorCell(be.origin(), be.forward())).getLocalStrength();
            be.rotorSpeed = s < MIN_CURRENT ? 0f : ROTOR_DEG * Math.min(2f, s);
        }
        be.prevRotor = be.rotor;
        be.rotor += be.rotorSpeed;
        if (be.rotor > 3600f)
        {
            be.rotor -= 3600f;
            be.prevRotor -= 3600f;
        }
    }

    /** rotor angle in degrees, interpolated */
    public float rotorAngle(float partialTick)
    {
        return prevRotor + (rotor - prevRotor) * partialTick;
    }

    /** whole-structure render bounds (NeoForge: asked by GeneratorRenderer#getRenderBoundingBox) */
    public AABB renderBox()
    {
        return kind().box(origin(), forward()).inflate(0.5);
    }

    // ---------------------------------------------------------------- caps / NBT

    /** fuel slot for hoppers / pipes (biofuel only); Capabilities.ItemHandler.BLOCK registered in ModGenerators */
    @Nullable
    public IItemHandler itemHandler()
    {
        return kind() == GeneratorKind.BIOFUEL ? fuelSlot : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        tag.putInt("Fuel", fuel);
        tag.put("Slot", fuelSlot.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        fuel = tag.getInt("Fuel");
        if (tag.contains("Slot")) fuelSlot.deserializeNBT(registries, tag.getCompound("Slot"));
    }
}
