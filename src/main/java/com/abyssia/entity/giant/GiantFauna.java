package com.abyssia.entity.giant;

import com.abyssia.entity.boss.BossChain;
import com.abyssia.entity.giant.ability.GiantAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

import javax.annotation.Nullable;

/**
 * A wild animal of 30+ blocks (GNT01): a head (this entity) dragging a {@link BossChain}, hitboxes as {@link GiantPart}s.
 * No pathfinding: it swims in a straight line and steers by three rays from the head, cast every {@link #RAY_INTERVAL}
 * ticks, so the body keeps out of rock and water's edge. Temper and attacks are in {@link GiantBrain}. Not persistent: it
 * leaves when no player is near (see {@link #leaving}).
 */
public abstract class GiantFauna extends PathfinderMob implements software.bernie.geckolib.animatable.GeoEntity
{
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_CHASING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(GiantFauna.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_GLOW =
            net.minecraft.network.syncher.SynchedEntityData.defineId(GiantFauna.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_HOLD =
            net.minecraft.network.syncher.SynchedEntityData.defineId(GiantFauna.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** GeckoLib near view: "main" loops idle; "action" holds the species' hold animation while chasing and plays attack when the server triggers it (animation names shared by every species). */
    private final software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache geoCache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        entityData.define(DATA_CHASING, false);
        entityData.define(DATA_GLOW, true);
        entityData.define(DATA_HOLD, false);
    }

    /** Whether the emissive glow layer is drawn (synced; a species can switch it off, for example the glassback's flare). */
    public boolean glowOn() { return entityData.get(DATA_GLOW); }

    public void setGlow(boolean b)
    {
        if (glowOn() != b) entityData.set(DATA_GLOW, b);
    }

    /** True while the brain is in CHASE (synced to clients, drives the jaw_out animation). */
    public boolean chasing() { return entityData.get(DATA_CHASING); }

    public void setChasing(boolean b)
    {
        if (chasing() != b) entityData.set(DATA_CHASING, b);
    }

    /** True while the species' hold animation plays (synced; by default the chase, a species ability can decide otherwise). */
    public boolean holding() { return entityData.get(DATA_HOLD); }

    private void setHold(boolean b)
    {
        if (holding() != b) entityData.set(DATA_HOLD, b);
    }

    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<GiantFauna>(this, "main", 0,
                state -> state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("idle"))));
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<GiantFauna>(this, "action", 0,
                state -> holding() && spec.holdAnim() != null ? state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenPlayAndHold(spec.holdAnim()))
                        : software.bernie.geckolib.core.object.PlayState.STOP)
                .triggerableAnim("attack", software.bernie.geckolib.core.animation.RawAnimation.begin().thenPlay("attack")));
    }

    @Override
    public software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return geoCache;
    }

    private static final int RAY_INTERVAL = 5;
    private static final double RAY_LENGTH = 24, RAY_STEP = 2, SIDE_ANGLE = Math.toRadians(40);
    /** Leaves when the nearest player is farther than this. */
    public static final double DESPAWN_DISTANCE = 128;
    /** Leaves when no player stays within this radius for {@link #ABSENT_TICKS}. */
    public static final double PRESENCE_RADIUS = 160;
    public static final long ABSENT_TICKS = 1200;

    private final GiantSpec spec;
    private final BossChain chain;
    private final GiantPart[] parts;
    private final GiantBrain brain;
    private final GiantAbility ability;
    private Vec3 vel = Vec3.ZERO;
    @Nullable
    private Vec3 home;
    /** Game time a player was last within {@link #PRESENCE_RADIUS}; -1 = not set yet. Saved, so time spent unloaded counts. */
    private long lastPlayerSeen = -1;
    private Vec3 avoid = Vec3.ZERO;
    private double avoidWeight;
    private double speedScale = 1.0;
    private boolean probeNow;

    protected GiantFauna(EntityType<? extends GiantFauna> type, Level level, GiantSpec spec)
    {
        super(type, level);
        this.spec = spec;
        this.noPhysics = true;
        this.setNoGravity(true);
        this.chain = new BossChain(spec.joints(), spec.spacing());
        this.parts = new GiantPart[spec.partCount()];
        for (int i = 0; i < parts.length; i++) parts[i] = new GiantPart(this, i);
        // part ids follow the animal id (the client needs the same ids to attack a part), like the ender dragon
        this.setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        this.ability = GiantAbility.create(this);
        this.brain = new GiantBrain(this, spec.temper());
    }

    public static AttributeSupplier.Builder createAttributes(GiantSpec spec)
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, spec.health()).add(Attributes.ARMOR, spec.armor())
                .add(Attributes.FOLLOW_RANGE, 64.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public GiantSpec spec() { return spec; }

    public BossChain chain() { return chain; }

    public GiantBrain brain() { return brain; }

    public GiantAbility ability() { return ability; }

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand)
    {
        net.minecraft.world.InteractionResult r = ability.interact(player, hand);
        return r != net.minecraft.world.InteractionResult.PASS ? r : super.mobInteract(player, hand);
    }

    public Vec3 headPos()
    {
        return new Vec3(getX(), getY() + getBbHeight() * 0.5, getZ());
    }

    private void setHeadPos(Vec3 p)
    {
        setPos(p.x, p.y - getBbHeight() * 0.5, p.z);
    }

    /** Spawn point (head coordinates); the chase never goes farther than {@link GiantBrain#LEASH} from it. */
    public Vec3 home()
    {
        if (home == null) home = headPos();
        return home;
    }

    /** Unit vector of the swimming direction. */
    public Vec3 heading()
    {
        return vel.lengthSqr() > 1.0E-6 ? vel.normalize() : new Vec3(0, 0, 1);
    }

    /** Places the head and sets the first heading (the chain trails behind it). Call before adding to the level. */
    public void launch(Vec3 head, Vec3 dir)
    {
        setHeadPos(head);
        home = head;
        vel = dir.normalize().scale(spec.speed());
    }

    // ---- tick

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide) tickChain();
        else serverTick();
    }

    private void tickChain()
    {
        Vec3 h = headPos();
        chain.tick(h.x, h.y, h.z, -vel.x, -vel.y, -vel.z);
        updateParts();
    }

    private void serverTick()
    {
        long now = level().getGameTime();
        if (!isDeadOrDying())
        {
            brain.tick(now);
            ability.tick(now, brain.state(), brain.target());
            setHold(ability.holding(brain.state()));
            Vec3 anchor = ability.anchor();
            if (anchor != null) anchorTo(anchor);
            else move();
        }
        tickChain();
        if (!isDeadOrDying() && tickCount % 20 == 0 && leaving(now))
        {
            discard();
            return;
        }
        if (tickCount % 10 == 0 && chain.isInitialised()) GiantNetwork.sendToPlayersTrackingEntity(this, new GiantChainPayload(getId(), chain.snapshot()));
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player)
    {
        super.startSeenByPlayer(player);
        if (chain.isInitialised()) GiantNetwork.sendToPlayer(player, new GiantChainPayload(getId(), chain.snapshot()));
    }

    /** Not persistent: gone when the nearest player is beyond {@link #DESPAWN_DISTANCE}, or no player was within {@link #PRESENCE_RADIUS} for {@link #ABSENT_TICKS}. */
    private boolean leaving(long now)
    {
        if (lastPlayerSeen < 0) lastPlayerSeen = now;
        Vec3 h = headPos();
        Player nearest = level().getNearestPlayer(h.x, h.y, h.z, -1.0, false);
        if (nearest != null)
        {
            double d2 = nearest.distanceToSqr(h);
            if (d2 > DESPAWN_DISTANCE * DESPAWN_DISTANCE) return true;
            if (d2 <= PRESENCE_RADIUS * PRESENCE_RADIUS) lastPlayerSeen = now;
        }
        return now - lastPlayerSeen > ABSENT_TICKS;
    }

    /** The vanilla rule (64 block despawn, peaceful removal) does not apply: {@link #leaving} decides. */
    @Override
    public void checkDespawn() {}

    // ---- movement: straight steering, three rays from the head

    /** True where the animal cannot be: unloaded, not water, or a solid collision shape. */
    public static boolean blocked(Level level, double x, double y, double z)
    {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(pos)) return true;
        BlockState s = level.getBlockState(pos);
        return !s.getFluidState().is(FluidTags.WATER) || !s.getCollisionShape(level, pos).isEmpty();
    }

    /** {@link #blocked} at p and at four points {@code r} away from it (up, down, both sides across {@code dir}). */
    public static boolean blockedAround(Level level, Vec3 p, Vec3 dir, double r)
    {
        if (blocked(level, p.x, p.y, p.z)) return true;
        double sx = -dir.z, sz = dir.x, sl = Math.sqrt(sx * sx + sz * sz);
        if (sl < 0.1) { sx = 1; sz = 0; sl = 1; }
        sx = sx / sl * r;
        sz = sz / sl * r;
        return blocked(level, p.x, p.y + r, p.z) || blocked(level, p.x, p.y - r, p.z)
                || blocked(level, p.x + sx, p.y, p.z + sz) || blocked(level, p.x - sx, p.y, p.z - sz);
    }

    private static Vec3 rotY(Vec3 v, double a)
    {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
    }

    /** Free distance along dir from origin (up to {@link #RAY_LENGTH}), sampled every {@link #RAY_STEP} blocks with the body radius. */
    private double ray(Vec3 origin, Vec3 dir)
    {
        double r = spec.radius() * 0.8;
        for (double d = RAY_STEP; d <= RAY_LENGTH; d += RAY_STEP)
            if (blockedAround(level(), origin.add(dir.scale(d)), dir, r)) return d - RAY_STEP;
        return RAY_LENGTH;
    }

    private void probe(Vec3 head)
    {
        Vec3 h = heading();
        double dF = ray(head, h), dL = ray(head, rotY(h, SIDE_ANGLE)), dR = ray(head, rotY(h, -SIDE_ANGLE));
        if (dF >= RAY_LENGTH)
        {
            avoidWeight = 0;
            speedScale = 1.0;
            return;
        }
        Vec3 side = rotY(h, dL >= dR ? Math.PI / 2 : -Math.PI / 2);
        Vec3 flat = new Vec3(side.x, 0, side.z);
        flat = flat.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : flat.normalize();
        // boxed in on the sides too: add a vertical turn (down if heading up, else up)
        double y = Math.max(dL, dR) < RAY_LENGTH * 0.6 ? (h.y > 0 ? -0.8 : 0.8) : 0;
        avoid = new Vec3(flat.x * 0.6, y, flat.z * 0.6).normalize();
        avoidWeight = Mth.clamp((1 - dF / RAY_LENGTH) * 1.6, 0.0, 1.0);
        speedScale = 1.0;
        if (dF < 8)
        {
            avoidWeight = 1.0;
            speedScale = 0.5;
        }
    }

    private void move()
    {
        Vec3 head = headPos();
        if (probeNow || tickCount % RAY_INTERVAL == 0)
        {
            probe(head);
            probeNow = false;
        }
        Vec3 dir = brain.direction(head);
        if (avoidWeight > 0)
        {
            dir = dir.scale(1 - avoidWeight).add(avoid.scale(avoidWeight));
            if (dir.lengthSqr() < 0.01) dir = avoid;
        }
        double hl = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (hl < 0.05)
        {
            Vec3 hv = new Vec3(vel.x, 0, vel.z);
            dir = hv.lengthSqr() > 1.0E-6 ? hv.normalize() : new Vec3(0, 0, 1);
        }
        else
        {
            // at most about 30 degrees up or down
            dir = new Vec3(dir.x, Mth.clamp(dir.y, -0.577 * hl, 0.577 * hl), dir.z).normalize();
        }
        double speed = spec.speed() * brain.speedFactor() * speedScale;
        double turn = avoidWeight > 0 ? 0.12 : brain.turnRate();
        vel = vel.add(dir.scale(speed).subtract(vel).scale(turn));
        Vec3 next = head.add(vel);
        // the only per-tick lookup: never step into rock or out of the water; look again at once
        if (blocked(level(), next.x, next.y, next.z))
        {
            vel = vel.scale(0.3);
            probeNow = true;
            next = head;
        }
        setHeadPos(next);
        if (vel.lengthSqr() > 1.0E-6)
        {
            setYRot((float) (Math.atan2(-vel.x, vel.z) * (180.0 / Math.PI)));
            setXRot((float) (-Math.asin(Mth.clamp(vel.y / vel.length(), -1.0, 1.0)) * (180.0 / Math.PI)));
        }
    }

    /** Puts the head at p (a lamprey on a hull) and lets the chain trail behind the hull's motion. */
    private void anchorTo(Vec3 p)
    {
        Vec3 d = p.subtract(headPos());
        if (d.lengthSqr() > 0.0004) vel = d.scale(0.5).add(vel.scale(0.5));
        setHeadPos(p);
        if (vel.lengthSqr() > 1.0E-6)
        {
            setYRot((float) (Math.atan2(-vel.x, vel.z) * (180.0 / Math.PI)));
            setXRot((float) (-Math.asin(Mth.clamp(vel.y / vel.length(), -1.0, 1.0)) * (180.0 / Math.PI)));
        }
    }

    private void updateParts()
    {
        if (!chain.isInitialised()) return;
        int per = spec.jointsPerPart();
        for (GiantPart part : parts)
        {
            int j = Math.min(1 + per * part.index() + (per - 1) / 2, chain.size() - 1);
            part.setPos(chain.x(j), chain.y(j) - part.getBbHeight() * 0.5, chain.z(j));
        }
    }

    // ---- measurements (command stats, devtest)

    /** Sum of the joint to joint distances from the head to the tail end. */
    public double measuredLength()
    {
        double sum = 0;
        for (int i = 0; i + 1 < chain.size(); i++)
            sum += Math.sqrt(sq(chain.x(i + 1) - chain.x(i)) + sq(chain.y(i + 1) - chain.y(i)) + sq(chain.z(i + 1) - chain.z(i)));
        return sum;
    }

    private static double sq(double d) { return d * d; }

    /** Hitbox parts whose centre lies in a block with a collision shape (loaded chunks only). */
    public int partsInSolid()
    {
        int n = 0;
        for (GiantPart part : parts)
        {
            BlockPos pos = BlockPos.containing(part.getX(), part.getY() + part.getBbHeight() * 0.5, part.getZ());
            if (solidAt(pos)) n++;
        }
        return n;
    }

    /** Chain joints in a block with a collision shape. */
    public int jointsInSolid()
    {
        int n = 0;
        for (int i = 0; i < chain.size(); i++) if (solidAt(BlockPos.containing(chain.x(i), chain.y(i), chain.z(i)))) n++;
        return n;
    }

    private boolean solidAt(BlockPos pos)
    {
        return level().hasChunkAt(pos) && !level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty();
    }

    // ---- damage

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide)
        {
            ability.onHurt(source, amount);
            brain.onHurt(source.getEntity(), level().getGameTime());
        }
        return hit;
    }

    /** A hit on a body part counts like a hit on the head. */
    public boolean hurtFromPart(GiantPart part, DamageSource source, float amount)
    {
        return hurt(source, amount);
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
        if (!chain.isInitialised()) return super.getBoundingBoxForCulling();
        double side = Math.max(2.5 * spec.radius(), spec.cullSide());
        return chain.bounds(side).expandTowards(0, -spec.cullDown(), 0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance)
    {
        double max = 128 + spec.length() + spec.radius();
        return distance < max * max;
    }

    @Override
    public void travel(Vec3 input) {}

    @Override
    protected void pushEntities() {}

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isPushedByFluid() { return false; }

    @Override
    public boolean canDrownInFluidType(net.minecraftforge.fluids.FluidType type) { return false; }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    /** Not persisted: a giant simply vanishes when its chunk unloads. */
    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean canBeLeashed(Player player) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (home != null)
        {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
        if (lastPlayerSeen >= 0) tag.putLong("LastPlayerSeen", lastPlayerSeen);
        tag.putLong("CalmUntil", brain.calmUntil());
        ability.save(tag);
        chain.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        if (tag.contains("LastPlayerSeen")) lastPlayerSeen = tag.getLong("LastPlayerSeen");
        brain.setCalmUntil(tag.getLong("CalmUntil"));
        ability.load(tag);
        chain.load(tag);
        if (chain.isInitialised())
        {
            Vec3 fwd = new Vec3(chain.x(0) - chain.x(1), chain.y(0) - chain.y(1), chain.z(0) - chain.z(1));
            if (fwd.lengthSqr() > 1.0E-6) vel = fwd.normalize().scale(spec.speed());
        }
    }
}
