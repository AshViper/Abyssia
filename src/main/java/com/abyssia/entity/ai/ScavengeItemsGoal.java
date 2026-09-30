package com.abyssia.entity.ai;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Walks to food lying on the seabed (an item tag) and eats a piece of it. */
public class ScavengeItemsGoal extends Goal
{
    private final PathfinderMob mob;
    private final TagKey<Item> food;
    private final double range;
    private final double speed;
    private final BooleanSupplier ready;
    private final Consumer<ItemEntity> eat;
    @Nullable
    private ItemEntity item;
    private int ticks;

    public ScavengeItemsGoal(PathfinderMob mob, TagKey<Item> food, double range, double speed, BooleanSupplier ready, Consumer<ItemEntity> eat)
    {
        this.mob = mob;
        this.food = food;
        this.range = range;
        this.speed = speed;
        this.ready = ready;
        this.eat = eat;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (this.mob.getRandom().nextInt(10) != 0 || !this.ready.getAsBoolean()) return false;
        this.item = this.mob.level().getEntitiesOfClass(ItemEntity.class, this.mob.getBoundingBox().inflate(this.range, 4.0, this.range),
                        e -> e.isAlive() && e.getItem().is(this.food))
                .stream().min(Comparator.comparingDouble(this.mob::distanceToSqr)).orElse(null);
        return this.item != null;
    }

    @Override
    public void start()
    {
        this.ticks = 0;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.item != null && this.item.isAlive() && this.ticks < 400 && this.ready.getAsBoolean();
    }

    @Override
    public void tick()
    {
        if (this.item == null) return;
        if (this.ticks++ % 10 == 0) this.mob.getNavigation().moveTo(this.item, this.speed);
        this.mob.getLookControl().setLookAt(this.item, 30.0F, 30.0F);
        if (this.mob.distanceToSqr(this.item) < 1.1 * 1.1)
        {
            ItemEntity food = this.item;
            this.item = null;
            this.eat.accept(food);
            food.getItem().shrink(1);
            if (food.getItem().isEmpty()) food.discard();
        }
    }

    @Override
    public void stop()
    {
        this.item = null;
        this.mob.getNavigation().stop();
    }
}
