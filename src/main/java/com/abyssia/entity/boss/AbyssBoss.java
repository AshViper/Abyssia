package com.abyssia.entity.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import javax.annotation.Nullable;

/**
 * A 200 block class boss: a head (this entity) dragging a {@link BossChain}, hitboxes as {@link BossPart}s, a boss bar,
 * three phases (66% / 33% health) and a spherical arena. No pathfinding: the subclass steers the head in a straight line.
 */
public abstract class AbyssBoss extends PathfinderMob
{
    public static final double BAR_RANGE = 128;
    public static final double CONTACT_RANGE = 220;
    public static final double CONTACT_DISTANCE = 2.5;
    public static final float CONTACT_DAMAGE = 6.0F;
    private static final float HEAD_MULTIPLIER = 1.5F;

    protected final BossChain chain;
    private final BossPart[] parts;
    private final ServerBossEvent bossEvent;
    /** Arena centre (head coordinates) and radius: a sphere around the spawn point. */
    @Nullable
    protected Vec3 center;
    protected double boundRadius = 110;
    protected Vec3 vel = Vec3.ZERO;
    protected int phase = 1;
    /** Retreat: no non-spectator player within {@link BossFarPayload#RANGE} for this many ticks and the boss leaves (no drops). */
    private static final long RETREAT_TICKS = 1200;
    /** Game time a player was last seen within range; -1 = not set yet (fresh spawn). Saved, so time spent unloaded counts. */
    private long lastPlayerSeen = -1;
    private boolean retreatChecked;

    protected AbyssBoss(EntityType<? extends AbyssBoss> type, Level level, int joints, double spacing, BossEvent.BossBarColor color)
    {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.chain = new BossChain(joints, spacing);
        int per = jointsPerPart();
        this.parts = new BossPart[(joints - 1 + per - 1) / per];
        for (int i = 0; i < parts.length; i++) parts[i] = new BossPart(this, i);
        // part ids follow the boss id (the client needs the same ids to attack a part), like the ender dragon
        this.setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        this.bossEvent = new ServerBossEvent(type.getDescription(), color, BossEvent.BossBarOverlay.PROGRESS);
    }

    public BossChain chain() { return chain; }

    /** Chain by snapshot number (0 = the spine); null if there is none. Tentacle bosses add more. */
    @Nullable
    public BossChain chainAt(int index) { return index == 0 ? chain : null; }

    /** Natural spawn: arena sphere centred on the spawn point, first heading {@code launch} (the chain trails behind it). */
    public void bindArena(Vec3 arenaCenter, double radius, Vec3 launch)
    {
        this.center = arenaCenter;
        this.boundRadius = radius * arenaScale();
        this.vel = launch;
    }

    /** Body scale against the original 200-block design (1 = unchanged); renderers and hitboxes follow it. */
    public double size() { return 1.0; }

    /** Body radius in blocks: contact damage reach from the spine, and the margin for culling and the fog test. */
    public double bodyRadius() { return CONTACT_DISTANCE * size(); }

    /** Joints per hitbox part (called from the constructor, so return a constant). */
    protected int jointsPerPart() { return 3; }

    /** The natural-spawn arena radius is multiplied by this (big bodies need room to turn). */
    protected double arenaScale() { return 1.0; }

    /** Entity scan range round the head for contact damage: the whole chain must fit inside. */
    protected double reach() { return Math.max(CONTACT_RANGE, (chain.size() - 1) * chain.spacing() + 4 * bodyRadius()); }

    /** Kind id for the far silhouette (BossFarPayload). */
    protected int farKind() { return 0; }

    public Vec3 headPos()
    {
        return new Vec3(getX(), getY() + getBbHeight() * 0.5, getZ());
    }

    protected void setHeadPos(Vec3 p)
    {
        setPos(p.x, p.y - getBbHeight() * 0.5, p.z);
    }

    /** Subclass AI: choose targets / states and call {@link #steer}. Server only, once per tick. */
    protected abstract void aiTick();

    /** Both sides, right after the chain moved (tail swipes and the like). */
    protected void afterChainTick() {}

    protected void onPhaseChange(int newPhase) {}

    /** False while the body must not hurt anyone (a burrowing boss inside the ground). */
    protected boolean contactActive() { return true; }

    /** Whether hits are accepted: {@code part} is null for the head. False while the target part is inside the ground. */
    protected boolean attackable(@Nullable BossPart part) { return true; }

