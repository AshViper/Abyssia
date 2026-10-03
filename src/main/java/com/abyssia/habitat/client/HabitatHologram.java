package com.abyssia.habitat.client;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatBuilder;
import com.abyssia.habitat.HabitatConstructorItem;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildPlacement;
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
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * H02 / BT01a hologram while the constructor is in the main hand: the selected entry's ghost blocks
 * (BuildPlacement.ghost), translucent, tinted by state (cyan = buildable, light blue = snapped, red = blocked,
 * yellow = missing materials, orange = dismantle preview via {@link #setDismantlePreview}), plus the outline and the
 * placement's highlight boxes (connector panels). The mesh is a cached vertex buffer rebuilt only when the placement
 * changes; the tint is the shader colour.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HabitatHologram
{
    private static final float ALPHA = 0.4f;
    public static final float[] VALID = {0.35f, 1.0f, 0.95f}, SNAPPED = {0.55f, 0.8f, 1.0f}, INVALID = {1.0f, 0.3f, 0.3f},
            MISSING = {1.0f, 0.9f, 0.3f}, DISMANTLE = {1.0f, 0.55f, 0.15f};

    /** BT01b hook: a box to show in orange instead of the build hologram (null = no dismantle target). */
    @FunctionalInterface
    public interface DismantlePreview
    {
        @Nullable
        AABB box(LocalPlayer player, ItemStack stack, float partialTick);
    }

    @Nullable
    private static DismantlePreview dismantlePreview;

    @Nullable
    private static BuildPlacement plan;
    private static float[] tint = VALID;
    private static long planTick = Long.MIN_VALUE;
    private static float planYaw = Float.NaN, planPitch = Float.NaN;
    private static int planRot = -1, planDist = -1;
    private static BuildEntry planEntry;

    private static VertexBuffer mesh;
    private static BufferBuilder builder;
    /** entry + placement the mesh was built for (placements are records: value equality) */
    private static BuildEntry meshEntry;
    private static BuildPlacement meshPlan;
    /** the last placement had no ghost blocks (TARGET entries): draw outlines only */
    private static boolean meshEmpty;

    private HabitatHologram() {}

    public static void setDismantlePreview(@Nullable DismantlePreview preview)
    {
        dismantlePreview = preview;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) return;
        ItemStack stack = HabitatClient.heldConstructor();
        if (stack == null) return;
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        AABB dismantle = dismantlePreview == null ? null : dismantlePreview.box(player, stack, event.getPartialTick());
        if (dismantle != null)
        {
            drawLines(mc, pose, cam, dismantle, DISMANTLE, null);
            return;
        }
        update(mc, player, stack, event.getPartialTick());
        if (plan == null) return;
        drawMesh(pose, event, cam);
        drawLines(mc, pose, cam, plan.box(), tint, plan);
    }

    private static void drawLines(Minecraft mc, PoseStack pose, Vec3 cam, AABB box, float[] colour, @Nullable BuildPlacement highlights)
    {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        LevelRenderer.renderLineBox(pose, lines, box.inflate(0.002), colour[0], colour[1], colour[2], 1.0f);
        if (highlights != null)
            for (AABB panel : highlights.highlights())
                LevelRenderer.renderLineBox(pose, lines, panel.inflate(0.01), SNAPPED[0], SNAPPED[1], SNAPPED[2], 1.0f);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /** Re-plans at most once per tick (or when the view / entry / rotation / distance changes); rebuilds the mesh on a new placement. */
    private static void update(Minecraft mc, LocalPlayer player, ItemStack stack, float partialTick)
    {
        BuildEntry entry = HabitatConstructorItem.entry(stack);
        int rot = HabitatConstructorItem.rotation(stack, player);
        int dist = HabitatConstructorItem.distance(stack);
        long tick = mc.level.getGameTime();
        if (entry != planEntry || rot != planRot || dist != planDist || tick != planTick
                || player.getYRot() != planYaw || player.getXRot() != planPitch)
        {
            plan = entry.plan(player, rot, dist, partialTick);
            if (plan != null)
            {
                boolean placeable = entry.check(mc.level, player, plan).ok();
                boolean paid = player.getAbilities().instabuild || HabitatBuilder.missing(player, entry.cost(mc.level, plan)).isEmpty();
                tint = !placeable ? INVALID : !paid ? MISSING : plan.snapped() ? SNAPPED : VALID;
            }
            planEntry = entry;
            planRot = rot;
            planDist = dist;
            planTick = tick;
            planYaw = player.getYRot();
            planPitch = player.getXRot();
        }
        if (plan != null && (entry != meshEntry || !Objects.equals(plan, meshPlan))) rebuild(mc, entry, plan);
    }

    private static void rebuild(Minecraft mc, BuildEntry entry, BuildPlacement target)
    {
        Map<BlockPos, BlockState> ghost = target.ghost(mc.level);
        meshEntry = entry;
        meshPlan = target;
        meshEmpty = ghost.isEmpty();
        if (meshEmpty) return;
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        // one off-heap builder for the session (a new BufferBuilder per rebuild would leak native memory)
        if (builder == null) builder = new BufferBuilder(DefaultVertexFormat.BLOCK.getVertexSize() * 4 * 6 * 1024);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        PoseStack pose = new PoseStack();
        BlockPos origin = target.origin();
        for (Map.Entry<BlockPos, BlockState> e : ghost.entrySet())
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
    }

    private static void drawMesh(PoseStack pose, RenderLevelStageEvent event, Vec3 cam)
    {
        if (mesh == null || meshPlan == null || meshEmpty) return;
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
