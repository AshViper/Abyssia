package com.abyssia.entity.boss;

import com.abyssia.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Altar state: the boss UUID, when it was summoned, when a player was last within {@link #LEAVE_RANGE}, and the 5 second
 * warm-up of a summon in progress. Everything is saved, so the retreat rule holds across restarts: when no player has been
 * within 512 blocks for 60 seconds, or the boss has been missing (unloaded or gone) for 60 seconds, the boss is removed and
 * one lure is dropped at the altar. A defeated boss clears the altar (no lure back).
 */
public class AbyssalAltarBlockEntity extends BlockEntity
{
    public static final int WARMUP_TICKS = 100;
    public static final double SPAWN_DISTANCE = 80;
    /** Arena radius around the altar (the spawn point at 80 blocks lies inside it). */
    public static final double ARENA_RADIUS = 110;
    /** Same as the LOD distance: the boss stays while someone could still see it. */
    public static final double LEAVE_RANGE = BossFarPayload.RANGE;
    public static final long LEAVE_TICKS = 1200;

    @Nullable
    private UUID bossId;
    private long summonedAt;
    private long lastPlayerSeen;
    private long missingSince = -1;
    private int warmup;
    /** Lures given back (retreat, failed spawn). Kept in the altar: a dropped item floats away in the hall's water. */
    private final int[] storedLures = new int[BossContent.KINDS];
    /** Boss kind of the summon in progress or the boss out (a {@link BossContent} KIND_ constant, decided by the lure). */
    private int kind;

    public AbyssalAltarBlockEntity(BlockPos pos, BlockState state)
    {
        super(BossContent.ALTAR_ENTITY.get(), pos, state);
    }

    /** True while a summon is warming up or a boss is out. */
    public boolean busy()
    {
        return warmup > 0 || bossId != null;
    }

    public boolean owns(UUID boss)
    {
        return boss.equals(bossId);
    }

    public void startSummon(ServerLevel level, int kind)
    {
        this.kind = kind;
        warmup = WARMUP_TICKS;
        bossId = null;
        summonedAt = level.getGameTime();
        lastPlayerSeen = summonedAt;
        setChanged();
        Vec3 c = getBlockPos().getCenter();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 4.0F, 0.5F);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AbyssalAltarBlockEntity be)
    {
        if (!(level instanceof ServerLevel sl)) return;
        if (be.warmup > 0)
        {
            be.warmupTick(sl);
            return;
        }
        if (be.bossId == null || sl.getGameTime() % 20 != 0) return;
        be.watchTick(sl);
    }

    private void warmupTick(ServerLevel level)
    {
        Vec3 c = getBlockPos().getCenter();
        warmup--;
        int t = WARMUP_TICKS - warmup;
        level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + 1.0, c.z, 2, 1.2, 0.8, 1.2, 0.02);
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, c.x, c.y + 0.6, c.z, 6, 0.6, 0.2, 0.6, 0.0);
        if (t % 20 == 0) level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 6.0F, 0.6F + t * 0.004F);
        if (warmup > 0) return;
        setChanged();
        if (!spawnBoss(level))
        {
            // no free point at all (should be rare): give the lure back
            storedLures[kind]++;
        }
    }

    private boolean spawnBoss(ServerLevel level)
    {
        Vec3 c = getBlockPos().getCenter();
        boolean wyrm = kind == BossContent.KIND_WYRM;
        AbyssBoss boss = wyrm ? ModEntities.TRENCH_WYRM.get().create(level)
                : kind == BossContent.KIND_MOTHER ? ModEntities.ABYSS_MOTHER.get().create(level)
                : kind == BossContent.KIND_KRAKEN ? ModEntities.ABYSS_KRAKEN.get().create(level) : ModEntities.ABYSS_SERPENT.get().create(level);
        if (boss == null) return false;
        double h = boss.getBbHeight();
        double w = boss.getBbWidth();
        for (int attempt = 0; attempt < 24; attempt++)
        {
            double dist = SPAWN_DISTANCE - (attempt / 6) * 15.0;
            double ang = level.random.nextDouble() * Math.PI * 2;
            double x = c.x + Math.cos(ang) * dist, z = c.z + Math.sin(ang) * dist;
            // the wyrm starts inside the ground (it burrows), the serpent in open water
            double y = wyrm ? c.y - 3 : c.y + 4 + level.random.nextDouble() * 20;
            AABB box = new AABB(x - w / 2, y - h / 2, z - w / 2, x + w / 2, y + h / 2, z + w / 2);
            if (!level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) continue;
            if (!wyrm && !level.noCollision(box)) continue;
            boss.setPos(x, y - h / 2, z);
            boss.bindAltar(getBlockPos(), c, ARENA_RADIUS, new Vec3(c.x - x, 0, c.z - z).normalize().scale(0.8));
            if (!level.addFreshEntity(boss)) return false;
            bossId = boss.getUUID();
            summonedAt = level.getGameTime();
            lastPlayerSeen = summonedAt;
            missingSince = -1;
            level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 10.0F, 0.5F);
            return true;
        }
        return false;
    }

    private void watchTick(ServerLevel level)
    {
        long now = level.getGameTime();
        Vec3 c = getBlockPos().getCenter();
        double r2 = LEAVE_RANGE * LEAVE_RANGE;
        for (ServerPlayer p : level.players())
            if (!p.isSpectator() && p.distanceToSqr(c) < r2)
            {
                lastPlayerSeen = now;
                break;
            }
        Entity boss = level.getEntity(bossId);
        if (boss != null && boss.isAlive())
        {
            missingSince = -1;
        }
        else if (boss != null)
        {
            return; // dying: bossDefeated() clears the altar
        }
        else if (missingSince < 0)
        {
            missingSince = now;
            setChanged();
        }
        boolean gone = missingSince >= 0 && now - missingSince > LEAVE_TICKS;
        boolean abandoned = now - lastPlayerSeen > LEAVE_TICKS;
        if (gone || abandoned) retreat(level, false);
    }

    /** Removes the boss (if it is loaded), clears the altar and returns one lure: kept in the altar, or dropped when the altar is broken. */
    public void retreat(ServerLevel level, boolean altarBroken)
    {
        if (altarBroken)
            for (int k = 0; k < storedLures.length; k++)
                if (storedLures[k] > 0)
                {
                    Block.popResource(level, getBlockPos(), new ItemStack(BossContent.lureItem(k), storedLures[k]));
                    storedLures[k] = 0;
                }
        if (!busy()) return;
        if (bossId != null)
        {
            Entity boss = level.getEntity(bossId);
            if (boss != null) boss.discard();
        }
        clear();
        if (altarBroken) Block.popResource(level, getBlockPos(), new ItemStack(BossContent.lureItem(kind)));
        else storedLures[kind]++;
    }

    /** Hands the stored lures to the player; false when there are none. */
    public boolean giveStoredLures(net.minecraft.world.entity.player.Player player)
    {
        boolean any = false;
        for (int k = 0; k < storedLures.length; k++)
        {
            if (storedLures[k] <= 0) continue;
            ItemStack stack = new ItemStack(BossContent.lureItem(k), storedLures[k]);
            storedLures[k] = 0;
            any = true;
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        if (any) setChanged();
        return any;
    }

    private void clear()
    {
        bossId = null;
        warmup = 0;
        missingSince = -1;
        setChanged();
    }

    /** Called from the boss's death: the fight is over, nothing is returned. */
    public static void bossDefeated(ServerLevel level, BlockPos altar, UUID boss)
    {
        if (level.getBlockEntity(altar) instanceof AbyssalAltarBlockEntity be && boss.equals(be.bossId)) be.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        if (bossId != null) tag.putUUID("Boss", bossId);
        tag.putLong("SummonedAt", summonedAt);
        tag.putLong("LastPlayerSeen", lastPlayerSeen);
        tag.putLong("MissingSince", missingSince);
        tag.putInt("Warmup", warmup);
        tag.putInt("StoredLures", storedLures[BossContent.KIND_SERPENT]);
        tag.putInt("StoredWyrmLures", storedLures[BossContent.KIND_WYRM]);
        tag.putInt("StoredKrakenLures", storedLures[BossContent.KIND_KRAKEN]);
        tag.putInt("StoredMotherLures", storedLures[BossContent.KIND_MOTHER]);
        tag.putInt("Kind", kind);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        bossId = tag.hasUUID("Boss") ? tag.getUUID("Boss") : null;
        summonedAt = tag.getLong("SummonedAt");
        lastPlayerSeen = tag.getLong("LastPlayerSeen");
        missingSince = tag.contains("MissingSince") ? tag.getLong("MissingSince") : -1;
        warmup = tag.getInt("Warmup");
        storedLures[BossContent.KIND_SERPENT] = tag.getInt("StoredLures");
        storedLures[BossContent.KIND_WYRM] = tag.getInt("StoredWyrmLures");
        storedLures[BossContent.KIND_KRAKEN] = tag.getInt("StoredKrakenLures");
        storedLures[BossContent.KIND_MOTHER] = tag.getInt("StoredMotherLures");
        kind = Mth.clamp(tag.getInt("Kind"), 0, BossContent.KINDS - 1);
    }
}
