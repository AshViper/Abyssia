package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.furniture.HydroPlanterBlockEntity;
import com.abyssia.furniture.PlanterCrop;
import com.abyssia.industry.MachineKind;
import com.abyssia.industry.block.FacingDecorBlock;
import com.abyssia.industry.blockentity.ProcessingMachineBlockEntity;
import com.abyssia.industry.recipe.MachineRecipes;
import com.abyssia.industry.recipe.ProcessRecipe;
import com.abyssia.logistics.CargoNetwork;
import com.abyssia.logistics.CargoUplinkBlock;
import com.abyssia.logistics.CargoUplinkBlockEntity;
import com.abyssia.logistics.LogisticsContent;
import com.abyssia.logistics.LogisticsPumpBlockEntity;
import com.abyssia.registry.ModFurniture;
import com.abyssia.registry.ModIndustry;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Dev-only (LOG01): with -Dabyssia.log01test (gradle runLog01Test) the dedicated server builds the logistics cases in
 * forced chunks 112 blocks apart, runs them in parallel in three phases (default config, logistics_enabled=false,
 * cargo_buffer_stacks=1), logs one "LOG01 name PASS|FAIL measured" line each and halts. Vanilla barrels stand in for the
 * large locker. Everything placed is emptied and removed at the end of its case (run/ keeps its world).
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Log01Test
{
    private static final Logger LOG = LogUtils.getLogger();
    private static final int BASE_X = 6400, BASE_Z = 4, SPACING = 112, MARGIN = 10, FAR = 1008, Y0 = 70;

    /** one test case; positions are offsets from pos (the case origin) */
    private static final class Case
    {
        final String name;
        final int phase;
        int ticks;
        final Consumer<Case> setup;
        final Function<Case, Boolean> check; // true = PASS; the measured value is always logged
        BiConsumer<Case, Integer> tick = (c, t) -> {};
        final List<int[]> boxes = new ArrayList<>();
        final List<UUID> owners = new ArrayList<>();
        BlockPos pos;
        String measured = "";
        boolean done, farChunk;
        int stage, mark;
        Item in, out;
        int perCraft, perResult;
        Map<Item, Integer> expected = new HashMap<>();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        long a, b;

        Case(String name, int phase, int ticks, Consumer<Case> setup, Function<Case, Boolean> check)
        {
            this.name = name;
            this.phase = phase;
            this.ticks = ticks;
            this.setup = setup;
            this.check = check;
            owners.add(owner);
            owners.add(other);
        }

        Case box(int x1, int y1, int z1, int x2, int y2, int z2)
        {
            boxes.add(new int[] {x1, y1, z1, x2, y2, z2});
            return this;
        }

        Case ticking(BiConsumer<Case, Integer> tick)
        {
            this.tick = tick;
            return this;
        }

        BlockPos at(int x, int y, int z)
        {
            return pos.offset(x, y, z);
        }

        BlockPos far(int x, int y, int z)
        {
            return pos.offset(x, y, z + FAR);
        }

        List<ChunkPos> chunks()
        {
            List<ChunkPos> list = new ArrayList<>();
            list.add(new ChunkPos(pos));
            if (farChunk) list.add(new ChunkPos(far(0, 0, 0)));
            return list;
        }
    }

    private static final List<Case> ALL = new ArrayList<>();
    private static ServerLevel level;
    private static int phase, t, serverTicks, passes, fails, next, waited;
    private static boolean started, finished, running, prepared;
    private static List<Case> active = List.of();

    // ---------------------------------------------------------------- helpers

    private static BlockState pump(Direction facing)
    {
        return LogisticsContent.LOGISTICS_PUMP.get().defaultBlockState().setValue(FacingDecorBlock.FACING, facing);
    }

    private static BlockState pipe()
    {
        return ModIndustry.INDUSTRIAL_PIPE.get().defaultBlockState();
    }

    private static BlockState barrel()
    {
        return Blocks.BARREL.defaultBlockState();
    }

    private static void set(BlockPos p, BlockState state)
    {
        level.setBlock(p, state, 3);
    }

    private static void fill(BlockPos p, Item item, int n)
    {
        Container c = (Container) level.getBlockEntity(p);
        int slot = 0;
        while (n > 0)
        {
            int k = Math.min(n, new ItemStack(item).getMaxStackSize());
            c.setItem(slot++, new ItemStack(item, k));
            n -= k;
        }
    }

    private static int count(BlockPos p, Item item)
    {
        return level.getBlockEntity(p) instanceof Container c ? c.countItem(item) : -1;
    }

    private static int total(BlockPos p)
    {
        int n = 0;
        if (level.getBlockEntity(p) instanceof Container c)
            for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
        return n;
    }

    private static LogisticsPumpBlockEntity pumpAt(BlockPos p)
    {
        return (LogisticsPumpBlockEntity) level.getBlockEntity(p);
    }

    private static CargoUplinkBlockEntity uplinkAt(BlockPos p)
    {
        return (CargoUplinkBlockEntity) level.getBlockEntity(p);
    }

    private static void uplink(BlockPos p, boolean receiver, UUID owner)
    {
        set(p, LogisticsContent.CARGO_UPLINK.get().defaultBlockState().setValue(CargoUplinkBlock.RECEIVER, receiver));
        uplinkAt(p).setOwner(owner);
    }

    private static int slots(CargoUplinkBlockEntity be)
    {
        int n = 0;
        for (int i = 0; i < be.getContainerSize(); i++) n += be.getItem(i).getCount();
        return n;
    }

    private static CargoNetwork network()
    {
        return CargoNetwork.get(level.getServer());
    }

    /** empties and removes everything in the box (block entities go with their blocks) */
    private static void clear(BlockPos a, BlockPos b)
    {
        for (BlockPos p : BlockPos.betweenClosed(a, b))
        {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof HydroPlanterBlockEntity planter)
                for (int i = 0; i < HydroPlanterBlockEntity.CELLS; i++) planter.removeSeedling(i);
            else if (be instanceof Clearable clearable) clearable.clearContent();
            if (!level.getBlockState(p).isAir()) level.removeBlock(p.immutable(), false);
        }
    }

    private static void clearBoxes(Case c)
    {
        for (int[] b : c.boxes) clear(c.at(b[0], b[1], b[2]), c.at(b[3], b[4], b[5]));
    }

    /** posts the place event MachineGate listens to, for the block already standing at pos */
    private static boolean placeCancelled(BlockPos pos, ServerPlayer player)
    {
        var event = new net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent(
                net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(), level, pos),
                level.getBlockState(pos.below()), player);
        return net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
    }

    private static Case add(Case c)
    {
        ALL.add(c);
        return c;
    }

    // ---------------------------------------------------------------- cases

    private static void define()
    {
        // 1 locker stand-in -> pump -> 20 pipes -> container: 40 operations of 8 in 400 ticks
        add(new Case("pump_400_ticks", 0, 400 + MARGIN, c ->
        {
            set(c.at(0, 0, 0), barrel());
            fill(c.at(0, 0, 0), Items.COBBLESTONE, 640);
            set(c.at(0, 1, 0), pump(Direction.UP));
            for (int i = 0; i < 20; i++) set(c.at(0, 2 + i, 0), pipe());
            set(c.at(0, 22, 0), barrel());
        }, c ->
        {
            int moved = count(c.at(0, 22, 0), Items.COBBLESTONE), left = count(c.at(0, 0, 0), Items.COBBLESTONE);
            LogisticsPumpBlockEntity be = pumpAt(c.at(0, 1, 0));
            c.measured = "moved=" + moved + " src=" + left + " sum=" + (moved + left) + " pipes=" + be.pipeCount() + " dests=" + be.destCount();
            return moved >= 312 && moved <= 328 && moved + left == 640 && be.pipeCount() == 20 && be.destCount() == 1;
        }).box(-1, 0, -1, 1, 23, 1));

        // 2 crusher near, container far: only the crusher's recipe input goes into it, the rest goes on
        add(new Case("crusher_filter", 0, 400 + MARGIN, c ->
        {
            ProcessRecipe r = MachineRecipes.get(level).forKind(MachineKind.CRUSHER).get(0);
            c.in = r.inputs().get(0).getItems()[0].getItem();
            set(c.at(0, 0, 0), barrel());
            fill(c.at(0, 0, 0), c.in, 40);
            Container src = (Container) level.getBlockEntity(c.at(0, 0, 0));
            int slot = 0;
            while (!src.getItem(slot).isEmpty()) slot++;
            int stack = new ItemStack(c.in).getMaxStackSize();
            // 80 of the input in all, then 40 sticks (the crusher takes no sticks)
            int more = 40;
            while (more > 0)
            {
                int k = Math.min(more, stack);
                src.setItem(slot++, new ItemStack(c.in, k));
                more -= k;
            }
            for (int i = 0; i < 40; i += 20) src.setItem(slot++, new ItemStack(Items.STICK, 20));
            set(c.at(1, 0, 0), pump(Direction.EAST));
            for (int i = 0; i < 5; i++) set(c.at(2 + i, 0, 0), pipe());
            set(c.at(2, 0, 1), ModIndustry.CRUSHER.get().defaultBlockState());
            set(c.at(7, 0, 0), barrel());
        }, c ->
        {
            ProcessingMachineBlockEntity m = (ProcessingMachineBlockEntity) level.getBlockEntity(c.at(2, 0, 1));
            int machIn = 0;
            boolean onlyInput = true;
            for (int i = 0; i < m.items().getSlots(); i++)
            {
                ItemStack s = m.items().getStackInSlot(i);
                if (s.isEmpty()) continue;
                if (m.items().isInput(i)) machIn += s.getCount();
                onlyInput &= s.is(c.in);
            }
            int destIn = count(c.at(7, 0, 0), c.in), sticks = count(c.at(7, 0, 0), Items.STICK);
            int srcLeft = total(c.at(0, 0, 0));
            c.measured = "item=" + BuiltInRegistries.ITEM.getKey(c.in) + " crusher=" + machIn + " far=" + destIn + " sticks=" + sticks
                    + " srcLeft=" + srcLeft + " onlyInput=" + onlyInput + " acceptsStick=" + MachineKind.CRUSHER.acceptsInput(level, new ItemStack(Items.STICK));
            return onlyInput && machIn > 0 && machIn + destIn == 80 && sticks == 40 && srcLeft == 0
                    && !MachineKind.CRUSHER.acceptsInput(level, new ItemStack(Items.STICK));
        }).box(-1, 0, -1, 8, 1, 2));

        // 3 container -> pump -> refinery furnace (fed FE every tick) -> pump -> container
        add(new Case("refinery_chain", 0, 700, c ->
        {
            ProcessRecipe r = MachineRecipes.get(level).forKind(MachineKind.REFINERY_FURNACE).stream()
                    .filter(x -> x.inputs().size() == 1).findFirst().orElseThrow();
            c.in = r.inputs().get(0).getItems()[0].getItem();
            c.out = r.result().getItem();
            c.perCraft = r.count();
            c.perResult = r.result().getCount();
            set(c.at(0, 0, 0), barrel());
            fill(c.at(0, 0, 0), c.in, 64);
            set(c.at(1, 0, 0), pump(Direction.EAST));
            set(c.at(2, 0, 0), pipe());
            set(c.at(2, 0, 1), ModIndustry.REFINERY_FURNACE.get().defaultBlockState());
            set(c.at(2, 0, 2), pump(Direction.SOUTH));
            set(c.at(2, 0, 3), pipe());
            set(c.at(2, 0, 4), barrel());
        }, c ->
        {
            ProcessingMachineBlockEntity m = (ProcessingMachineBlockEntity) level.getBlockEntity(c.at(2, 0, 1));
            int machIn = 0, machOut = 0;
            for (int i = 0; i < m.items().getSlots(); i++)
            {
                ItemStack s = m.items().getStackInSlot(i);
                if (m.items().isInput(i) && s.is(c.in)) machIn += s.getCount();
                if (m.items().isOutput(i) && s.is(c.out)) machOut += s.getCount();
            }
            int srcLeft = count(c.at(0, 0, 0), c.in), destOut = count(c.at(2, 0, 4), c.out);
            int outTotal = machOut + destOut, consumed = 64 - srcLeft - machIn;
            int crafts = consumed / c.perCraft;
            c.measured = "in=" + BuiltInRegistries.ITEM.getKey(c.in) + "x" + c.perCraft + " out=" + BuiltInRegistries.ITEM.getKey(c.out) + "x" + c.perResult
                    + " crafts=" + crafts + " outTotal=" + outTotal + " (dest " + destOut + ", machine " + machOut + ") srcLeft=" + srcLeft + " machIn=" + machIn;
            return crafts >= 1 && consumed % c.perCraft == 0 && outTotal == crafts * c.perResult;
        }).ticking((c, t) ->
        {
            if (level.getBlockEntity(c.at(2, 0, 1)) instanceof ProcessingMachineBlockEntity m) m.getCapability(ForgeCapabilities.ENERGY, null).ifPresent(e -> e.receiveEnergy(256, false));
        }).box(-1, 0, -1, 4, 1, 5));

        // 4 ripe hydro planter (4 different crops): every product ends up in the container, the cells restart
        add(new Case("planter_harvest", 0, 150, c ->
        {
            set(c.at(0, 0, 0), ModFurniture.HYDRO_PLANTER.get().defaultBlockState());
            HydroPlanterBlockEntity be = (HydroPlanterBlockEntity) level.getBlockEntity(c.at(0, 0, 0));
            PlanterCrop[] crops = {PlanterCrop.MUSHROOM, PlanterCrop.GOURD, PlanterCrop.KELP, PlanterCrop.AMBER_FAN};
            for (int i = 0; i < 4; i++) be.plant(i, crops[i].seedStack());
            for (int i = 0; i < 4; i++) for (int g = 0; g < 40 && !be.isRipe(i); g++) be.advance(i);
            IItemHandler h = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(IllegalStateException::new);
            for (int i = 0; i < 4; i++)
            {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty()) c.expected.merge(s.getItem(), s.getCount(), Integer::sum);
            }
            set(c.at(1, 0, 0), pump(Direction.EAST));
            set(c.at(2, 0, 0), pipe());
            set(c.at(3, 0, 0), pipe());
            set(c.at(4, 0, 0), barrel());
        }, c ->
        {
            HydroPlanterBlockEntity be = (HydroPlanterBlockEntity) level.getBlockEntity(c.at(0, 0, 0));
            boolean same = !c.expected.isEmpty();
            StringBuilder sb = new StringBuilder();
            int want = 0;
            for (Map.Entry<Item, Integer> e : c.expected.entrySet())
            {
                int got = count(c.at(4, 0, 0), e.getKey());
                same &= got == e.getValue();
                want += e.getValue();
                sb.append(BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).append(' ').append(got).append('/').append(e.getValue()).append(' ');
            }
            boolean restarted = true, kept = true;
            for (int i = 0; i < 4; i++)
            {
                restarted &= !be.isRipe(i);
                kept &= be.crop(i) != PlanterCrop.NONE;
            }
            c.measured = "harvested=" + total(c.at(4, 0, 0)) + "/" + want + " [" + sb.toString().trim() + "] restarted=" + restarted + " seedlingsKept=" + kept;
            return same && total(c.at(4, 0, 0)) == want && restarted && kept;
        }).box(-1, 0, -1, 5, 1, 1));

        // 5 limits: the 129th pipe is not followed, a gap in the pipes stops the network
        add(new Case("pipe_limit_129", 0, 60, c ->
        {
            set(c.at(0, 0, 0), barrel());
            fill(c.at(0, 0, 0), Items.COBBLESTONE, 64);
            set(c.at(0, 1, 0), pump(Direction.UP));
            for (int i = 0; i < 129; i++) set(c.at(0, 2 + i, 0), pipe());
            set(c.at(1, 130, 0), barrel());
        }, c ->
        {
            LogisticsPumpBlockEntity be = pumpAt(c.at(0, 1, 0));
            int moved = total(c.at(1, 130, 0));
            c.measured = "pipes=" + be.pipeCount() + " dests=" + be.destCount() + " moved=" + moved;
            return be.pipeCount() == 128 && be.destCount() == 0 && moved == 0;
        }).box(-1, 0, -1, 2, 132, 1));
        add(new Case("pipe_limit_128_control", 0, 60, c ->
        {
            set(c.at(0, 0, 0), barrel());
            fill(c.at(0, 0, 0), Items.COBBLESTONE, 64);
            set(c.at(0, 1, 0), pump(Direction.UP));
            for (int i = 0; i < 128; i++) set(c.at(0, 2 + i, 0), pipe());
            set(c.at(1, 129, 0), barrel());
        }, c ->
        {
            LogisticsPumpBlockEntity be = pumpAt(c.at(0, 1, 0));
            int moved = total(c.at(1, 129, 0));
            c.measured = "pipes=" + be.pipeCount() + " dests=" + be.destCount() + " moved=" + moved;
            return be.pipeCount() == 128 && be.destCount() == 1 && moved > 0;
        }).box(-1, 0, -1, 2, 132, 1));
        add(new Case("no_pump_link", 0, 60, c ->
        {
            set(c.at(0, 0, 0), barrel());
            fill(c.at(0, 0, 0), Items.COBBLESTONE, 64);
            set(c.at(0, 1, 0), pump(Direction.UP));
            // one block of air between the pump and the pipes: a network without a pump
            for (int i = 0; i < 4; i++) set(c.at(0, 3 + i, 0), pipe());
            set(c.at(0, 7, 0), barrel());
        }, c ->
        {
            LogisticsPumpBlockEntity be = pumpAt(c.at(0, 1, 0));
            int moved = total(c.at(0, 7, 0)), left = count(c.at(0, 0, 0), Items.COBBLESTONE);
            c.measured = "pipes=" + be.pipeCount() + " dests=" + be.destCount() + " moved=" + moved + " src=" + left;
            return be.pipeCount() == 0 && be.destCount() == 0 && moved == 0 && left == 64;
        }).box(-1, 0, -1, 1, 8, 1));

        // 6 cargo uplinks 1,008 blocks apart
        Case a = new Case("uplink_128_items", 0, 210, c ->
        {
            uplink(c.at(0, 0, 0), false, c.owner);
            uplink(c.far(0, 0, 0), true, c.owner);
            CargoUplinkBlockEntity s = uplinkAt(c.at(0, 0, 0));
            s.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
            s.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
            s.energy().generate(10_000);
        }, c ->
        {
            CargoUplinkBlockEntity s = uplinkAt(c.at(0, 0, 0)), r = uplinkAt(c.far(0, 0, 0));
            int recv = slots(r), buf = network().total(c.owner), consumed = 10_000 - s.energy().getEnergyStored();
            c.measured = "receiver=" + recv + " buffer=" + buf + " sender=" + slots(s) + " fe=" + consumed;
            return recv + buf == 128 && recv == 128 && slots(s) == 0 && consumed == 1024;
        }).box(0, 0, 0, 0, 0, 0);
        a.farChunk = true;
        add(a);
        a.boxes.add(new int[] {0, 0, FAR, 0, 0, FAR});

        Case noFe = new Case("uplink_no_fe", 0, 100, c ->
        {
            uplink(c.at(0, 0, 0), false, c.owner);
            uplink(c.far(0, 0, 0), true, c.owner);
            uplinkAt(c.at(0, 0, 0)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        }, c ->
        {
            CargoUplinkBlockEntity s = uplinkAt(c.at(0, 0, 0)), r = uplinkAt(c.far(0, 0, 0));
            c.measured = "sender=" + slots(s) + " buffer=" + network().total(c.owner) + " receiver=" + slots(r);
            return slots(s) == 64 && network().total(c.owner) == 0 && slots(r) == 0;
        });
        noFe.farChunk = true;
        noFe.boxes.add(new int[] {0, 0, 0, 0, 0, 0});
        noFe.boxes.add(new int[] {0, 0, FAR, 0, 0, FAR});
        add(noFe);

        Case wrongOwner = new Case("uplink_other_owner", 0, 150, c ->
        {
            uplink(c.at(0, 0, 0), false, c.owner);
            uplink(c.far(0, 0, 0), true, c.other);
            CargoUplinkBlockEntity s = uplinkAt(c.at(0, 0, 0));
            s.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
            s.energy().generate(10_000);
        }, c ->
        {
            CargoUplinkBlockEntity s = uplinkAt(c.at(0, 0, 0)), r = uplinkAt(c.far(0, 0, 0));
            c.measured = "receiver=" + slots(r) + " bufferA=" + network().total(c.owner) + " bufferB=" + network().total(c.other) + " sender=" + slots(s);
            return slots(r) == 0 && network().total(c.owner) == 64 && network().total(c.other) == 0 && slots(s) == 0;
        });
        wrongOwner.farChunk = true;
        wrongOwner.boxes.add(new int[] {0, 0, 0, 0, 0, 0});
        wrongOwner.boxes.add(new int[] {0, 0, FAR, 0, 0, FAR});
        add(wrongOwner);

        // receiver chunk unloaded while the sender works: the items wait in the buffer and arrive after the reload
        Case unload = new Case("uplink_receiver_unloaded", 0, 6000, c ->
        {
            uplink(c.at(0, 0, 0), false, c.owner);
            uplink(c.far(0, 0, 0), true, c.owner);
            ChunkPos rc = new ChunkPos(c.far(0, 0, 0));
            level.setChunkForced(rc.x, rc.z, false);
        }, c ->
        {
            CargoUplinkBlockEntity r = uplinkAt(c.far(0, 0, 0));
            int recv = slots(r), buf = network().total(c.owner);
            c.measured = "bufferWhileUnloaded=" + c.a + " loadedThen=" + (c.b == 1) + " receiverAfter=" + recv + " bufferAfter=" + buf;
            return c.a == 128 && c.b == 0 && recv == 128 && buf == 0;
        });
        unload.farChunk = true;
        unload.boxes.add(new int[] {0, 0, 0, 0, 0, 0});
        unload.boxes.add(new int[] {0, 0, FAR, 0, 0, FAR});
        unload.ticking((c, t) ->
        {
            ChunkPos rc = new ChunkPos(c.far(0, 0, 0));
            switch (c.stage)
            {
                case 0 -> // wait until the receiver's chunk is gone, then load the sender
                {
                    if (level.getChunkSource().getChunkNow(rc.x, rc.z) == null)
                    {
                        CargoUplinkBlockEntity s = uplinkAt(c.at(0, 0, 0));
                        s.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                        s.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
                        s.energy().generate(10_000);
                        c.mark = t;
                        c.stage = 1;
                    }
                    else if (t > 1500)
                    {
                        c.measured = "receiver chunk did not unload";
                        c.stage = 9;
                        c.ticks = t;
                    }
                }
                case 1 ->
                {
                    if (t >= c.mark + 200)
                    {
                        c.a = network().total(c.owner);
                        c.b = level.getChunkSource().getChunkNow(rc.x, rc.z) != null ? 1 : 0;
                        level.setChunkForced(rc.x, rc.z, true);
                        c.mark = t;
                        c.stage = 2;
                    }
                }
                case 2 ->
                {
                    if (level.getChunkSource().getChunkNow(rc.x, rc.z) != null && ready(rc, level.getChunkAt(c.far(0, 0, 0))))
                    {
                        c.mark = t;
                        c.stage = 3;
                    }
                }
                case 3 ->
                {
                    if (t >= c.mark + 100) c.ticks = t;
                }
                default -> {}
            }
        });
        add(unload);

        // 6f buffer data survives save -> load
        add(new Case("cargo_saved_data", 0, 1, c -> {}, c ->
        {
            CargoNetwork n = new CargoNetwork();
            UUID u1 = UUID.randomUUID(), u2 = UUID.randomUUID();
            ItemStack named = new ItemStack(Items.DIAMOND_SWORD, 1);
            named.setHoverName(net.minecraft.network.chat.Component.literal("log01"));
            n.insert(u1, new ItemStack(Items.COBBLESTONE, 150));
            n.insert(u1, named);
            n.insert(u2, new ItemStack(Items.STICK, 7));
            CompoundTag tag = n.save(new CompoundTag());
            CargoNetwork back = CargoNetwork.load(tag);
            boolean same = back.total(u1) == n.total(u1) && back.total(u2) == n.total(u2) && back.stacks(u1) == n.stacks(u1);
            List<ItemStack> x = n.contents(u1), y = back.contents(u1);
            for (int i = 0; i < x.size() && same; i++) same &= ItemStack.matches(x.get(i), y.get(i));
            c.measured = "u1=" + back.total(u1) + "/" + n.total(u1) + " stacks=" + back.stacks(u1) + "/" + n.stacks(u1) + " u2=" + back.total(u2) + "/" + n.total(u2);
            return same && back.total(u1) == 151 && back.total(u2) == 7;
        }));

        // 7 time of one pump operation on a 128-pipe network with 8 destinations
        add(new Case("pump_operation_time", 0, 1, c ->
        {
            set(c.at(0, 0, 0), barrel());
            fill(c.at(0, 0, 0), Items.COBBLESTONE, 27 * 64);
            set(c.at(0, 1, 0), pump(Direction.UP));
            for (int i = 0; i < 128; i++) set(c.at(0, 2 + i, 0), pipe());
            for (int i = 0; i < 8; i++) set(c.at(1, 2 + 16 * i, 0), barrel());
        }, c ->
        {
            LogisticsPumpBlockEntity be = pumpAt(c.at(0, 1, 0));
            for (int i = 0; i < 20; i++) be.rescan(level); // scan warm-up
            double scanMax = 0, scanSum = 0;
            for (int i = 0; i < 50; i++)
            {
                long s0 = System.nanoTime();
                be.rescan(level);
                double ms = (System.nanoTime() - s0) / 1e6;
                scanSum += ms;
                scanMax = Math.max(scanMax, ms);
            }
            double scanMs = scanSum / 50;
            for (int i = 0; i < 20; i++) be.operate(level); // warm-up
            int moved = 0;
            long t0 = System.nanoTime();
            for (int i = 0; i < 200; i++) moved += be.operate(level);
            double avg = (System.nanoTime() - t0) / 1e6 / 200;
            c.measured = String.format("avg=%.4f ms/op over 200 (scan avg %.3f ms, max %.3f ms over 50) pipes=%d dests=%d moved=%d", avg, scanMs, scanMax, be.pipeCount(), be.destCount(), moved);
            return avg < 0.05 && scanMs < 1.0 && be.pipeCount() == 128 && be.destCount() == 8
                    && moved == 27 * 64 - 20 * 8; // the barrel holds 1728; the 20 warm-up operations took 160, the rest is all moved
        }).box(-1, 0, -1, 2, 131, 1));

        // 8 research lock
        add(new Case("research_gate", 0, 1, c -> {}, c ->
        {
            ServerPlayer p = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("log01".getBytes()), "log01"));
            String[][] pairs = {{"technology/logistics", "pump"}, {"technology/cargo_logistics", "uplink"}};
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            for (String[] pair : pairs)
            {
                ResourceLocation tech = new ResourceLocation(Abyssia.MODID, pair[0]);
                Block block = pair[1].equals("pump") ? LogisticsContent.LOGISTICS_PUMP.get() : LogisticsContent.CARGO_UPLINK.get();
                com.abyssia.research.ResearchManager.reset(p, null);
                level.setBlock(c.pos, block.defaultBlockState(), 3);
                String lockedBy = com.abyssia.research.gate.MachineGate.lockedBy(p, block).map(x -> x.id().toString()).orElse("none");
                boolean before = placeCancelled(c.pos, p);
                boolean unlocked = com.abyssia.research.ResearchManager.unlock(p, tech);
                boolean free = com.abyssia.research.gate.MachineGate.lockedBy(p, block).isEmpty();
                boolean after = placeCancelled(c.pos, p);
                com.abyssia.research.ResearchManager.reset(p, null);
                level.removeBlock(c.pos, false);
                ok &= lockedBy.equals(tech.toString()) && before && unlocked && free && !after;
                sb.append(pair[1]).append(": lockedBy=").append(lockedBy).append(" cancelled=").append(before).append(" unlock=").append(unlocked)
                        .append(" freeAfter=").append(free).append(" cancelledAfter=").append(after).append("; ");
            }
            c.measured = sb.toString().trim();
            return ok;
        }).box(0, 0, 0, 0, 0, 0));

        // phase 1: logistics_enabled=false
        add(new Case("disabled_pump", 1, 100, c ->
        {
            set(c.at(0, 0, 0), barrel());
            fill(c.at(0, 0, 0), Items.COBBLESTONE, 64);
            set(c.at(0, 1, 0), pump(Direction.UP));
            for (int i = 0; i < 3; i++) set(c.at(0, 2 + i, 0), pipe());
            set(c.at(0, 5, 0), barrel());
        }, c ->
        {
            LogisticsPumpBlockEntity be = pumpAt(c.at(0, 1, 0));
            int moved = total(c.at(0, 5, 0));
            c.measured = "dests=" + be.destCount() + " pipes=" + be.pipeCount() + " moved=" + moved + " enabled=" + Config.LOGISTICS_ENABLED.get();
            return be.destCount() == 0 && moved == 0 && !Config.LOGISTICS_ENABLED.get();
        }).box(-1, 0, -1, 1, 6, 1));
        add(new Case("disabled_uplink", 1, 100, c ->
        {
            uplink(c.at(0, 0, 0), false, c.owner);
            CargoUplinkBlockEntity s = uplinkAt(c.at(0, 0, 0));
            s.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
            s.energy().generate(10_000);
        }, c ->
        {
            CargoUplinkBlockEntity s = uplinkAt(c.at(0, 0, 0));
            c.measured = "sender=" + slots(s) + " buffer=" + network().total(c.owner) + " fe=" + s.energy().getEnergyStored();
            return slots(s) == 64 && network().total(c.owner) == 0 && s.energy().getEnergyStored() == 10_000;
        }).box(0, 0, 0, 0, 0, 0));

        // phase 2: cargo_buffer_stacks=1: a full buffer leaves the rest in the sender, and only what moved costs FE
        add(new Case("uplink_buffer_full", 2, 150, c ->
        {
            uplink(c.at(0, 0, 0), false, c.owner);
            CargoUplinkBlockEntity s = uplinkAt(c.at(0, 0, 0));
            s.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
            s.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
            s.energy().generate(10_000);
        }, c ->
        {
            CargoUplinkBlockEntity s = uplinkAt(c.at(0, 0, 0));
            int consumed = 10_000 - s.energy().getEnergyStored();
            c.measured = "sender=" + slots(s) + " buffer=" + network().total(c.owner) + " stacks=" + network().stacks(c.owner) + " fe=" + consumed;
            return slots(s) == 64 && network().total(c.owner) == 64 && network().stacks(c.owner) == 1 && consumed == 512;
        }).box(0, 0, 0, 0, 0, 0));
    }

    // ---------------------------------------------------------------- run

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || System.getProperty("abyssia.log01test") == null || finished) return;
        MinecraftServer server = event.getServer();
        if (++serverTicks < 100) return;
        if (!started)
        {
            started = true;
            define();
            level = server.overworld();
            LOG.info("LOG01 start");
        }
        if (!running)
        {
            if (!beginPhase(phase)) return;
            running = true;
        }
        t++;
        boolean allDone = true;
        for (Case c : active)
        {
            if (!c.done)
            {
                try
                {
                    c.tick.accept(c, t);
                }
                catch (Exception e)
                {
                    c.measured = "exception " + e;
                    c.ticks = t;
                }
                if (t >= c.ticks) finish(c);
            }
            allDone &= c.done;
        }
        if (allDone)
        {
            running = false;
            prepared = false;
            phase++;
        }
    }

    private static void finish(Case c)
    {
        c.done = true;
        boolean ok;
        try
        {
            ok = c.measured.startsWith("exception") || c.measured.startsWith("receiver chunk") ? false : c.check.apply(c);
        }
        catch (Exception e)
        {
            c.measured = "exception " + e;
            ok = false;
        }
        if (ok) passes++;
        else fails++;
        LOG.info("LOG01 {} {} {}", c.name, ok ? "PASS" : "FAIL", c.measured);
        cleanup(c);
    }

    private static void cleanup(Case c)
    {
        try
        {
            for (ChunkPos cp : c.chunks()) level.setChunkForced(cp.x, cp.z, true);
            clearBoxes(c);
        }
        catch (Exception e)
        {
            LOG.warn("LOG01 cleanup {} failed: {}", c.name, e.toString());
        }
        for (UUID u : c.owners) network().remove(u);
        for (ChunkPos cp : c.chunks()) level.setChunkForced(cp.x, cp.z, false);
    }

    /** the same test LevelChunk#isTicking makes before it runs a block entity ticker */
    private static boolean ready(ChunkPos cp, net.minecraft.world.level.chunk.LevelChunk chunk)
    {
        long l = cp.toLong();
        return chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING) && level.areEntitiesLoaded(l) && level.shouldTickBlocksAt(l);
    }

    /** returns false while the chunks are still loading */
    private static boolean beginPhase(int index)
    {
        if (index >= 3)
        {
            finished = true;
            Config.LOGISTICS_ENABLED.set(true);
            Config.CARGO_BUFFER_STACKS.set(27);
            for (Case c : ALL) if (c.pos != null) cleanup(c);
            LOG.info("LOG01 DONE pass={} fail={}", passes, fails);
            level.getServer().halt(false);
            return false;
        }
        if (!prepared)
        {
            prepared = true;
            active = new ArrayList<>();
            for (Case c : ALL) if (c.phase == index) active.add(c);
            for (Case c : active)
            {
                c.pos = new BlockPos(BASE_X + SPACING * next++, Y0, BASE_Z);
                for (ChunkPos cp : c.chunks()) level.setChunkForced(cp.x, cp.z, true);
            }
        }
        for (Case c : active)
            for (ChunkPos cp : c.chunks())
                if (level.getChunkSource().getChunkNow(cp.x, cp.z) == null) return false;
        // a loaded chunk does not tick its block entities until its neighbours are in
        for (Case c : active)
            for (ChunkPos cp : c.chunks())
                if (!ready(cp, level.getChunk(cp.x, cp.z)))
                {
                    waited++;
                    return false;
                }
        LOG.info("LOG01 phase {} waited {} ticks for block ticking", index, waited);
        waited = 0;
        Config.LOGISTICS_ENABLED.set(index != 1);
        Config.CARGO_BUFFER_STACKS.set(index == 2 ? 1 : 27);
        for (Case c : active)
        {
            try
            {
                clearBoxes(c); // run/ keeps its world: leftovers of an earlier run would keep their items
                c.setup.accept(c);
            }
            catch (Exception e)
            {
                c.measured = "exception in setup " + e;
                c.ticks = 0;
                LOG.warn("LOG01 setup {} failed", c.name, e);
            }
        }
        t = 0;
        return true;
    }
}
