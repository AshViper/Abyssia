package com.abyssia.vehicle.client;

import com.abyssia.client.light.Spotlight;
import com.abyssia.vehicle.Submarine;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * The submarine's two headlights as {@link Spotlight}s: one at each lamp of {@link SubmarineMesh#LAMPS} (mesh space,
 * front = -Z, turned and pitched like {@link SubmarineRenderer}), pointing along the hull and slightly down.
 * Purely client side: every client shines the lamps of every submarine whose headlights are on.
 */
public final class SubmarineLamps
{
    // lamp faces in mesh space (centres of the LAMPS quads, a little in front of the face)
    private static final float LAMP_X = 1.188f, LAMP_Y = 1.242f, LAMP_Z = -0.80f;
    private static final float TILT_DOWN = 6.0f * Mth.DEG_TO_RAD;
    private static final float RANGE = 36.0f;
    private static final float HALF_ANGLE = 24.0f * Mth.DEG_TO_RAD;
    // a cold, slightly blue white
    private static final int COLOR = 0xE4F0FF;
    private static final float BEAM_LENGTH = 18.0f;
    private static final float BEAM_RADIUS = 0.17f;

    private SubmarineLamps() {}

    public static void collect(ClientLevel level, float partialTick, Consumer<Spotlight> out)
    {
        for (Entity entity : level.entitiesForRendering())
            if (entity instanceof Submarine sub && sub.lights() && sub.isAlive()) lamps(sub, partialTick, out);
    }

    private static void lamps(Submarine sub, float partialTick, Consumer<Spotlight> out)
    {
        Vec3 position = sub.getPosition(partialTick);
        // the renderer's pose: yaw, then the SUB05 pitch about the hull mid-height (xRot + = nose down)
        float pitch = SubmarineSteering.pitch(sub, partialTick);
        Matrix4f pose = new Matrix4f()
                .rotateY((180.0f - SubmarineSteering.yaw(sub, partialTick)) * Mth.DEG_TO_RAD)
                .translate(0.0f, (float) Submarine.PIVOT_Y, 0.0f)
                .rotateX(-pitch * Mth.DEG_TO_RAD)
                .translate(0.0f, (float) -Submarine.PIVOT_Y, 0.0f);
        Vec3 direction = vec(pose.transformDirection(new Vector3f(0.0f, -Mth.sin(TILT_DOWN), -Mth.cos(TILT_DOWN)))).normalize();
        for (float side : new float[] {-1.0f, 1.0f})
        {
            Vec3 origin = position.add(vec(pose.transformPosition(new Vector3f(side * LAMP_X, LAMP_Y, LAMP_Z))));
            out.accept(new Spotlight(origin, direction, RANGE, HALF_ANGLE, COLOR, 1.0f, BEAM_LENGTH, BEAM_RADIUS, sub));
        }
    }

    private static Vec3 vec(Vector3f v)
    {
        return new Vec3(v.x, v.y, v.z);
    }
}
