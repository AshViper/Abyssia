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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AB05 per-player lidar scan session (memory only). While the scanner is held the server resolves the looked-at scan target each
 * tick; progress = elapsed / (scanSeconds * 20). Looking away is forgiven for {@link #GRACE_TICKS}; too far, item switch, death and
 * logout abort. Completion calls {@link ResearchManager#scan}; the client only draws {@link ScanProgressPayload}.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerScanState
{
    public static final int GRACE_TICKS = 6, SEND_INTERVAL = 4;
    /** MK0 scanner distance; MK1 / MK2 (technologies scanner_mk1 / scanner_mk2) reach 24 / 32 blocks */
    /** far block rays stay inside the loaded area (Level.clip would load chunks synchronously) */
    public static final double LONG_RANGE_CAP = 112.0;
    public static final double MK0_RANGE = 16.0, MK1_RANGE = 24.0, MK2_RANGE = 32.0;
    /** scan time multipliers of MK1 / MK2; a scan never takes less than {@link #MIN_SCAN_TICKS} */
    public static final double MK1_TIME = 0.8, MK2_TIME = 0.65;
    public static final int MIN_SCAN_TICKS = 40;
    private static final ResourceLocation SCANNER_MK1 = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "technology/scanner_mk1");
    private static final ResourceLocation SCANNER_MK2 = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "technology/scanner_mk2");

    /** what the item should do after a tick */
    public enum Tick { RUNNING, SCAN_DONE, IDLE_DONE }

    private static final Map<UUID, PlayerScanState> STATES = new HashMap<>();

    private ResourceLocation targetId;
    private long startTick;
    private int duration, elapsed, grace, sinceSend, idleTicks;

    private PlayerScanState() {}

    /** Scanner distance of this player: 16 base, 24 with scanner_mk1, 32 with scanner_mk2. */
    public static double scannerRange(ServerPlayer player)
    {
        if (ResearchManager.isUnlocked(player, SCANNER_MK2)) return MK2_RANGE;
        if (ResearchManager.isUnlocked(player, SCANNER_MK1)) return MK1_RANGE;
        return MK0_RANGE;
    }

    /** Scan time multiplier of this player's scanner tier. */
    public static double scanTimeScale(ServerPlayer player)
    {
        if (ResearchManager.isUnlocked(player, SCANNER_MK2)) return MK2_TIME;
        if (ResearchManager.isUnlocked(player, SCANNER_MK1)) return MK1_TIME;
        return 1.0;
    }

    /** One server tick of a held scanner. */
    public static Tick tick(ServerPlayer player)
    {
        PlayerScanState s = STATES.computeIfAbsent(player.getUUID(), id -> new PlayerScanState());
        ServerLevel level = player.serverLevel();
        double scanRange = scannerRange(player);
        HitResult hit = raycast(player, level, scanRange, Math.max(scanRange, Math.min(longestTargetRange() * scanRange / MK0_RANGE, LONG_RANGE_CAP)));
        var target = hit == null ? null : ScanTargetRegistry.find(player, hit).orElse(null);
        boolean tooFar = false;
        if (target != null)
        {
            // the target's own range is its MK0 limit; better scanners scale it (16 -> 24 -> 32)
            double reach = target.range() * scanRange / MK0_RANGE;
            double dist = player.getEyePosition().distanceTo(hit.getLocation());
            if (dist > reach + 0.5)
            {
                target = null;
                // beyond the plain scanner distance only long-range targets are looked for: anything else counts as a miss (as before)
                tooFar = dist <= scanRange + 0.5;
            }
        }

        if (target != null)
        {
            ResourceLocation id = target.id();
            if (!id.equals(s.targetId))
            {
                if (s.targetId != null) send(player, s.targetId, 0, ScanProgressPayload.ABORTED);
                s.targetId = id;
                s.startTick = level.getGameTime();
                s.duration = Math.max(MIN_SCAN_TICKS, (int) Math.round(target.scanSeconds() * 20.0 * scanTimeScale(player)));
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

    /** First scan target along the look vector: block (outline) or entity, whichever is nearer, within the scanner distance. */
    private static HitResult raycast(ServerPlayer player, ServerLevel level, double range, double blockRange)
    {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        // the block clip may reach further (long-range targets such as wrecks); entities stay within the scanner distance
        BlockHitResult block = level.clip(new ClipContext(eye, eye.add(look.scale(blockRange)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double maxSq = block.getType() == HitResult.Type.MISS ? range * range : Math.min(range * range, eye.distanceToSqr(block.getLocation()));
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end,
                player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
                e -> !e.isSpectator() && e.isPickable(), maxSq);
        if (entity != null) return entity;
        return block.getType() == HitResult.Type.MISS ? null : block;
    }

    /** Largest MK0 range among the registered scan targets. */
    private static double longestTargetRange()
    {
        double max = MK0_RANGE;
        for (var t : ScanTargetRegistry.all()) max = Math.max(max, t.range());
        return max;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || STATES.isEmpty()) return;
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
