package com.abyssia.client.light;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A light that shines in a cone this frame, drawn on this client only: onto the blocks by {@link SpotlightProjector}
 * (with shadows) and as a glow plus a faint cone of light in the water by {@link SpotlightBeams}.
 *
 * @param origin     where it is in the level
 * @param direction  which way it points, normalized
 * @param range      how far it reaches, in blocks
 * @param halfAngle  between the middle of the cone and its edge, in radians
 * @param color      0xRRGGBB
 * @param brightness 1 = a full-strength lamp
 * @param beamLength how far the visible cone in the water reaches at most, in blocks (0 = none)
 * @param beamRadius radius of the lamp's face where the visible cone starts, in blocks
 * @param owner      ignored when tracing the visible cone (the lamp's own vehicle), may be null
 */
public record Spotlight(Vec3 origin, Vec3 direction, float range, float halfAngle, int color, float brightness,
                        float beamLength, float beamRadius, @Nullable Entity owner)
{
    /** Adds the lights that are on this frame. Registered with {@link SpotlightProjector#addSource}. */
    @FunctionalInterface
    public interface Source
    {
        void collect(ClientLevel level, float partialTick, Consumer<Spotlight> out);
    }
}
