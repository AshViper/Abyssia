package com.abyssia.research.scan;

import com.abyssia.Abyssia;
import com.abyssia.item.scanner.LidarScannerItem;
import com.abyssia.network.AbyssiaNetwork;
import com.abyssia.research.ResearchManager;
import com.abyssia.research.ScanTargetRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AB05 per-player lidar scan session (memory only). While the scanner is held the server resolves the looked-at scan target each
 * tick; progress = elapsed / (scanSeconds * 20). Looking away is forgiven for {@link #GRACE_TICKS}; too far, item switch, death and
 * logout abort. Completion calls {@link ResearchManager#scan}; the client only draws {@link ScanProgressPayload}.
 */
@EventBusSubscriber(modid = Abyssia.MODID)
public final class PlayerScanState
{
    public static final int GRACE_TICKS = 6, SEND_INTERVAL = 4;
    /** MK0 scanner distance (MK1/MK2 later: 24 / 32) */
    public static final double MK0_RANGE = 16.0;

    /** what the item should do after a tick */
    public enum Tick { RUNNING, SCAN_DONE, IDLE_DONE }

    private static final Map<UUID, PlayerScanState> STATES = new HashMap<>();

    private ResourceLocation targetId;
    private long startTick;
    private int duration, elapsed, grace, sinceSend, idleTicks;

    private PlayerScanState() {}

    /** One server tick of a held scanner. */
    public static Tick tick(ServerPlayer player)
    {
        PlayerScanState s = STATES.computeIfAbsent(player.getUUID(), id -> new PlayerScanState());
        ServerLevel level = player.serverLevel();
        HitResult hit = raycast(player, level);
        var target = hit == null ? null : ScanTargetRegistry.find(player, hit).orElse(null);
        boolean tooFar = false;
        if (target != null)
        {
            double reach = Math.min((double) target.range(), MK0_RANGE);
            if (player.getEyePosition().distanceTo(hit.getLocation()) > reach + 0.5) { target = null; tooFar = true; }
        }

        if (target != null)
        {
            ResourceLocation id = target.id();
            if (!id.equals(s.targetId))
            {
                if (s.targetId != null) send(player, s.targetId, 0, ScanProgressPayload.ABORTED);
                s.targetId = id;
                s.startTick = level.getGameTime();
                s.duration = Math.max(1, (int) Math.round(target.scanSeconds() * 20.0));
                s.elapsed = 0;
                s.sinceSend = SEND_INTERVAL;
            }
            s.grace = GRACE_TICKS;
            s.idleTicks = 0;
            s.elapsed++;
            if (s.elapsed >= s.duration)
            {
                String key = ScanTargetRegistry.discoveryKey(player, target, hit);
                var result = ResearchManager.scan(player, id, key);
                byte state = switch (result.outcome().name())
                {
                    case "NEW" -> ScanProgressPayload.COMPLETE_NEW;
                    case "FRAGMENT" -> ScanProgressPayload.COMPLETE_FRAGMENT;
                    default -> ScanProgressPayload.COMPLETE_KNOWN;
                };
                boolean fresh = state != ScanProgressPayload.COMPLETE_KNOWN;
                Vec3 at = hit.getLocation();
                level.sendParticles(ParticleTypes.GLOW, at.x, at.y, at.z, fresh ? 20 : 6, 0.5, 0.5, 0.5, 0.05);
                level.playSound(null, at.x, at.y, at.z, fresh ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.CONDUIT_ACTIVATE,
                        SoundSource.PLAYERS, 0.8F, fresh ? 1.3F : 1.6F);
                send(player, id, 1.0F, state);
                s.targetId = null;
                return Tick.SCAN_DONE;
            }
            if (++s.sinceSend >= SEND_INTERVAL)
            {
                s.sinceSend = 0;
                send(player, id, Mth.clamp((float) s.elapsed / s.duration, 0F, 1F), ScanProgressPayload.SCANNING);
            }
            return Tick.RUNNING;
        }

        if (s.targetId != null)
        {
            if (tooFar || --s.grace < 0) abort(player);
            return Tick.RUNNING;
        }
        // nothing scannable: after SCAN_TICKS of holding the scanner only lists deposits (old behaviour)
        if (++s.idleTicks >= LidarScannerItem.SCAN_TICKS)
        {
            s.idleTicks = 0;
            return Tick.IDLE_DONE;
        }
        return Tick.RUNNING;
    }

    /** Ends the running scan with an ABORTED message (no-op when no target was locked). */
    public static void abort(ServerPlayer player)
    {
        PlayerScanState s = STATES.get(player.getUUID());
        if (s == null || s.targetId == null) return;
        send(player, s.targetId, 0, ScanProgressPayload.ABORTED);
        s.targetId = null;
        s.idleTicks = 0;
    }

    private static void clear(ServerPlayer player)
    {
        abort(player);
        STATES.remove(player.getUUID());
    }

    private static void send(ServerPlayer player, ResourceLocation id, float progress, byte state)
    {
        AbyssiaNetwork.sendTo(player, new ScanProgressPayload(id, progress, state));
    }

    /** First scan target along the look vector: block (outline) or entity, whichever is nearer, within the MK0 distance. */
    private static HitResult raycast(ServerPlayer player, ServerLevel level)
    {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(MK0_RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double maxSq = block.getType() == HitResult.Type.MISS ? MK0_RANGE * MK0_RANGE : eye.distanceToSqr(block.getLocation());
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end,
                player.getBoundingBox().expandTowards(look.scale(MK0_RANGE)).inflate(1.0),
                e -> !e.isSpectator() && e.isPickable(), maxSq);
        if (entity != null) return entity;
        return block.getType() == HitResult.Type.MISS ? null : block;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || STATES.isEmpty()) return;
        if (!STATES.containsKey(player.getUUID())) return;
        // item switched / released: the scanner is no longer in use
        if (!(player.isUsingItem() && player.getUseItem().getItem() instanceof LidarScannerItem)) clear(player);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) STATES.remove(player.getUUID());
    }
}
