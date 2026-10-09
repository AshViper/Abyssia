package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.Config;
import com.abyssia.client.thermal.ClientVentTracker;
import com.abyssia.environment.CaveAmbience;
import com.abyssia.worldgen.DeepLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import com.abyssia.registry.ModMobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Client-side depth presentation of the ocean world and the deep layer below its bedrock band: underwater fog with a
 * fixed distance (client config) whose density and darkness grow with depth, and deep-sea ambience.
 * <p>
 * Above the old transition depth (overworld Y -40 = deep Y {@link DeepLayer#DEPTH_ORIGIN_DEEP_Y}) the ocean curve
 * runs from the surface; below it the deep curve, tuned in old deep-ocean Y, takes over continuously.
 */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class DeepOceanClientEffects
{
    private static final int SEA_LEVEL = 63;
    /** Old deep-ocean Y where the ocean curve ends and the deep curve starts (overworld Y -40). */
    private static final int DEEP_CURVE_TOP = DeepLayer.DEPTH_ORIGIN_DEEP_Y;
    private static final double DEEP_CURVE_TOP_Y = DeepLayer.fromDeepY(DEEP_CURVE_TOP);
    /** Old deep-ocean Y of the world bottom. */
    private static final double DEEP_CURVE_BOTTOM = DeepLayer.toDeepY(DeepLayer.DEEP_BOTTOM_Y);

    /**
     * Where the fog starts, as a share of the fog end: the view distance stays the same at every depth
     * ({@link ClientConfig#DEEP_SEA_FOG_DISTANCE}), and depth shows as fog that starts closer (denser) and darker.
     * Negative = fog already a little in front of the camera, as vanilla water fog (-8).
     */
    private static final float SURFACE_FOG_START = 0.4f;
    private static final float BOUNDARY_FOG_START = 0.12f;
    private static final float ABYSS_FOG_START = -0.05f;
    /** Vent haze and caverns together shorten the fog to no less than this share of the configured distance. */
    private static final float MIN_FOG_FACTOR = 0.6f;
    /** Fraction of the fog distance removed, and how far fog turns milky grey, at full vent temperature. */
    private static final float VENT_FOG = 0.35f;
    private static final float VENT_HAZE_COLOR = 0.35f;
    /** Vent temperature at the camera, eased over time so walking past a vent never snaps the fog. */
    private static float ventHaze;
    /** How deep inside a large cavern the camera is (0..1), eased: clear near water, hazy middle, far walls hidden. */
    private static float cavernHaze;
    /** AB04: the same probe for the hall tweaks (independent of the cavern_fog switch), eased. */
    private static float hallHaze;

    private static int ambientCooldown = 200;

    private static float fogEnd = -1;
    private static long lastFogNanos;

    private DeepOceanClientEffects() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        fogEnd = -1;
        cavernHaze = 0;
        hallHaze = 0;
    }

    /** 0 at the surface, 1 at the old transition depth (Y -40). */
    private static float oceanDepth01(double y)
    {
        return Mth.clamp((float) ((SEA_LEVEL - y) / (SEA_LEVEL - DEEP_CURVE_TOP_Y)), 0f, 1f);
    }

    /** 0 at the old transition depth (Y -40), 1 at the bottom of the deep layer. */
    private static float abyssDepth01(double y)
    {
        return Mth.clamp((float) ((DEEP_CURVE_TOP - DeepLayer.toDeepY(y)) / (DEEP_CURVE_TOP - DEEP_CURVE_BOTTOM)), 0f, 1f);
    }

    private static boolean deepCurve(double y)
    {
        return y < DEEP_CURVE_TOP_Y;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        if (player.isUnderWater()) tickDepthAmbience(mc, player);
        float heat = player.isUnderWater() ? ClientVentTracker.temperatureAt(player.getX(), player.getEyeY(), player.getZ()) : 0f;
        ventHaze += (smoothstep(Mth.clamp(heat, 0f, 1f)) - ventHaze) * 0.05f;
        float cavern = player.isUnderWater() && ClientConfig.CAVERN_FOG.get() ? CaveAmbience.cavernFactor() : 0f;
        cavernHaze += (cavern - cavernHaze) * 0.03f;
        float hall = player.isUnderWater() ? CaveAmbience.cavernFactor() : 0f;
        hallHaze += (hall - hallHaze) * 0.03f;
    }

    // Suspended particles are handled by MarineSnowClientManager.
    private static void tickDepthAmbience(Minecraft mc, LocalPlayer player)
    {
        RandomSource random = player.getRandom();
        if (DeepLayer.isDeep(player.level(), player.getEyeY()) && --ambientCooldown <= 0)
        {
            ambientCooldown = 300 + random.nextInt(600);
            SoundEvent sound = random.nextFloat() < 0.3f ? SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE : SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE;
            playAt(mc, player, sound, 0.7f, 0.5f + random.nextFloat() * 0.2f);
        }
    }

    private static void playAt(Minecraft mc, LocalPlayer player, SoundEvent sound, float volume, float pitch)
    {
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch, player.getRandom(),
                player.getX(), player.getY(), player.getZ()));
    }

    /**
     * Whether Abyssia's underwater fog applies at this eye Y: the whole ocean world, but only the deep layer (below
     * the bedrock band) of any other Abyssia overworld, where the surface and ocean fog stay vanilla.
     */
    private static boolean abyssiaFog(Level level, double y)
    {
        if (level.dimension() != Level.OVERWORLD) return false;
        return DeepLayer.OCEAN_WORLD_EFFECTS.equals(level.dimensionType().effectsLocation()) || DeepLayer.isDeep(y);
    }

    /** Target far-plane distance for underwater fog at the camera's depth. */
    private static float targetFogEnd(LocalPlayer player)
    {
        double y = player.getEyeY();
        if (!abyssiaFog(player.level(), y)) return -1;
        float end = ClientConfig.DEEP_SEA_FOG_DISTANCE.get();
        // Marine snow does not shorten the view: the configured distance is what open deep water shows (VD01 review).
        float factor = 1f;
        // Hot vent water is cloudy with minerals.
        factor *= 1f - VENT_FOG * ventHaze;
        // Large caverns: a little hazier, so the far walls dissolve instead of closing the space off like a box.
        // AB04 halls: see further across the vault instead (the probe that finds caverns finds the halls).
        if (ClientConfig.HALL_FOG.get()) factor *= Mth.lerp(hallHaze, 1f, ClientConfig.HALL_FOG_DISTANCE.get().floatValue());
        else factor *= Mth.lerp(cavernHaze, 1f, ClientConfig.CAVERN_FOG_DISTANCE.get().floatValue());
        end *= Math.max(MIN_FOG_FACTOR, factor);
        if (player.hasEffect(MobEffects.NIGHT_VISION) || player.hasEffect(MobEffects.CONDUIT_POWER)) end *= 2f;
        // EN01: Deep Sight pushes the fog out (+64 / +144 blocks; the render distance still caps it in onRenderFog) and
        // clears the near water (onRenderFog); Murk pulls it in.
        MobEffectInstance sight = player.getEffect(ModMobEffects.DEEP_SIGHT);
        if (sight != null) end += sight.getAmplifier() >= 1 ? 144f : 64f;
        if (player.hasEffect(ModMobEffects.MURK))
            end *= sight == null ? 0.5f : sight.getAmplifier() >= 1 ? 1f : 0.75f;
        return end;
    }

    /** Fog start as a share of the fog end at the camera's depth: deeper water is denser from closer in. */
    private static float fogStartShare(double y)
    {
        return deepCurve(y)
                ? Mth.lerp(smoothstep(abyssDepth01(y)), BOUNDARY_FOG_START, ABYSS_FOG_START)
                : Mth.lerp(smoothstep(oceanDepth01(y)), SURFACE_FOG_START, BOUNDARY_FOG_START);
    }

    private static float smoothstep(float t)
    {
        return t * t * (3f - 2f * t);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event)
    {
        if (event.getType() != FogType.WATER || !Config.DEEP_OCEAN_ENABLE_FOG.get()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        float target = targetFogEnd(player);
        if (target < 0) return;

        // Ease toward the target so fog never jumps.
        long now = System.nanoTime();
        float dt = lastFogNanos == 0 ? 1f : Math.min((now - lastFogNanos) / 1e9f, 1f);
        lastFogNanos = now;
        fogEnd = fogEnd < 0 ? target : fogEnd + (target - fogEnd) * Math.min(1f, dt * 1.5f);

        // The event's far plane is vanilla's water fog (96 x water vision), not the render distance: cap by the render
        // distance instead, and keep vanilla's eyes-adjusting ramp (from 1/4 of the distance) right after diving in.
        float waterVision = Mth.clamp(player.getWaterVision(), 0.25f, 1f);
        float end = Math.min(Minecraft.getInstance().gameRenderer.getRenderDistance(), fogEnd * waterVision);
        // In a cavern the fog starts a little way out: nearby rock and plants stay crisp, the middle distance hazes over.
        float share = fogStartShare(player.getEyeY());
        // EN01: Deep Sight moves the start of the fog out toward the clear surface-water share (I 35%, II 65% of the way).
        MobEffectInstance sight = player.getEffect(ModMobEffects.DEEP_SIGHT);
        if (sight != null) share = Mth.lerp(sight.getAmplifier() >= 1 ? 0.65f : 0.35f, share, SURFACE_FOG_START);
        float near = end * Mth.lerp(cavernHaze, share, ClientConfig.CAVERN_FOG_CLEAR.get().floatValue());
        // AB04: the water right around you in a hall stays clear.
        if (ClientConfig.HALL_NEAR_CLEAR.get()) near = Math.min(end * 0.9f, Math.max(near, ClientConfig.HALL_NEAR_CLEAR_DISTANCE.get() * hallHaze));
        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(end);
        event.setCanceled(true);
        if (event.getMode() == FogRenderer.FogMode.FOG_TERRAIN) ShaderFogPass.submit(near, end);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event)
    {
        if (event.getCamera().getFluidInCamera() != FogType.WATER || !Config.DEEP_OCEAN_ENABLE_FOG.get()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        double y = player.getEyeY();
        if (!abyssiaFog(player.level(), y)) return;
        // The biome's own water fog colour, before depth darkens it (the hall tint aims at it).
        float biomeR = event.getRed(), biomeG = event.getGreen(), biomeB = event.getBlue();
        float brightness = deepCurve(y)
                ? Mth.lerp(abyssDepth01(y), 0.35f, 0.05f)
                : Mth.lerp(oceanDepth01(y), 1f, 0.35f);
        // EN01: Murk darkens the water (Deep Sight I halves the effect, II cancels it); Deep Sight lifts the darkness toward full brightness.
        MobEffectInstance sight = player.getEffect(ModMobEffects.DEEP_SIGHT);
        if (player.hasEffect(ModMobEffects.MURK))
            brightness *= sight == null ? 0.7f : sight.getAmplifier() >= 1 ? 1f : 0.85f;
        if (sight != null) brightness += (1f - brightness) * (sight.getAmplifier() >= 1 ? 0.55f : 0.30f);
        // The far reaches of a cavern fall into darkness.
        brightness *= 1f - cavernHaze * ClientConfig.CAVERN_FOG_DARKENING.get().floatValue();
        float haze = ventHaze * VENT_HAZE_COLOR;
        float grey = 0.3f * brightness + 0.1f;
        event.setRed(Mth.lerp(haze, event.getRed() * brightness, grey));
        event.setGreen(Mth.lerp(haze, event.getGreen() * brightness, grey));
        event.setBlue(Mth.lerp(haze, event.getBlue() * brightness, grey));
        if (hallHaze > 0.01f) hallFogColor(event, biomeR, biomeG, biomeB);
        ShaderFogPass.color(event.getRed(), event.getGreen(), event.getBlue());
    }

    /** Biome water colour scaled to this brightness is what the hall tint aims at. */
    private static final float HALL_TINT_BRIGHTNESS = 0.3f;

    /**
     * AB04: inside a hall, tint the fog toward the biome's water colour and keep it above a minimum brightness, so walls,
     * columns and light silhouettes read against a coloured haze instead of black. Only the fog colour (a render correction).
     */
    private static void hallFogColor(ViewportEvent.ComputeFogColor event, float biomeR, float biomeG, float biomeB)
    {
        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
        if (ClientConfig.HALL_FOG_TINT.get())
        {
            float t = hallHaze * ClientConfig.HALL_FOG_TINT_STRENGTH.get().floatValue();
            r = Mth.lerp(t, r, biomeR * HALL_TINT_BRIGHTNESS);
            g = Mth.lerp(t, g, biomeG * HALL_TINT_BRIGHTNESS);
            b = Mth.lerp(t, b, biomeB * HALL_TINT_BRIGHTNESS);
        }
        if (ClientConfig.HALL_BRIGHTNESS_FLOOR.get())
        {
            float floor = hallHaze * ClientConfig.HALL_BRIGHTNESS_FLOOR_VALUE.get().floatValue();
            float luma = 0.3f * r + 0.59f * g + 0.11f * b;
            if (luma < floor)
            {
                if (luma > 1e-4f)
                {
                    float k = Math.min(floor / luma, 8f);
                    r = Math.min(1f, r * k);
                    g = Math.min(1f, g * k);
                    b = Math.min(1f, b * k);
                }
                luma = 0.3f * r + 0.59f * g + 0.11f * b;
                if (luma < floor)
                {
                    float add = floor - luma;
                    r += add;
                    g += add;
                    b += add;
                }
            }
        }
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }
}
