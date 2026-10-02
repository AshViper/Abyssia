package com.abyssia.item.electric;

import com.abyssia.Abyssia;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of the propulsion screw's two-handed grip. Third person: a custom two-handed ArmPose that raises both
 * arms forward; while thrusting in water the whole model is laid flat like vanilla swimming (rotated at render time,
 * the real pose / hitbox stays standing) with the arms stretched ahead along the body and the head facing the travel
 * direction. First person: while the screw is in the main hand the vanilla hand rendering (both hands) is cancelled
 * and the screw plus both arms are drawn centred in front of the player.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PropulsionScrewClient
{
    /** Both arms are posed here whichever arm the pose is attached to (two-handed: the last-posed arm wins). */
    public static final HumanoidModel.ArmPose POSE = HumanoidModel.ArmPose.create("ABYSSIA_PROPULSION_SCREW", true,
            (model, entity, arm) ->
            {
                if (thrusting(entity))
                {
                    // body is horizontal (see renderPlayerPre): arms along the body = overhead in model space
                    model.head.xRot = -1.35F;
                    model.rightArm.xRot = -2.95F;
                    model.leftArm.xRot = -2.95F;
                    model.rightArm.yRot = -0.18F;
                    model.leftArm.yRot = 0.18F;
                    model.rightArm.zRot = 0.0F;
                    model.leftArm.zRot = 0.0F;
                    return;
                }
                float pitch = -1.45F + model.head.xRot;
                model.rightArm.xRot = pitch;
                model.leftArm.xRot = pitch;
                model.rightArm.yRot = -0.2F + model.head.yRot;
                model.leftArm.yRot = 0.2F + model.head.yRot;
                model.rightArm.zRot = 0.0F;
                model.leftArm.zRot = 0.0F;
            });

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions()
    {
        @Override
        public @Nullable HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack)
        {
            return hand == InteractionHand.MAIN_HAND ? POSE : null;
        }
    };

    private PropulsionScrewClient() {}

    /** Thrusting = using the screw with the eyes in water. */
    public static boolean thrusting(LivingEntity entity)
    {
        return entity.isUsingItem() && entity.getUseItem().getItem() instanceof PropulsionScrewItem && entity.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
    }

    /** Lays the model flat like vanilla swimming (PlayerRenderer.setupRotations) while thrusting. */
    @SubscribeEvent
    public static void renderPlayerPre(RenderPlayerEvent.Pre event)
    {
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();
        if (!thrusting(player) || player.isVisuallySwimming()) return;
        float partial = event.getPartialTick();
        float bodyYaw = net.minecraft.util.Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float pitch = net.minecraft.util.Mth.lerp(partial, player.xRotO, player.getXRot());
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F - pitch));
        pose.translate(0.0F, -1.0F, 0.3F);
        pose.mulPose(Axis.YP.rotationDegrees(bodyYaw - 180.0F));
        LAID_FLAT.add(player.getId());
    }

    @SubscribeEvent
    public static void renderPlayerPost(RenderPlayerEvent.Post event)
    {
        if (LAID_FLAT.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    private static final java.util.Set<Integer> LAID_FLAT = new java.util.HashSet<>();

    private static boolean holdsScrew(Minecraft mc)
    {
        return mc.player != null && mc.player.getMainHandItem().getItem() instanceof PropulsionScrewItem;
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event)
    {
        Minecraft mc = Minecraft.getInstance();
        if (!holdsScrew(mc)) return;
        event.setCanceled(true);                                  // vanilla draws neither hand nor the offhand item
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        AbstractClientPlayer player = mc.player;
        PoseStack pose = event.getPoseStack();
        float equip = event.getEquipProgress();
        int light = event.getPackedLight();
        boolean leftHanded = player.getMainArm() == HumanoidArm.LEFT;

        pose.pushPose();
        pose.translate(0.0, -0.6 * equip, 0.0);

        pose.pushPose();                                          // the screw, centred lower-middle
        pose.translate(0.0, -0.27, -1.1);
        pose.scale(0.7F, 0.7F, 0.7F);
        Minecraft.getInstance().getItemRenderer().renderStatic(event.getItemStack(),
                leftHanded ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                light, OverlayTexture.NO_OVERLAY, pose, event.getMultiBufferSource(), player.level(), 0);
        pose.popPose();

        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        // The arm part sits at x = -+5/16, y = 2/16 and points down (+Y): raise it forward and slightly inward so the
        // hands meet the side grips (values tuned from in-game screenshots).
        pose.pushPose();
        pose.translate(0.5, -0.74, -0.25);
        pose.mulPose(Axis.YP.rotationDegrees(-8.0F));
        pose.mulPose(Axis.XP.rotationDegrees(-62.0F));
        renderer.renderRightHand(pose, event.getMultiBufferSource(), light, player);
        pose.popPose();
        pose.pushPose();
        pose.translate(-0.5, -0.74, -0.25);
        pose.mulPose(Axis.YP.rotationDegrees(8.0F));
        pose.mulPose(Axis.XP.rotationDegrees(-62.0F));
        renderer.renderLeftHand(pose, event.getMultiBufferSource(), light, player);
        pose.popPose();

        pose.popPose();
    }
}
