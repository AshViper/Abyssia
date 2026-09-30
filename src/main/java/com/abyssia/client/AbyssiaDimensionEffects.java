package com.abyssia.client;

import com.abyssia.Abyssia;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Sky, cloud and lightmap behaviour for the ocean world and deep ocean dimension types. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AbyssiaDimensionEffects
{
    /** Below this camera Y in the ocean world, sun, moon, stars and clouds are no longer drawn. */
    private static final double OCEAN_HIDE_SKY_BELOW_Y = 48;
    private static final float DEEP_TOP_Y = 256f;
    private static final float DEEP_BOTTOM_Y = -128f;
    /** Lightmap multiplier at the top and bottom of the deep ocean, so it keeps darkening as you descend. */
    private static final float DEEP_TOP_LIGHT = 1f;
    private static final float DEEP_BOTTOM_LIGHT = 0.35f;

    private AbyssiaDimensionEffects() {}

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event)
    {
        event.register(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "ocean_world"), new OceanWorldEffects());
        event.register(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "deep_ocean"), new DeepOceanEffects());
    }

    private static boolean deepUnderwater(Camera camera)
    {
        return camera.getFluidInCamera() == FogType.WATER && camera.getPosition().y < OCEAN_HIDE_SKY_BELOW_Y;
    }

    /** Overworld sky above water; nothing but fog colour once the camera is well below the surface. */
    static final class OceanWorldEffects extends DimensionSpecialEffects.OverworldEffects
    {
        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog)
        {
            return deepUnderwater(camera);
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projectionMatrix)
        {
            return deepUnderwater(Minecraft.getInstance().gameRenderer.getMainCamera());
        }
    }

    /** Fully submerged dimension: no sky, celestial bodies or clouds. */
    static final class DeepOceanEffects extends DimensionSpecialEffects
    {
        DeepOceanEffects()
        {
            super(Float.NaN, true, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness)
        {
            return color;
        }

        @Override
        public boolean isFoggyAt(int x, int z)
        {
            return false;
        }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog)
        {
            return true;
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projectionMatrix)
        {
            return true;
        }

        @Override
        public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, net.minecraft.client.renderer.LightTexture lightTexture, double camX, double camY, double camZ)
        {
            return true;
        }

        @Override
        public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight, int pixelX, int pixelY, Vector3f colors)
        {
            double y = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y;
            float t = Mth.clamp((float) ((DEEP_TOP_Y - y) / (DEEP_TOP_Y - DEEP_BOTTOM_Y)), 0f, 1f);
            colors.mul(Mth.lerp(t, DEEP_TOP_LIGHT, DEEP_BOTTOM_LIGHT));
        }
    }
}
