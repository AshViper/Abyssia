package com.abyssia.research.client;

import com.abyssia.Abyssia;
import com.abyssia.item.scanner.LidarScannerItem;
import com.abyssia.research.scan.ScanProgressPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Scan effect while the lidar scanner is analysing: enchant-glint shimmer on the target box (front face strongest), a sweeping scan line,
 * pulsing edges and a short flash on completion. Display only; vanilla render types, so shader packs work.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ScanEffectRenderer
{
    private static final ResourceLocation GLINT = new ResourceLocation("textures/misc/enchanted_glint_item.png");
    private static final float CR = 0.35F, CG = 0.90F, CB = 1.00F;
    private static final int FLASH_TICKS = 10;

    // target picked each client tick
    private static boolean valid;
    private static Entity tEntity;
    private static AABB tBlockBox;
    private static Direction tFront = Direction.UP;
    // last drawn box (for the completion flash)
    private static AABB lastBox;
    private static Direction lastFront = Direction.UP;

    private static final double[] C = new double[12];

    private ScanEffectRenderer() {}

    private static boolean holdingScanner(LocalPlayer p)
    {
        return p.isUsingItem() && p.getUseItem().getItem() instanceof LidarScannerItem;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        valid = false;
        if (player == null || mc.level == null || !holdingScanner(player) || !ScanHud.isScanning()) return;
        pick(mc, player);
    }

    /** same targeting as the server: block outline up to 112, entity up to 32 when nearer */
    private static void pick(Minecraft mc, LocalPlayer player)
    {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult bh = mc.level.clip(new ClipContext(eye, eye.add(look.scale(112.0)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double blockDist = bh.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : eye.distanceToSqr(bh.getLocation());
        Vec3 entEnd = eye.add(look.scale(32.0));
        AABB search = player.getBoundingBox().expandTowards(look.scale(32.0)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(player, eye, entEnd, search, e -> !e.isSpectator() && e.isPickable(), 32.0 * 32.0);
        if (eh != null && eye.distanceToSqr(eh.getLocation()) < blockDist)
        {
            tEntity = eh.getEntity();
            tBlockBox = null;
            valid = true;
            return;
        }
        if (bh.getType() == HitResult.Type.MISS) return;
        BlockPos pos = bh.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        VoxelShape shape = state.getShape(mc.level, pos);
        AABB local = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        tBlockBox = local.move(pos).inflate(0.015);
        tFront = bh.getDirection();
        tEntity = null;
        valid = true;
    }

    private static AABB currentBox(float pt)
    {
        if (tEntity != null)
        {
            if (tEntity.isRemoved()) return null;
            double dx = Mth.lerp(pt, tEntity.xOld, tEntity.getX()) - tEntity.getX();
            double dy = Mth.lerp(pt, tEntity.yOld, tEntity.getY()) - tEntity.getY();
            double dz = Mth.lerp(pt, tEntity.zOld, tEntity.getZ()) - tEntity.getZ();
            return tEntity.getBoundingBox().move(dx, dy, dz).inflate(0.1);
        }
        return tBlockBox;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) return;
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();

        AABB box;
        Direction front;
        float k;
        if (valid && holdingScanner(player) && ScanHud.isScanning() && (box = currentBox(pt)) != null)
        {
            if (tEntity != null) front = Direction.getNearest(cam.x - box.getCenter().x, cam.y - box.getCenter().y, cam.z - box.getCenter().z);
            else front = tFront;
            lastBox = box;
            lastFront = front;
            k = 0.75F + 0.25F * ScanHud.progress();
        }
        else
        {
            byte st = ScanHud.resultState();
            boolean complete = st != ScanProgressPayload.SCANNING && st != ScanProgressPayload.ABORTED && st != ScanProgressPayload.NO_TARGET;
            int since = ScanHud.sinceResult();
            if (lastBox == null || !complete || since > FLASH_TICKS)
            {
                if (!ScanHud.isScanning()) lastBox = null;
                return;
            }
            float f = Math.min(1F, (since + pt) / FLASH_TICKS);
            box = lastBox.inflate(0.15 * f);
            front = lastFront;
            k = 1.6F * (1F - f);
            if (k <= 0.01F) return;
        }
        draw(mc, event.getPoseStack(), cam, box, front, k);
    }

    private static void draw(Minecraft mc, PoseStack pose, Vec3 cam, AABB world, Direction front, float k)
    {
        AABB b = world.move(-cam.x, -cam.y, -cam.z);
        float t = (Util.getMillis() % 1000000L) / 1000F;
        PoseStack.Pose last = pose.last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // 1. enchant shimmer: two glint layers scrolling in different directions
        RenderType glintType = RenderType.eyes(GLINT);
        VertexConsumer glint = buffers.getBuffer(glintType);
        for (Direction d : Direction.values())
        {
            float a = Math.min(1F, (d == front ? 0.75F : 0.22F) * k);
            corners(b, d, C);
            glintQuad(glint, last, d, a, 0.5F, t * 0.05F, t * 0.025F, false);
            glintQuad(glint, last, d, a * 0.8F, 0.37F, -t * 0.035F, t * 0.045F, true);
        }
        buffers.endBatch(glintType);

        // 2. scan line sweeping up and down, just outside the four side faces
        double h = b.maxY - b.minY;
        double yc = b.minY + (Math.sin(t * Math.PI * 2 / 1.5) * 0.5 + 0.5) * h;
        RenderType lightning = RenderType.lightning();
        VertexConsumer band = buffers.getBuffer(lightning);
        bandRing(band, last, b, yc, 0.07, 0.03, 0.30F * k);
        bandRing(band, last, b, yc, 0.02, 0.035, 0.85F * k);
        buffers.endBatch(lightning);

        // 3. edges
        float pulse = (0.6F + 0.2F * Mth.sin(t * 6F)) * Math.min(1F, k);
        RenderType linesType = RenderType.lines();
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(linesType), b, CR, CG, CB, pulse);
        buffers.endBatch(linesType);
    }

    private static void bandRing(VertexConsumer vc, PoseStack.Pose last, AABB b, double yc, double halfH, double out, float a)
    {
        AABB band = new AABB(b.minX, yc - halfH, b.minZ, b.maxX, yc + halfH, b.maxZ).inflate(out, 0, out);
        for (Direction d : Direction.Plane.HORIZONTAL)
        {
            corners(band, d, C);
            for (int i = 0; i < 4; i++)
                vc.vertex(last.pose(), (float) C[i * 3], (float) C[i * 3 + 1], (float) C[i * 3 + 2]).color(CR, CG, CB, Math.min(1F, a)).endVertex();
        }
    }

    private static void glintQuad(VertexConsumer vc, PoseStack.Pose last, Direction d, float a, float scale, float uo, float vo, boolean swap)
    {
        float w = (float) Math.sqrt(sq(C[3] - C[0]) + sq(C[4] - C[1]) + sq(C[5] - C[2]));
        float h = (float) Math.sqrt(sq(C[6] - C[3]) + sq(C[7] - C[4]) + sq(C[8] - C[5]));
        float nx = d.getStepX(), ny = d.getStepY(), nz = d.getStepZ();
        for (int i = 0; i < 4; i++)
        {
            float u = (i == 1 || i == 2 ? w : 0F) * scale + uo;
            float v = (i >= 2 ? h : 0F) * scale + vo;
            vc.vertex(last.pose(), (float) C[i * 3], (float) C[i * 3 + 1], (float) C[i * 3 + 2]).color(CR, CG, CB, a)
                    .uv(swap ? v : u, swap ? u : v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(last.normal(), nx, ny, nz).endVertex();
        }
    }

    private static double sq(double v) { return v * v; }

    /** the four corners of face d of b, counter-clockwise seen from outside, bottom-left first */
    private static void corners(AABB b, Direction d, double[] o)
    {
        switch (d)
        {
            case UP -> set(o, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ, b.minX, b.maxY, b.minZ);
            case DOWN -> set(o, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ);
            case NORTH -> set(o, b.maxX, b.minY, b.minZ, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ);
            case SOUTH -> set(o, b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ);
            case WEST -> set(o, b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ);
            default -> set(o, b.maxX, b.minY, b.maxZ, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ);
        }
    }

    private static void set(double[] o, double... v)
    {
        System.arraycopy(v, 0, o, 0, 12);
    }
}
