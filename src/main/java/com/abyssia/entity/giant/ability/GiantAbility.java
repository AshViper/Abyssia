package com.abyssia.entity.giant.ability;

import com.abyssia.entity.giant.GiantBrain;
import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.vehicle.Submarine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The species-specific ability of a giant (GNT01): small hooks that {@link GiantBrain} and {@link GiantFauna} call along the
 * normal state course (WANDER, WARN, CHASE, RETREAT). The base class changes nothing; each species overrides what it needs.
 * All hooks run on the server, except {@link #interact} (both sides).
 */
public class GiantAbility
{
    protected final GiantFauna g;

    protected GiantAbility(GiantFauna g)
    {
        this.g = g;
    }

    public static GiantAbility create(GiantFauna g)
    {
        return switch (g.spec().id())
        {
            case "lancejaw" -> new LancejawAbility(g);
            case "lumenwing" -> new LumenwingAbility(g);
            case "lantern_grazer" -> new LanternGrazerAbility(g);
            case "hush_whale" -> new HushWhaleAbility(g);
            case "slateback" -> new SlatebackAbility(g);
            case "rift_lamprey" -> new RiftLampreyAbility(g);
            case "shroud_ray" -> new ShroudRayAbility(g);
            case "glassback" -> new GlassbackAbility(g);
            case "crestfin" -> new CrestfinAbility(g);
            case "duskmaw" -> new DuskmawAbility(g);
            default -> new GiantAbility(g);
        };
    }

    // ---- brain course

    public int warnTicks() { return GiantBrain.WARN_TICKS; }

    public int chaseMax() { return GiantBrain.CHASE_MAX; }

    /** How far away this animal notices the target (players and submarines). */
    public double detect(Entity target) { return GiantBrain.DETECT; }

    /** The largest value {@link #detect} returns (the search box). */
    public double maxDetect() { return GiantBrain.DETECT; }

    /** Whether the species' hold animation plays in this state (default: while chasing). */
    public boolean holding(GiantBrain.State state) { return state == GiantBrain.State.CHASE; }

    public void onState(GiantBrain.State from, GiantBrain.State to, @Nullable Entity target) {}

    /** Every server tick, after the brain. */
    public void tick(long now, GiantBrain.State state, @Nullable Entity target) {}

    /** Multiplies the brain's speed factor. */
    public double speedMul(GiantBrain.State state, int stateTicks, @Nullable Entity target) { return 1.0; }

    public double turnRate(GiantBrain.State state, double def) { return def; }

    /** May change the wanted swimming direction (unit vector). */
    public Vec3 steer(GiantBrain.State state, Vec3 head, @Nullable Entity target, Vec3 dir) { return dir; }

    /** When not null the head is put here instead of swimming (a lamprey on a hull). */
    @Nullable
    public Vec3 anchor() { return null; }

    public boolean canAttack(int stateTicks) { return true; }

    /** Replaces the brain's reach test when not null. */
    @Nullable
    public Boolean contact(Entity target) { return null; }

    /** Strength of the knockback of an attack (0 = none). */
    public double knockback() { return 0; }

    public void onHit(Entity target) {}

    // ---- damage and use

    public void onHurt(DamageSource source, float amount) {}

    public InteractionResult interact(Player player, InteractionHand hand) { return InteractionResult.PASS; }

    public void save(CompoundTag tag) {}

    public void load(CompoundTag tag) {}

    // ---- helpers

    protected static Vec3 center(Entity e) { return e.getBoundingBox().getCenter(); }

    protected static double distToBox(Vec3 p, AABB b)
    {
        double dx = Math.max(Math.max(b.minX - p.x, 0), p.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - p.y, 0), p.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - p.z, 0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Left of a submarine, horizontal (as in the submarine's own drive). */
    protected static Vec3 left(Entity sub)
    {
        float yaw = sub.getYRot() * (float) (Math.PI / 180.0);
        return new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
    }

    @Nullable
    protected Submarine sub(@Nullable UUID id)
    {
        if (id == null || !(g.level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(id) instanceof Submarine s && s.isAlive() ? s : null;
    }

    /** An item of this mod by path, or null while it is not registered. */
    @Nullable
    protected static Item item(String path)
    {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("abyssia", path)).orElse(null);
    }
}
