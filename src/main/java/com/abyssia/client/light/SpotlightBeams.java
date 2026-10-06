package com.abyssia.client.light;

import com.abyssia.Abyssia;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * The lamps of {@link Spotlight}s themselves (ported from AshWarfare's WeaponLights + Glows): a glow at the lamp that
 * flares up when it points at the camera, and a faint cone of light in the water in front of it that ends where each
 * side hits a block. Drawn with {@link RenderType#eyes} (added to what is behind, unlit) after the particles, so it
 * glows over marine snow and water too.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SpotlightBeams
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/effect/light_glow.png");
    // the texture's white middle gives the cone a plain colour
    private static final float MIDDLE = 0.5f;
    // glow at the lamp: radius as a part of the beam radius, brightness seen from in front; flare when it points at the camera
    private static final float GLOW_SCALE = 2.2f;
    private static final float GLOW_BRIGHTNESS = 0.9f;
    private static final float FLARE_RADIUS = 2.5f;
    private static final float FLARE_BRIGHTNESS = 0.6f;
    // brought this far toward the camera, so the lamp's own model does not hide it, in blocks
    private static final float GLOW_LIFT = 0.1f;
    // the cone's surface at the lamp, and after how many blocks that is halved as the light spreads out
    private static final float BEAM_BRIGHTNESS = 0.14f;
    private static final float BEAM_HALF_DISTANCE = 4.0f;
    private static final int BEAM_SIDES = 16;
    private static final int BEAM_PIECES = 10;
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private SpotlightBeams() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || level == null) return;
        Camera camera = event.getCamera();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType renderType = RenderType.eyes(TEXTURE);
        VertexConsumer consumer = null;
        for (Spotlight light : SpotlightProjector.collect(level, event.getPartialTick()))
        {
            if (light.origin().distanceToSqr(camera.getPosition()) > 160.0 * 160.0) continue;
            if (consumer == null) consumer = buffers.getBuffer(renderType);
            render(consumer, level, camera, light);
        }
        if (consumer != null) buffers.endBatch(renderType);
    }

    private static void render(VertexConsumer consumer, ClientLevel level, Camera camera, Spotlight light)
    {
        Vector3f origin = light.origin().subtract(camera.getPosition()).toVector3f();
        Vector3f direction = light.direction().toVector3f();
        float distance = origin.length();
        if (distance <= GLOW_LIFT) return;
        // 1 when the lamp points right at the camera, 0 across the line of sight or away
        float facing = Math.max(-origin.dot(direction) / distance, 0.0f);
        float strength = Math.min(light.brightness(), 1.0f);
        if (facing > 0)
        {
            Vector3f glow = origin.mul((distance - GLOW_LIFT) / distance, new Vector3f());
            glow(consumer, camera, glow, light.beamRadius() * GLOW_SCALE, light.color(), GLOW_BRIGHTNESS * facing * strength);
            float flare = Mth.square(Mth.square(Mth.square(facing)));
            glow(consumer, camera, glow, FLARE_RADIUS * flare, light.color(), FLARE_BRIGHTNESS * flare * strength);
        }
        // the cone fades out as it turns toward the camera, where the flare takes over
        float brightness = BEAM_BRIGHTNESS * strength * (1.0f - facing * facing);
        if (brightness > 0 && light.beamLength() > 0) cone(consumer, level, camera, light, brightness);
    }

    /**
     * A cone of light out of the rim of the lamp whose sides each end where they hit a block. Each point of its surface
     * is as bright as it faces the camera, so it is brightest down the middle and fades to its edges, like light in
     * murky water.
     */
    private static void cone(VertexConsumer consumer, ClientLevel level, Camera camera, Spotlight light, float brightness)
    {
        Vec3 dir = light.direction();
        Vec3 side0 = dir.cross(Math.abs(dir.y) < 0.99 ? UP : new Vec3(1.0, 0.0, 0.0)).normalize();
        Vec3 side1 = dir.cross(side0);
        double sin = Math.sin(light.halfAngle());
        double cos = Math.cos(light.halfAngle());
        Vec3 cameraPosition = camera.getPosition();
        float maxLength = light.beamLength();
        Vector3f[] starts = new Vector3f[BEAM_SIDES];
        Vector3f[] ways = new Vector3f[BEAM_SIDES];
        float[] lengths = new float[BEAM_SIDES];
        Vector3f[] normals = new Vector3f[BEAM_SIDES];
        for (int side = 0; side < BEAM_SIDES; side++)
        {
            float angle = side * Mth.TWO_PI / BEAM_SIDES;
            Vec3 out = side0.scale(Mth.cos(angle)).add(side1.scale(Mth.sin(angle)));
            Vec3 start = light.origin().add(out.scale(light.beamRadius()));
            Vec3 way = dir.scale(cos).add(out.scale(sin));
            Vec3 end = start.add(way.scale(maxLength));
            HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, light.owner()));
            starts[side] = start.subtract(cameraPosition).toVector3f();
            ways[side] = way.toVector3f();
            lengths[side] = hit.getType() == HitResult.Type.MISS ? maxLength : (float) start.distanceTo(hit.getLocation());
            normals[side] = out.scale(cos).subtract(dir.scale(sin)).toVector3f();
        }
        for (int side = 0; side < BEAM_SIDES; side++)
        {
            int next = (side + 1) % BEAM_SIDES;
            for (int piece = 0; piece < BEAM_PIECES; piece++)
            {
                // shorter pieces near the lamp, where the brightness changes the most
                float from = Mth.square((float) piece / BEAM_PIECES);
                float to = Mth.square((float) (piece + 1) / BEAM_PIECES);
                Vector3f a = point(starts[side], ways[side], lengths[side] * from);
                Vector3f b = point(starts[side], ways[side], lengths[side] * to);
                Vector3f c = point(starts[next], ways[next], lengths[next] * to);
                Vector3f d = point(starts[next], ways[next], lengths[next] * from);
                quad(consumer, light.color(),
                        a, brightness * surface(a, normals[side], lengths[side] * from, maxLength),
                        b, brightness * surface(b, normals[side], lengths[side] * to, maxLength),
                        c, brightness * surface(c, normals[next], lengths[next] * to, maxLength),
                        d, brightness * surface(d, normals[next], lengths[next] * from, maxLength));
            }
        }
    }

    private static Vector3f point(Vector3f start, Vector3f way, float distance)
    {
        return way.mul(distance, new Vector3f()).add(start);
    }

    // how bright the cone's surface is at a point relative to the camera, the given distance from the lamp
    private static float surface(Vector3f position, Vector3f normal, float distance, float maxLength)
    {
        float fromCamera = position.length();
        if (fromCamera < 1.0E-4f) return 0.0f;
        float facing = Math.abs(position.dot(normal)) / fromCamera;
        return facing / (1.0f + distance / BEAM_HALF_DISTANCE) * Mth.square(1.0f - distance / maxLength);
    }

    // a round glow facing the camera
    private static void glow(VertexConsumer consumer, Camera camera, Vector3f center, float radius, int color, float brightness)
    {
        Vector3f left = new Vector3f(camera.getLeftVector()).mul(radius);
        Vector3f up = new Vector3f(camera.getUpVector()).mul(radius);
        Vector3f[] corners = {
                new Vector3f(center).add(left).add(up),
                new Vector3f(center).sub(left).add(up),
                new Vector3f(center).sub(left).sub(up),
                new Vector3f(center).add(left).sub(up)};
        float[] uvs = {0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f};
        for (int corner = 0; corner < 4; corner++) vertex(consumer, corners[corner], color, brightness, uvs[corner * 2], uvs[corner * 2 + 1]);
        for (int corner = 3; corner >= 0; corner--) vertex(consumer, corners[corner], color, brightness, uvs[corner * 2], uvs[corner * 2 + 1]);
    }

    // both sides of a quad in a plain colour, as bright as given at each corner
    private static void quad(VertexConsumer consumer, int color, Vector3f a, float brightnessA, Vector3f b, float brightnessB,
                             Vector3f c, float brightnessC, Vector3f d, float brightnessD)
    {
        vertex(consumer, a, color, brightnessA, MIDDLE, MIDDLE);
        vertex(consumer, b, color, brightnessB, MIDDLE, MIDDLE);
        vertex(consumer, c, color, brightnessC, MIDDLE, MIDDLE);
        vertex(consumer, d, color, brightnessD, MIDDLE, MIDDLE);
        vertex(consumer, d, color, brightnessD, MIDDLE, MIDDLE);
        vertex(consumer, c, color, brightnessC, MIDDLE, MIDDLE);
        vertex(consumer, b, color, brightnessB, MIDDLE, MIDDLE);
        vertex(consumer, a, color, brightnessA, MIDDLE, MIDDLE);
    }

    private static void vertex(VertexConsumer consumer, Vector3f position, int color, float brightness, float u, float v)
    {
        float scale = Mth.clamp(brightness, 0.0f, 1.0f) / 255.0f;
        consumer.vertex(position.x, position.y, position.z)
                .color(FastColor.ARGB32.red(color) * scale, FastColor.ARGB32.green(color) * scale, FastColor.ARGB32.blue(color) * scale, 1.0f)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0f, 1.0f, 0.0f)
                .endVertex();
    }
}
