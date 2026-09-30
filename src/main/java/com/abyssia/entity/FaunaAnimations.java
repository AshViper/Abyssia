package com.abyssia.entity;

import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * The named keyframe clips of a fauna model (from tools/bbmodel-generator: "idle", "swim", "walk", "mouth_open",
 * "curl", ...) and which of them play right now. Owned by the entity and driven from its client tick; the model
 * plays every running clip by name. Clips the model does not have are simply never looked up.
 */
public final class FaunaAnimations
{
    private final LivingEntity entity;
    private final Map<String, AnimationState> states = new HashMap<>();
    private final Map<String, Integer> stopAt = new HashMap<>();

    public FaunaAnimations(LivingEntity entity)
    {
        this.entity = entity;
    }

    @Nullable
    public AnimationState get(String clip)
    {
        return this.states.get(clip);
    }

    private AnimationState state(String clip)
    {
        return this.states.computeIfAbsent(clip, c -> new AnimationState());
    }

    /** Runs a looping (or held) clip while {@code on}; stops it otherwise. */
    public void when(String clip, boolean on)
    {
        if (on) this.state(clip).startIfStopped(this.entity.tickCount);
        else if (this.states.containsKey(clip)) this.states.get(clip).stop();
    }

    /** (Re)starts a clip; with {@code ticks} > 0 it stops by itself after that many ticks (one-shot clips). */
    public void play(String clip, int ticks)
    {
        this.state(clip).start(this.entity.tickCount);
        if (ticks > 0) this.stopAt.put(clip, this.entity.tickCount + ticks);
        else this.stopAt.remove(clip);
    }

    public void stop(String clip)
    {
        if (this.states.containsKey(clip)) this.states.get(clip).stop();
        this.stopAt.remove(clip);
    }

    public boolean playing(String clip)
    {
        AnimationState s = this.states.get(clip);
        return s != null && s.isStarted();
    }

    /**
     * Common per-tick bookkeeping: ends one-shot clips, idles or moves ({@code move} is the locomotion clip, "swim" or
     * "walk"), holds the death clip while dying. Hurt is started from {@link #hurt()}.
     */
    public void tick(String move, boolean moving)
    {
        this.stopAt.entrySet().removeIf(e -> {
            if (this.entity.tickCount < e.getValue()) return false;
            AnimationState s = this.states.get(e.getKey());
            if (s != null) s.stop();
            return true;
        });
        boolean dying = this.entity.isDeadOrDying();
        this.when(move, moving && !dying);
        this.when("idle", !moving && !dying);
        this.when("death", dying);
    }

    /** {@link #tick(String, boolean)} for animals with several gaits: {@code motion} (one of {@code gaits}, null = idle) runs, the others stop. */
    public void tick(@Nullable String motion, String... gaits)
    {
        this.tick(motion != null ? motion : gaits[0], motion != null);
        for (String gait : gaits)
        {
            if (!gait.equals(motion)) this.when(gait, false);
        }
    }

    public void hurt()
    {
        this.play("hurt", 10);
    }
}