    /** State name for the saved "BossState" tag (observation only). */
    protected String stateName() { return ""; }

    // ---- tick

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide) clientTick();
        else serverTick();
    }

    private void tickChain()
    {
        Vec3 h = headPos();
        double bx = -vel.x, by = -vel.y, bz = -vel.z;
        chain.tick(h.x, h.y, h.z, bx, by, bz);
        afterChainTick();
        updateParts();
    }

    private void clientTick()
    {
        tickChain();
    }

    private void serverTick()
    {
        if (center == null) center = headPos();
        if (!isDeadOrDying())
        {
            float ratio = getHealth() / getMaxHealth();
            int np = ratio > 0.66F ? 1 : ratio > 0.33F ? 2 : 3;
            if (np > phase)
            {
                phase = np;
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0F, 0.6F);
                onPhaseChange(np);
            }
            bossEvent.setProgress(Math.max(0, ratio));
            if (tickCount % 10 == 0) updateBarPlayers();
            // NoAI (e.g. /summon ... {NoAI:1b}, devtest BossShot): the boss stays where it is, the body lies straight behind the head
            if (!isNoAi())
            {
                aiTick();
                applyMovement();
            }
        }
        tickChain();
        if (!isDeadOrDying() && tickCount % 2 == 0 && contactActive()) contactDamage();
        if (!isDeadOrDying() && retreatCheck()) { discard(); return; }
        if (tickCount % 10 == 0) PacketDistributor.sendToPlayersTrackingEntity(this, snapshotPayload());
        if (tickCount % 20 == 0)
        {
            sendFar();
        }
        if (tickCount % 10 == 0) keepLoaded();
    }

    /** True when no player has been within range for {@link #RETREAT_TICKS} (counting time spent unloaded). */
    private boolean retreatCheck()
    {
        long now = level().getGameTime();
        if (lastPlayerSeen < 0) lastPlayerSeen = now;
        boolean first = !retreatChecked;
        if (!first && tickCount % 20 != 0) return false;
        retreatChecked = true;
        // the elapsed time first: a boss frozen in a non-ticking chunk (no player kept it loaded) resumes with the
        // returning player already near, and refreshing first would forget that it was left alone
        if (now - lastPlayerSeen > RETREAT_TICKS) return true;
        double r2 = (double) BossFarPayload.RANGE * BossFarPayload.RANGE;
        for (ServerPlayer p : ((net.minecraft.server.level.ServerLevel) level()).players())
            if (!p.isSpectator() && p.distanceToSqr(this) < r2) { lastPlayerSeen = now; return false; }
        return now - lastPlayerSeen > RETREAT_TICKS;
    }

    /** Keeps the head's chunks entity-ticking (2 chunks around) while a player is within the LOD range; refreshed every 10 ticks, expires by timeout. */
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> BOSS_TICKET =
            net.minecraft.server.level.TicketType.create("abyssia_boss", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 60);

    /**
     * Holds the chunks round the head while a player is near. Also called by BossSpawner right after the spawn: a boss
     * spawned beyond the simulation distance would otherwise never tick, so it would never add the ticket itself.
     */
    void keepLoaded()
    {
        if (isDeadOrDying()) return;
        var sl = (net.minecraft.server.level.ServerLevel) level();
        double r2 = (double) BossFarPayload.RANGE * BossFarPayload.RANGE;
        boolean near = false;
        for (ServerPlayer p : sl.players())
            if (!p.isSpectator() && p.distanceToSqr(this) < r2) { near = true; break; }
        if (!near) return;
        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(blockPosition());
        // ticket distance 4 = entity ticking out to 2 chunks (distance 2 would tick only the centre chunk, and the head
        // leaves it between refreshes, freezes and lets the ticket expire)
        sl.getChunkSource().addRegionTicket(BOSS_TICKET, cp, 4, cp);
    }

    /** BOSS01 LOD: head and every 4th joint to every player within 512 blocks, tracked or not. */
    private void sendFar()
    {
        if (!chain.isInitialised()) return;
        short[] rel = farRel();
        BossFarPayload calm = new BossFarPayload(getId(), farKind(), false, chain.x(0), chain.y(0), chain.z(0), rel);
        BossFarPayload fight = new BossFarPayload(getId(), farKind(), true, chain.x(0), chain.y(0), chain.z(0), rel);
        double r2 = (double) BossFarPayload.RANGE * BossFarPayload.RANGE;
        for (ServerPlayer p : ((net.minecraft.server.level.ServerLevel) level()).players())
            if (p.distanceToSqr(chain.x(0), chain.y(0), chain.z(0)) < r2) PacketDistributor.sendToPlayer(p, bossEvent.getPlayers().contains(p) ? fight : calm);
    }

    /** Far silhouette points after the head: head-relative, {@link BossChain#UNIT} units, x y z each (default: every 4th joint). */
    protected short[] farRel()
    {
        int n = chain.size();
        int count = 0;
        for (int i = BossFarPayload.STEP; i < n; i += BossFarPayload.STEP) count++;
        if ((n - 1) % BossFarPayload.STEP != 0) count++;
        short[] rel = new short[3 * count];
        int k = 0;
        for (int i = BossFarPayload.STEP; ; i += BossFarPayload.STEP)
        {
            int j = Math.min(i, n - 1);
            if (j <= 0) break;
            rel[k++] = (short) Math.round((chain.x(j) - chain.x(0)) * BossChain.UNIT);
            rel[k++] = (short) Math.round((chain.y(j) - chain.y(0)) * BossChain.UNIT);
            rel[k++] = (short) Math.round((chain.z(j) - chain.z(0)) * BossChain.UNIT);
            if (j == n - 1) break;
        }
        return rel;
    }

    private BossChainPayload snapshotPayload()
    {
        return new BossChainPayload(getId(), chain.snapshot());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player)
    {
        super.startSeenByPlayer(player);
        if (chain.isInitialised()) PacketDistributor.sendToPlayer(player, snapshotPayload());
    }

    private void updateBarPlayers()
    {
        for (ServerPlayer p : new java.util.ArrayList<>(bossEvent.getPlayers()))
            if (p.isRemoved() || p.level() != level() || p.distanceToSqr(this) >= BAR_RANGE * BAR_RANGE) bossEvent.removePlayer(p);
        for (ServerPlayer p : ((net.minecraft.server.level.ServerLevel) level()).players())
            if (p.distanceToSqr(this) < BAR_RANGE * BAR_RANGE) bossEvent.addPlayer(p);
    }

    @Override
    public void onRemovedFromLevel()
    {
        bossEvent.removeAllPlayers();
        super.onRemovedFromLevel();
    }

    // ---- movement: straight steering inside the arena sphere

    /** Turns the velocity toward {@code point} (turn = share of the difference per tick) and caps it at {@code speed}. */
    protected void steer(Vec3 point, double speed, double turn)
    {
        Vec3 to = point.subtract(headPos());
        double len = to.length();
        Vec3 desired = len < 1.0E-4 ? vel : to.scale(speed / len);
        vel = vel.add(desired.subtract(vel).scale(turn));
        double v = vel.length();
        if (v > speed) vel = vel.scale(speed / v);
    }

    private void applyMovement()
    {
        Vec3 head = headPos();
        Vec3 c = center;
        double margin = 12;
        double dc = head.distanceTo(c);
        if (dc > boundRadius - margin)
        {
            Vec3 in = c.subtract(head).scale(1.0 / Math.max(dc, 1.0E-4));
            vel = vel.add(in.scale(0.08 * Math.min(1.0, (dc - (boundRadius - margin)) / margin)));
        }
        Vec3 next = head.add(vel);
        Vec3 off = next.subtract(c);
        double d = off.length();
        if (d > boundRadius)
        {
            Vec3 n = off.scale(1.0 / d);
            next = c.add(n.scale(boundRadius));
            double outward = vel.dot(n);
            if (outward > 0) vel = vel.subtract(n.scale(outward));
        }
        setHeadPos(next);
        if (vel.lengthSqr() > 1.0E-6)
        {
            setYRot((float) (Math.atan2(-vel.x, vel.z) * (180.0 / Math.PI)));
            setXRot((float) (-Math.asin(vel.y / vel.length()) * (180.0 / Math.PI)));
        }
    }

    protected void updateParts()
    {
        if (!chain.isInitialised()) return;
        for (BossPart part : parts)
        {
            int per = jointsPerPart();
            int j = Math.min(1 + per * part.index() + (per - 1) / 2, chain.size() - 1);
            part.setPos(chain.x(j), chain.y(j) - part.getBbHeight() * 0.5, chain.z(j));
        }
    }

    // ---- damage

    private void contactDamage()
    {
        Vec3 head = headPos();
        double r2 = reach() * reach();
        double cd = bodyRadius();
        for (LivingEntity p : attackables())
        {
            Vec3 pc = p.getBoundingBox().getCenter();
            if (pc.distanceToSqr(head) > r2) continue;
            if (chain.distanceSqr(pc.x, pc.y, pc.z) < cd * cd) p.hurt(damageSources().mobAttack(this), CONTACT_DAMAGE);
        }
    }

    /** Design health; MAX_HEALTH is capped at 1024 by vanilla, so above that the damage taken is scaled down. */
    protected double designHealth() { return 1024.0; }

    /** Incoming damage scaled so that {@link #designHealth()} hit points empty the (capped) health bar. */
    private float scaleToDesign(float amount)
    {
        double d = designHealth();
        return d > 1024.0 ? (float) (amount * 1024.0 / d) : amount;
    }

    public static final String DUMMY_TAG = "abyssia_boss_dummy";

    public static boolean isValidTarget(@Nullable LivingEntity e)
    {
        if (e == null || !e.isAlive()) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e.getTags().contains(DUMMY_TAG);
    }

    /** Nearest live player in range, else the nearest entity tagged {@link #DUMMY_TAG}. */
    @Nullable
    protected LivingEntity findTarget(double range)
    {
        Player p = level().getNearestPlayer(getX(), getY(), getZ(), range, e -> e instanceof Player pl && isValidTarget(pl));
        if (p != null) return p;
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range), x -> x != this && isValidTarget(x)))
        {
            double d = e.distanceToSqr(this);
            if (d <= range * range && d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private java.util.List<LivingEntity> attackables()
    {
        java.util.List<LivingEntity> list = new java.util.ArrayList<>();
        for (Player p : level().players()) if (isValidTarget(p)) list.add(p);
        list.addAll(level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(CONTACT_RANGE),
                x -> x != this && !(x instanceof Player) && isValidTarget(x)));
        return list;
    }

    /** Hits on the head count 1.5x; a bypassing source (kill command) is left alone. */
    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !attackable(null)) return false;
        return super.hurt(source, source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? amount : scaleToDesign(amount * HEAD_MULTIPLIER));
    }

    /** A hit on a body part: x1, the boss's own invulnerability window collapses multi-hits of one tick into one. */
    public boolean hurtFromPart(BossPart part, DamageSource source, float amount)
    {
        if (!attackable(part)) return false;
        return super.hurt(source, scaleToDesign(amount));
    }

    // ---- multipart

    @Override
    public boolean isMultipartEntity() { return true; }

    @Override
    public PartEntity<?>[] getParts() { return parts; }

    @Override
    public void setId(int id)
    {
        super.setId(id);
        if (parts != null) for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    // ---- misc entity rules

    @Override
    public AABB getBoundingBoxForCulling()
    {
        return chain.isInitialised() ? chain.bounds(Math.max(3.0, 2.5 * bodyRadius())) : super.getBoundingBoxForCulling();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 384.0 * 384.0; }

    @Override
    public void travel(Vec3 input) {}

    @Override
    protected void pushEntities() {}

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isPushedByFluid() { return false; }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) { return false; }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public void setCustomName(@Nullable Component name)
    {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public void remove(RemovalReason reason)
    {
        bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (center != null)
        {
            tag.putDouble("CenterX", center.x);
            tag.putDouble("CenterY", center.y);
            tag.putDouble("CenterZ", center.z);
        }
        tag.putDouble("BoundRadius", boundRadius);
        if (lastPlayerSeen >= 0) tag.putLong("LastPlayerSeen", lastPlayerSeen);
        tag.putString("BossState", stateName());
        tag.putInt("Phase", phase);
        chain.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.contains("CenterX")) center = new Vec3(tag.getDouble("CenterX"), tag.getDouble("CenterY"), tag.getDouble("CenterZ"));
        if (tag.contains("LastPlayerSeen")) lastPlayerSeen = tag.getLong("LastPlayerSeen");
        if (tag.contains("BoundRadius")) boundRadius = tag.getDouble("BoundRadius");
        chain.load(tag);
        float ratio = getHealth() / getMaxHealth();
        phase = ratio > 0.66F ? 1 : ratio > 0.33F ? 2 : 3;
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }
}
