package com.abyssia.habitat.client;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.HabitatConstructorItem;
import com.abyssia.habitat.HabitatMode;
import com.abyssia.habitat.HabitatPlan;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * H02 hologram while the constructor is in the main hand: the real shell blocks, translucent, tinted by state
 * (cyan = buildable, light blue = snapped to a hatch, red = blocked, yellow = missing materials), plus the connector
 * panels outlined. The mesh is a cached vertex buffer rebuilt only when the plan (mode / position / facing) changes;
 * the tint is the shader colour.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HabitatHologram
{
    private static final float ALPHA = 0.4f;
    private static final float[] VALID = {0.35f, 1.0f, 0.95f}, SNAPPED = {0.55f, 0.8f, 1.0f}, INVALID = {1.0f, 0.3f, 0.3f},
            MISSING = {1.0f, 0.9f, 0.3f};

    private static HabitatPlan plan;
    private static float[] tint = VALID;
    private static long planTick = Long.MIN_VALUE;
    private static float planYaw = Float.NaN, planPitch = Float.NaN;
    private static int planRot = -1;
    private static HabitatMode planMode;

    private static VertexBuffer mesh;
    private static BufferBuilder builder;
    /** plan the mesh was built for (record equality: mode, origin, forward, snapped) */
    private static HabitatPlan meshPlan;

    private HabitatHologram() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) return;
        ItemStack stack = HabitatClient.heldConstructor();
        if (stack == null) return;
        update(mc, player, stack, event.getPartialTick());

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        drawMesh(pose, event, cam);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        LevelRenderer.renderLineBox(pose, lines, plan.box().inflate(0.002), tint[0], tint[1], tint[2], 1.0f);
        for (AABB panel : plan.connectorPanels())
            LevelRenderer.renderLineBox(pose, lines, panel.inflate(0.01), SNAPPED[0], SNAPPED[1], SNAPPED[2], 1.0f);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /** Re-plans at most once per tick (or when the view / mode / rotation changes); rebuilds the mesh on a new plan. */
    private static void update(Minecraft mc, LocalPlayer player, ItemStack stack, float partialTick)
    {
        HabitatMode mode = HabitatConstructorItem.mode(stack);
        int rot = HabitatConstructorItem.rotation(stack, player);
        long tick = mc.level.getGameTime();
        if (plan == null || mode != planMode || rot != planRot || tick != planTick
                || player.getYRot() != planYaw || player.getXRot() != planPitch)
        {
            plan = HabitatPlan.plan(player, mode, rot, partialTick);
            boolean placeable = plan.check(mc.level, player, stack) == HabitatPlan.Problem.NONE;
            boolean paid = player.getAbilities().instabuild || HabitatBuilder.missing(player, mode).isEmpty();
            tint = !placeable ? INVALID : !paid ? MISSING : plan.snapped() ? SNAPPED : VALID;
            planMode = mode;
            planRot = rot;
            planTick = tick;
            planYaw = player.getYRot();
            planPitch = player.getXRot();
        }
        if (!plan.equals(meshPlan)) rebuild(mc, plan);
    }

    private static void rebuild(Minecraft mc, HabitatPlan target)
    {
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        // one off-heap builder for the session (a new BufferBuilder per rebuild would leak native memory)
        if (builder == null) builder = new BufferBuilder(DefaultVertexFormat.BLOCK.getVertexSize() * 4 * 6 * 1024);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        PoseStack pose = new PoseStack();
        BlockPos origin = target.origin();
        for (Map.Entry<BlockPos, BlockState> e : HabitatBuilder.shellStates(target, mc.level).entrySet())
        {
            BlockPos pos = e.getKey();
            pose.pushPose();
            pose.translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
            blocks.getModelRenderer().renderModel(pose.last(), builder, e.getValue(), blocks.getBlockModel(e.getValue()),
                    1.0f, 1.0f, 1.0f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.translucent());
            pose.popPose();
        }
        if (mesh == null) mesh = new VertexBuffer(VertexBuffer.Usage.STATIC);
        mesh.bind();
        mesh.upload(builder.end());
        VertexBuffer.unbind();
        meshPlan = target;
    }

    private static void drawMesh(PoseStack pose, RenderLevelStageEvent event, Vec3 cam)
    {
        if (mesh == null || meshPlan == null) return;
        ShaderInstance shader = GameRenderer.getRendertypeTranslucentShader();
        if (shader == null) return;
        BlockPos origin = meshPlan.origin();
        RenderType type = RenderType.translucent();
        type.setupRenderState();
        if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0.0f, 0.0f, 0.0f);
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], ALPHA);
        pose.pushPose();
        pose.translate(origin.getX() - cam.x, origin.getY() - cam.y, origin.getZ() - cam.z);
        mesh.bind();
        mesh.drawWithShader(pose.last().pose(), event.getProjectionMatrix(), shader);
        VertexBuffer.unbind();
        pose.popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        type.clearRenderState();
    }
}
