package com.abyssia.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Client: one running loop per working machine / generator (sounds from tools/machine_sounds.py). Called from the
 * block entity's client ticker each tick; a loop starts when the machine works and nothing plays at that position, and
 * fades out by itself once {@code running} turns false, the block entity goes away, or the player leaves the range.
 * Only call on the logical client.
 */
public final class MachineSounds
{
    /** beyond this the loop is not started (and a playing one stops); the sound itself fades out by ~16 blocks */
    private static final double RANGE = 24.0;
    private static final Map<BlockPos, Loop> PLAYING = new HashMap<>();

    private MachineSounds() {}

    /**
     * @param running checked every tick by the playing loop; false fades it out
     */
    public static void tick(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch, BiPredicate<Level, BlockPos> running)
    {
        if (!running.test(level, pos)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > RANGE * RANGE) return;
        Loop loop = PLAYING.get(pos);
        if (loop != null && !loop.isStopped() && mc.getSoundManager().isActive(loop) && loop.level == level && loop.getLocation().equals(sound.getLocation())) return;
        if (loop != null) mc.getSoundManager().stop(loop);
        BlockPos key = pos.immutable();
        loop = new Loop(level, key, sound, volume, pitch, running);
        PLAYING.put(key, loop);
        mc.getSoundManager().play(loop);
    }

    private static final class Loop extends AbstractTickableSoundInstance
    {
        private static final int FADE = 10;
        private final Level level;
        private final BlockPos pos;
        private final float baseVolume;
        private final BiPredicate<Level, BlockPos> running;
        private int fade;
        private boolean ending;

        Loop(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch, BiPredicate<Level, BlockPos> running)
        {
            super(sound, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.level = level;
            this.pos = pos;
            this.baseVolume = volume;
            this.running = running;
            this.looping = true;
            this.delay = 0;
            this.pitch = pitch;
            this.volume = 0.01f;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
        }

        @Override
        public void tick()
        {
            Minecraft mc = Minecraft.getInstance();
            // re-evaluated every tick, so a machine that restarts during the fade-out just fades back in
            ending = mc.level != level || mc.player == null || !level.isLoaded(pos) || !running.test(level, pos)
                    || mc.player.distanceToSqr(x, y, z) > RANGE * RANGE;
            fade = ending ? fade - 1 : Math.min(FADE, fade + 1);
            volume = Math.max(0.01f, baseVolume * fade / FADE);
            if (ending && fade <= 0)
            {
                stop();
                PLAYING.remove(pos, this);
            }
        }

        @Override
        public boolean canStartSilent()
        {
            return true;
        }
    }
}
