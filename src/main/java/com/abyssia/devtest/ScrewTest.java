package com.abyssia.devtest;

import com.abyssia.Abyssia;
import com.abyssia.item.electric.ElectricTools;
import com.abyssia.item.electric.PropulsionScrewItem;
import com.abyssia.item.electric.PropulsionScrewServer;
import com.abyssia.item.electric.ScrewLightPacket;
import com.abyssia.item.electric.ScrewThrustStatePacket;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Dev-only (propulsion screw rework): with -Dabyssia.screwtest the dedicated server fills a water cube, drives a FakePlayer
 * holding a charged screw through PropulsionScrewServer (posting the player tick event by hand) and logs one
 * "SCREW name PASS|FAIL measured" line per check, then halts. The client-side thrust is the formula
 * PropulsionScrewItem.applyThrust (client only in the game), so velocity is measured by calling that next to the server tick.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ScrewTest
{
    private static final Logger LOG = LogUtils.getLogger();
    private static final int N = 20;
    private static int ticks, passes, fails;
    private static boolean done;
    private static ServerLevel level;

    private static void report(String name, boolean ok, String measured)
    {
        if (ok) passes++;
        else fails++;
        LOG.info("SCREW {} {} {}", name, ok ? "PASS" : "FAIL", measured);
    }

    private static void guard(String name, Runnable r)
    {
        try { r.run(); }
        catch (Throwable t)
        {
            report(name, false, "exception " + t);
            LOG.error("SCREW " + name + " exception", t);
        }
    }

    /** one server tick of the player: fluid state refresh (a FakePlayer is not ticked) then the Forge player tick event */
    private static void serverTick(ServerPlayer p)
    {
        p.setAirSupply(300);
        p.baseTick();
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
    }

    private static ServerPlayer place(ServerPlayer p, double y)
    {
        p.setPos(7004.5, y, 4.5);
        p.setDeltaMovement(Vec3.ZERO);
        p.setYRot(0.0F);
        p.setXRot(-30.0F);   // looking up a little: velocity must follow the look vector
        return p;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || System.getProperty("abyssia.screwtest") == null || done) return;
        MinecraftServer server = event.getServer();
        if (++ticks < 100) return;
        done = true;
        level = server.overworld();
        LOG.info("SCREW start");
        guard("all", ScrewTest::run);
        LOG.info("SCREW DONE pass={} fail={}", passes, fails);
        server.halt(false);
    }

    private static void run()
    {
        level.setChunkForced(7004 >> 4, 4 >> 4, true);
        level.getChunk(7004 >> 4, 4 >> 4);
        BlockPos c = new BlockPos(7004, 100, 4);
        for (int dx = -6; dx <= 6; dx++)
            for (int dy = -6; dy <= 25; dy++)
                for (int dz = -6; dz <= 6; dz++)
                    level.setBlock(c.offset(dx, dy, dz), dy <= 14 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        ServerPlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("screwtest".getBytes()), "screwtest"));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack stack = ElectricTools.fullStack(ElectricTools.PROPULSION_SCREW.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);

        // 1: in water, charged, thrust state on -> FE drops, velocity builds along the look vector
        place(p, 105.0);
        serverTick(p);   // settle the eye-in-water flag
        int fe0 = ElectricTools.getEnergy(stack);
        int bc0 = PropulsionScrewServer.broadcasts;
        PropulsionScrewServer.set(p, true);
        double speed = 0, along = 0;
        for (int i = 0; i < N; i++)
        {
            serverTick(p);
            PropulsionScrewItem.applyThrust(p);   // what the client does each tick while left-click is held
            Vec3 v = p.getDeltaMovement();
            speed = v.length();
            along = v.dot(p.getLookAngle());
            p.setDeltaMovement(v.scale(0.8));      // vanilla water drag
        }
        int fe1 = ElectricTools.getEnergy(stack);
        report("1_thrust_in_water", fe0 - fe1 == N * PropulsionScrewItem.COST_PER_TICK && along > 0.1 && PropulsionScrewServer.isActive(p),
                "fe " + fe0 + "->" + fe1 + " (-" + (fe0 - fe1) + ", expected " + N * PropulsionScrewItem.COST_PER_TICK + ") speed=" + String.format("%.3f", speed)
                        + " along_look=" + String.format("%.3f", along) + " active=" + PropulsionScrewServer.isActive(p));

        // 5: one S2C broadcast on the state change on, one more when it turns off
        int bcOn = PropulsionScrewServer.broadcasts - bc0;
        PropulsionScrewServer.set(p, false);
        int bcOff = PropulsionScrewServer.broadcasts - bc0 - bcOn;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ScrewThrustStatePacket.encode(new ScrewThrustStatePacket(p.getId(), true), buf);
        ScrewThrustStatePacket back = ScrewThrustStatePacket.decode(buf);
        report("5_state_packet", bcOn == 1 && bcOff == 1 && back.entityId() == p.getId() && back.on() && !PropulsionScrewServer.isActive(p),
                "broadcast_on=" + bcOn + " broadcast_off=" + bcOff + " codec_roundtrip=" + (back.entityId() == p.getId() && back.on()));

        // 2: out of water -> no drain, not active
        place(p, 130.0);
        int fe2 = ElectricTools.getEnergy(stack);
        PropulsionScrewServer.set(p, true);
        for (int i = 0; i < N; i++) serverTick(p);
        int fe3 = ElectricTools.getEnergy(stack);
        report("2_no_thrust_out_of_water", fe2 == fe3 && !PropulsionScrewServer.isActive(p),
                "eye_in_water=" + p.isEyeInFluidType(net.minecraftforge.common.ForgeMod.WATER_TYPE.get()) + " fe " + fe2 + "->" + fe3 + " active=" + PropulsionScrewServer.isActive(p));
        PropulsionScrewServer.set(p, false);

        // 3: FE 0 in water -> no drain, not active
        place(p, 105.0);
        ElectricTools.setEnergy(stack, 0);
        PropulsionScrewServer.set(p, true);
        for (int i = 0; i < N; i++) serverTick(p);
        int fe4 = ElectricTools.getEnergy(stack);
        report("3_no_thrust_without_fe", fe4 == 0 && !PropulsionScrewServer.isActive(p),
                "eye_in_water=" + p.isEyeInFluidType(net.minecraftforge.common.ForgeMod.WATER_TYPE.get()) + " fe=" + fe4 + " active=" + PropulsionScrewServer.isActive(p));
        PropulsionScrewServer.set(p, false);

        // 4: the light packet handler flips the "Light" tag (main hand, then offhand)
        boolean l0 = PropulsionScrewItem.isLightOn(stack);
        ScrewLightPacket.apply(p);
        boolean l1 = PropulsionScrewItem.isLightOn(stack);
        ScrewLightPacket.apply(p);
        boolean l2 = PropulsionScrewItem.isLightOn(stack);
        ItemStack off = ElectricTools.fullStack(ElectricTools.PROPULSION_SCREW.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setItemInHand(InteractionHand.OFF_HAND, off);
        ScrewLightPacket.apply(p);
        boolean l3 = PropulsionScrewItem.isLightOn(off);
        report("4_light_toggle", !l0 && l1 && !l2 && l3, "main " + l0 + "->" + l1 + "->" + l2 + " offhand_after_toggle=" + l3);
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }
}
