package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.worldgen.DeepLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Sky, cloud and lightmap behaviour of the ocean world, including the deep layer below its bedrock band (the former
 * deep ocean dimension): there the old deep-ocean lightmap applies by camera Y.
 */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AbyssiaDimensionEffects
{
    /** Below this camera Y in the ocean world, sun, moon, stars and clouds are no longer drawn. */
    private static final double OCEAN_HIDE_SKY_BELOW_Y = 48;
    /** Old deep-ocean Y range of the deep lightmap curve. */
    private static final float DEEP_TOP_Y = 256f;
    private static final float DEEP_BOTTOM_Y = -128f;
    /** Lightmap multiplier at the top and bottom of the deep curve, so it keeps darkening as you descend. */
    private static final float DEEP_TOP_LIGHT = 1f;
    private static final float DEEP_BOTTOM_LIGHT = 0.35f;
    /** The old deep ocean dimension type's ambient_light, recreated in the deep layer's lightmap. */
    private static final float DEEP_AMBIENT_LIGHT = 0.1f;

    private AbyssiaDimensionEffects() {}

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event)
    {
        event.register(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "ocean_world"), new OceanWorldEffects());
        event.register(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "overworld"), new OverworldDeepEffects());
    }

    private static boolean deepUnderwater(Camera camera)
    {
        return camera.getFluidInCamera() == FogType.WATER && camera.getPosition().y < OCEAN_HIDE_SKY_BELOW_Y
                || DeepLayer.isDeep(camera.getPosition().y);
    }

    private static Camera camera()
    {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    /** How far the deep layer's look applies at a camera Y: 0 above the bedrock band, 1 under the rock ceiling. */
    private static float deepBlend(double y)
    {
        return Mth.clamp((float) ((DeepLayer.BAND_TOP_Y - y) / (DeepLayer.BAND_TOP_Y - DeepLayer.CEILING_BOTTOM_Y)), 0f, 1f);
    }

    /** Vanilla LightTexture.getBrightness for a light level and an ambient light. */
    private static float brightness(int level, float ambient)
    {
        float f = level / 15f;
        return Mth.lerp(ambient, f / (4f - 3f * f), 1f);
    }

    /** Vanilla block light colour for a block brightness. */
    private static Vector3f blockColor(float b)
    {
        return new Vector3f(b, b * ((b * 0.6f + 0.4f) * 0.6f + 0.4f), b * (b * b * 0.6f + 0.4f));
    }

    /**
     * In the deep layer: the old deep ocean's ambient light (added as the difference it made to vanilla's colour),
     * then its darkening curve in old deep-ocean Y. Faded in across the bedrock band and rock ceiling.
     */
    private static void deepLightmap(float skyDarken, float blockLightRedFlicker, int pixelX, int pixelY, Vector3f colors)
    {
        double y = camera().getPosition().y;
        float k = deepBlend(y);
        if (k <= 0f) return;

        float skyScale = skyDarken * 0.95f + 0.05f;
        Vector3f skyColor = new Vector3f(skyDarken, skyDarken, 1f).lerp(new Vector3f(1f, 1f, 1f), 0.35f);
        float skyLift = (brightness(pixelY, DEEP_AMBIENT_LIGHT) - brightness(pixelY, 0f)) * skyScale;
        Vector3f lift = blockColor(brightness(pixelX, DEEP_AMBIENT_LIGHT) * blockLightRedFlicker)
                .sub(blockColor(brightness(pixelX, 0f) * blockLightRedFlicker))
                .add(skyColor.mul(skyLift))
                .mul(0.96f * k);
        colors.add(lift);

        float t = Mth.clamp((float) ((DEEP_TOP_Y - DeepLayer.toDeepY(y)) / (DEEP_TOP_Y - DEEP_BOTTOM_Y)), 0f, 1f);
        colors.mul(Mth.lerp(k, 1f, Mth.lerp(t, DEEP_TOP_LIGHT, DEEP_BOTTOM_LIGHT)));
    }

    /** Overworld sky above water; nothing but fog colour once the camera is well below the surface or in the deep layer. */
    static final class OceanWorldEffects extends DimensionSpecialEffects.OverworldEffects
    {
        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog)
        {
            return deepUnderwater(camera);
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix)
        {
            return deepUnderwater(camera());
        }

        @Override
        public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ)
        {
            return DeepLayer.isDeep(camY);
        }

        /** In the deep layer: the old deep ocean's lightmap, see {@link #deepLightmap}. */
        @Override
        public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight, int pixelX, int pixelY, Vector3f colors)
        {
            deepLightmap(skyDarken, blockLightRedFlicker, pixelX, pixelY, colors);
        }
    }

    /** Vanilla-style world: vanilla overworld look above the deep layer; below it, no sky and the deep lightmap. */
    static final class OverworldDeepEffects extends DimensionSpecialEffects.OverworldEffects
    {
        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog)
        {
            return DeepLayer.isDeep(camera.getPosition().y);
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix)
        {
            return DeepLayer.isDeep(camY);
        }

        @Override
        public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ)
        {
            return DeepLayer.isDeep(camY);
        }

        @Override
        public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight, int pixelX, int pixelY, Vector3f colors)
        {
            deepLightmap(skyDarken, blockLightRedFlicker, pixelX, pixelY, colors);
        }
    }
}
