package com.abyssia.industry.client;

import com.abyssia.Abyssia;
import com.abyssia.industry.block.IndustryEntityBlock;
import com.abyssia.industry.blockentity.AbyssalExcavatorBlockEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * ORE01: draws a whole excavator from the element models written by tools/bt01/excavator_assets.py (same scheme as
 * the BT01d GeneratorRenderer). Model space = the 3 x 3 x 3 structure with x mirrored (x' = 48 - x), one chunk shifted
 * by -1 block; layers _c cutout (lit), _t translucent, _e emissive (full bright). The auger drill is a separate model
 * (axis through pixel (8, y, 8)) placed on the structure centre and spun about the vertical axis, faster and
 * smoothly spun up while the machine works (LIT).
 */
public class ExcavatorRenderer implements BlockEntityRenderer<AbyssalExcavatorBlockEntity>
{
    private static final String[] TIERS = {"abyssal_excavator", "abyssal_excavator_mk2"};
    private static final String[] LAYERS = {"c", "t", "e"};
    /** drill speed in degrees per tick while the machine works */
    private static final float WORK_SPEED = 18f;

    /** client-only spin state per block entity: angle (deg), speed (deg/tick), last render time (ticks) */
    private static final Map<AbyssalExcavatorBlockEntity, float[]> SPIN = new WeakHashMap<>();

    public ExcavatorRenderer(BlockEntityRendererProvider.Context context) {}

    public static ResourceLocation model(String name)
    {
        return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "block/excavator/" + name);
    }

    /** every additional model to register (ModelEvent.RegisterAdditional) */
    public static List<ResourceLocation> allModels()
    {
        List<ResourceLocation> out = new ArrayList<>();
        for (String tier : TIERS)
            for (String layer : LAYERS)
            {
                out.add(model(tier + "_0_0_0_" + layer));
                out.add(model(tier + "_drill_" + layer));
            }
        return out;
    }

    @Override
    public void render(AbyssalExcavatorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay)
    {
        BlockState state = be.getBlockState();
        Direction forward = state.hasProperty(IndustryEntityBlock.FACING) ? state.getValue(IndustryEntityBlock.FACING) : Direction.NORTH;
        String tier = be.tier().tier >= 2 ? TIERS[1] : TIERS[0];
        boolean working = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);

        // model x axis = left; anchor = centre of structure cell (2, 0, 0) = master - forward + clockwise
        BlockPos offset = BlockPos.ZERO.relative(forward, -1).relative(forward.getClockWise(), 1);

        pose.pushPose();
        pose.translate(offset.getX() + 0.5, offset.getY() + 0.5, offset.getZ() + 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-forward.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);

        pose.pushPose();
        pose.translate(1, 1, 1);
        drawLayers(pose, buffers, tier + "_0_0_0_", light, overlay);
        pose.popPose();

        // the drill: structure centre, axis through pixel (8, ., 8) of its models
        pose.pushPose();
        pose.translate(1.5, 0, 1.5);
        pose.mulPose(Axis.YP.rotationDegrees(spin(be, working, partialTick)));
        pose.translate(-0.5, 0, -0.5);
        drawLayers(pose, buffers, tier + "_drill_", light, overlay);
        pose.popPose();

        pose.popPose();
    }

    /** drill angle in degrees; spins up / down over about a second */
    private static float spin(AbyssalExcavatorBlockEntity be, boolean working, float partialTick)
    {
        if (be.getLevel() == null) return 0f;
        float now = be.getLevel().getGameTime() + partialTick;
        float[] s = SPIN.computeIfAbsent(be, k -> new float[]{0f, 0f, now});
        float dt = Math.max(0f, Math.min(5f, now - s[2]));
        s[2] = now;
        s[1] += ((working ? WORK_SPEED : 0f) - s[1]) * Math.min(1f, 0.05f * dt);
        if (Math.abs(s[1]) < 0.01f) s[1] = 0f;
        s[0] = (s[0] + s[1] * dt) % 360f;
        return s[0];
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
        BakedModel model = models.getModel(id);
        if (model == models.getMissingModel()) return;
        ModelBlockRenderer renderer = mc.getBlockRenderer().getModelRenderer();
        renderer.renderModel(pose.last(), buffers.getBuffer(type), null, model, 1.0f, 1.0f, 1.0f, light, overlay, ModelData.EMPTY, type);
    }

    @Override
    public boolean shouldRenderOffScreen(AbyssalExcavatorBlockEntity be)
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return 96;
    }
}
