package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.joml.Matrix4f;
import net.neoforged.fml.common.EventBusSubscriber;

import java.io.IOException;

/**
 * The depth fog of the ocean world and its deep layer, redrawn for Iris / Oculus shader packs. A pack replaces vanilla fog
 * with its own fixed water fog, so the mod's fog distance and colour (depth, marine snow, vents, caverns) are lost.
 * Iris has finished its final pass by {@link RenderLevelStageEvent.Stage#AFTER_LEVEL}, and the main depth buffer still
 * holds the scene, so the same spherical fog is rebuilt from depth and laid over the pack's image: first into a
 * separate target (the depth texture is never sampled while it is attached), then blended onto the screen.
 */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ShaderFogPass
{
    private static ShaderInstance fogShader;
    private static ShaderInstance compositeShader;
    private static TextureTarget fogTarget;

    // Fog of the frame being drawn, published by DeepOceanClientEffects.
    private static boolean pending;
    private static float fogStart, fogEnd;
    private static float red, green, blue;

    private ShaderFogPass() {}

    /** Terrain fog distances set this frame; the pass only runs on frames that publish them. */
    public static void submit(float start, float end)
    {
        fogStart = start;
        fogEnd = end;
        pending = true;
    }

    public static void color(float r, float g, float b)
    {
        red = r;
        green = g;
        blue = b;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        boolean run = pending;
        pending = false;
        if (!run || fogShader == null || compositeShader == null || !ClientConfig.SHADER_FOG.get() || !ShaderCompat.shaderPackInUse()) return;

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (fogTarget == null) fogTarget = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        else if (fogTarget.width != main.width || fogTarget.height != main.height) fogTarget.resize(main.width, main.height, Minecraft.ON_OSX);

        // Not FogStart / FogEnd / FogColor: vanilla overwrites uniforms of those names with the current (by now disabled) fog.
        fogShader.safeGetUniform("InvProjMat").set(new Matrix4f(event.getProjectionMatrix()).invert());
        fogShader.safeGetUniform("FogNear").set(fogStart);
        fogShader.safeGetUniform("FogFar").set(Math.max(fogEnd, fogStart + 0.1f));
        fogShader.safeGetUniform("FogTint").set(red, green, blue, ClientConfig.SHADER_FOG_OPACITY.get().floatValue());
        fogShader.setSampler("DepthSampler", main.getDepthTextureId());
        compositeShader.setSampler("FogSampler", fogTarget.getColorTextureId());

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // The fog amount travels in the alpha channel, which the pipeline may have left masked off.
        RenderSystem.colorMask(true, true, true, true);
        fogTarget.bindWrite(true);
        // Overwrite, never blend: the target is not cleared, and the shader's cached opaque blend mode may not reapply.
        RenderSystem.disableBlend();
        drawFullScreen(fogShader);
        main.bindWrite(true);
        drawFullScreen(compositeShader);
        // The composite's blend mode stays "last applied"; hand back the opaque default the vanilla shaders expect.
        new BlendMode().apply();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void drawFullScreen(ShaderInstance shader)
    {
        RenderSystem.setShader(() -> shader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1, -1, 0);
        buffer.addVertex(1, -1, 0);
        buffer.addVertex(1, 1, 0);
        buffer.addVertex(-1, 1, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    @EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration
    {
        private Registration() {}

        @SubscribeEvent
        public static void registerShaders(RegisterShadersEvent event) throws IOException
        {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "shader_fog"),
                    DefaultVertexFormat.POSITION), shader -> fogShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "shader_fog_composite"),
                    DefaultVertexFormat.POSITION), shader -> compositeShader = shader);
        }
    }
}
