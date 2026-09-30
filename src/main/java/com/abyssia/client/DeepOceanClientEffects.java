package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.Config;
import com.abyssia.DeepOceanTransition;
import com.abyssia.client.thermal.ClientVentTracker;
import com.abyssia.environment.CaveAmbience;
import com.abyssia.network.DepthSettingsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side presentation of the ocean world / deep ocean boundary: depth-based fog and darkness,
 * a fade around the teleport, particles and sounds, and hiding the terrain loading screen while it happens.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DeepOceanClientEffects
{
    private static final int SEA_LEVEL = 63;
    private static final int DEEP_MIN_Y = -128;
    /** Lowest ocean world Y without bedrock (the bedrock floor fills Y -64..-60). */
    private static final int OCEAN_BEDROCK_TOP_Y = -59;
    /** Blocks before a boundary over which the screen fades out. */
    private static final float FADE_BLOCKS = 24f;
    private static final float MAX_PRE_FADE = 0.6f;
    private static final int ARRIVAL_FADE_TICKS = 40;
    /** Upper bound on how long the arrival fade holds while waiting for the chunk under the player. */
    private static final long ARRIVAL_HOLD_MS = 5000;
    private static final long HIDE_LOADING_WINDOW_MS = 3000;
    private static final int OVERLAY_RGB = 0x000814;

    private static final float SURFACE_FOG_END = 96f;
    private static final float BOUNDARY_FOG_END = 28f;
    private static final float ABYSS_FOG_END = 10f;
    /** Fraction of the fog distance removed at full marine snow density. */
    private static final float MARINE_SNOW_FOG = 0.2f;
    /** Fraction of the fog distance removed, and how far fog turns milky grey, at full vent temperature. */
    private static final float VENT_FOG = 0.35f;
    private static final float VENT_HAZE_COLOR = 0.35f;
    /** Vent temperature at the camera, eased over time so walking past a vent never snaps the fog. */
    private static float ventHaze;
    /** How deep inside a large cavern the camera is (0..1), eased: clear near water, hazy middle, far walls hidden. */
    private static float cavernHaze;

    // Boundaries as sent by the server; until then fall back to the local config.
    private static boolean serverSettings;
    private static boolean enabled;
    private static int transitionY;
    private static int returnY;

    private static ResourceKey<Level> lastDimension;
    private static float preFade;
    private static float lastPreFade;
    private static boolean preSoundPlayed;
    private static int arrivalTicks;
    private static long arrivalAt;
    private static long hideLoadingUntil;
    private static int ambientCooldown = 200;

    private static float fogEnd = -1;
    private static long lastFogNanos;

    private DeepOceanClientEffects() {}

    public static void applyServerSettings(DepthSettingsPacket packet)
    {
        serverSettings = true;
        enabled = packet.enabled();
        transitionY = packet.transitionY();
        returnY = packet.returnY();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        serverSettings = false;
        lastDimension = null;
        preFade = lastPreFade = 0;
        arrivalTicks = 0;
        fogEnd = -1;
        cavernHaze = 0;
    }

    private static void refreshLocalSettings()
    {
        if (serverSettings) return;
        enabled = Config.DEEP_OCEAN_ENABLED.get();
        transitionY = Config.DEEP_OCEAN_TRANSITION_Y.get();
        returnY = Config.DEEP_OCEAN_RETURN_Y.get();
    }

    private static boolean effectsOn()
    {
        return enabled && Config.DEEP_OCEAN_ENABLE_TRANSITION_EFFECT.get();
    }

    /** 0 at the surface, 1 at the ocean world transition depth. */
    private static float oceanDepth01(double y)
    {
        return Mth.clamp((float) (SEA_LEVEL - y) / (SEA_LEVEL - transitionY), 0f, 1f);
    }

    /** 0 at the deep ocean return height, 1 at the bottom of the dimension. */
    private static float abyssDepth01(double y)
    {
        return Mth.clamp((float) (returnY - y) / (returnY - DEEP_MIN_Y), 0f, 1f);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        refreshLocalSettings();

        ResourceKey<Level> dim = player.level().dimension();
        if (lastDimension != null && lastDimension != dim && isBoundaryPair(lastDimension, dim)) onArrive(mc, player);
        lastDimension = dim;

        lastPreFade = preFade;
        preFade = effectsOn() ? computePreFade(player, dim) : 0f;
        if (preFade > 0.4f) hideLoadingUntil = System.currentTimeMillis() + HIDE_LOADING_WINDOW_MS;
        if (preFade > 0.3f && !preSoundPlayed)
        {
            preSoundPlayed = true;
            playAt(mc, player, SoundEvents.AMBIENT_UNDERWATER_ENTER, 0.6f, 0.5f);
        }
        else if (preFade == 0f)
        {
            preSoundPlayed = false;
        }

        if (arrivalTicks > 0 && (chunkReady(mc, player) || System.currentTimeMillis() - arrivalAt > ARRIVAL_HOLD_MS)) arrivalTicks--;

        if (effectsOn() && player.isUnderWater()) tickDepthAmbience(mc, player, dim);
        float heat = player.isUnderWater() ? ClientVentTracker.temperatureAt(player.getX(), player.getEyeY(), player.getZ()) : 0f;
        ventHaze += (smoothstep(Mth.clamp(heat, 0f, 1f)) - ventHaze) * 0.05f;
        float cavern = player.isUnderWater() && ClientConfig.CAVERN_FOG.get() ? CaveAmbience.cavernFactor() : 0f;
        cavernHaze += (cavern - cavernHaze) * 0.03f;
    }

    private static boolean isBoundaryPair(ResourceKey<Level> from, ResourceKey<Level> to)
    {
        return (from == DeepOceanTransition.OCEAN_WORLD && to == DeepOceanTransition.DEEP_OCEAN)
                || (from == DeepOceanTransition.DEEP_OCEAN && to == DeepOceanTransition.OCEAN_WORLD);
    }

    private static float computePreFade(LocalPlayer player, ResourceKey<Level> dim)
    {
        double y = player.getY();
        float remaining;
        if (dim == DeepOceanTransition.OCEAN_WORLD)
        {
            // A transition depth inside the bedrock floor is only reached down a rift: no fade over other deep floors.
            if (transitionY < OCEAN_BEDROCK_TOP_Y && !player.level().getBiome(player.blockPosition()).is(DeepOceanTransition.ABYSSAL_RIFT)) return 0f;
            remaining = (float) (y - transitionY);
        }
        else if (dim == DeepOceanTransition.DEEP_OCEAN) remaining = (float) (returnY - y);
        else return 0f;
        float t = Mth.clamp(1f - remaining / FADE_BLOCKS, 0f, 1f);
        return t * t * (3f - 2f * t) * MAX_PRE_FADE;
    }

    private static void onArrive(Minecraft mc, LocalPlayer player)
    {
        if (!effectsOn()) return;
        arrivalTicks = ARRIVAL_FADE_TICKS;
        arrivalAt = System.currentTimeMillis();
        RandomSource random = player.getRandom();
        for (int i = 0; i < 60; i++)
        {
            mc.level.addParticle(ParticleTypes.BUBBLE,
                    player.getX() + random.nextGaussian() * 1.5, player.getY() + random.nextDouble() * 2.5, player.getZ() + random.nextGaussian() * 1.5,
                    random.nextGaussian() * 0.05, 0.1 + random.nextDouble() * 0.2, random.nextGaussian() * 0.05);
        }
        playAt(mc, player, SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, 0.8f, 0.6f);
        playAt(mc, player, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.5f, 0.7f);
    }

    private static boolean chunkReady(Minecraft mc, LocalPlayer player)
    {
        return mc.levelRenderer.isChunkCompiled(player.blockPosition());
    }

    // Suspended particles are handled by MarineSnowClientManager.
    private static void tickDepthAmbience(Minecraft mc, LocalPlayer player, ResourceKey<Level> dim)
    {
        RandomSource random = player.getRandom();
        if (dim == DeepOceanTransition.DEEP_OCEAN && --ambientCooldown <= 0)
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

    /** The teleport would otherwise flash a "Loading terrain" screen; the fade overlay covers that moment instead. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event)
    {
        if (!effectsOn() || System.currentTimeMillis() > hideLoadingUntil) return;
        if (event.getNewScreen() instanceof ReceivingLevelScreen || event.getNewScreen() instanceof ProgressScreen)
        {
            event.setCanceled(true);
        }
    }

    private static float overlayAlpha(float partialTick)
    {
        float pre = Mth.lerp(partialTick, lastPreFade, preFade);
        float arrival = 0f;
        if (arrivalTicks > 0)
        {
            Minecraft mc = Minecraft.getInstance();
            boolean holding = mc.player != null && !chunkReady(mc, mc.player) && System.currentTimeMillis() - arrivalAt <= ARRIVAL_HOLD_MS;
            arrival = holding ? 1f : Mth.clamp((arrivalTicks - partialTick) / ARRIVAL_FADE_TICKS, 0f, 1f);
        }
        return Math.max(pre, arrival);
    }

    /** Target far-plane distance for underwater fog at the camera's depth. */
    private static float targetFogEnd(LocalPlayer player)
    {
        ResourceKey<Level> dim = player.level().dimension();
        float end;
        if (dim == DeepOceanTransition.OCEAN_WORLD) end = Mth.lerp(smoothstep(oceanDepth01(player.getEyeY())), SURFACE_FOG_END, BOUNDARY_FOG_END);
        else if (dim == DeepOceanTransition.DEEP_OCEAN) end = Mth.lerp(smoothstep(abyssDepth01(player.getEyeY())), BOUNDARY_FOG_END, ABYSS_FOG_END);
        else return -1;
        // Denser marine snow scatters more light: thicken fog with it (eased, so never a sudden change).
        end *= 1f - MARINE_SNOW_FOG * smoothstep(Mth.clamp(MarineSnowClientManager.currentDensity(), 0f, 1f));
        // Hot vent water is cloudy with minerals.
        end *= 1f - VENT_FOG * ventHaze;
        // Large caverns: a little hazier, so the far walls dissolve instead of closing the space off like a box.
        end *= Mth.lerp(cavernHaze, 1f, ClientConfig.CAVERN_FOG_DISTANCE.get().floatValue());
        if (player.hasEffect(MobEffects.NIGHT_VISION) || player.hasEffect(MobEffects.CONDUIT_POWER)) end *= 2f;
        return end;
    }

    private static float smoothstep(float t)
    {
        return t * t * (3f - 2f * t);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event)
    {
        if (event.getType() != FogType.WATER || !enabled || !Config.DEEP_OCEAN_ENABLE_FOG.get()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        float target = targetFogEnd(player);
        if (target < 0) return;

        // Ease toward the target so fog never jumps, including across the teleport.
        long now = System.nanoTime();
        float dt = lastFogNanos == 0 ? 1f : Math.min((now - lastFogNanos) / 1e9f, 1f);
        lastFogNanos = now;
        fogEnd = fogEnd < 0 ? target : fogEnd + (target - fogEnd) * Math.min(1f, dt * 1.5f);

        float end = Math.min(event.getFarPlaneDistance(), fogEnd);
        // In a cavern the fog starts a little way out: nearby rock and plants stay crisp, the middle distance hazes over.
        float near = Mth.lerp(cavernHaze, -8f, end * ClientConfig.CAVERN_FOG_CLEAR.get().floatValue());
        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(end);
        event.setCanceled(true);
        if (event.getMode() == FogRenderer.FogMode.FOG_TERRAIN) ShaderFogPass.submit(near, end);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event)
    {
        if (event.getCamera().getFluidInCamera() != FogType.WATER || !enabled || !Config.DEEP_OCEAN_ENABLE_FOG.get()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ResourceKey<Level> dim = player.level().dimension();
        float brightness;
        if (dim == DeepOceanTransition.OCEAN_WORLD) brightness = Mth.lerp(oceanDepth01(player.getEyeY()), 1f, 0.35f);
        else if (dim == DeepOceanTransition.DEEP_OCEAN) brightness = Mth.lerp(abyssDepth01(player.getEyeY()), 0.35f, 0.05f);
        else return;
        // The far reaches of a cavern fall into darkness.
        brightness *= 1f - cavernHaze * ClientConfig.CAVERN_FOG_DARKENING.get().floatValue();
        float haze = ventHaze * VENT_HAZE_COLOR;
        float grey = 0.3f * brightness + 0.1f;
        event.setRed(Mth.lerp(haze, event.getRed() * brightness, grey));
        event.setGreen(Mth.lerp(haze, event.getGreen() * brightness, grey));
        event.setBlue(Mth.lerp(haze, event.getBlue() * brightness, grey));
        ShaderFogPass.color(event.getRed(), event.getGreen(), event.getBlue());
    }

    @Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration
    {
        private Registration() {}

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event)
        {
            event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "deep_ocean_transition", (gui, graphics, partialTick, width, height) -> {
                float alpha = overlayAlpha(partialTick);
                if (alpha <= 0.001f) return;
                graphics.fill(0, 0, width, height, (Math.round(alpha * 255) << 24) | OVERLAY_RGB);
            });
        }
    }
}
