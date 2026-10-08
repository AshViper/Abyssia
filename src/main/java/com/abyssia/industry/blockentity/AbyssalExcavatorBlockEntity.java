package com.abyssia.industry.blockentity;

import com.abyssia.industry.ExcavatorTier;
import com.abyssia.industry.block.AbyssalExcavatorBlock;
import com.abyssia.registry.ModIndustry;
import com.abyssia.worldgen.deposit.OreDeposit;
import com.abyssia.worldgen.deposit.OreDepositManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import com.abyssia.industry.ExcavatorMinerals;
import com.abyssia.registry.ModBlocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Abyssal Excavator: mines an {@link OreDeposit} with FE. It is not a recipe machine: it only reuses what
 * IndustryBlockEntity offers (energy buffer, the single output slot, GUI data, LIT, capabilities, drops, menu).
 *
 * One tick: find / refresh the target deposit -> check energy -> check the output can take the ore -> consume FE and
 * advance -> on the last tick put the ore in the output slot, THEN OreDepositManager.incrementMined. The output is
 * checked before any FE is spent, so the deposit is never reduced without the ore being delivered.
 *
 * GUI data: the common ContainerData (progress, energy, rate, status) plus {@link #aux}:
 * aux[0] block registry id of the mineral, aux[1..2] remaining ore (lo/hi), aux[3..4] total ore (lo/hi).
 */
public class AbyssalExcavatorBlockEntity extends IndustryBlockEntity
{
    /** Status codes shown in the GUI (IndustryBlockEntity#status). */
    public static final int STATUS_IDLE = 0;
    public static final int STATUS_NO_DEPOSIT = 1;
    public static final int STATUS_NO_POWER = 2;
    public static final int STATUS_MINING = 3;
    public static final int STATUS_OUTPUT_FULL = 4;
    public static final int STATUS_DEPLETED = 5;
    /** the nearest deposit needs a higher excavator tier (ExcavatorMinerals) */
    public static final int STATUS_TIER_TOO_LOW = 6;

    private static final int SEARCH_INTERVAL = 20;
    /** positions looked at per tick when a depleted deposit's ore is turned into host rock */
    private static final int REPLACE_SCAN_PER_TICK = 512;

    private final ExcavatorTier tier;
    @Nullable
    private UUID targetDepositId;
    /** the deposit as of this tick (server side only, never saved) */
    @Nullable
    private OreDeposit targetDeposit;
    private int searchCooldown;
    /** depletion sweep over the deposit's bounds (server only, not saved: a reload simply sweeps again) */
    private long sweepCursor;
    private boolean sweepDone;
    private final int[] aux = new int[5];
    /** game time of the last adoption scan (server only, not saved) */
    private long lastAdoptTick = Long.MIN_VALUE / 2;

    private final ContainerData excavatorData = new ContainerData()
    {
        @Override
        public int get(int index)
        {
            if (index < data.getCount())
                return data.get(index);

            int auxIndex = index - data.getCount();
            return auxIndex >= 0 && auxIndex < aux.length ? aux[auxIndex] : 0;
        }

        @Override
        public void set(int index, int value)
        {
            // Client -> Server の書き込みは不要
        }

        @Override
        public int getCount()
        {
            return data.getCount() + aux.length;
        }
    };

    public ContainerData data()
    {
        return excavatorData;
    }

    public AbyssalExcavatorBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModIndustry.EXCAVATOR_ENTITY.get(), pos, state,
                tierOf(state).energyCapacity, tierOf(state).energyReceive, 0);
        this.tier = tierOf(state);
        this.maxProgress = tier.cycleTicks;
    }

    private static ExcavatorTier tierOf(BlockState state)
    {
        return state.getBlock() instanceof AbyssalExcavatorBlock block ? block.tier() : ExcavatorTier.MK1;
    }

    public ExcavatorTier tier()
    {
        return tier;
    }

    // ---------------------------------------------------------------- target deposit

    @Nullable
    public UUID targetDepositId()
    {
        return targetDepositId;
    }

    /** For the future scanner: point this excavator at a deposit (null = search again by itself). */
    public void setTargetDeposit(@Nullable UUID depositId)
    {
        if (Objects.equals(depositId, targetDepositId)) return;
        targetDepositId = depositId;
        targetDeposit = null;
        progress = 0;
        searchCooldown = 0;
        setChanged();
    }

    // ---------------------------------------------------------------- tick

    @Override
    protected void work()
    {
        rate = 0;
        boolean working = level instanceof ServerLevel serverLevel && mine(serverLevel);
        setWorking(working);
        refreshDisplay();
    }

    /** One tick of the work cycle; returns whether energy was spent mining. */
    private boolean mine(ServerLevel serverLevel)
    {
        refreshTarget(serverLevel);
        if (targetDeposit == null) return stop(STATUS_NO_DEPOSIT, true);

        int remaining = targetDeposit.remainingOre();
        if (remaining <= 0)
        {
            if (sweepDone)
            {
                // everything replaced: look for another deposit
                targetDepositId = null;
                targetDeposit = null;
                setChanged();
            }
            else
                replaceDepletedOre(serverLevel, targetDeposit);
            return stop(STATUS_DEPLETED, true);
        }
        if (!ExcavatorMinerals.canMine(tier, targetDeposit.mineralId())) return stop(STATUS_TIER_TOO_LOW, true);

        if (energy.getEnergyStored() < tier.energyPerTick) return stop(STATUS_NO_POWER, false);

        // never take more than the deposit has left (incrementMined does not clamp)
        int amount = Math.min(tier.orePerCycle, remaining);
        ItemStack ore = mineralStack(serverLevel, targetDeposit, amount);
        if (ore.isEmpty()) return stop(STATUS_NO_DEPOSIT, true); // mineral id is not a block with an item
        if (!canStore(ore)) return stop(STATUS_OUTPUT_FULL, false);

        status = STATUS_MINING;
        energy.consume(tier.energyPerTick);
        rate = tier.energyPerTick;

        if (++progress >= maxProgress)
        {
            progress = 0;
            store(ore);
            rollByproducts(serverLevel, targetDeposit);
            OreDepositManager.incrementMined(serverLevel, targetDepositId, amount);
            targetDeposit = OreDepositManager.get(serverLevel, targetDepositId).orElse(null);
            sweepCursor = 0;
            sweepDone = false;
        }
        setChanged();
        return true;
    }

    /** Not working: a missing deposit or a depleted one drops the progress, missing power or a full output only pauses it. */
    private boolean stop(int newStatus, boolean resetProgress)
    {
        status = newStatus;
        if (resetProgress) progress = 0;
        return false;
    }

    private void refreshTarget(ServerLevel serverLevel)
    {
        if (targetDepositId == null && --searchCooldown <= 0)
        {
            searchCooldown = SEARCH_INTERVAL;
            targetDepositId = findDeposit(serverLevel);
            if (targetDepositId != null) setChanged();
        }
        if (targetDepositId == null)
        {
            targetDeposit = null;
            return;
        }
        targetDeposit = OreDepositManager.get(serverLevel, targetDepositId).orElse(null);
        if (targetDeposit != null && !ExcavatorMinerals.canMine(tier, targetDeposit.mineralId()) && --searchCooldown <= 0)
        {
            // too-high deposit targeted: switch if a minable one is in reach (findDeposit prefers those)
            searchCooldown = SEARCH_INTERVAL;
            UUID other = findDeposit(serverLevel);
            if (other != null && !other.equals(targetDepositId))
            {
                targetDepositId = other;
                targetDeposit = OreDepositManager.get(serverLevel, other).orElse(null);
                progress = 0;
                setChanged();
            }
        }
        if (targetDeposit == null)
        {
            // the deposit was removed from the world data: look for another one
            targetDepositId = null;
            setChanged();
        }
    }

    /** how far above / below the machine a deposit may be and still be mined, in blocks */
    private static final int VERTICAL_REACH = 24;

    /** deposits with ore left whose bounds come within VERTICAL_REACH of this machine's height */
    private List<OreDeposit> reachable(List<OreDeposit> nearby)
    {
        int y = worldPosition.getY();
        return nearby.stream()
                .filter(deposit -> deposit.remainingOre() > 0)
                .filter(deposit -> deposit.bounds().maxY >= y - VERTICAL_REACH && deposit.bounds().minY <= y + VERTICAL_REACH)
                .toList();
    }

    /** The nearest deposit with ore left among those OreDepositManager#findNearby reports. */
    @Nullable
    private UUID findDeposit(ServerLevel serverLevel)
    {
        // findNearby is horizontal only: also require the deposit to be within VERTICAL_REACH of the machine (the deep layer
        // is 300 blocks tall, a vein far below is not in reach)
        List<OreDeposit> found = reachable(OreDepositManager.findNearby(serverLevel, worldPosition, tier.workingRadius));
        if (found.isEmpty() && adoptVeins(serverLevel))
            found = reachable(OreDepositManager.findNearby(serverLevel, worldPosition, tier.workingRadius));
        // deposits this tier can mine first; a too-high one is still reported (STATUS_TIER_TOO_LOW)
        List<OreDeposit> minable = found.stream().filter(deposit -> ExcavatorMinerals.canMine(tier, deposit.mineralId())).toList();
        return (minable.isEmpty() ? found : minable).stream()
                .min(Comparator.comparingDouble((OreDeposit deposit) -> deposit.center().distSqr(worldPosition)))
                .map(OreDeposit::depositId)
                .orElse(null);
    }

    // ---------------------------------------------------------------- adoption of unregistered veins

    private static final int ADOPT_INTERVAL = 100;
    private static final int ADOPT_VERTICAL = 24;
    private static final int ADOPT_COMPONENT_CAP = 4096;
    /** blocks looked at per scan call (sections without deposit ores are skipped without counting) */
    private static final int ADOPT_WORK_CAP = 400_000;

    /**
     * Veins without a deposit record (worlds generated before the worldgen registration fix, or a missed vein): scans the
     * loaded sections around the machine for deposit-only ore, flood-fills each unregistered cluster and registers it.
     * At most once per ADOPT_INTERVAL ticks. Returns whether a deposit was registered.
     */
    private boolean adoptVeins(ServerLevel serverLevel)
    {
        long now = serverLevel.getGameTime();
        if (now - lastAdoptTick < ADOPT_INTERVAL) return false;
        lastAdoptTick = now;

        int r = tier.workingRadius;
        int minX = worldPosition.getX() - r, maxX = worldPosition.getX() + r;
        int minZ = worldPosition.getZ() - r, maxZ = worldPosition.getZ() + r;
        int minY = Math.max(serverLevel.getMinBuildHeight(), worldPosition.getY() - ADOPT_VERTICAL);
        int maxY = Math.min(serverLevel.getMaxBuildHeight() - 1, worldPosition.getY() + ADOPT_VERTICAL);
        List<OreDeposit> known = new java.util.ArrayList<>(OreDepositManager.getAll(serverLevel).values());
        java.util.Set<Long> visited = new java.util.HashSet<>();
        int work = 0;
        boolean adopted = false;

        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
        {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
            {
                net.minecraft.world.level.chunk.LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++)
                {
                    int index = chunk.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSectionsCount()) continue;
                    net.minecraft.world.level.chunk.LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(state -> ExcavatorMinerals.isDepositOnly(state.getBlock()))) continue;
                    int x0 = Math.max(minX, cx << 4), x1 = Math.min(maxX, (cx << 4) + 15);
                    int z0 = Math.max(minZ, cz << 4), z1 = Math.min(maxZ, (cz << 4) + 15);
                    int y0 = Math.max(minY, sy << 4), y1 = Math.min(maxY, (sy << 4) + 15);
                    for (int y = y0; y <= y1; y++)
                    {
                        for (int z = z0; z <= z1; z++)
                        {
                            for (int x = x0; x <= x1; x++)
                            {
                                if (++work > ADOPT_WORK_CAP) return adopted;
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (!ExcavatorMinerals.isDepositOnly(state.getBlock())) continue;
                                if (visited.contains(BlockPos.asLong(x, y, z))) continue;
                                ResourceLocation mineral = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                                if (inKnownDeposit(known, mineral, x, y, z)) continue;
                                OreDeposit made = adoptComponent(serverLevel, state.getBlock(), mineral, new BlockPos(x, y, z), visited);
                                if (made != null)
                                {
                                    known.add(made);
                                    adopted = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return adopted;
    }

    private static boolean inKnownDeposit(List<OreDeposit> known, ResourceLocation mineral, int x, int y, int z)
    {
        for (OreDeposit deposit : known)
            if (deposit.mineralId().equals(mineral) && deposit.bounds().contains(x + 0.5, y + 0.5, z + 0.5)) return true;
        return false;
    }

    /** Flood-fills the 26-neighbour component of {@code ore} around {@code start} (capped) and registers it as a deposit. */
    @Nullable
    private static OreDeposit adoptComponent(ServerLevel serverLevel, Block ore, ResourceLocation mineral, BlockPos start, java.util.Set<Long> visited)
    {
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        visited.add(start.asLong());
        int count = 0;
        int lx = start.getX(), ly = start.getY(), lz = start.getZ(), hx = lx, hy = ly, hz = lz;
        long sx = 0, sy = 0, sz = 0;
        while (!queue.isEmpty() && count < ADOPT_COMPONENT_CAP)
        {
            BlockPos p = queue.poll();
            count++;
            sx += p.getX(); sy += p.getY(); sz += p.getZ();
            lx = Math.min(lx, p.getX()); hx = Math.max(hx, p.getX());
            ly = Math.min(ly, p.getY()); hy = Math.max(hy, p.getY());
            lz = Math.min(lz, p.getZ()); hz = Math.max(hz, p.getZ());
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = p.offset(dx, dy, dz);
                        if (serverLevel.isOutsideBuildHeight(n) || !serverLevel.isLoaded(n)) continue;
                        if (!serverLevel.getBlockState(n).is(ore) || !visited.add(n.asLong())) continue;
                        queue.add(n);
                    }
        }
        if (count <= 0) return null;
        com.abyssia.worldgen.OreVeinFeature.Size size = count < 40 ? com.abyssia.worldgen.OreVeinFeature.Size.SMALL
                : count < 150 ? com.abyssia.worldgen.OreVeinFeature.Size.MEDIUM
                : count < 500 ? com.abyssia.worldgen.OreVeinFeature.Size.LARGE
                : com.abyssia.worldgen.OreVeinFeature.Size.HUGE;
        net.minecraft.world.phys.AABB bounds = new net.minecraft.world.phys.AABB(lx - 2, ly - 2, lz - 2, hx + 3, hy + 3, hz + 3);
        BlockPos center = new BlockPos((int) Math.round((double) sx / count), (int) Math.round((double) sy / count), (int) Math.round((double) sz / count));
        return OreDepositManager.register(serverLevel, mineral, center, bounds, size,
                com.abyssia.worldgen.OreVeinFeature.Shape.VEIN, count).orElse(null);
    }

    // ---------------------------------------------------------------- output

    /** The yield of a cycle: the mineral's raw item (ExcavatorMinerals#rawItem), else the deposit's mineral block as an item. */
    private static ItemStack mineralStack(ServerLevel serverLevel, OreDeposit deposit, int count)
    {
        var raw = ExcavatorMinerals.rawItem(deposit.mineralId()).flatMap(BuiltInRegistries.ITEM::getOptional).filter(item -> item != Items.AIR);
        if (raw.isPresent()) return new ItemStack(raw.get(), count);
        return deposit.mineralState(serverLevel.registryAccess())
                .map(state -> state.getBlock().asItem())
                .filter(item -> item != Items.AIR)
                .map(item -> new ItemStack(item, count))
                .orElse(ItemStack.EMPTY);
    }

    /** Mk2: by-product of cobalt / manganese / nickel; goes in the rare slot, dropped if it does not fit. */
    private void rollByproducts(ServerLevel serverLevel, OreDeposit deposit)
    {
        int slot = kind.outputSlot() + 1;
        for (ExcavatorMinerals.Byproduct byproduct : ExcavatorMinerals.byproducts(tier, deposit.mineralId()))
        {
            if (serverLevel.random.nextFloat() >= byproduct.chance()) continue;
            ItemStack stack = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("abyssia", byproduct.rawItem()))
                    .map(ItemStack::new).orElse(ItemStack.EMPTY);
            ItemStack existing = items.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (existing.isEmpty())
                items.setStackInSlot(slot, stack);
            else if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize())
                items.setStackInSlot(slot, existing.copyWithCount(existing.getCount() + 1));
            // else: no room, the by-product is lost (the main ore has priority)
        }
    }

    /** A depleted deposit: its ore blocks inside the bounds become mineral_host_rock, REPLACE_SCAN_PER_TICK positions a tick. */
    private void replaceDepletedOre(ServerLevel serverLevel, OreDeposit deposit)
    {
        Block ore = BuiltInRegistries.BLOCK.get(deposit.mineralId());
        var bounds = deposit.bounds();
        int minX = (int) Math.floor(bounds.minX), minY = (int) Math.floor(bounds.minY), minZ = (int) Math.floor(bounds.minZ);
        long sx = Math.max(1, (int) Math.ceil(bounds.maxX) - minX), sy = Math.max(1, (int) Math.ceil(bounds.maxY) - minY);
        long sz = Math.max(1, (int) Math.ceil(bounds.maxZ) - minZ);
        long total = sx * sy * sz;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState rock = ModBlocks.MINERAL_HOST_ROCK.get().defaultBlockState();
        for (int i = 0; i < REPLACE_SCAN_PER_TICK && sweepCursor < total; i++, sweepCursor++)
        {
            long c = sweepCursor;
            pos.set(minX + (int) (c % sx), minY + (int) (c / sx % sy), minZ + (int) (c / (sx * sy)));
            if (serverLevel.isInWorldBounds(pos) && serverLevel.hasChunkAt(pos) && serverLevel.getBlockState(pos).is(ore))
                serverLevel.setBlock(pos, rock, 2);
        }
        if (sweepCursor >= total) sweepDone = true;
    }

    private boolean canStore(ItemStack ore)
    {
        ItemStack existing = items.getStackInSlot(kind.outputSlot());
        if (existing.isEmpty()) return ore.getCount() <= ore.getMaxStackSize();
        return ItemStack.isSameItemSameComponents(existing, ore)
                && existing.getCount() + ore.getCount() <= existing.getMaxStackSize();
    }

    /** Only after {@link #canStore}; setStackInSlot notifies the inventory (setChanged, comparators). */
    private void store(ItemStack ore)
    {
        int slot = kind.outputSlot();
        ItemStack existing = items.getStackInSlot(slot);
        items.setStackInSlot(slot, existing.isEmpty() ? ore.copy() : existing.copyWithCount(existing.getCount() + ore.getCount()));
    }

    // ---------------------------------------------------------------- GUI data

    private void refreshDisplay()
    {
        OreDeposit deposit = targetDeposit;
        int remaining = deposit == null ? 0 : deposit.remainingOre();
        int total = deposit == null ? 0 : deposit.totalOre();
        aux[0] = deposit == null ? 0 : BuiltInRegistries.BLOCK.getOptional(deposit.mineralId())
                                       .map(BuiltInRegistries.BLOCK::getId).orElse(0);
        aux[1] = remaining & 0xFFFF;
        aux[2] = remaining >>> 16;
        aux[3] = total & 0xFFFF;
        aux[4] = total >>> 16;
    }

    // ---------------------------------------------------------------- save / load

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries); // Items, Energy, Progress
        if (targetDepositId != null) tag.putUUID("TargetDeposit", targetDepositId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        targetDepositId = tag.hasUUID("TargetDeposit") ? tag.getUUID("TargetDeposit") : null;
        targetDeposit = null;
    }
}