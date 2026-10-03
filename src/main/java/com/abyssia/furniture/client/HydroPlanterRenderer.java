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
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.function.Function;

/**
 * Draws the four planter cells (PL02): each crop is a cross of two double-sided quads (like a vanilla crop) of
 * 8 x 8 px standing on the slab top inside its quarter, textured block/planter_&lt;crop&gt;_&lt;stage&gt; (block atlas,
 * cutout). Nothing is drawn for empty cells.
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
            if (vc == null)
            {
                vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
                atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
            }
            TextureAtlasSprite sprite = atlas.apply(TEXTURES[crop.ordinal()][be.stage(cell)]);
            float x0 = (cell & 1) * SIZE, z0 = (cell >> 1) * SIZE;
            PoseStack.Pose last = poseStack.last();
            Matrix4f pose = last.pose();
            // two diagonals of the cell square
            plane(vc, pose, last, sprite, light, x0, z0, x0 + SIZE, z0 + SIZE);
            plane(vc, pose, last, sprite, light, x0, z0 + SIZE, x0 + SIZE, z0);
        }
    }

    /** One vertical quad from (ax, az) to (bx, bz), drawn on both sides. */
    private static void plane(VertexConsumer vc, Matrix4f pose, PoseStack.Pose normal, TextureAtlasSprite s, int light,
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

    private static void vertex(VertexConsumer vc, Matrix4f pose, PoseStack.Pose normal, float x, float y, float z,
                               float u, float v, int light, float nx, float nz)
    {
        vc.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(normal, nx, 1.0f, nz);
    }
}
