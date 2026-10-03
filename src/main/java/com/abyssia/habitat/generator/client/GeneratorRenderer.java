package com.abyssia.habitat.generator.client;

import com.abyssia.Abyssia;
import com.abyssia.habitat.generator.GeneratorBlockEntity;
import com.abyssia.habitat.generator.GeneratorKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

/**
 * BT01d: draws a whole generator from the element models written by tools/bt01/generator_assets.py. Model space =
 * the structure with x mirrored (x' = width - x, so the transform is a pure rotation), split into 3 x 3 x 3 block
 * chunks (element coordinates must stay within -16..32): chunk (cx, cy, cz) holds structure blocks cx..cx+2 shifted
 * by -(c + 1) blocks. Each chunk has three layers: _c cutout (lit), _t translucent (tank glass), _e emissive (full
 * bright). The turbine rotor (hub + one blade drawn 3 times at 120 degrees) spins around local z.
 */
public class GeneratorRenderer implements BlockEntityRenderer<GeneratorBlockEntity>
{
    private static final String[] LAYERS = {"c", "t", "e"};
    /** rotor hub centre, structure blocks (local x right, y up, z forward) */
    private static final double ROTOR_X = 2.5, ROTOR_Y = 2.5, ROTOR_Z = 4.5;

    public GeneratorRenderer(BlockEntityRendererProvider.Context context) {}

    public static ResourceLocation model(String name)
    {
        return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "block/generator/" + name);
    }

    public static ResourceLocation chunkModel(GeneratorKind kind, int cx, int cy, int cz, String layer)
    {
        return model(kind.id + "_" + cx + "_" + cy + "_" + cz + "_" + layer);
    }

    /** chunk origins (blocks) along an axis of length n */
    private static int[] chunks(int n)
    {
        return n > 3 ? new int[]{0, 3} : new int[]{0};
    }

    /** every additional model to register (ModelEvent.RegisterAdditional) */
    public static List<ResourceLocation> allModels()
    {
        List<ResourceLocation> out = new ArrayList<>();
        for (GeneratorKind kind : GeneratorKind.values())
            for (int cx : chunks(kind.width))
                for (int cy : chunks(kind.height))
                    for (int cz : chunks(kind.depth))
                        for (String layer : LAYERS) out.add(chunkModel(kind, cx, cy, cz, layer));
        for (String layer : LAYERS)
        {
            out.add(model("current_turbine_hub_" + layer));
            out.add(model("current_turbine_blade_" + layer));
        }
        return out;
    }

    @Override
    public void render(GeneratorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay)
    {
        GeneratorKind kind = be.kind();
        Direction forward = be.forward();
        BlockPos controller = be.getBlockPos();
        BlockPos origin = kind.origin(controller, forward);
        // model x axis = left; anchor = centre of structure cell (width - 1, 0, 0)
        BlockPos anchor = GeneratorKind.at(origin, forward, kind.width - 1, 0, 0);

        pose.pushPose();
        pose.translate(anchor.getX() - controller.getX() + 0.5, anchor.getY() - controller.getY() + 0.5, anchor.getZ() - controller.getZ() + 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-forward.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);

        for (int cx : chunks(kind.width))
            for (int cy : chunks(kind.height))
                for (int cz : chunks(kind.depth))
                {
                    pose.pushPose();
                    pose.translate(cx + 1, cy + 1, cz + 1);
                    drawLayers(pose, buffers, kind.id + "_" + cx + "_" + cy + "_" + cz + "_", light, overlay);
                    pose.popPose();
                }

        if (kind == GeneratorKind.CURRENT_TURBINE)
        {
            float angle = be.rotorAngle(partialTick);
            pose.pushPose();
            pose.translate(kind.width - ROTOR_X, ROTOR_Y, ROTOR_Z);
            pose.mulPose(Axis.ZP.rotationDegrees(angle));
            // rotor models: axis through (8, -8, 8) pixels
            pose.pushPose();
            pose.translate(-0.5, 0.5, -0.5);
            drawLayers(pose, buffers, "current_turbine_hub_", light, overlay);
            pose.popPose();
            for (int i = 0; i < 3; i++)
            {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(120f * i));
                pose.translate(-0.5, 0.5, -0.5);
                drawLayers(pose, buffers, "current_turbine_blade_", light, overlay);
                pose.popPose();
            }
            pose.popPose();
        }
        pose.popPose();
    }

    private static void drawLayers(PoseStack pose, MultiBufferSource buffers, String prefix, int light, int overlay)
    {
        draw(pose, buffers, model(prefix + "c"), Sheets.cutoutBlockSheet(), light, overlay);
        draw(pose, buffers, model(prefix + "t"), Sheets.translucentCullBlockSheet(), light, overlay);
        draw(pose, buffers, model(prefix + "e"), Sheets.cutoutBlockSheet(), LightTexture.FULL_BRIGHT, overlay);
    }

    private static void draw(PoseStack pose, MultiBufferSource buffers, ResourceLocation id, RenderType type, int light, int overlay)
    {
        Minecraft mc = Minecraft.getInstance();
        ModelManager models = mc.getModelManager();
        BakedModel model = models.getModel(ModelResourceLocation.standalone(id));
        if (model == models.getMissingModel()) return;
        ModelBlockRenderer renderer = mc.getBlockRenderer().getModelRenderer();
        renderer.renderModel(pose.last(), buffers.getBuffer(type), null, model, 1.0f, 1.0f, 1.0f, light, overlay, ModelData.EMPTY, type);
    }

    /** NeoForge: render bounds live on the renderer (Forge: BlockEntity#getRenderBoundingBox) */
    @Override
    public AABB getRenderBoundingBox(GeneratorBlockEntity be)
    {
        return be.renderBox();
    }

    @Override
    public boolean shouldRenderOffScreen(GeneratorBlockEntity be)
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return 96;
    }
}
