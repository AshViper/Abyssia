package com.abyssia.client.light;

import com.abyssia.Abyssia;
import com.abyssia.client.ShaderCompat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link Spotlight}s shone onto the blocks, drawn on this client only (ported from AshWarfare's FlashlightProjector):
 * <ul>
 * <li>each pixel of the blocks in the cone is lit by its distance from the light and from the cone's middle
 * (shaders/core/spotlight.fsh) and how much it faces the light: a round spot with a hotspot
 * and soft edges;</li>
 * <li>shadows: the blocks in the cone are first drawn from the light into a depth map, and pixels behind something
 * closer to the light stay dark.</li>
 * </ul>
 * The blocks are drawn a second time with the light added ({@link BlockMeshes}); the level itself is not changed.
 * Only the {@link #MAX_LIGHTS} lights closest to the camera are shone.
 * <p>
 * Without a shader pack this runs right after the blocks and entities, before the water, which is drawn over it
 * (seen from underwater the water has no faces, so nothing covers it). With Iris / Oculus it runs at AFTER_LEVEL over
 * the finished picture, before {@link com.abyssia.client.ShaderFogPass} lays the mod's fog over it.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SpotlightProjector
{
    private static final int MAX_LIGHTS = 4;
    // lights farther than their range plus this from the camera cannot light anything that is drawn, in blocks
    private static final double MAX_DISTANCE = 96.0;
    private static final int SHADOW_MAP_SIZE = 1024;
    private static final float SHADOW_NEAR = 0.05f;
    // the shadow map reaches a little past the edge of the cone
    private static final float SHADOW_MARGIN = 1.1f;
    // how bright a full-strength light is added right in front of it; above 1 overexposes the hotspot up close
    private static final float INTENSITY = 2.2f;
    // brings the light toward the camera in depth, so it is drawn on the blocks it copies; shader packs jitter more
    private static final float DEPTH_OFFSET_FACTOR = -1.0f;
    private static final float SHADER_PACK_DEPTH_OFFSET_FACTOR = -2.0f;
    private static final float DEPTH_OFFSET_UNITS = -10.0f;
    // copies are made this much wider around the cone, in radians, so they are there when it turns
    private static final double MESH_MARGIN = Math.toRadians(10.0);
    // half the diagonal of a section, in blocks
    private static final double SECTION_RADIUS = 8.0 * Math.sqrt(3.0);
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private static final List<Spotlight.Source> SOURCES = new CopyOnWriteArrayList<>();

    @Nullable private static ShaderInstance lightShader;
    @Nullable private static ShaderInstance shadowShader;
    @Nullable private static TextureTarget shadowMap;
    // the fog of the blocks this frame, kept for when this is drawn after the level
    private static float fogStart = Float.MAX_VALUE;
    private static float fogEnd = Float.MAX_VALUE;
    private static int fogShape;

    private SpotlightProjector() {}

    public static void addSource(Spotlight.Source source)
    {
        SOURCES.add(source);
    }

    /** Every light that is on this frame (projector and beams). */
    static List<Spotlight> collect(ClientLevel level, float partialTick)
    {
        List<Spotlight> lights = new ArrayList<>();
        for (Spotlight.Source source : SOURCES) source.collect(level, partialTick, lights::add);
        return lights;
    }

    private static RenderLevelStageEvent.Stage stage()
    {
        return ShaderCompat.shaderPackInUse() ? RenderLevelStageEvent.Stage.AFTER_LEVEL : RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES;
    }

    // before ShaderFogPass, which fogs the light over with a shader pack on
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)
        {
            fogStart = RenderSystem.getShaderFogStart();
            fogEnd = RenderSystem.getShaderFogEnd();
            fogShape = RenderSystem.getShaderFogShape().getIndex();
        }
        boolean shaderPack = ShaderCompat.shaderPackInUse();
        ClientLevel level = Minecraft.getInstance().level;
        if (event.getStage() != stage() || level == null || lightShader == null || shadowShader == null || SOURCES.isEmpty()) return;
        Camera camera = event.getCamera();
        Matrix4f modelView = new Matrix4f(event.getPoseStack().last().pose());
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        // made here, not taken from the event: after the level, the renderer's frustum may be one Iris left behind
        Frustum frustum = new Frustum(modelView, projection);
        frustum.prepare(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
        List<Spotlight> lights = visible(level, camera, frustum, event.getPartialTick());
        List<List<BlockMeshes.Mesh>> meshes = new ArrayList<>();
        for (Spotlight light : lights) meshes.add(meshes(level, light));
        BlockMeshes.make(level);
        if (lights.isEmpty()) return;
        if (shaderPack) syncRenderState();
        float depthOffset = shaderPack ? SHADER_PACK_DEPTH_OFFSET_FACTOR : DEPTH_OFFSET_FACTOR;
        UntrackedState state = UntrackedState.reset();
        for (int i = 0; i < lights.size(); i++)
        {
            List<BlockMeshes.Mesh> made = meshes.get(i).stream().filter(mesh -> mesh.buffer != null).toList();
            if (!made.isEmpty()) shine(lights.get(i), made, camera, modelView, projection, depthOffset);
        }
        state.restore();
        if (shaderPack) syncRenderState();
    }

    /** Makes Minecraft's record of the render state match GL again (a shader pack may have changed it behind its back). */
    private static void syncRenderState()
    {
        ForcedState.depth(true, GL11.GL_LEQUAL, true);
        ForcedState.blend(false);
        ForcedState.cull(true);
        ForcedState.colorMask(true);
        ForcedState.noPolygonOffset();
        unbindTextures();
    }

    /**
     * Sets tracked render state so it really reaches GL: Minecraft skips a call when it thinks the state is already so,
     * which a shader pack may have changed. Each is first set to something else.
     */
    static final class ForcedState
    {
        private ForcedState() {}

        static void depth(boolean test, int function, boolean write)
        {
            RenderSystem.disableDepthTest();
            RenderSystem.enableDepthTest();
            if (!test) RenderSystem.disableDepthTest();
            RenderSystem.depthFunc(function == GL11.GL_ALWAYS ? GL11.GL_LEQUAL : GL11.GL_ALWAYS);
            RenderSystem.depthFunc(function);
            RenderSystem.depthMask(!write);
            RenderSystem.depthMask(write);
        }

        // adds the light to what is there when enabled
        static void blend(boolean enabled)
        {
            RenderSystem.disableBlend();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ZERO);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            if (!enabled) RenderSystem.disableBlend();
        }

        static void cull(boolean enabled)
        {
            RenderSystem.disableCull();
            RenderSystem.enableCull();
            if (!enabled) RenderSystem.disableCull();
        }

        static void colorMask(boolean write)
        {
            RenderSystem.colorMask(!write, !write, !write, !write);
            RenderSystem.colorMask(write, write, write, write);
        }

        static void polygonOffset(float factor, float units)
        {
            RenderSystem.disablePolygonOffset();
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(factor + 1.0f, units + 1.0f);
            RenderSystem.polygonOffset(factor, units);
        }

        static void noPolygonOffset()
        {
            polygonOffset(0.0f, 0.0f);
            RenderSystem.disablePolygonOffset();
        }
    }

    /** Unbinds the units the shaders read from, so {@link ShaderInstance#apply} really binds theirs. */
    private static void unbindTextures()
    {
        for (int unit = 0; unit < UntrackedState.UNITS; unit++)
        {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + (unit + 1) % UntrackedState.UNITS);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
            GlStateManager._bindTexture(1);
            GlStateManager._bindTexture(0);
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    /**
     * GL state Minecraft does not track, set as its drawing expects while the lights are drawn and put back afterwards,
     * since shader packs may leave it otherwise: sampler objects, front face, blend equation, polygon mode, logic op,
     * rasterizer discard, clip distances.
     */
    record UntrackedState(int[] samplers, int frontFace, int blendEquationRgb, int blendEquationAlpha, int polygonMode, boolean logicOp,
                          boolean rasterizerDiscard, boolean[] clipDistances)
    {
        private static final int UNITS = 2;
        private static final int CLIP_DISTANCES = 8;

        static UntrackedState reset()
        {
            int[] samplers = new int[UNITS];
            if (samplerObjects())
            {
                int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                for (int unit = 0; unit < UNITS; unit++)
                {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                    samplers[unit] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
                    GL33.glBindSampler(unit, 0);
                }
                GL13.glActiveTexture(active);
            }
            boolean[] clipDistances = new boolean[CLIP_DISTANCES];
            for (int i = 0; i < CLIP_DISTANCES; i++) clipDistances[i] = GL11.glIsEnabled(GL30.GL_CLIP_DISTANCE0 + i);
            UntrackedState state = new UntrackedState(samplers, GL11.glGetInteger(GL11.GL_FRONT_FACE),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
                    GL11.glGetInteger(GL11.GL_POLYGON_MODE), GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP), GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD),
                    clipDistances);
            GL11.glFrontFace(GL11.GL_CCW);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
            GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            for (int i = 0; i < CLIP_DISTANCES; i++) GL11.glDisable(GL30.GL_CLIP_DISTANCE0 + i);
            return state;
        }

        void restore()
        {
            if (samplerObjects())
                for (int unit = 0; unit < UNITS; unit++) GL33.glBindSampler(unit, samplers[unit]);
            GL11.glFrontFace(frontFace);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygonMode);
            set(GL11.GL_COLOR_LOGIC_OP, logicOp);
            set(GL30.GL_RASTERIZER_DISCARD, rasterizerDiscard);
            for (int i = 0; i < CLIP_DISTANCES; i++) set(GL30.GL_CLIP_DISTANCE0 + i, clipDistances[i]);
        }

        private static void set(int capability, boolean enabled)
        {
            if (enabled) GL11.glEnable(capability);
            else GL11.glDisable(capability);
        }

        // sampler objects came with OpenGL 3.3; Minecraft needs only 3.2, but shader packs do not run without them
        private static boolean samplerObjects()
        {
            GLCapabilities capabilities = GL.getCapabilities();
            return capabilities.OpenGL33 || capabilities.GL_ARB_sampler_objects;
        }
    }

    // the lights that could light something drawn, closest to the camera first
    private static List<Spotlight> visible(ClientLevel level, Camera camera, Frustum frustum, float partialTick)
    {
        List<Spotlight> lights = new ArrayList<>();
        for (Spotlight light : collect(level, partialTick))
        {
            if (light.origin().distanceTo(camera.getPosition()) > light.range() + MAX_DISTANCE
                    || !frustum.isVisible(coneBounds(light.origin(), light.direction(), light.range(), light.halfAngle()))) continue;
            lights.add(light);
        }
        lights.sort(Comparator.comparingDouble(light -> light.origin().distanceToSqr(camera.getPosition())));
        return lights.size() > MAX_LIGHTS ? lights.subList(0, MAX_LIGHTS) : lights;
    }

    private static AABB coneBounds(Vec3 origin, Vec3 direction, float range, double halfAngle)
    {
        Vec3 end = origin.add(direction.scale(range));
        double radius = range * Math.tan(halfAngle);
        // how far the rim of the cone's end reaches along each axis
        Vec3 rim = new Vec3(radius * Math.sqrt(Math.max(1.0 - direction.x * direction.x, 0.0)),
                radius * Math.sqrt(Math.max(1.0 - direction.y * direction.y, 0.0)),
                radius * Math.sqrt(Math.max(1.0 - direction.z * direction.z, 0.0)));
        return new AABB(origin, origin).minmax(new AABB(end.subtract(rim), end.add(rim)));
    }

    // the copies of the sections the cone reaches into, a little wider
    private static List<BlockMeshes.Mesh> meshes(ClientLevel level, Spotlight light)
    {
        double halfAngle = Math.min(light.halfAngle() + MESH_MARGIN, Math.toRadians(80.0));
        AABB bounds = coneBounds(light.origin(), light.direction(), light.range(), halfAngle);
        double sin = Math.sin(halfAngle);
        double cos = Math.cos(halfAngle);
        List<BlockMeshes.Mesh> meshes = new ArrayList<>();
        for (int x = SectionPos.blockToSectionCoord(bounds.minX); x <= SectionPos.blockToSectionCoord(bounds.maxX); x++)
            for (int y = SectionPos.blockToSectionCoord(bounds.minY); y <= SectionPos.blockToSectionCoord(bounds.maxY); y++)
                for (int z = SectionPos.blockToSectionCoord(bounds.minZ); z <= SectionPos.blockToSectionCoord(bounds.maxZ); z++)
                {
                    SectionPos pos = SectionPos.of(x, y, z);
                    Vec3 toSection = pos.center().getCenter().subtract(light.origin());
                    double along = toSection.dot(light.direction());
                    double across = toSection.subtract(light.direction().scale(along)).length();
                    // a ball around the section: outside the cone when behind the light, past its range or off to the side
                    if (along < -SECTION_RADIUS || along > light.range() + SECTION_RADIUS || across * cos - along * sin > SECTION_RADIUS) continue;
                    meshes.add(BlockMeshes.get(level, pos, toSection.length()));
                }
        return meshes;
    }

    private static void shine(Spotlight light, List<BlockMeshes.Mesh> meshes, Camera camera, Matrix4f modelView, Matrix4f projection,
                              float depthOffset)
    {
        Vec3 cameraPosition = camera.getPosition();
        Vector3f origin = light.origin().subtract(cameraPosition).toVector3f();
        Vector3f direction = light.direction().toVector3f();
        Vec3 up = Math.abs(light.direction().y) < 0.99 ? UP : new Vec3(1.0, 0.0, 0.0);
        // from positions relative to the camera to the light's view
        Matrix4f lightViewProjection = new Matrix4f()
                .perspective(2.0f * Math.min(light.halfAngle() * SHADOW_MARGIN, 1.5f), 1.0f, SHADOW_NEAR, light.range())
                .lookAt(origin, origin.add(direction, new Vector3f()), up.toVector3f());
        drawShadowMap(meshes, lightViewProjection, cameraPosition);
        drawLight(light, meshes, lightViewProjection, origin, direction, cameraPosition, modelView, projection, depthOffset);
    }

    // the depth of the blocks in the cone as seen from the light; cutout blocks only where they are not see-through
    private static void drawShadowMap(List<BlockMeshes.Mesh> meshes, Matrix4f lightViewProjection, Vec3 cameraPosition)
    {
        ShaderInstance shader = shadowShader;
        if (shadowMap == null) shadowMap = new TextureTarget(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, true, Minecraft.ON_OSX);
        // through another framebuffer first, so the binding really changes even if a cache of what is bound is stale
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        shadowMap.bindWrite(true);
        // before clearing, which only clears the depth while it can be written
        ForcedState.depth(true, GL11.GL_LEQUAL, true);
        RenderSystem.clearDepth(1.0);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        ForcedState.blend(false);
        // faces seen from behind still cast shadows (plants)
        ForcedState.cull(false);
        ForcedState.colorMask(false);
        // pushed away from the light where faces slope away, so they do not shadow themselves
        ForcedState.polygonOffset(1.5f, 4.0f);

        shader.setSampler("Sampler0", Minecraft.getInstance().getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS));
        shader.safeGetUniform("LightViewProj").set(lightViewProjection);
        drawMeshes(shader, meshes, cameraPosition);

        RenderSystem.polygonOffset(0.0f, 0.0f);
        RenderSystem.disablePolygonOffset();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableCull();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    // the light added to the blocks in the cone where they can be seen
    private static void drawLight(Spotlight light, List<BlockMeshes.Mesh> meshes, Matrix4f lightViewProjection, Vector3f origin, Vector3f direction,
                                  Vec3 cameraPosition, Matrix4f modelView, Matrix4f projection, float depthOffset)
    {
        ShaderInstance shader = lightShader;
        ForcedState.depth(true, GL11.GL_LEQUAL, false);
        ForcedState.cull(true);
        ForcedState.colorMask(true);
        ForcedState.blend(true);
        ForcedState.polygonOffset(depthOffset, DEPTH_OFFSET_UNITS);

        float brightness = INTENSITY * light.brightness();
        int color = light.color();
        shader.setSampler("Sampler0", Minecraft.getInstance().getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS));
        shader.setSampler("ShadowMap", shadowMap.getDepthTextureId());
        shader.safeGetUniform("ModelViewMat").set(modelView);
        shader.safeGetUniform("ProjMat").set(projection);
        shader.safeGetUniform("LightViewProj").set(lightViewProjection);
        shader.safeGetUniform("LightPosition").set(origin.x, origin.y, origin.z);
        shader.safeGetUniform("LightDirection").set(direction.x, direction.y, direction.z);
        shader.safeGetUniform("LightColor").set(FastColor.ARGB32.red(color) / 255.0f * brightness,
                FastColor.ARGB32.green(color) / 255.0f * brightness, FastColor.ARGB32.blue(color) / 255.0f * brightness);
        shader.safeGetUniform("LightRange").set(light.range());
        shader.safeGetUniform("LightHalfAngle").set(light.halfAngle());
        shader.safeGetUniform("ShadowClip").set(SHADOW_NEAR, light.range());
        // not FogStart / FogEnd / FogShape: vanilla overwrites uniforms of those names with the current fog
        shader.safeGetUniform("LightFogStart").set(fogStart);
        shader.safeGetUniform("LightFogEnd").set(fogEnd);
        shader.safeGetUniform("LightFogShape").set(fogShape);
        drawMeshes(shader, meshes, cameraPosition);

        RenderSystem.polygonOffset(0.0f, 0.0f);
        RenderSystem.disablePolygonOffset();
        // the shader's additive blend mode stays "last applied"; hand back the opaque default
        new BlendMode().apply();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    // like vanilla draws its sections: each with its origin relative to the camera
    private static void drawMeshes(ShaderInstance shader, List<BlockMeshes.Mesh> meshes, Vec3 cameraPosition)
    {
        unbindTextures();
        shader.apply();
        for (BlockMeshes.Mesh mesh : meshes)
        {
            if (shader.CHUNK_OFFSET != null)
            {
                shader.CHUNK_OFFSET.set((float) (mesh.origin().getX() - cameraPosition.x), (float) (mesh.origin().getY() - cameraPosition.y),
                        (float) (mesh.origin().getZ() - cameraPosition.z));
                shader.CHUNK_OFFSET.upload();
            }
            VertexBuffer buffer = mesh.buffer;
            buffer.bind();
            buffer.draw();
        }
        if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0.0f, 0.0f, 0.0f);
        shader.clear();
        VertexBuffer.unbind();
    }

    @Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration
    {
        private Registration() {}

        @SubscribeEvent
        public static void registerShaders(RegisterShadersEvent event) throws IOException
        {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "spotlight"),
                    DefaultVertexFormat.BLOCK), shader -> lightShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "spotlight_shadow"),
                    DefaultVertexFormat.BLOCK), shader -> shadowShader = shader);
        }
    }
}
