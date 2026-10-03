package com.abyssia.furniture.client;

import com.abyssia.Abyssia;
import com.abyssia.furniture.HydroPlanterBlockEntity;
import com.abyssia.furniture.PlanterCrop;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.client.model.data.ModelData;
import com.mojang.math.Axis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.function.Function;

/**
 * Draws the four planter cells (PL02): each crop is a cross of two double-sided quads (like a vanilla crop) of
 * 8 x 8 px standing on the slab top inside its quarter, textured block/planter_&lt;crop&gt;_&lt;stage&gt; (block atlas,
 * cutout). Nothing is drawn for empty cells.
 * <p>
 * GENERIC cells (any edible plant) draw the plant block of the planted item at half size, its "age" set from the stage
 * (and "berries" when ripe); an item without a plant block is drawn as a floating item that grows with the stage.
 */
public class HydroPlanterRenderer implements BlockEntityRenderer<HydroPlanterBlockEntity>
{
    private static final float TOP = 0.5f;     // slab height
    private static final float SIZE = 0.5f;    // cell size and plant height
    private static final ResourceLocation[][] TEXTURES = new ResourceLocation[PlanterCrop.values().length][3];

    static
    {
        for (PlanterCrop crop : PlanterCrop.values())
            for (int s = 0; s < 3; s++)
                TEXTURES[crop.ordinal()][s] = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID,
                        "block/planter_" + crop.getSerializedName() + "_" + s);
    }

    public HydroPlanterRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HydroPlanterBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay)
    {
        VertexConsumer vc = null;
        Function<ResourceLocation, TextureAtlasSprite> atlas = null;
        for (int cell = 0; cell < HydroPlanterBlockEntity.CELLS; cell++)
        {
            PlanterCrop crop = be.crop(cell);
            if (crop == PlanterCrop.NONE) continue;
            if (crop == PlanterCrop.GENERIC)
            {
                renderGeneric(be, cell, poseStack, buffers, light, overlay);
                continue;
            }
            if (vc == null)
            {
                vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
                atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
            }
            TextureAtlasSprite sprite = atlas.apply(TEXTURES[crop.ordinal()][be.stage(cell)]);
            float x0 = (cell & 1) * SIZE, z0 = (cell >> 1) * SIZE;
            Matrix4f pose = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            // two diagonals of the cell square
            plane(vc, pose, normal, sprite, light, x0, z0, x0 + SIZE, z0 + SIZE);
            plane(vc, pose, normal, sprite, light, x0, z0 + SIZE, x0 + SIZE, z0);
        }
    }

    private static void renderGeneric(HydroPlanterBlockEntity be, int cell, PoseStack poseStack, MultiBufferSource buffers,
                                      int light, int overlay)
    {
        Item item = be.plantedItem(cell);
        if (item == null) return;
        int stage = be.stage(cell);
        float x0 = (cell & 1) * SIZE, z0 = (cell >> 1) * SIZE;
        Minecraft mc = Minecraft.getInstance();
        if (item instanceof BlockItem bi && bi.getBlock().defaultBlockState().getRenderShape() == RenderShape.MODEL)
        {
            BlockState state = grown(bi.getBlock().defaultBlockState(), stage);
            poseStack.pushPose();
            poseStack.translate(x0, TOP, z0);
            poseStack.scale(SIZE, SIZE, SIZE);
            mc.getBlockRenderer().renderSingleBlock(state, poseStack, buffers, light, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, RenderType.cutout());
            poseStack.popPose();
            return;
        }
        float scale = 0.3f + 0.15f * stage;
        poseStack.pushPose();
        poseStack.translate(x0 + SIZE / 2, TOP + 0.05f + scale * 0.25f, z0 + SIZE / 2);
        poseStack.mulPose(Axis.YP.rotationDegrees(cell * 90 + 45));
        poseStack.scale(scale, scale, scale);
        mc.getItemRenderer().renderStatic(new ItemStack(item), ItemDisplayContext.FIXED, light, overlay, poseStack, buffers,
                be.getLevel(), (int) be.getBlockPos().asLong() + cell);
        poseStack.popPose();
    }

    /** The plant state for a stage: "age" from 0 to its maximum, "berries" when ripe. */
    private static BlockState grown(BlockState state, int stage)
    {
        for (Property<?> p : state.getProperties())
        {
            if (p instanceof IntegerProperty age && p.getName().equals("age"))
            {
                int min = age.getPossibleValues().stream().min(Integer::compare).orElse(0);
                int max = age.getPossibleValues().stream().max(Integer::compare).orElse(0);
                state = state.setValue(age, min + (max - min) * stage / 2);
            }
            else if (p instanceof BooleanProperty b && p.getName().equals("berries")) state = state.setValue(b, stage == 2);
        }
        return state;
    }

    /** One vertical quad from (ax, az) to (bx, bz), drawn on both sides. */
    private static void plane(VertexConsumer vc, Matrix4f pose, Matrix3f normal, TextureAtlasSprite s, int light,
                              float ax, float az, float bx, float bz)
    {
        float y0 = TOP, y1 = TOP + SIZE;
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        float nx = 0, nz = 0; // up normal on both sides: evenly lit like a vanilla cross model
        vertex(vc, pose, normal, ax, y0, az, u0, v1, light, nx, nz);
        vertex(vc, pose, normal, bx, y0, bz, u1, v1, light, nx, nz);
        vertex(vc, pose, normal, bx, y1, bz, u1, v0, light, nx, nz);
        vertex(vc, pose, normal, ax, y1, az, u0, v0, light, nx, nz);
        // back side
        vertex(vc, pose, normal, ax, y1, az, u0, v0, light, nx, nz);
        vertex(vc, pose, normal, bx, y1, bz, u1, v0, light, nx, nz);
        vertex(vc, pose, normal, bx, y0, bz, u1, v1, light, nx, nz);
        vertex(vc, pose, normal, ax, y0, az, u0, v1, light, nx, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float x, float y, float z,
                               float u, float v, int light, float nx, float nz)
    {
        vc.vertex(pose, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(normal, nx, 1.0f, nz).endVertex();
    }
}
