package com.abyssia.furniture.client;

import com.abyssia.furniture.LargeLockerBlock;
import com.abyssia.furniture.LargeLockerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * The large locker's name plate: the custom name centred on the 2 x 2 front, in the middle of the top row (rendered
 * from the bottom-left base), on a dark backing so it reads from outside. Long names shrink to fit the front.
 */
public class LargeLockerRenderer implements BlockEntityRenderer<LargeLockerBlockEntity>
{
    /** text height ~ 3/16 block */
    private static final float SCALE = 0.02f;
    /** widest the plate may get, in blocks (the front is 2 wide) */
    private static final float MAX_WIDTH = 1.75f;
    private static final int TEXT = 0xFFB8E6EE;
    private static final int BACKING = 0xC00B141B;

    private final Font font;

    public LargeLockerRenderer(BlockEntityRendererProvider.Context context)
    {
        font = context.getFont();
    }

    @Override
    public void render(LargeLockerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay)
    {
        Component name = be.getCustomName();
        BlockState state = be.getBlockState();
        if (name == null || !state.hasProperty(LargeLockerBlock.FACING)) return;
        Direction facing = state.getValue(LargeLockerBlock.FACING);
        Direction right = facing.getCounterClockWise();
        FormattedCharSequence text = name.getVisualOrderText();
        int width = font.width(text);
        if (width == 0) return;
        float scale = Math.min(SCALE, MAX_WIDTH / width);

        pose.pushPose();
        // centre of the front's top row: half a block to the right, one and a half up, on the front face
        pose.translate(0.5 + right.getStepX() * 0.5 + facing.getStepX() * 0.502, 1.5,
                0.5 + right.getStepZ() * 0.5 + facing.getStepZ() * 0.502);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.scale(scale, -scale, scale);
        // own backing quad: Font's background quad lands in front of the glyphs in this pose and hides them at an angle
        Matrix4f m = pose.last().pose();
        VertexConsumer quad = buffers.getBuffer(RenderType.gui());
        float x0 = -width / 2f - 1, x1 = width / 2f + 1;
        int a = BACKING >>> 24, r = (BACKING >> 16) & 255, g = (BACKING >> 8) & 255, b = BACKING & 255;
        // both windings: gui() culls back faces
        quad.vertex(m, x0, -5f, 0).color(r, g, b, a).endVertex();
        quad.vertex(m, x0, 5f, 0).color(r, g, b, a).endVertex();
        quad.vertex(m, x1, 5f, 0).color(r, g, b, a).endVertex();
        quad.vertex(m, x1, -5f, 0).color(r, g, b, a).endVertex();
        quad.vertex(m, x1, -5f, 0).color(r, g, b, a).endVertex();
        quad.vertex(m, x1, 5f, 0).color(r, g, b, a).endVertex();
        quad.vertex(m, x0, 5f, 0).color(r, g, b, a).endVertex();
        quad.vertex(m, x0, -5f, 0).color(r, g, b, a).endVertex();
        pose.translate(0, 0, 0.0004f / scale); // text just in front of the backing
        font.drawInBatch(text, -width / 2f, -4f, TEXT, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL,
                0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
