package com.abyssia.entity;

import com.abyssia.entity.ai.HomeLayer;
import com.abyssia.entity.ai.SeabedRandomPos;
import com.abyssia.fauna.CarrionScent;
import com.abyssia.registry.ModSounds;
import com.abyssia.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * ダイオウグソクムシ: the giant isopod Bathynomus giganteus, a slow scavenger of the continental slope.
 * <p>
 * It walks the seabed (it does not swim here), keeps close to rock, and spends long spells motionless. Carrion
 * draws it from far off: food items lying on the seabed and the scent of animals that died nearby. When food turns
 * up it gorges, then rests for minutes (the real animal can fast for years). Hurt or crowded, it curls up behind its
 * calcareous armour. It never attacks.
 */
public class GiantIsopod extends WaterAnimal implements FaunaAnimated, HomeLayer.Bound
{
    private final HomeLayer homeLayer = new HomeLayer();
    private static final int DEATH_TICKS = 44;

    private static final EntityDataAccessor<Boolean> CURLED = SynchedEntityData.defineId(GiantIsopod.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FEEDING = SynchedEntityData.defineId(GiantIsopod.class, EntityDataSerializers.BOOLEAN);
    private static final ResourceLocation CURL_ARMOR = ResourceLocation.fromNamespaceAndPath("abyssia", "curled_up");
    private static final float CURLED_DAMAGE = 0.35F;

    private int curlTicks;
    private int restTicks;
    private int shyTicks;
    private int meals;
    @Nullable
    private Vec3 disturbedFrom;

    // client side: the keyframe clips, whether the curl is shown
    private final FaunaAnimations animations = new FaunaAnimations(this);
    private boolean shownCurled;

    public GiantIsopod(EntityType<? extends GiantIsopod> type, Level level)
    {
        super(type, level);
        this.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1.0);
        this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.ARMOR, 6.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4).add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(CURLED, false);
        builder.define(FEEDING, false);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new CurlGoal());
        this.goalSelector.addGoal(1, new ScavengeGoal());
        this.goalSelector.addGoal(2, new RestGoal());
        this.goalSelector.addGoal(3, new ShelterStrollGoal());
    }

    public boolean isCurled()
    {
        return this.entityData.get(CURLED);
    }

    public boolean isFeeding()
    {
        return this.entityData.get(FEEDING);
    }

    @Override
    public FaunaAnimations animations()
    {
        return this.animations;
    }

    @Override
    public HomeLayer homeLayer()
    {
        return this.homeLayer;
    }

    /** Long enough for the model's own death clip (vanilla removes the body after 20 ticks). */
    @Override
    protected void tickDeath()
    {
        ++this.deathTime;
        if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide() && !this.isRemoved())
        {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void handleDamageEvent(DamageSource source)
    {
        super.handleDamageEvent(source);
        this.animations().hurt();
    }

    /** Resting (motionless, antennae still sweeping) - the model slows its idle motion. */
    public boolean isResting()
    {
        return this.restTicks > 0;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            boolean curled = this.isCurled();
            this.animations.tick("walk", !curled && this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4);
            if (curled && !this.shownCurled) this.animations.play("curl", 0);
            if (!curled && this.shownCurled)
            {
                this.animations.stop("curl");
                this.animations.play("uncurl", 14);
            }
            this.shownCurled = curled;
            return;
        }
        if (this.restTicks > 0) --this.restTicks;
        // disturbed by someone crowding it: roll up now and then
        if (this.tickCount % 10 == 0 && !this.isCurled())
        {
            Player near = this.level().getNearestPlayer(this, 1.6);
            this.shyTicks = near != null && !near.isSpectator() ? this.shyTicks + 10 : Math.max(0, this.shyTicks - 10);
            if (near != null && this.shyTicks > 40 && this.random.nextInt(3) == 0)
            {
                this.curlUp(60 + this.random.nextInt(60), near.position());
                this.shyTicks = 0;
            }
        }
    }

    private void curlUp(int ticks, @Nullable Vec3 from)
    {
        if (!this.isCurled()) this.playSound(ModSounds.GIANT_ISOPOD_CURL.get(), 0.7F, 0.9F + this.random.nextFloat() * 0.2F);
        this.curlTicks = Math.max(this.curlTicks, ticks);
        this.disturbedFrom = from;
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        // the rolled-up armour turns most of a blow
        if (this.isCurled()) amount *= CURLED_DAMAGE;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive())
        {
            Entity attacker = source.getEntity();
            this.curlUp(80 + this.random.nextInt(60), attacker != null ? attacker.position() : null);
            this.restTicks = 0;
        }
        return hurt;
    }

    /** Walks at its land pace on the seabed (see BenthicWalker.SEABED_STRIDE: the swim thrust alone barely moves it). */
    @Override
    public void travel(Vec3 input)
    {
        super.travel(this.isInWater() && this.onGround() ? input.scale(BenthicWalker.SEABED_STRIDE) : input);
    }

    /**
     * A walker, not a swimmer: in water it only pushes itself up while clambering against rock (ledges and outcrops),
     * never floating off the seabed after something above it.
     */
    @Override
    public void jumpInFluid(FluidType type)
    {
        if (this.horizontalCollision) super.jumpInFluid(type);
    }

    @Override
    protected void jumpInLiquid(TagKey<Fluid> fluid)
    {
        if (this.horizontalCollision) super.jumpInLiquid(fluid);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        this.homeLayer.save(tag);
        tag.putInt("RestTicks", this.restTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.homeLayer.load(tag);
        this.restTicks = tag.getInt("RestTicks");
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected SoundEvent getAmbientSound()
    {
        return this.isCurled() ? null : ModSounds.GIANT_ISOPOD_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 300;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.GIANT_ISOPOD_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.GIANT_ISOPOD_DEATH.get();
    }

    /** Walking the seabed plays through the swim-sound path (the game treats any movement in water as swimming). */
    @Override
    protected SoundEvent getSwimSound()
    {
        return ModSounds.GIANT_ISOPOD_STEP.get();
    }

    @Override
    protected void playSwimSound(float volume)
    {
        this.playSound(this.getSwimSound(), 0.12F, 0.9F + this.random.nextFloat() * 0.3F);
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.5F;
    }

    // ---------------------------------------------------------------- behaviour

    /** Rolled up: no movement, armour doubled and knockback shrugged off; afterwards it walks away from the trouble. */
    private class CurlGoal extends Goal
    {
        CurlGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public boolean canUse()
        {
            return GiantIsopod.this.curlTicks > 0;
        }

        @Override
        public void start()
        {
            GiantIsopod self = GiantIsopod.this;
            self.getNavigation().stop();
            self.entityData.set(CURLED, true);
            self.entityData.set(FEEDING, false);
            AttributeInstance armor = self.getAttribute(Attributes.ARMOR);
            if (armor != null && armor.getModifier(CURL_ARMOR) == null)
            {
                armor.addTransientModifier(new AttributeModifier(CURL_ARMOR, 6.0, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        @Override
        public void tick()
        {
            --GiantIsopod.this.curlTicks;
        }

        @Override
        public void stop()
        {
            GiantIsopod self = GiantIsopod.this;
            self.curlTicks = 0;
            self.entityData.set(CURLED, false);
            AttributeInstance armor = self.getAttribute(Attributes.ARMOR);
            if (armor != null) armor.removeModifier(CURL_ARMOR);
            if (self.disturbedFrom != null)
            {
                Vec3 away = SeabedRandomPos.getPosAway(self, 8, 3, self.disturbedFrom);
                if (away != null) self.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
                self.disturbedFrom = null;
            }
        }
    }

    /** Chemoreception: carrion items on the seabed, or the scent of a recent death, draw it in to feed. */
    private class ScavengeGoal extends Goal
    {
        @Nullable
        private ItemEntity item;
        @Nullable
        private CarrionScent.Scent scent;
        private int feeding, ticks;

        ScavengeGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public boolean canUse()
        {
            GiantIsopod self = GiantIsopod.this;
            if (self.isCurled() || (self.restTicks > 0 && self.meals > 0) || self.random.nextInt(10) != 0
                    || !(self.level() instanceof ServerLevel level)) return false;
            List<ItemEntity> food = level.getEntitiesOfClass(ItemEntity.class, self.getBoundingBox().inflate(16, 4, 16),
                    e -> e.isAlive() && e.onGround() && e.getItem().is(ModTags.ISOPOD_FOOD));
            ItemEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (ItemEntity e : food)
            {
                double d = e.distanceToSqr(self);
                if (d < best)
                {
                    best = d;
                    nearest = e;
                }
            }
            this.item = nearest;
            this.scent = nearest == null ? CarrionScent.nearest(level, self.blockPosition(), 24) : null;
            return this.item != null || this.scent != null;
        }

        @Override
        public void start()
        {
            this.feeding = 0;
            this.ticks = 0;
            GiantIsopod.this.restTicks = 0;
            this.approach();
        }

        private Vec3 target()
        {
            return this.item != null ? this.item.position() : Vec3.atBottomCenterOf(this.scent.pos);
        }

        private void approach()
        {
            Vec3 t = this.target();
            GiantIsopod.this.getNavigation().moveTo(t.x, t.y, t.z, 1.25);
        }

        @Override
        public boolean canContinueToUse()
        {
            if (GiantIsopod.this.isCurled() || this.ticks > 1200) return false;
            return this.item != null ? this.item.isAlive() : this.scent != null && this.scent.isLeft();
        }

        @Override
        public void tick()
        {
            // ticked between canContinueToUse checks too: the food may be finished already
            if (this.item == null && this.scent == null) return;
            GiantIsopod self = GiantIsopod.this;
            ++this.ticks;
            Vec3 t = this.target();
            self.getLookControl().setLookAt(t.x, t.y, t.z, 20.0F, 20.0F);
            double reach = this.item != null ? 1.1 : 1.6;
            if (self.position().distanceToSqr(t) > reach * reach)
            {
                self.entityData.set(FEEDING, false);
                if (this.ticks % 20 == 0 || self.getNavigation().isDone()) this.approach();
                return;
            }
            self.getNavigation().stop();
            self.entityData.set(FEEDING, true);
            if (++this.feeding % 12 == 0)
            {
                self.playSound(ModSounds.GIANT_ISOPOD_EAT.get(), 0.6F, 0.8F + self.random.nextFloat() * 0.4F);
                if (this.item != null && self.level() instanceof ServerLevel level)
                {
                    level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, this.item.getItem()), t.x, t.y + 0.1, t.z, 4, 0.1, 0.05, 0.1, 0.03);
                }
            }
            if (this.feeding >= (this.item != null ? 50 : 80))
            {
                if (this.item != null)
                {
                    ItemStack stack = this.item.getItem();
                    stack.shrink(1);
                    if (stack.isEmpty()) this.item.discard();
                    else this.item.setItem(stack);
                }
                else
                {
                    this.scent.consume();
                }
                self.heal(3.0F);
                self.gameEvent(GameEvent.EAT);
                this.feeding = 0;
                // gorge on what is there, then rest for minutes
                if (++self.meals >= 3 || (this.item != null && !this.item.isAlive()) || (this.scent != null && !this.scent.isLeft()))
                {
                    self.restTicks = 2400 + self.random.nextInt(2400);
                    this.item = null;
                    this.scent = null;
                }
            }
        }

        @Override
        public void stop()
        {
            GiantIsopod.this.entityData.set(FEEDING, false);
            GiantIsopod.this.getNavigation().stop();
            if (GiantIsopod.this.restTicks == 0) GiantIsopod.this.meals = 0;
            this.item = null;
            this.scent = null;
        }
    }

    /** Long motionless spells, longer still after a meal. */
    private class RestGoal extends Goal
    {
        RestGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            GiantIsopod self = GiantIsopod.this;
            if (self.isCurled()) return false;
            if (self.restTicks == 0 && self.random.nextInt(240) == 0) self.restTicks = 300 + self.random.nextInt(900);
            return self.restTicks > 0;
        }

        @Override
        public void start()
        {
            GiantIsopod.this.getNavigation().stop();
        }

        @Override
        public boolean canContinueToUse()
        {
            return GiantIsopod.this.restTicks > 0 && !GiantIsopod.this.isCurled();
        }

        @Override
        public void stop()
        {
            GiantIsopod.this.meals = 0;
        }
    }

    /** Wanders the seabed, choosing spots sheltered by rock (and dark) over open ground. */
    private class ShelterStrollGoal extends Goal
    {
        private int ticks;

        ShelterStrollGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public boolean canUse()
        {
            GiantIsopod self = GiantIsopod.this;
            if (self.isCurled() || self.restTicks > 0 || self.random.nextInt(60) != 0) return false;
            Vec3 best = null;
            double bestScore = -1;
            for (int i = 0; i < 5; i++)
            {
                Vec3 p = SeabedRandomPos.getPos(self, 10, 4);
                if (p == null) continue;
                double score = shelter(self.level(), BlockPos.containing(p)) + self.random.nextDouble() * 2;
                if (score > bestScore)
                {
                    best = p;
                    bestScore = score;
                }
            }
            if (best == null) return false;
            this.ticks = 0;
            return self.getNavigation().moveTo(best.x, best.y, best.z, 1.0);
        }

        @Override
        public boolean canContinueToUse()
        {
            return !GiantIsopod.this.getNavigation().isDone() && ++this.ticks < 400 && !GiantIsopod.this.isCurled();
        }
    }

    /** How sheltered a seabed spot is: rock around it at body height and overhead, and darkness. */
    public static double shelter(Level level, BlockPos pos)
    {
        int rock = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx += 2)
        {
            for (int dz = -2; dz <= 2; dz += 2)
            {
                for (int dy = 0; dy <= 2; dy++)
                {
                    if (dx == 0 && dz == 0 && dy < 2) continue;
                    if (level.getBlockState(p.setWithOffset(pos, dx, dy, dz)).is(ModTags.FAUNA_ROCK)) rock++;
                }
            }
        }
        return rock - level.getMaxLocalRawBrightness(pos) * 0.3;
    }
}
