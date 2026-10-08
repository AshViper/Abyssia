package com.abyssia.item.scanner;

import com.abyssia.Abyssia;
import com.abyssia.vehicle.client.LidarScannerMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Draws the baked LidarScannerMesh (tools/vehicle_model.py) as the scanner's item model in every display context. */
public final class LidarScannerClient
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/entity/lidar_scanner.png");
    /** The model is 1.285 blocks tall; shrink it into the item cube. */
    private static final float SCALE = 0.72F;

    private static Renderer renderer;

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions()
    {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer()
        {
            if (renderer == null) renderer = new Renderer(Minecraft.getInstance());
            return renderer;
        }
    };

    private LidarScannerClient() {}

    /** Mod-bus registration of the client item extension (1.20: Item#initializeClient). */
    @EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
    public static final class Setup
    {
        private Setup() {}

        @SubscribeEvent
        public static void registerExtensions(RegisterClientExtensionsEvent event)
        {
            event.registerItem(EXTENSIONS, LidarScannerItem.LIDAR_SCANNER.get());
        }
    }

    private static final class Renderer extends BlockEntityWithoutLevelRenderer
    {
        Renderer(Minecraft mc)
        {
            super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                                 int light, int overlay)
        {
            pose.pushPose();
            pose.translate(0.5, 0.5 - LidarScannerMesh.HEIGHT * SCALE / 2.0, 0.5);
            pose.scale(SCALE, SCALE, SCALE);
            VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
            PoseStack.Pose last = pose.last();
            float[] mesh = LidarScannerMesh.HULL;
            for (int q = 0; q + LidarScannerMesh.STRIDE <= mesh.length; q += LidarScannerMesh.STRIDE)
            {
                float nx = mesh[q + 20], ny = mesh[q + 21], nz = mesh[q + 22];
                for (int v = 0; v < 4; v++)
                {
                    int i = q + v * 5;
                    out.addVertex(last, mesh[i], mesh[i + 1], mesh[i + 2]).setColor(255, 255, 255, 255).setUv(mesh[i + 3], mesh[i + 4])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, nx, ny, nz);
                }
            }
            pose.popPose();
        }
    }
}
